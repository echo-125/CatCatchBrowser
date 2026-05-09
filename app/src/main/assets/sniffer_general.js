// M3U8 嗅探脚本 - 通用模式
// 基础网络请求拦截 + 响应内容检查 + 内联脚本扫描

(function() {
    'use strict';

    const TAG = 'General';
    const C = window.__M3u8Common;
    if (!C) { console.error('[M3U8Sniffer] Common module not loaded'); return; }

    // ==================== 增强型 XHR 拦截 ====================
    function hookXHR() {
        const _xhrOpen = XMLHttpRequest.prototype.open;
        const _xhrSetHeader = XMLHttpRequest.prototype.setRequestHeader;

        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
            if (!this._snifferHeaders) this._snifferHeaders = {};
            this._snifferHeaders[key] = value;
            return _xhrSetHeader.apply(this, arguments);
        };

        XMLHttpRequest.prototype.open = function(method, url) {
            this._snifferUrl = url;
            this.addEventListener('load', function() {
                const urlStr = this._snifferUrl || url || '';
                const headers = this._snifferHeaders || {};

                // URL 匹配
                if (C.isM3u8Url(urlStr)) {
                    const text = typeof this.responseText === 'string' ? this.responseText : null;
                    C.handleM3u8Url(urlStr, text, headers, TAG);
                    return;
                }

                // 响应内容检查：非 m3u8 URL 但响应内容是 M3U8
                if (typeof this.response === 'string' && this.response.length > 0) {
                    const upper = this.response.substring(0, 30).toUpperCase();
                    if (upper.includes('#EXTM3U')) {
                        C.handleM3u8Text(this.response, this.responseURL || urlStr, TAG);
                        return;
                    }
                    // JSON 响应中查找 m3u8 URL
                    if (upper.charAt(0) === '{' || upper.charAt(0) === '[') {
                        try {
                            const json = JSON.parse(this.response);
                            C.findMediaUrlsInObject(json, function(found, type) {
                                if (type === 'url') {
                                    C.handleM3u8Url(found, null, headers, TAG);
                                } else {
                                    C.handleM3u8Text(found, urlStr, TAG);
                                }
                            });
                        } catch (e) {}
                    }
                }
            });
            return _xhrOpen.apply(this, arguments);
        };

        C.log(TAG, 'XHR 拦截已启用');
    }

    // ==================== 增强型 Fetch 拦截 ====================
    function hookFetch() {
        const _fetch = window.fetch;

        window.fetch = function(input, init) {
            const requestHeaders = C.extractFetchHeaders(init?.headers);

            return _fetch.apply(this, arguments).then(function(response) {
                const clone = response.clone();
                const requestUrl = (typeof input === 'object' ? input.url : input) || '';

                // URL 匹配
                if (C.isM3u8Url(requestUrl)) {
                    clone.text().then(function(text) {
                        C.handleM3u8Url(requestUrl, text, requestHeaders, TAG);
                    }).catch(function() {
                        C.handleM3u8Url(requestUrl, null, requestHeaders, TAG);
                    });
                    return response;
                }

                // 响应内容检查
                clone.text().then(function(text) {
                    if (!text) return;
                    var upper = text.substring(0, 30).toUpperCase();
                    if (upper.includes('#EXTM3U')) {
                        C.handleM3u8Text(text, requestUrl, TAG);
                        return;
                    }
                    // JSON 中查找
                    if (upper.charAt(0) === '{' || upper.charAt(0) === '[') {
                        try {
                            var json = JSON.parse(text);
                            C.findMediaUrlsInObject(json, function(found, type) {
                                if (type === 'url') {
                                    C.handleM3u8Url(found, null, requestHeaders, TAG);
                                } else {
                                    C.handleM3u8Text(found, requestUrl, TAG);
                                }
                            });
                        } catch (e) {}
                    }
                }).catch(function() {});

                return response;
            });
        };

        C.log(TAG, 'Fetch 拦截已启用');
    }

    // ==================== JSON.parse Hook ====================
    function hookJSONParse() {
        var _JSONparse = JSON.parse;

        JSON.parse = function() {
            var data = _JSONparse.apply(this, arguments);
            try {
                C.findMediaUrlsInObject(data, function(found, type) {
                    if (type === 'url') {
                        C.handleM3u8Url(found, null, {}, TAG);
                    } else if (type === 'inline') {
                        C.handleM3u8Text(found, '', TAG);
                    }
                });
            } catch (e) {}
            return data;
        };
    }

    // ==================== 内联 Script 扫描 ====================
    function scanInlineScripts() {
        var urlPattern = /["']((?:(?:https?:)?\/\/)?[^"'\s]*?\.m3u8(?:\?[^"'\s]*)?)["']/gi;

        document.querySelectorAll('script:not([src])').forEach(function(script) {
            var content = script.textContent || '';
            if (!content) return;

            var match;
            while ((match = urlPattern.exec(content)) !== null) {
                var url = (match[1] || '').replace(/^['"]|['"]$/g, '').trim();
                if (!url) continue;
                if (url.startsWith('//')) {
                    url = window.location.protocol + url;
                } else if (url.startsWith('/')) {
                    url = window.location.origin + url;
                }
                if (url.includes('.m3u8')) {
                    C.handleM3u8Url(url, null, {}, TAG);
                }
            }
        });

        C.log(TAG, '内联 Script 扫描完成');
    }

    // ==================== 初始化 ====================
    function init() {
        C.log(TAG, 'Script loaded (General Mode)');
        if (C.hasBridge()) {
            try { window['SnifferBridge'].onScriptLoaded('General'); } catch (e) {}
        }

        // 安装 hooks
        hookXHR();
        hookFetch();
        hookJSONParse();

        // 网络请求拦截（公共模块的 XHR/fetch 拦截，作为补充）
        C.interceptNetworkRequests(TAG);

        // DOM 扫描
        setTimeout(function() {
            scanInlineScripts();
            C.deepScan(TAG);
            C.observeVideoElements(TAG);
        }, 1000);

        // 多次延迟扫描（捕获动态加载的内容）
        setTimeout(function() { C.deepScan(TAG); }, 3000);
        setTimeout(function() { C.deepScan(TAG); }, 6000);
        setTimeout(function() { C.deepScan(TAG); }, 10000);
    }

    init();
})();
