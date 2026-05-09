// M3U8 嗅探脚本 - 吃瓜网站专用模式
// 特点: 深度扫描 + DOM监听 + 多链接支持

(function() {
    'use strict';

    const TAG = 'Chigua';
    const C = window.__M3u8Common;
    if (!C) { console.error('[M3U8Sniffer] Common module not loaded'); return; }

    const DURATION = {
        MIN_SECONDS: 60,
        INIT_DELAY_MS: 2000,
        MAX_RESULT_ITEMS: 80
    };

    // 吃瓜模式额外状态
    const chiguaState = {
        capturedNames: new Set(),
        playlistVariants: new Map()
    };

    // ==================== 吃瓜专用 URL 处理（深度扫描 + 短时长过滤） ====================
    async function handleChiguaUrl(url, options = {}) {
        if (!url) return;
        const urlStr = url.toString();
        const responseText = options.responseText || null;
        C.buildRequestHeaders(urlStr, options.requestHeaders || {});

        C.log(TAG, '检查URL: ' + urlStr.substring(0, 80));

        const isM3u8 = urlStr.includes('.m3u8');
        const isBlob = urlStr.startsWith('blob:');
        const isVideoSegment = urlStr.includes('.ts') || urlStr.includes('.mp4') || urlStr.includes('segment');

        if (!isM3u8 && !isBlob && !isVideoSegment) {
            return;
        }

        // blob URL 尝试从 performance API 获取真实 URL
        if (isBlob) {
            C.log(TAG, '发现blob URL: ' + urlStr);
            try {
                const entries = performance.getEntriesByType('resource');
                for (const entry of entries) {
                    if (entry.name && entry.name.includes('.m3u8')) {
                        C.log(TAG, '从performance找到m3u8: ' + entry.name.substring(0, 80));
                        await handleChiguaUrl(entry.name, {});
                    }
                }
            } catch (e) {}
            return;
        }

        const urlName = urlStr.split('/').pop().split('?')[0];

        if (chiguaState.capturedNames.has(urlName) || C.state.capturedUrls.has(urlStr) || C.state.pendingUrls.has(urlStr)) {
            return;
        }

        C.state.pendingUrls.add(urlStr);

        try {
            let m3u8Content = responseText;
            if (!m3u8Content && !urlStr.startsWith('blob:')) {
                m3u8Content = await C.fetchM3u8Content(urlStr);
                C.log(TAG, '成功获取内容，长度: ' + (m3u8Content?.length || 0));
            }

            const variants = C.parseM3u8Variants(m3u8Content, urlStr);

            if (variants && variants.length > 0) {
                chiguaState.playlistVariants.set(urlStr, variants);
                C.state.capturedUrls.add(urlStr);
                chiguaState.capturedNames.add(urlName);
                const variantDuration = await C.resolveVariantDuration(variants, TAG);
                C.reportVariants(urlStr, variants, C.state.requestHeaders.get(urlStr) || {}, variantDuration);
                C.log(TAG, '找到播放列表: ' + urlName + ' (' + variants.length + '个分辨率)');
                return;
            }

            const duration = C.parseM3u8Duration(m3u8Content);
            C.log(TAG, '计算时长: ' + duration + 's');

            // 短时长过滤（与其他模式保持一致）
            if (duration > 0 && duration < DURATION.MIN_SECONDS) {
                C.log(TAG, '过滤短时长: ' + urlName + ' (' + duration + 's)');
                return;
            }

            C.state.capturedUrls.add(urlStr);
            chiguaState.capturedNames.add(urlName);

            C.reportM3u8(urlStr, duration, C.state.requestHeaders.get(urlStr) || {});
            C.log(TAG, '捕获链接: ' + urlName + ' (' + duration + 's)');
        } catch (e) {
            C.log(TAG, '处理链接出错: ' + e.message);
        } finally {
            C.state.pendingUrls.delete(urlStr);
        }
    }

    // ==================== 吃瓜专用网络拦截（扫描所有请求） ====================
    function interceptChiguaNetwork() {
        const originalXHRSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
            if (!this._m3u8RequestHeaders) this._m3u8RequestHeaders = {};
            this._m3u8RequestHeaders[key] = value;
            return originalXHRSetRequestHeader.apply(this, arguments);
        };

        const originalXHROpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            const xhrUrl = url;
            this.addEventListener('load', function() {
                const responseText = typeof this.responseText === 'string' ? this.responseText : null;
                C.log(TAG, 'XHR请求: ' + xhrUrl.toString().substring(0, 80));
                handleChiguaUrl(xhrUrl, {
                    responseText,
                    requestHeaders: this._m3u8RequestHeaders || {}
                });
            });
            return originalXHROpen.apply(this, arguments);
        };

        const originalFetch = window.fetch;
        window.fetch = function(url, options) {
            const requestHeaders = C.extractFetchHeaders(options?.headers);
            const fetchUrl = url;
            C.log(TAG, 'Fetch请求: ' + fetchUrl?.toString?.()?.substring?.(0, 80) || 'unknown');
            return originalFetch.apply(this, arguments).then(response => {
                response.clone().text()
                    .then(text => handleChiguaUrl(fetchUrl, { responseText: text, requestHeaders }))
                    .catch(() => handleChiguaUrl(fetchUrl, { requestHeaders }));
                return response;
            });
        };

        const originalCreateElement = document.createElement;
        document.createElement = function(tagName) {
            const element = originalCreateElement.call(document, tagName);
            if (tagName.toLowerCase() === 'script') {
                const originalSetAttribute = element.setAttribute;
                element.setAttribute = function(name, value) {
                    if (name === 'src' && String(value).includes('.m3u8')) {
                        handleChiguaUrl(String(value), {});
                    }
                    return originalSetAttribute.call(this, name, value);
                };
            }
            return element;
        };
    }

    // ==================== 吃瓜专用深度扫描（扫描 window 对象） ====================
    async function deepScanChigua() {
        C.log(TAG, '开始深度扫描...');

        // 先用公共扫描
        const patterns = [
            /https?:\/\/[^\s"'`<>]+\.m3u8[^\s"'`<>]*/gi,
            /["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi,
            /url\s*[:=]\s*["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi,
        ];

        const foundUrls = new Set();

        // 扫描 HTML
        const html = document.documentElement.outerHTML;
        patterns.forEach(pattern => {
            const matches = html.match(pattern);
            if (matches) {
                matches.forEach(url => {
                    url = url.replace(/^["']|["']$/g, '').replace(/[;,\)\]}\>'"`]+$/, '');
                    if (url.includes('.m3u8')) foundUrls.add(url);
                });
            }
        });

        // 扫描 script 标签
        document.querySelectorAll('script').forEach(script => {
            const content = script.textContent || script.innerHTML;
            if (content) {
                patterns.forEach(pattern => {
                    const matches = content.match(pattern);
                    if (matches) {
                        matches.forEach(url => {
                            url = url.replace(/^["']|["']$/g, '').replace(/[;,\)\]}\>'"`]+$/, '');
                            if (url.includes('.m3u8')) foundUrls.add(url);
                        });
                    }
                });
            }
        });

        // 扫描 video 元素
        document.querySelectorAll('video').forEach(video => {
            const src = video.src || video.currentSrc;
            if (src) foundUrls.add(src);
        });

        // 扫描 window 对象（吃瓜模式特有）
        try {
            for (let key in window) {
                try {
                    const value = String(window[key]);
                    if (value.includes('.m3u8')) {
                        const matches = value.match(/https?:\/\/[^\s"'`<>]+\.m3u8[^\s"'`<>]*/gi);
                        if (matches) {
                            matches.forEach(url => foundUrls.add(url));
                        }
                    }
                } catch(e) {}
            }
        } catch(e) {}

        C.log(TAG, '扫描到 ' + foundUrls.size + ' 个可能的链接');
        for (const url of foundUrls) {
            await handleChiguaUrl(url, {});
        }

        C.log(TAG, '深度扫描完成');
    }

    // ==================== 初始化 ====================
    function init() {
        C.log(TAG, 'Script loaded (Chigua Mode)');
        if (C.hasBridge()) {
            try { window['SnifferBridge'].onScriptLoaded('Chigua'); } catch (e) {}
        }

        interceptChiguaNetwork();

        setTimeout(() => {
            C.observeVideoElements(TAG, DURATION.MIN_SECONDS);
            deepScanChigua();
        }, DURATION.INIT_DELAY_MS);

        setTimeout(deepScanChigua, 5000);
        setTimeout(deepScanChigua, 10000);
    }

    init();
})();
