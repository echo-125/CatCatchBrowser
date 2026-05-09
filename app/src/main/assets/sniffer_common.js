// M3U8 嗅探 - 公共模块
// 所有模式共享的工具函数、M3U8 解析、网络拦截、DOM 扫描
// 注入后暴露在 window.__M3u8Common 上

(function() {
    'use strict';

    if (window.__M3u8Common) return;

    const SNIFFER_BRIDGE = 'SnifferBridge';

    // ==================== 公共状态 ====================
    const state = {
        capturedUrls: new Set(),
        pendingUrls: new Set(),
        durationCache: new Map(),
        requestHeaders: new Map()
    };

    // ==================== Bridge 工具 ====================
    function hasBridge() {
        return typeof window[SNIFFER_BRIDGE] !== 'undefined';
    }

    function log(tag, msg) {
        console.log('[M3U8Sniffer-' + tag + '] ' + msg);
        if (hasBridge()) {
            try { window[SNIFFER_BRIDGE].log(msg); } catch (e) {}
        }
    }

    // ==================== M3U8 上报 ====================
    function reportM3u8(url, duration, headers) {
        if (!hasBridge() || state.capturedUrls.has(url)) return;
        state.capturedUrls.add(url);

        try {
            const title = document.title || '';
            const headersJson = headers ? JSON.stringify(headers) : '';
            window[SNIFFER_BRIDGE].reportM3u8(url, title, duration || 0, headersJson);
            log('common', 'Captured: ' + url.substring(0, 60) + '...');
        } catch (e) {
            console.error('[M3U8Sniffer] Report error:', e);
        }
    }

    function reportVariants(url, variants, headers, duration) {
        if (!hasBridge()) return;

        try {
            const variantsJson = JSON.stringify(variants);
            const headersJson = headers ? JSON.stringify(headers) : '';
            const title = document.title || '';
            window[SNIFFER_BRIDGE].reportPlaylistVariants(url, variantsJson, headersJson, duration || 0, title);
            log('common', 'Playlist variants: ' + variants.length + ' resolutions' + (duration ? ' (' + duration + 's)' : ''));
        } catch (e) {
            console.error('[M3U8Sniffer] Report variants error:', e);
        }
    }

    // ==================== URL 判断 ====================
    function isM3u8Url(url) {
        if (!url) return false;
        return url.toString().toLowerCase().includes('.m3u8');
    }

    function isAdUrl(url, adKeywords) {
        if (!url) return false;
        const lower = url.toString().toLowerCase();
        return adKeywords.some(kw => lower.includes(kw));
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

    function buildRequestHeaders(url, headers) {
        const result = { ...(headers || {}) };

        if (!result['Referer']) {
            result['Referer'] = window.location.href;
        }
        if (!result['Origin']) {
            result['Origin'] = window.location.origin;
        }

        state.requestHeaders.set(url, result);
        return result;
    }

    // ==================== M3U8 内容获取 ====================
    async function fetchM3u8Content(url, timeout) {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout || 10000);

        try {
            const response = await fetch(url, { signal: controller.signal });
            const content = await response.text();
            clearTimeout(timeoutId);
            return content;
        } catch (e) {
            clearTimeout(timeoutId);
            return null;
        }
    }

    // ==================== 核心处理 ====================
    async function handleM3u8Url(url, responseText, requestHeaders, tag, minDuration) {
        if (!isM3u8Url(url)) return;

        const urlStr = url.toString();

        if (state.capturedUrls.has(urlStr) || state.pendingUrls.has(urlStr)) {
            return;
        }

        state.pendingUrls.add(urlStr);
        const headers = buildRequestHeaders(urlStr, requestHeaders || {});

        try {
            let m3u8Content = responseText;

            if (!m3u8Content && !urlStr.startsWith('blob:')) {
                m3u8Content = await fetchM3u8Content(urlStr);
            }

            // 检查是否是 master playlist
            const variants = parseM3u8Variants(m3u8Content, urlStr);

            if (variants && variants.length > 0) {
                state.capturedUrls.add(urlStr);
                const variantDuration = await resolveVariantDuration(variants, tag);
                reportVariants(urlStr, variants, headers, variantDuration);
                return;
            }

            // 计算时长
            const duration = parseM3u8Duration(m3u8Content);

            // 短时长过滤
            if (minDuration && duration > 0 && duration < minDuration) {
                log(tag, '过滤短时长: ' + urlStr.substring(0, 50) + ' (' + duration + 's)');
                return;
            }

            state.capturedUrls.add(urlStr);
            reportM3u8(urlStr, duration, headers);
        } catch (e) {
            state.capturedUrls.add(urlStr);
            reportM3u8(urlStr, 0, headers);
        } finally {
            state.pendingUrls.delete(urlStr);
        }
    }

    // ==================== 网络请求拦截 ====================
    function interceptNetworkRequests(tag, minDuration) {
        // XHR 请求头拦截
        const originalXHRSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
            if (!this._snifferHeaders) this._snifferHeaders = {};
            this._snifferHeaders[key] = value;
            return originalXHRSetRequestHeader.apply(this, arguments);
        };

        // XHR 拦截
        const originalXHROpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.addEventListener('load', function() {
                const responseText = typeof this.responseText === 'string' ? this.responseText : null;
                if (isM3u8Url(this._snifferUrl || url)) {
                    handleM3u8Url(this._snifferUrl || url, responseText, this._snifferHeaders || {}, tag, minDuration);
                }
            });
            this._snifferUrl = url;
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
                        .then(text => handleM3u8Url(url, text, requestHeaders, tag, minDuration))
                        .catch(() => handleM3u8Url(url, null, requestHeaders, tag, minDuration));
                }

                return response;
            });
        };

        // 动态 script 标签拦截
        const originalCreateElement = document.createElement;
        document.createElement = function(tagName) {
            const element = originalCreateElement.call(document, tagName);

            if (tagName.toLowerCase() === 'script') {
                const originalSetAttribute = element.setAttribute;
                element.setAttribute = function(name, value) {
                    if (name === 'src' && String(value).includes('.m3u8')) {
                        handleM3u8Url(String(value), null, {}, tag, minDuration);
                    }
                    return originalSetAttribute.call(this, name, value);
                };
            }

            return element;
        };
    }

    // ==================== DOM 扫描 ====================
    async function deepScan(tag, minDuration) {
        log(tag, 'Starting deep scan...');

        const patterns = [
            /https?:\/\/[^\s"'`<>]+\.m3u8[^\s"'`<>]*/gi,
            /["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi
        ];

        const foundUrls = new Set();

        // 扫描 HTML
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

        // 扫描 script 标签
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

        // 扫描 video 元素
        document.querySelectorAll('video').forEach(video => {
            const src = video.src || video.currentSrc;
            if (src) foundUrls.add(src);
            video.querySelectorAll('source').forEach(source => {
                if (source.src) foundUrls.add(source.src);
            });
        });

        log(tag, 'Deep scan found ' + foundUrls.size + ' potential URLs');
        for (const url of foundUrls) {
            await handleM3u8Url(url, null, {}, tag, minDuration);
        }
    }

    // ==================== MutationObserver ====================
    function observeVideoElements(tag, minDuration) {
        const observer = new MutationObserver((mutations) => {
            mutations.forEach(mutation => {
                mutation.addedNodes.forEach(node => {
                    if (node.nodeName === 'VIDEO') {
                        const src = node.src || node.currentSrc;
                        if (src && src.includes('.m3u8')) {
                            handleM3u8Url(src, null, {}, tag, minDuration);
                        }
                    }
                    if (node.querySelectorAll) {
                        node.querySelectorAll('video, video source').forEach(el => {
                            const src = el.src;
                            if (src && src.includes('.m3u8')) {
                                handleM3u8Url(src, null, {}, tag, minDuration);
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

    // ==================== 内容嗅探辅助 ====================

    // Data URI M3U8 检测
    const dataM3u8RE = /^data:(application|video|audio)\//i;
    const m3u8MimeTypes = ['vnd.apple.mpegurl', 'x-mpegurl', 'mpegurl'];

    function isDataM3u8(text) {
        if (!text || typeof text !== 'string') return false;
        if (!dataM3u8RE.test(text.substring(0, 30))) return false;
        const afterSlash = text.substring(text.indexOf('/') + 1).toLowerCase();
        return m3u8MimeTypes.some(mime => afterSlash.startsWith(mime));
    }

    function decodeDataM3u8(text) {
        try {
            const afterScheme = text.substring(text.indexOf('/') + 1);
            const matchedMime = m3u8MimeTypes.find(mime =>
                afterScheme.toLowerCase().startsWith(mime));
            if (!matchedMime) return null;
            const remaining = afterScheme.slice(matchedMime.length + 1);
            const [prefix, data] = remaining.split(/,(.+)/);
            if (!data) return null;
            if (prefix.toLowerCase() === 'base64') {
                return atob(data);
            }
            return data;
        } catch (e) {
            return null;
        }
    }

    // 处理 M3U8 文本内容（非 URL），解析时长/变体后上报
    async function handleM3u8Text(m3u8Text, sourceUrl, tag, minDuration) {
        if (!m3u8Text || typeof m3u8Text !== 'string') return;
        if (!m3u8Text.toUpperCase().includes('#EXTM3U')) return;

        const urlKey = sourceUrl || ('text://' + m3u8Text.substring(0, 50));
        if (state.capturedUrls.has(urlKey)) return;
        state.capturedUrls.add(urlKey);

        try {
            const variants = parseM3u8Variants(m3u8Text, sourceUrl || window.location.href);

            if (variants && variants.length > 0) {
                const variantDuration = await resolveVariantDuration(variants, tag);
                const headers = buildRequestHeaders(sourceUrl || '', {});
                reportVariants(sourceUrl || urlKey, variants, headers, variantDuration);
                return;
            }

            const duration = parseM3u8Duration(m3u8Text);

            if (minDuration && duration > 0 && duration < minDuration) {
                log(tag, '过滤短时长文本: ' + duration + 's');
                return;
            }

            const headers = buildRequestHeaders(sourceUrl || '', {});
            reportM3u8(sourceUrl || urlKey, duration, headers);
        } catch (e) {
            log(tag, '处理M3U8文本出错: ' + e.message);
        }
    }

    // 递归遍历对象，查找 URL 和内联 M3U8（用于 JSON.parse hook）
    const mediaUrlRE = /\.(m3u8|mp4|flv|mpd)(\?[^"'\s]*)?$/i;

    function findMediaUrlsInObject(data, callback, depth) {
        if (!data || typeof data !== 'object') return;
        if (depth === undefined) depth = 0;
        if (depth > 10) return;

        for (const key in data) {
            const val = data[key];
            if (typeof val === 'string') {
                if (mediaUrlRE.test(val)) {
                    callback(val, 'url');
                } else if (val.substring(0, 7).toUpperCase() === '#EXTM3U') {
                    callback(val, 'inline');
                } else if (isDataM3u8(val)) {
                    const decoded = decodeDataM3u8(val);
                    if (decoded) callback(decoded, 'datauri');
                }
            } else if (typeof val === 'object' && val !== null) {
                findMediaUrlsInObject(val, callback, depth + 1);
            }
        }
    }

    // ==================== 变体时长解析 ====================
    async function resolveVariantDuration(variants, tag) {
        try {
            const bestVariant = variants.reduce((best, v) =>
                (v.bandwidth > best.bandwidth) ? v : best, variants[0]);
            if (bestVariant.url) {
                const variantContent = await fetchM3u8Content(bestVariant.url);
                const duration = parseM3u8Duration(variantContent);
                log(tag || 'common', '变体时长: ' + duration + 's (' + bestVariant.url.substring(0, 50) + ')');
                return duration;
            }
        } catch (e) {
            log(tag || 'common', '变体时长解析失败: ' + e.message);
        }
        return 0;
    }

    // ==================== 暴露公共 API ====================
    window.__M3u8Common = {
        state,
        hasBridge,
        log,
        reportM3u8,
        reportVariants,
        isM3u8Url,
        isAdUrl,
        parseM3u8Duration,
        parseM3u8Variants,
        extractFetchHeaders,
        buildRequestHeaders,
        fetchM3u8Content,
        handleM3u8Url,
        interceptNetworkRequests,
        deepScan,
        observeVideoElements,
        resolveVariantDuration,
        isDataM3u8,
        decodeDataM3u8,
        handleM3u8Text,
        findMediaUrlsInObject
    };
})();
