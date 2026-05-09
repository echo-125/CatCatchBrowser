// M3U8 嗅探脚本 - 通用模式
// 基础网络请求拦截

(function() {
    'use strict';

    const SNIFFER_BRIDGE = 'SnifferBridge';

    const state = {
        capturedUrls: new Set(),
        pendingUrls: new Set(),
        durationCache: new Map(),
        requestHeaders: new Map()
    };

    function hasBridge() {
        return typeof window[SNIFFER_BRIDGE] !== 'undefined';
    }

    function log(msg) {
        console.log('[M3U8Sniffer-General] ' + msg);
        // 同时发送到 Android 端
        if (hasBridge()) {
            try {
                window[SNIFFER_BRIDGE].log(msg);
            } catch (e) {}
        }
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
        const urlStr = url.toString().toLowerCase();
        return urlStr.includes('.m3u8');
    }

    function isAdUrl(url) {
        if (!url) return false;
        const lower = url.toString().toLowerCase();
        const adKeywords = ['advertisement', 'preroll', 'midroll', 'postroll', 'ads.', '/ad/'];
        return adKeywords.some(kw => lower.includes(kw));
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

    function extractFetchHeaders(headers) {
        if (!headers) return {};

        if (headers instanceof Headers) {
            const result = {};
            headers.forEach((value, key) => { result[key] = value; });
            return result;
        }

        if (Array.isArray(headers)) return Object.fromEntries(headers);
        if (typeof headers === 'object') return { ...headers };

        return {};
    }

    function buildRequestHeaders(url, headers) {
        const result = { ...headers };

        if (!result['Referer']) {
            result['Referer'] = window.location.href;
        }
        if (!result['Origin']) {
            result['Origin'] = window.location.origin;
        }

        state.requestHeaders.set(url, result);
        return result;
    }

    async function handleM3u8Url(url, responseText, requestHeaders) {
        if (!isM3u8Url(url)) {
            log('跳过非m3u8: ' + url?.toString?.()?.substring?.(0, 50));
            return;
        }

        const urlStr = url.toString();
        log('检查m3u8: ' + urlStr.substring(0, 80));

        if (state.capturedUrls.has(urlStr) || state.pendingUrls.has(urlStr)) {
            log('跳过重复: ' + urlStr.substring(0, 50));
            return;
        }

        if (isAdUrl(urlStr)) {
            log('跳过广告URL: ' + urlStr.substring(0, 50));
            return;
        }

        state.pendingUrls.add(urlStr);
        const headers = buildRequestHeaders(urlStr, requestHeaders || {});

        log('开始处理: ' + urlStr.substring(0, 60));

        try {
            let m3u8Content = responseText;

            if (!m3u8Content && !urlStr.startsWith('blob:')) {
                try {
                    const controller = new AbortController();
                    const timeoutId = setTimeout(() => controller.abort(), 10000);

                    const response = await fetch(urlStr, {
                        method: 'GET',
                        headers: headers,
                        signal: controller.signal
                    });
                    m3u8Content = await response.text();
                    clearTimeout(timeoutId);
                } catch (e) {
                    log('Failed to fetch content: ' + e.message);
                }
            }

            const variants = parseM3u8Variants(m3u8Content, urlStr);

            if (variants && variants.length > 0) {
                state.capturedUrls.add(urlStr);
                reportVariants(urlStr, variants, headers);
            } else {
                const duration = parseM3u8Duration(m3u8Content);
                state.capturedUrls.add(urlStr);
                reportM3u8(urlStr, duration, headers);
            }
        } catch (e) {
            state.capturedUrls.add(urlStr);
            reportM3u8(urlStr, 0, headers);
        } finally {
            state.pendingUrls.delete(urlStr);
        }
    }

    // 拦截 XHR setRequestHeader
    const originalXHRSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
        if (!this._snifferHeaders) this._snifferHeaders = {};
        this._snifferHeaders[key] = value;
        return originalXHRSetRequestHeader.apply(this, arguments);
    };

    // 拦截 XHR open
    const originalXHROpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._snifferUrl = url;

        this.addEventListener('load', function() {
            const responseText = typeof this.responseText === 'string' ? this.responseText : null;
            if (isM3u8Url(this._snifferUrl)) {
                handleM3u8Url(this._snifferUrl, responseText, this._snifferHeaders || {});
            }
        });

        return originalXHROpen.apply(this, arguments);
    };

    // 拦截 fetch
    const originalFetch = window.fetch;
    window.fetch = function(url, options) {
        const requestHeaders = extractFetchHeaders(options?.headers);

        return originalFetch.apply(this, arguments).then(response => {
            const urlStr = url?.toString?.() || '';

            if (urlStr.includes('.m3u8')) {
                response.clone().text()
                    .then(text => handleM3u8Url(url, text, requestHeaders))
                    .catch(() => handleM3u8Url(url, null, requestHeaders));
            }

            return response;
        });
    };

    // 拦截动态创建的 script 标签
    const originalCreateElement = document.createElement;
    document.createElement = function(tagName) {
        const element = originalCreateElement.call(document, tagName);

        if (tagName.toLowerCase() === 'script') {
            const originalSetAttribute = element.setAttribute;
            element.setAttribute = function(name, value) {
                if (name === 'src' && String(value).includes('.m3u8')) {
                    handleM3u8Url(String(value), null, {});
                }
                return originalSetAttribute.call(this, name, value);
            };
        }

        return element;
    };

    // 深度扫描
    function deepScan() {
        log('Starting deep scan...');

        const patterns = [
            /https?:\/\/[^\s"'`<>]+\.m3u8[^\s"'`<>]*/gi,
            /["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi
        ];

        const foundUrls = new Set();

        const html = document.documentElement.outerHTML;
        patterns.forEach(pattern => {
            const matches = html.match(pattern);
            if (matches) {
                matches.forEach(url => {
                    const cleanUrl = url.replace(/^["']|["']$/g, '').replace(/[;,)\]}>'"`]+$/, '');
                    if (cleanUrl.includes('.m3u8')) {
                        foundUrls.add(cleanUrl);
                    }
                });
            }
        });

        document.querySelectorAll('script').forEach(script => {
            const content = script.textContent || script.innerHTML || '';
            patterns.forEach(pattern => {
                try {
                    const matches = content.match(pattern);
                    if (matches) {
                        matches.forEach(url => {
                            const cleanUrl = url.replace(/^["']|["']$/g, '').replace(/[;,)\]}>'"`]+$/, '');
                            if (cleanUrl.includes('.m3u8')) {
                                foundUrls.add(cleanUrl);
                            }
                        });
                    }
                } catch (e) {}
            });
        });

        document.querySelectorAll('video').forEach(video => {
            const src = video.src || video.currentSrc;
            if (src) {
                foundUrls.add(src);
            }
            video.querySelectorAll('source').forEach(source => {
                if (source.src) {
                    foundUrls.add(source.src);
                }
            });
        });

        log('Deep scan found ' + foundUrls.size + ' potential URLs');
        foundUrls.forEach(url => {
            handleM3u8Url(url, null, {});
        });
    }

    function init() {
        log('Script loaded (General Mode)');
        // 通知 Android 端脚本已加载
        if (hasBridge()) {
            try {
                window[SNIFFER_BRIDGE].onScriptLoaded('General');
            } catch (e) {}
        }

        setTimeout(deepScan, 1500);
        setTimeout(deepScan, 4000);

        const observer = new MutationObserver((mutations) => {
            mutations.forEach(mutation => {
                mutation.addedNodes.forEach(node => {
                    if (node.nodeName === 'VIDEO') {
                        const src = node.src || node.currentSrc;
                        if (src && src.includes('.m3u8')) {
                            handleM3u8Url(src, null, {});
                        }
                    }
                    if (node.querySelectorAll) {
                        node.querySelectorAll('video, video source').forEach(el => {
                            const src = el.src;
                            if (src && src.includes('.m3u8')) {
                                handleM3u8Url(src, null, {});
                            }
                        });
                    }
                });
            });
        });

        observer.observe(document.documentElement, {
            childList: true,
            subtree: true
        });
    }

    init();
})();
