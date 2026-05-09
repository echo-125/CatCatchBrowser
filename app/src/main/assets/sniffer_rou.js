// M3U8 嗅探脚本 - rou模式 (rouva4.xyz 专用)
// 基于 cat-catch-assistant/m3u8_sniffer_userscript.js 重写
// 特点: 自动点击播放按钮 + 广告拦截 + 多分辨率选择 + 伪装URL识别

(function() {
    'use strict';

    const SNIFFER_BRIDGE = 'SnifferBridge';

    // ==================== 常量定义 ====================
    const SELECTORS = {
        PLAY_SVG_PATH: 'svg path[d*="M8 5v14l11-7z"]',
        VIDEO: 'video',
        IFRAME: 'iframe',
        SCRIPT: 'script',
        TITLE: 'h1, title, [class*="title"], [class*="video-title"]'
    };

    const DURATION = {
        MIN_SECONDS: 90,
        RETRY_INTERVAL_MS: 1000,
        INIT_DELAY_MS: 2000,
        FETCH_TIMEOUT_MS: 10000,
        MAX_LOG_ITEMS: 60,
        URL_CHECK_MS: 500,
        AD_WAIT_MS: 5000
    };

    const RETRY = { TIMES: 3 };

    const FILTER = {
        AD_KEYWORDS: ['ad', 'ads', 'adv', 'advertisement', 'silent-basis'],
        TARGET_MARKERS: ['index.jpg', 'index.m3u8']
    };

    // ==================== 状态管理 ====================
    const state = {
        capturedUrls: new Set(),
        pendingUrls: new Set(),
        durationCache: new Map(),
        requestHeaders: new Map(),
        playlistVariants: new Map(),
        videoCounter: 0,
        originalDomain: window.location.hostname,
        originalURL: window.location.href,
        allowOpenOnce: false,
        isJumpingBack: false
    };

    // ==================== 工具函数 ====================
    function hasBridge() {
        return typeof window[SNIFFER_BRIDGE] !== 'undefined';
    }

    const log = (msg) => {
        console.log('[M3U8Sniffer-Rou] ' + msg);
        if (hasBridge()) {
            try { window[SNIFFER_BRIDGE].log(msg); } catch (e) {}
        }
    };

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // ==================== Bridge 上报 ====================
    function reportM3u8(url, duration, headers) {
        if (!hasBridge() || state.capturedUrls.has(url)) return;
        state.capturedUrls.add(url);

        try {
            const title = document.title || '';
            const headersJson = headers ? JSON.stringify(headers) : '';
            window[SNIFFER_BRIDGE].reportM3u8(url, title, duration || 0, headersJson);
            log('Captured: ' + url.substring(0, 60) + '...');
        } catch (e) {
            console.error('[M3U8Sniffer] Report error:', e);
        }
    }

    function reportVariants(url, variants, headers) {
        if (!hasBridge()) return;

        try {
            const variantsJson = JSON.stringify(variants);
            const headersJson = headers ? JSON.stringify(headers) : '';
            window[SNIFFER_BRIDGE].reportPlaylistVariants(url, variantsJson, headersJson);
            log('Playlist variants: ' + variants.length + ' resolutions');
        } catch (e) {
            console.error('[M3U8Sniffer] Report variants error:', e);
        }
    }

    // ==================== URL 判断 ====================
    function isAdUrl(url) {
        if (!url) return false;
        const lower = url.toString().toLowerCase();
        return FILTER.AD_KEYWORDS.some(kw => lower.includes(kw));
    }

    function isTargetUrl(url) {
        if (!url) return false;
        const lower = url.toString().toLowerCase();
        return FILTER.TARGET_MARKERS.some(m => lower.includes(m));
    }

    // ==================== M3U8 解析 ====================
    function parseM3u8Duration(content) {
        if (!content || typeof content !== 'string') return 0;
        let totalDuration = 0;

        for (const line of content.split('\n')) {
            const match = line.trim().match(/#EXTINF:([\d.]+)/);
            if (match) totalDuration += parseFloat(match[1]);
        }

        return totalDuration;
    }

    function parseM3u8Variants(content, baseUrl) {
        if (!content || typeof content !== 'string') return null;
        if (!content.includes('#EXT-X-STREAM-INF')) return null;

        const variants = [];
        const lines = content.split('\n');

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();

            if (line.startsWith('#EXT-X-STREAM-INF:')) {
                const variant = {
                    url: null,
                    bandwidth: 0,
                    resolution: null,
                    codecs: null,
                    frameRate: null
                };

                const bandwidthMatch = line.match(/BANDWIDTH=(\d+)/);
                if (bandwidthMatch) variant.bandwidth = parseInt(bandwidthMatch[1]);

                const resolutionMatch = line.match(/RESOLUTION=([\d]+x[\d]+)/);
                if (resolutionMatch) variant.resolution = resolutionMatch[1];

                const codecsMatch = line.match(/CODECS="([^"]+)"/);
                if (codecsMatch) variant.codecs = codecsMatch[1];

                const frameRateMatch = line.match(/FRAME-RATE=([\d.]+)/);
                if (frameRateMatch) variant.frameRate = parseFloat(frameRateMatch[1]);

                if (i + 1 < lines.length) {
                    const nextLine = lines[i + 1].trim();
                    if (nextLine && !nextLine.startsWith('#')) {
                        if (nextLine.startsWith('http')) {
                            variant.url = nextLine;
                        } else {
                            const base = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
                            variant.url = base + nextLine;
                        }
                    }
                }

                if (variant.url) variants.push(variant);
            }
        }

        return variants.length > 0 ? variants : null;
    }

    // ==================== 请求头管理 ====================
    function extractFetchHeaders(inputHeaders) {
        if (!inputHeaders) return {};

        if (inputHeaders instanceof Headers) {
            return Object.fromEntries(inputHeaders.entries());
        }

        if (Array.isArray(inputHeaders)) {
            return Object.fromEntries(inputHeaders);
        }

        if (typeof inputHeaders === 'object') {
            return { ...inputHeaders };
        }

        return {};
    }

    function buildRequestHeaders(url, headers = {}) {
        const normalized = {};
        for (const [key, value] of Object.entries(headers || {})) {
            if (key && value) {
                normalized[String(key).toLowerCase()] = String(value);
            }
        }

        const pageUrl = window.location.href;
        const pageOrigin = window.location.origin;
        if (!normalized.origin) normalized.origin = pageOrigin;
        if (!normalized.referer) normalized.referer = pageUrl;

        state.requestHeaders.set(url, normalized);
        return normalized;
    }

    // ==================== 时长获取 ====================
    async function getM3U8Duration(url, content = null) {
        if (content) {
            const parsedDuration = parseM3u8Duration(content);
            if (parsedDuration > 0) {
                state.durationCache.set(url, parsedDuration);
            }
            return parsedDuration;
        }

        if (state.durationCache.has(url)) {
            return state.durationCache.get(url);
        }

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), DURATION.FETCH_TIMEOUT_MS);

        try {
            const response = await fetch(url, { signal: controller.signal });
            const respContent = await response.text();
            const totalDuration = parseM3u8Duration(respContent);
            state.durationCache.set(url, totalDuration);
            return totalDuration;
        } catch (error) {
            log('获取时长失败: ' + error.message);
            return 0;
        } finally {
            clearTimeout(timeoutId);
        }
    }

    // ==================== 核心处理 ====================
    async function handleUrl(url, options = {}) {
        if (!url) return;
        const urlStr = url.toString();
        const responseText = options.responseText || null;
        buildRequestHeaders(urlStr, options.requestHeaders || {});

        log('检查URL: ' + urlStr.substring(0, 80));

        // 匹配规则：.m3u8 或伪装成 index.jpg / index.m3u8 的资源
        const isM3u8 = urlStr.includes('.m3u8');
        const isIndexJpg = urlStr.includes('index.jpg') || urlStr.includes('index.m3u8');
        const shouldCapture = isM3u8 || isIndexJpg;

        if (!shouldCapture || state.capturedUrls.has(urlStr) || state.pendingUrls.has(urlStr)) {
            if (!shouldCapture) {
                log('跳过非目标URL');
            }
            return;
        }

        state.pendingUrls.add(urlStr);
        log('开始处理: ' + urlStr.substring(0, 60));

        try {
            // 获取 m3u8 内容
            let m3u8Content = responseText;
            if (!m3u8Content) {
                try {
                    const controller = new AbortController();
                    const timeoutId = setTimeout(() => controller.abort(), DURATION.FETCH_TIMEOUT_MS);
                    const response = await fetch(urlStr, { signal: controller.signal });
                    m3u8Content = await response.text();
                    clearTimeout(timeoutId);
                } catch (e) {
                    log('获取M3U8内容失败: ' + e.message);
                }
            }

            // 检查是否是 master playlist（多分辨率）
            const variants = parseM3u8Variants(m3u8Content, urlStr);

            if (variants && variants.length > 0) {
                // 是 master playlist，上报变体信息
                state.playlistVariants.set(urlStr, variants);
                state.capturedUrls.add(urlStr);
                state.videoCounter++;
                reportVariants(urlStr, variants, state.requestHeaders.get(urlStr) || {});
                log('找到播放列表: ' + urlStr.substring(0, 50) + '... (' + variants.length + '个分辨率)');
                return;
            }

            // 普通 m3u8，计算时长
            const duration = await getM3U8Duration(urlStr, m3u8Content);
            const isShort = duration > 0 && duration < DURATION.MIN_SECONDS;
            const isZero = duration === 0;

            if (isZero || isShort) {
                log('过滤链接: ' + urlStr.substring(0, 50) + '... (时长: ' + duration + 's)');
                return;
            }

            state.capturedUrls.add(urlStr);

            if (urlStr.includes('.m3u8')) {
                state.videoCounter++;
            }

            if (isTargetUrl(urlStr)) {
                reportM3u8(urlStr, duration, state.requestHeaders.get(urlStr) || {});
                log('找到目标链接: ' + urlStr.substring(0, 50) + '... (时长: ' + duration + 's)');
            } else if (!isAdUrl(urlStr)) {
                reportM3u8(urlStr, duration, state.requestHeaders.get(urlStr) || {});
                log('捕获链接: ' + urlStr.substring(0, 50) + '... (时长: ' + duration + 's)');
            }
        } finally {
            state.pendingUrls.delete(urlStr);
        }
    }

    // ==================== 广告拦截 ====================
    function blockAdRedirects() {
        // 拦截 window.open
        const originalOpen = window.open;
        window.open = (url) => {
            if (state.allowOpenOnce) {
                state.allowOpenOnce = false;
                return originalOpen.call(window, url, '_blank', 'noopener,noreferrer');
            }
            log('拦截 window.open: ' + url);
            return null;
        };

        // URL 变化监控 - 检测跨域广告跳转
        setInterval(() => {
            if (state.isJumpingBack) return;
            if (window.location.hostname !== state.originalDomain) {
                log('检测到跨域跳转: ' + window.location.href);
                state.isJumpingBack = true;
                window.history.back();
                setTimeout(() => {
                    // 如果 back() 没有回到原域名，强制跳回
                    if (window.location.hostname !== state.originalDomain) {
                        window.location.href = state.originalURL;
                    }
                    state.isJumpingBack = false;
                }, 100);
            }
        }, DURATION.URL_CHECK_MS);

        // 拦截 document.write（防止广告注入）
        const originalWrite = document.write;
        document.write = function(content) {
            if (content && (content.includes('<iframe') || content.includes('window.location') || content.includes('document.location'))) {
                log('拦截 document.write (广告内容)');
                return;
            }
            return originalWrite.apply(document, arguments);
        };

        // 拦截广告链接点击（跨域链接）
        document.addEventListener('click', (e) => {
            let target = e.target;
            while (target && target.tagName !== 'A') target = target.parentElement;
            if (target && target.tagName === 'A') {
                const href = target.getAttribute('href');
                if (href && !href.startsWith('javascript:')) {
                    try {
                        const targetDomain = new URL(href, window.location.href).hostname;
                        if (targetDomain !== state.originalDomain) {
                            log('拦截广告链接点击: ' + href);
                            e.preventDefault();
                            e.stopPropagation();
                        }
                    } catch {}
                }
            }
        }, true);

        log('广告拦截已启用');
    }

    // ==================== 网络请求拦截 ====================
    function interceptNetworkRequests() {
        // XHR 请求头拦截
        const originalXHRSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
            if (!this._m3u8RequestHeaders) this._m3u8RequestHeaders = {};
            this._m3u8RequestHeaders[key] = value;
            return originalXHRSetRequestHeader.apply(this, arguments);
        };

        // XHR 拦截
        const originalXHROpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.addEventListener('load', () => {
                const responseText = typeof this.responseText === 'string' ? this.responseText : null;
                handleUrl(url, {
                    responseText,
                    requestHeaders: this._m3u8RequestHeaders || {}
                });
            });
            return originalXHROpen.apply(this, arguments);
        };

        // Fetch 拦截
        const originalFetch = window.fetch;
        window.fetch = function(url, options) {
            const requestHeaders = extractFetchHeaders(options?.headers);
            return originalFetch.apply(this, arguments).then(response => {
                const urlStr = url?.toString?.() || '';
                if (urlStr.includes('.m3u8')) {
                    response.clone().text()
                        .then(text => handleUrl(url, { responseText: text, requestHeaders }))
                        .catch(() => handleUrl(url, { requestHeaders }));
                } else {
                    handleUrl(url, { requestHeaders });
                }
                return response;
            });
        };

        log('网络请求拦截已启用');
    }

    // ==================== 播放按钮点击 ====================
    async function clickPlayButton() {
        log('正在查找播放按钮...');

        for (let retry = 0; retry < RETRY.TIMES; retry++) {
            log('第 ' + (retry + 1) + ' 次尝试查找播放按钮');

            // 查找 SVG 播放图标
            for (const path of document.querySelectorAll(SELECTORS.PLAY_SVG_PATH)) {
                const svg = path.closest('svg');
                if (!svg) continue;

                for (const parent of [svg.parentElement, svg.parentElement?.parentElement]) {
                    if (!parent) continue;
                    const style = window.getComputedStyle(parent);
                    if (style.borderRadius === '50%' || style.cursor === 'pointer' || parent.tagName === 'BUTTON') {
                        parent.click();
                        log('已点击播放按钮 (' + parent.tagName + ')');
                        return true;
                    }
                }
            }

            // 查找圆形播放按钮
            for (const div of document.querySelectorAll('div')) {
                const style = window.getComputedStyle(div);
                if (style.borderRadius === '50%' && style.display === 'flex' &&
                    div.querySelector(SELECTORS.PLAY_SVG_PATH)) {
                    div.click();
                    log('已点击播放按钮 (圆形div)');
                    return true;
                }
            }

            if (retry < RETRY.TIMES - 1) await sleep(DURATION.RETRY_INTERVAL_MS);
        }

        log('未找到播放按钮，请手动点击播放');
        return false;
    }

    // ==================== 跳过广告 ====================
    async function clickSkipAdButton() {
        log('等待广告播放...');
        await sleep(DURATION.AD_WAIT_MS);
        log('正在查找跳过广告按钮...');

        const skipPatterns = ['跳過廣告', '跳过广告', '跳', 'Skip', 'skip', '关闭广告', '廣告'];

        for (let retry = 0; retry < RETRY.TIMES; retry++) {
            log('第 ' + (retry + 1) + ' 次尝试查找跳过广告按钮');

            // XPath 查找
            for (const pattern of skipPatterns) {
                try {
                    const result = document.evaluate(
                        `//button[contains(text(), '${pattern}')]`,
                        document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null
                    );
                    const button = result.singleNodeValue;
                    if (button && button.offsetParent !== null) {
                        button.click();
                        log('已点击跳过广告按钮: ' + (button.textContent || '').substring(0, 30));
                        return true;
                    }
                } catch {}
            }

            // 遍历按钮和可点击元素
            for (const el of document.querySelectorAll('button, div[role="button"], span[role="button"], a')) {
                const text = (el.textContent || '').trim();
                if (skipPatterns.some(p => text.includes(p)) && el.offsetParent !== null) {
                    el.click();
                    log('已点击跳过广告按钮: ' + text.substring(0, 30));
                    return true;
                }
            }

            if (retry < RETRY.TIMES - 1) await sleep(1000);
        }

        log('未找到跳过广告按钮，请手动点击');
        return false;
    }

    // ==================== 初始化 ====================
    function init() {
        log('Script loaded (Rou Mode v2.0)');
        if (hasBridge()) {
            try { window[SNIFFER_BRIDGE].onScriptLoaded('Rou'); } catch (e) {}
        }

        blockAdRedirects();
        interceptNetworkRequests();

        // 延迟执行：等待页面加载后点击播放
        setTimeout(async () => {
            const playClicked = await clickPlayButton();
            if (playClicked) {
                await clickSkipAdButton();
            }
        }, DURATION.INIT_DELAY_MS);

        // 深度扫描：检查页面中已有的 video 元素
        setTimeout(() => {
            document.querySelectorAll('video').forEach(video => {
                const src = video.src || video.currentSrc;
                if (src) handleUrl(src, {});
            });
        }, 3000);
    }

    init();
})();
