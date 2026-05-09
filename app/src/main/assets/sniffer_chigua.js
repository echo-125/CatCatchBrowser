// M3U8 嗅探脚本 - 吃瓜网站专用模式
// 参考: m3u8_sniffer_chigua.js
// 特点: 深度扫描 + DOM监听 + 多链接支持

(function() {
    'use strict';

    const SNIFFER_BRIDGE = 'SnifferBridge';

    const SELECTORS = {
        VIDEO: 'video',
        IFRAME: 'iframe',
        SCRIPT: 'script',
        TITLE: 'h1, title, [class*="title"], [class*="video-title"]'
    };

    const DURATION = {
        MIN_SECONDS: 60,
        RETRY_INTERVAL_MS: 1000,
        INIT_DELAY_MS: 2000,
        FETCH_TIMEOUT_MS: 10000,
        MAX_LOG_ITEMS: 60,
        MAX_RESULT_ITEMS: 80
    };

    const FILTER = {
        AD_KEYWORDS: ['ad', 'ads', 'adv', 'advertisement', 'silent-basis'],
        PLAYLIST_MARKERS: ['playlist', 'master', 'variant', 'list.m3u8']
    };

    const state = {
        capturedUrls: new Set(),
        capturedNames: new Set(),
        pendingUrls: new Set(),
        durationCache: new Map(),
        requestHeaders: new Map(),
        playlistVariants: new Map()
    };

    function hasBridge() {
        return typeof window[SNIFFER_BRIDGE] !== 'undefined';
    }

    const log = (msg) => {
        console.log('[M3U8Sniffer-Chigua] ' + msg);
        // 同时发送到 Android 端
        if (hasBridge()) {
            try {
                window[SNIFFER_BRIDGE].log(msg);
            } catch (e) {}
        }
    };

    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

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

    function isM3u8Url(url) {
        if (!url) return false;
        return url.toString().toLowerCase().includes('.m3u8');
    }

    function isAdUrl(url) {
        if (!url) return false;
        const lower = url.toString().toLowerCase();
        return FILTER.AD_KEYWORDS.some(kw => lower.includes(kw));
    }

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

    async function getM3U8Duration(url, content = null) {
        if (url.startsWith('blob:')) {
            return -1;
        }

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
            return -1;
        } finally {
            clearTimeout(timeoutId);
        }
    }

    async function handleUrl(url, options = {}) {
        if (!url) return;
        const urlStr = url.toString();
        const responseText = options.responseText || null;
        buildRequestHeaders(urlStr, options.requestHeaders || {});

        // 记录所有扫描到的 URL
        log('检查URL: ' + urlStr.substring(0, 80));

        // 检查是否是 m3u8 或可能的视频片段
        const isM3u8 = urlStr.includes('.m3u8');
        const isBlob = urlStr.startsWith('blob:');
        const isVideoSegment = urlStr.includes('.ts') || urlStr.includes('.mp4') || urlStr.includes('segment');

        if (!isM3u8 && !isBlob && !isVideoSegment) {
            return;
        }

        // 对于 blob URL，记录但尝试获取信息
        if (isBlob) {
            log('发现blob URL: ' + urlStr);
            // blob URL 无法直接获取内容，但我们可以尝试从页面中查找原始 URL
            try {
                // 尝试从 performance API 获取
                const entries = performance.getEntriesByType('resource');
                for (const entry of entries) {
                    if (entry.name && entry.name.includes('.m3u8')) {
                        log('从performance找到m3u8: ' + entry.name.substring(0, 80));
                        await handleUrl(entry.name, {});
                    }
                }
            } catch (e) {}
            return;
        }

        const urlName = urlStr.split('/').pop().split('?')[0];

        if (state.capturedNames.has(urlName) || state.capturedUrls.has(urlStr) || state.pendingUrls.has(urlStr)) {
            log('跳过重复链接: ' + urlName);
            return;
        }

        state.pendingUrls.add(urlStr);
        log('开始处理: ' + urlName);

        try {
            let m3u8Content = responseText;
            if (!m3u8Content && !urlStr.startsWith('blob:')) {
                try {
                    const controller = new AbortController();
                    const timeoutId = setTimeout(() => controller.abort(), DURATION.FETCH_TIMEOUT_MS);
                    const response = await fetch(urlStr, { signal: controller.signal });
                    m3u8Content = await response.text();
                    clearTimeout(timeoutId);
                    log('成功获取内容，长度: ' + (m3u8Content?.length || 0));
                } catch (e) {
                    log('获取M3U8内容失败: ' + e.message);
                }
            }

            const variants = parseM3u8Variants(m3u8Content, urlStr);

            if (variants && variants.length > 0) {
                state.playlistVariants.set(urlStr, variants);
                state.capturedUrls.add(urlStr);
                state.capturedNames.add(urlName);
                reportVariants(urlStr, variants, state.requestHeaders.get(urlStr) || {});
                log('找到播放列表: ' + urlName + ' (' + variants.length + '个分辨率)');
                return;
            }

            const duration = await getM3U8Duration(urlStr, m3u8Content);
            log('计算时长: ' + duration + 's');

            state.capturedUrls.add(urlStr);
            state.capturedNames.add(urlName);

            reportM3u8(urlStr, duration, state.requestHeaders.get(urlStr) || {});

            if (duration < DURATION.MIN_SECONDS && duration >= 0) {
                log('捕获链接: ' + urlName + ' (' + duration + 's，可能过短)');
            } else {
                log('捕获链接: ' + urlName + ' (' + duration + 's)');
            }
        } catch (e) {
            log('处理链接出错: ' + e.message);
        } finally {
            state.pendingUrls.delete(urlStr);
        }
    }

    // 网络请求拦截
    function interceptNetworkRequests() {
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
                log('XHR请求: ' + xhrUrl.toString().substring(0, 80));
                handleUrl(xhrUrl, {
                    responseText,
                    requestHeaders: this._m3u8RequestHeaders || {}
                });
            });
            return originalXHROpen.apply(this, arguments);
        };

        const originalFetch = window.fetch;
        window.fetch = function(url, options) {
            const requestHeaders = extractFetchHeaders(options?.headers);
            const fetchUrl = url;
            log('Fetch请求: ' + fetchUrl?.toString?.()?.substring?.(0, 80) || 'unknown');
            return originalFetch.apply(this, arguments).then(response => {
                // 对所有请求都检查
                response.clone().text()
                    .then(text => handleUrl(fetchUrl, { responseText: text, requestHeaders }))
                    .catch(() => handleUrl(fetchUrl, { requestHeaders }));
                return response;
            });
        };

        // 动态创建script标签
        const originalCreateElement = document.createElement;
        document.createElement = function(tagName) {
            const element = originalCreateElement.call(document, tagName);

            if (tagName.toLowerCase() === 'script') {
                const originalSetAttribute = element.setAttribute;
                element.setAttribute = function(name, value) {
                    if (name === 'src' && String(value).includes('.m3u8')) {
                        handleUrl(String(value), {});
                    }
                    return originalSetAttribute.call(this, name, value);
                };
            }

            return element;
        };

        log('网络请求拦截已启用');
    }

    // 深度扫描
    async function deepScan() {
        log('开始深度扫描...');

        const html = document.documentElement.outerHTML;

        const patterns = [
            /https?:\/\/[^\s"'`<>]+\.m3u8[^\s"'`<>]*/gi,
            /["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi,
            /url\s*[:=]\s*["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi,
        ];

        const foundUrls = new Set();

        patterns.forEach((pattern) => {
            const matches = html.match(pattern);
            if (matches) {
                matches.forEach(url => {
                    url = url.replace(/^["']|["']$/g, '').replace(/[;,\)\]}\>'"`]+$/, '');
                    if (url.includes('.m3u8')) {
                        foundUrls.add(url);
                    }
                });
            }
        });

        const scripts = document.querySelectorAll('script');
        scripts.forEach((script) => {
            const content = script.textContent || script.innerHTML;
            if (content) {
                patterns.forEach(pattern => {
                    const matches = content.match(pattern);
                    if (matches) {
                        matches.forEach(url => {
                            url = url.replace(/^["']|["']$/g, '').replace(/[;,\)\]}\>'"`]+$/, '');
                            if (url.includes('.m3u8')) {
                                foundUrls.add(url);
                            }
                        });
                    }
                });
            }
        });

        const videos = document.querySelectorAll('video');
        videos.forEach((video) => {
            const src = video.src || video.currentSrc;
            if (src) {
                foundUrls.add(src);
            }
        });

        // 扫描window对象
        try {
            for (let key in window) {
                try {
                    const value = String(window[key]);
                    if (value.includes('.m3u8')) {
                        const matches = value.match(/https?:\/\/[^\s"'`<>]+\.m3u8[^\s"'`<>]*/gi);
                        if (matches) {
                            matches.forEach(url => {
                                foundUrls.add(url);
                            });
                        }
                    }
                } catch(e) {}
            }
        } catch(e) {}

        log('扫描到 ' + foundUrls.size + ' 个可能的链接');
        for (const url of foundUrls) {
            await handleUrl(url, {});
        }

        log('深度扫描完成');
    }

    // DOM监听
    function observeDOM() {
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                mutation.addedNodes.forEach((node) => {
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        if (node.tagName === 'VIDEO') {
                            const src = node.src || node.currentSrc;
                            if (src) {
                                handleUrl(src, {});
                            }
                        }

                        const videos = node.querySelectorAll && node.querySelectorAll('video');
                        if (videos) {
                            videos.forEach(video => {
                                const src = video.src || video.currentSrc;
                                if (src) {
                                    handleUrl(src, {});
                                }
                            });
                        }
                    }
                });
            });
        });

        observer.observe(document.documentElement, {
            childList: true,
            subtree: true
        });

        log('DOM监听已启用');
    }

    function init() {
        log('Script loaded (Chigua Mode)');
        // 通知 Android 端脚本已加载
        if (hasBridge()) {
            try {
                window[SNIFFER_BRIDGE].onScriptLoaded('Chigua');
            } catch (e) {}
        }
        interceptNetworkRequests();

        setTimeout(() => {
            observeDOM();
            deepScan();
        }, DURATION.INIT_DELAY_MS);

        // 多次延迟扫描
        setTimeout(deepScan, 5000);
        setTimeout(deepScan, 10000);
    }

    init();
})();
