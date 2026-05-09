// M3U8 嗅探脚本 - rou模式 (rouva4.xyz 专用)
// 特点: 自动点击播放按钮 + 广告拦截 + 伪装URL识别

(function() {
    'use strict';

    const TAG = 'Rou';
    const C = window.__M3u8Common;
    if (!C) { console.error('[M3U8Sniffer] Common module not loaded'); return; }

    const DURATION = {
        MIN_SECONDS: 90,
        RETRY_INTERVAL_MS: 1000,
        INIT_DELAY_MS: 2000,
        URL_CHECK_MS: 500,
        AD_WAIT_MS: 5000
    };

    const RETRY = { TIMES: 3 };

    const SELECTORS = {
        PLAY_SVG_PATH: 'svg path[d*="M8 5v14l11-7z"]'
    };

    // rou 模式额外状态
    const rouState = {
        videoCounter: 0,
        originalDomain: window.location.hostname,
        originalURL: window.location.href,
        allowOpenOnce: false,
        isJumpingBack: false,
        playlistVariants: new Map()
    };

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // ==================== rou 专用 URL 处理（支持伪装 URL + 短时长过滤） ====================
    async function handleRouUrl(url, options = {}) {
        if (!url) return;
        const urlStr = url.toString();
        const responseText = options.responseText || null;
        C.buildRequestHeaders(urlStr, options.requestHeaders || {});

        C.log(TAG, '检查URL: ' + urlStr.substring(0, 80));

        const isM3u8 = urlStr.includes('.m3u8');
        const isIndexJpg = urlStr.includes('index.jpg') || urlStr.includes('index.m3u8');
        const shouldCapture = isM3u8 || isIndexJpg;

        if (!shouldCapture || C.state.capturedUrls.has(urlStr) || C.state.pendingUrls.has(urlStr)) {
            return;
        }

        C.state.pendingUrls.add(urlStr);
        C.log(TAG, '开始处理: ' + urlStr.substring(0, 60));

        try {
            let m3u8Content = responseText;
            if (!m3u8Content) {
                m3u8Content = await C.fetchM3u8Content(urlStr);
            }

            const variants = C.parseM3u8Variants(m3u8Content, urlStr);

            if (variants && variants.length > 0) {
                rouState.playlistVariants.set(urlStr, variants);
                C.state.capturedUrls.add(urlStr);
                rouState.videoCounter++;
                const variantDuration = await C.resolveVariantDuration(variants, TAG);
                C.reportVariants(urlStr, variants, C.state.requestHeaders.get(urlStr) || {}, variantDuration);
                C.log(TAG, '找到播放列表: ' + urlStr.substring(0, 50) + '... (' + variants.length + '个分辨率)');
                return;
            }

            const duration = C.parseM3u8Duration(m3u8Content);
            const isShort = duration > 0 && duration < DURATION.MIN_SECONDS;
            const isZero = duration === 0;

            if (isZero || isShort) {
                C.log(TAG, '过滤链接: ' + urlStr.substring(0, 50) + '... (时长: ' + duration + 's)');
                return;
            }

            C.state.capturedUrls.add(urlStr);
            rouState.videoCounter++;

            C.reportM3u8(urlStr, duration, C.state.requestHeaders.get(urlStr) || {});
            C.log(TAG, '捕获链接: ' + urlStr.substring(0, 50) + '... (时长: ' + duration + 's)');
        } finally {
            C.state.pendingUrls.delete(urlStr);
        }
    }

    // ==================== 广告拦截 ====================
    function blockAdRedirects() {
        const originalOpen = window.open;
        window.open = (url) => {
            if (rouState.allowOpenOnce) {
                rouState.allowOpenOnce = false;
                return originalOpen.call(window, url, '_blank', 'noopener,noreferrer');
            }
            C.log(TAG, '拦截 window.open: ' + url);
            return null;
        };

        setInterval(() => {
            if (rouState.isJumpingBack) return;
            if (window.location.hostname !== rouState.originalDomain) {
                C.log(TAG, '检测到跨域跳转: ' + window.location.href);
                rouState.isJumpingBack = true;
                window.history.back();
                setTimeout(() => {
                    if (window.location.hostname !== rouState.originalDomain) {
                        window.location.href = rouState.originalURL;
                    }
                    rouState.isJumpingBack = false;
                }, 100);
            }
        }, DURATION.URL_CHECK_MS);

        const originalWrite = document.write;
        document.write = function(content) {
            if (content && (content.includes('<iframe') || content.includes('window.location') || content.includes('document.location'))) {
                C.log(TAG, '拦截 document.write (广告内容)');
                return;
            }
            return originalWrite.apply(document, arguments);
        };

        document.addEventListener('click', (e) => {
            let target = e.target;
            while (target && target.tagName !== 'A') target = target.parentElement;
            if (target && target.tagName === 'A') {
                const href = target.getAttribute('href');
                if (href && !href.startsWith('javascript:')) {
                    try {
                        const targetDomain = new URL(href, window.location.href).hostname;
                        if (targetDomain !== rouState.originalDomain) {
                            C.log(TAG, '拦截广告链接点击: ' + href);
                            e.preventDefault();
                            e.stopPropagation();
                        }
                    } catch {}
                }
            }
        }, true);
    }

    // ==================== rou 专用网络拦截（支持伪装 URL） ====================
    function interceptRouNetwork() {
        const originalXHRSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
            if (!this._m3u8RequestHeaders) this._m3u8RequestHeaders = {};
            this._m3u8RequestHeaders[key] = value;
            return originalXHRSetRequestHeader.apply(this, arguments);
        };

        const originalXHROpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.addEventListener('load', () => {
                const responseText = typeof this.responseText === 'string' ? this.responseText : null;
                handleRouUrl(url, {
                    responseText,
                    requestHeaders: this._m3u8RequestHeaders || {}
                });
            });
            return originalXHROpen.apply(this, arguments);
        };

        const originalFetch = window.fetch;
        window.fetch = function(url, options) {
            const requestHeaders = C.extractFetchHeaders(options?.headers);
            return originalFetch.apply(this, arguments).then(response => {
                const urlStr = url?.toString?.() || '';
                if (urlStr.includes('.m3u8') || urlStr.includes('index.jpg') || urlStr.includes('index.m3u8')) {
                    response.clone().text()
                        .then(text => handleRouUrl(url, { responseText: text, requestHeaders }))
                        .catch(() => handleRouUrl(url, { requestHeaders }));
                }
                return response;
            });
        };
    }

    // ==================== 播放按钮点击 ====================
    async function clickPlayButton() {
        C.log(TAG, '正在查找播放按钮...');

        for (let retry = 0; retry < RETRY.TIMES; retry++) {
            for (const path of document.querySelectorAll(SELECTORS.PLAY_SVG_PATH)) {
                const svg = path.closest('svg');
                if (!svg) continue;

                for (const parent of [svg.parentElement, svg.parentElement?.parentElement]) {
                    if (!parent) continue;
                    const style = window.getComputedStyle(parent);
                    if (style.borderRadius === '50%' || style.cursor === 'pointer' || parent.tagName === 'BUTTON') {
                        parent.click();
                        C.log(TAG, '已点击播放按钮 (' + parent.tagName + ')');
                        return true;
                    }
                }
            }

            for (const div of document.querySelectorAll('div')) {
                const style = window.getComputedStyle(div);
                if (style.borderRadius === '50%' && style.display === 'flex' &&
                    div.querySelector(SELECTORS.PLAY_SVG_PATH)) {
                    div.click();
                    C.log(TAG, '已点击播放按钮 (圆形div)');
                    return true;
                }
            }

            if (retry < RETRY.TIMES - 1) await sleep(DURATION.RETRY_INTERVAL_MS);
        }

        C.log(TAG, '未找到播放按钮，请手动点击播放');
        return false;
    }

    // ==================== 跳过广告 ====================
    async function clickSkipAdButton() {
        C.log(TAG, '等待广告播放...');
        await sleep(DURATION.AD_WAIT_MS);

        const skipPatterns = ['跳過廣告', '跳过广告', '跳', 'Skip', 'skip', '关闭广告', '廣告'];

        for (let retry = 0; retry < RETRY.TIMES; retry++) {
            for (const pattern of skipPatterns) {
                try {
                    const result = document.evaluate(
                        `//button[contains(text(), '${pattern}')]`,
                        document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null
                    );
                    const button = result.singleNodeValue;
                    if (button && button.offsetParent !== null) {
                        button.click();
                        C.log(TAG, '已点击跳过广告按钮: ' + (button.textContent || '').substring(0, 30));
                        return true;
                    }
                } catch {}
            }

            for (const el of document.querySelectorAll('button, div[role="button"], span[role="button"], a')) {
                const text = (el.textContent || '').trim();
                if (skipPatterns.some(p => text.includes(p)) && el.offsetParent !== null) {
                    el.click();
                    C.log(TAG, '已点击跳过广告按钮: ' + text.substring(0, 30));
                    return true;
                }
            }

            if (retry < RETRY.TIMES - 1) await sleep(1000);
        }

        C.log(TAG, '未找到跳过广告按钮，请手动点击');
        return false;
    }

    // ==================== 初始化 ====================
    function init() {
        C.log(TAG, 'Script loaded (Rou Mode v2.0)');
        if (C.hasBridge()) {
            try { window['SnifferBridge'].onScriptLoaded('Rou'); } catch (e) {}
        }

        blockAdRedirects();
        interceptRouNetwork();

        setTimeout(async () => {
            const playClicked = await clickPlayButton();
            if (playClicked) {
                await clickSkipAdButton();
            }
        }, DURATION.INIT_DELAY_MS);

        setTimeout(() => {
            document.querySelectorAll('video').forEach(video => {
                const src = video.src || video.currentSrc;
                if (src) handleRouUrl(src, {});
            });
        }, 3000);
    }

    init();
})();
