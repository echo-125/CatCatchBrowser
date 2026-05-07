// M3U8 嗅探脚本
// 注入到 WebView 中，拦截 XHR 和 fetch 请求

(function() {
    'use strict';

    const SNIFFER_BRIDGE = 'SnifferBridge';

    // 检查 Bridge 是否存在
    function hasBridge() {
        return typeof window[SNIFFER_BRIDGE] !== 'undefined';
    }

    // 通过 Bridge 报告捕获的 M3U8 URL
    function reportM3u8(url, duration, headers) {
        if (!hasBridge()) return;

        try {
            const title = document.title || '';
            const headersJson = headers ? JSON.stringify(headers) : '';
            window[SNIFFER_BRIDGE].reportM3u8(url, title, duration || 0, headersJson);
        } catch (e) {
            console.error('[M3U8Sniffer] Report error:', e);
        }
    }

    // 报告播放列表变体
    function reportVariants(url, variants, headers) {
        if (!hasBridge()) return;

        try {
            const variantsJson = JSON.stringify(variants);
            const headersJson = headers ? JSON.stringify(headers) : '';
            window[SNIFFER_BRIDGE].reportPlaylistVariants(url, variantsJson, headersJson);
        } catch (e) {
            console.error('[M3U8Sniffer] Report variants error:', e);
        }
    }

    // 检查是否是 M3U8 URL
    function isM3u8Url(url) {
        if (!url) return false;
        const lower = url.toLowerCase();
        return lower.includes('.m3u8');
    }

    // 检查是否是广告 URL
    function isAdUrl(url) {
        if (!url) return false;
        const lower = url.toLowerCase();
        const adKeywords = ['ad', 'ads', 'adv', 'advertisement', 'silent-basis'];
        return adKeywords.some(kw => lower.includes(kw));
    }

    // 解析 M3U8 时长
    function parseM3u8Duration(content) {
        if (!content || typeof content !== 'string') return 0;

        let totalDuration = 0;
        const lines = content.split('\n');

        for (const line of lines) {
            const match = line.trim().match(/#EXTINF:([\d.]+)/);
            if (match) {
                totalDuration += parseFloat(match[1]);
            }
        }

        return totalDuration;
    }

    // 解析 M3U8 播放列表变体
    function parseM3u8Variants(content, baseUrl) {
        if (!content || typeof content !== 'string') return null;

        // 检查是否是 master playlist
        if (!content.includes('#EXT-X-STREAM-INF')) {
            return null;
        }

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

                // 提取带宽
                const bandwidthMatch = line.match(/BANDWIDTH=(\d+)/);
                if (bandwidthMatch) {
                    variant.bandwidth = parseInt(bandwidthMatch[1]);
                }

                // 提取分辨率
                const resolutionMatch = line.match(/RESOLUTION=([\d]+x[\d]+)/);
                if (resolutionMatch) {
                    variant.resolution = resolutionMatch[1];
                }

                // 提取编码
                const codecsMatch = line.match(/CODECS="([^"]+)"/);
                if (codecsMatch) {
                    variant.codecs = codecsMatch[1];
                }

                // 提取帧率
                const frameRateMatch = line.match(/FRAME-RATE=([\d.]+)/);
                if (frameRateMatch) {
                    variant.frameRate = parseFloat(frameRateMatch[1]);
                }

                // 下一行是 URL
                if (i + 1 < lines.length) {
                    const nextLine = lines[i + 1].trim();
                    if (nextLine && !nextLine.startsWith('#')) {
                        if (nextLine.startsWith('http')) {
                            variant.url = nextLine;
                        } else {
                            // 相对路径
                            const base = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
                            variant.url = base + nextLine;
                        }
                    }
                }

                if (variant.url) {
                    variants.push(variant);
                }
            }
        }

        return variants.length > 0 ? variants : null;
    }

    // 获取请求头
    function extractHeaders(headers) {
        if (!headers) return {};

        if (headers instanceof Headers) {
            const result = {};
            headers.forEach((value, key) => {
                result[key] = value;
            });
            return result;
        }

        if (Array.isArray(headers)) {
            return Object.fromEntries(headers);
        }

        if (typeof headers === 'object') {
            return { ...headers };
        }

        return {};
    }

    // 处理 M3U8 URL
    async function handleM3u8Url(url, headers) {
        if (!isM3u8Url(url) || isAdUrl(url)) {
            return;
        }

        try {
            // 获取 M3U8 内容
            const response = await fetch(url, {
                method: 'GET',
                headers: headers
            });

            const content = await response.text();

            // 检查是否是 master playlist
            const variants = parseM3u8Variants(content, url);

            if (variants && variants.length > 0) {
                reportVariants(url, variants, headers);
            } else {
                // 计算时长
                const duration = parseM3u8Duration(content);
                reportM3u8(url, duration, headers);
            }
        } catch (e) {
            // 即使获取失败也报告 URL
            reportM3u8(url, 0, headers);
        }
    }

    // 拦截 XHR
    const originalXHROpen = XMLHttpRequest.prototype.open;
    const originalXHRSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;

    XMLHttpRequest.prototype.open = function(method, url) {
        this._m3u8Url = url;
        this._m3u8Headers = {};

        this.addEventListener('load', function() {
            if (isM3u8Url(this._m3u8Url)) {
                handleM3u8Url(this._m3u8Url, this._m3u8Headers);
            }
        });

        return originalXHROpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
        this._m3u8Headers[key] = value;
        return originalXHRSetRequestHeader.apply(this, arguments);
    };

    // 拦截 fetch
    const originalFetch = window.fetch;

    window.fetch = function(url, options) {
        const urlStr = url?.toString?.() || '';
        const headers = extractHeaders(options?.headers);

        if (isM3u8Url(urlStr)) {
            handleM3u8Url(urlStr, headers);
        }

        return originalFetch.apply(this, arguments);
    };

    console.log('[M3U8Sniffer] Script injected');
})();
