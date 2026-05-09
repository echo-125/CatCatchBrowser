// M3U8 嗅探脚本 - 猫抓模式
// 参考油猴插件 cat-catch (catch-script/search.js)
// 特点: 深度内容嗅探 - 不仅匹配 URL，还检查响应内容、hook 编码 API

(function() {
    'use strict';

    const TAG = 'CatCatch';
    const C = window.__M3u8Common;
    if (!C) { console.error('[M3U8Sniffer] Common module not loaded'); return; }

    const DURATION = {
        MIN_SECONDS: 60,
        INIT_DELAY_MS: 1500
    };

    // 媒体 URL 扩展名正则
    const mediaExtRE = /\.(m3u8|mp4|flv|mpd)(\?[^"'\s]*)?$/i;
    const inlineScriptUrlRE = /["']((?:(?:https?:)?\/\/)?[^"'\s]*?\.(?:m3u8|mp4|flv)(?:\?[^"'\s]*)?)["']/gi;

    // M3U8 文本累积缓冲（用于 String.fromCharCode hook）
    let m3u8Buffer = '';

    // toString 伪装辅助
    function spoofToString(fn, original) {
        fn.toString = function() { return original.toString(); };
        return fn;
    }

    // 上报发现的媒体 URL（去重后走公共模块处理）
    function reportMediaUrl(url) {
        if (!url || typeof url !== 'string') return;
        url = url.trim();
        if (C.state.capturedUrls.has(url)) return;
        if (url.includes('.m3u8')) {
            C.handleM3u8Url(url, null, {}, TAG, DURATION.MIN_SECONDS);
        }
    }

    // 上报 M3U8 文本内容
    function reportM3u8Text(text, sourceUrl) {
        if (!text || typeof text !== 'string') return;
        if (text.toUpperCase().includes('#EXTM3U')) {
            C.handleM3u8Text(text, sourceUrl, TAG, DURATION.MIN_SECONDS);
        }
    }

    // ==================== 层 1: 增强型 XHR 拦截 ====================
    function hookXHR() {
        const _xhrOpen = XMLHttpRequest.prototype.open;
        const _xhrSetHeader = XMLHttpRequest.prototype.setRequestHeader;

        // 拦截请求头
        XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
            if (!this._catCatchHeaders) this._catCatchHeaders = {};
            this._catCatchHeaders[key] = value;
            return _xhrSetHeader.apply(this, arguments);
        };
        spoofToString(XMLHttpRequest.prototype.setRequestHeader, _xhrSetHeader);

        // 拦截 open + 响应内容检查
        XMLHttpRequest.prototype.open = function(method, url) {
            this._catCatchUrl = url;
            this._catCatchMethod = (method || 'GET').toUpperCase();

            this.addEventListener('readystatechange', function() {
                if (this.status !== 200) return;

                const responseUrl = this.responseURL || this._catCatchUrl || '';

                // 检查响应类型
                if (this.responseType === 'arraybuffer' && this.response?.byteLength) {
                    // .ts 片段也记录 baseUrl（用于解析相对路径）
                    return;
                }

                if (typeof this.response === 'object' || this.response === '' || typeof this.response !== 'string') {
                    return;
                }

                const text = this.response;

                // Data URI 检测
                if (C.isDataM3u8(text)) {
                    const decoded = C.decodeDataM3u8(text);
                    if (decoded) reportM3u8Text(decoded, responseUrl);
                    return;
                }

                // URL 本身就是 Data URI
                if (C.isDataM3u8(responseUrl)) {
                    const decoded = C.decodeDataM3u8(responseUrl);
                    if (decoded) reportM3u8Text(decoded, responseUrl);
                    return;
                }

                // #EXTM3U 内容嗅探
                const upper = text.toUpperCase();
                if (upper.includes('#EXTM3U')) {
                    if (upper.substring(0, 7) === '#EXTM3U') {
                        // 响应直接就是 M3U8
                        reportM3u8Text(text, responseUrl);
                        return;
                    }
                    // M3U8 可能嵌套在 JSON 中
                    try {
                        const json = JSON.parse(text);
                        C.findMediaUrlsInObject(json, function(found, type) {
                            if (type === 'url') reportMediaUrl(found);
                            else reportM3u8Text(found, responseUrl);
                        });
                    } catch (e) {}
                    return;
                }

                // JSON 响应中查找媒体 URL
                try {
                    const json = JSON.parse(text);
                    C.findMediaUrlsInObject(json, function(found, type) {
                        if (type === 'url') reportMediaUrl(found);
                        else reportM3u8Text(found, responseUrl);
                    });
                } catch (e) {}
            });

            return _xhrOpen.apply(this, arguments);
        };
        spoofToString(XMLHttpRequest.prototype.open, _xhrOpen);

        C.log(TAG, 'XHR 拦截已启用');
    }

    // ==================== 层 2: 增强型 Fetch 拦截 ====================
    function hookFetch() {
        const _fetch = window.fetch;

        window.fetch = function(input, init) {
            const requestHeaders = C.extractFetchHeaders(init?.headers);

            return _fetch.apply(this, arguments).then(function(response) {
                const clone = response.clone();

                response.text().then(function(text) {
                    if (!text) return;

                    const requestUrl = (typeof input === 'object' ? input.url : input) || '';

                    // Data URI 检测
                    if (C.isDataM3u8(text.substring(0, 30))) {
                        const decoded = C.decodeDataM3u8(text);
                        if (decoded) reportM3u8Text(decoded, requestUrl);
                        return;
                    }

                    // #EXTM3U 内容嗅探
                    if (text.substring(0, 7).toUpperCase() === '#EXTM3U') {
                        reportM3u8Text(text, requestUrl);
                        return;
                    }

                    // JSON 响应中查找媒体 URL
                    if (text.charAt(0) === '{' || text.charAt(0) === '[') {
                        try {
                            const json = JSON.parse(text);
                            C.findMediaUrlsInObject(json, function(found, type) {
                                if (type === 'url') reportMediaUrl(found);
                                else reportM3u8Text(found, requestUrl);
                            });
                        } catch (e) {}
                    }
                }).catch(function() {});

                return clone;
            });
        };
        spoofToString(window.fetch, _fetch);

        C.log(TAG, 'Fetch 拦截已启用');
    }

    // ==================== 层 3: JSON.parse Hook ====================
    function hookJSONParse() {
        const _JSONparse = JSON.parse;

        JSON.parse = function() {
            const data = _JSONparse.apply(this, arguments);

            try {
                C.findMediaUrlsInObject(data, function(found, type) {
                    if (type === 'url') reportMediaUrl(found);
                    else reportM3u8Text(found);
                });
            } catch (e) {}

            return data;
        };
        spoofToString(JSON.parse, _JSONparse);

        C.log(TAG, 'JSON.parse Hook 已启用');
    }

    // ==================== 层 4: 编码 API Hooks ====================
    function hookEncodeAPI() {
        // btoa hook
        const _btoa = btoa;
        btoa = function(data) {
            const result = _btoa.apply(this, arguments);
            try {
                if (typeof data === 'string' && data.substring(0, 7).toUpperCase() === '#EXTM3U') {
                    reportM3u8Text(data);
                }
            } catch (e) {}
            return result;
        };
        spoofToString(btoa, _btoa);

        // atob hook
        const _atob = atob;
        atob = function(base64) {
            const result = _atob.apply(this, arguments);
            try {
                if (result.substring(0, 7).toUpperCase() === '#EXTM3U') {
                    reportM3u8Text(result);
                }
            } catch (e) {}
            return result;
        };
        spoofToString(atob, _atob);

        C.log(TAG, 'btoa/atob Hook 已启用');
    }

    // ==================== 层 5: Array.prototype.join Hook ====================
    function hookArrayJoin() {
        const _arrayJoin = Array.prototype.join;

        Array.prototype.join = function() {
            const result = _arrayJoin.apply(this, arguments);
            try {
                if (result && result.length >= 7 && result.substring(0, 7).toUpperCase() === '#EXTM3U') {
                    reportM3u8Text(result);
                }
            } catch (e) {}
            return result;
        };
        spoofToString(Array.prototype.join, _arrayJoin);

        C.log(TAG, 'Array.join Hook 已启用');
    }

    // ==================== 层 6: String.fromCharCode Hook ====================
    function hookStringFromCharCode() {
        const _fromCharCode = String.fromCharCode;

        // 使用 Proxy 拦截静态方法
        String.fromCharCode = new Proxy(_fromCharCode, {
            apply: function(target, thisArg, args) {
                const result = Reflect.apply(target, thisArg, args);

                try {
                    if (result.length >= 7) {
                        if (result.substring(0, 7) === '#EXTM3U' || result.includes('#EXTINF:')) {
                            m3u8Buffer += result;
                            if (m3u8Buffer.includes('#EXT-X-ENDLIST')) {
                                const complete = m3u8Buffer.split('#EXT-X-ENDLIST')[0] + '#EXT-X-ENDLIST';
                                reportM3u8Text(complete);
                                m3u8Buffer = '';
                            }
                        }
                    }
                } catch (e) {}

                return result;
            }
        });
        spoofToString(String.fromCharCode, _fromCharCode);

        C.log(TAG, 'String.fromCharCode Hook 已启用');
    }

    // ==================== 层 7: String.prototype.indexOf Hook ====================
    function hookStringIndexOf() {
        const _indexOf = String.prototype.indexOf;

        String.prototype.indexOf = function(searchValue, fromIndex) {
            const result = _indexOf.apply(this, arguments);

            try {
                if (searchValue === '#EXTM3U' && result !== -1) {
                    const data = this.substring(fromIndex || 0);
                    reportM3u8Text(data);
                }
            } catch (e) {}

            return result;
        };
        spoofToString(String.prototype.indexOf, _indexOf);

        C.log(TAG, 'String.indexOf Hook 已启用');
    }

    // ==================== 层 8: 内联 Script 扫描 ====================
    function scanInlineScripts() {
        const patterns = [
            /["']((?:(?:https?:)?\/\/)?[^"'\s]*?\.(?:m3u8|mp4|flv)(?:\?[^"'\s]*)?)["']/gi,
            /url\s*[:=]\s*["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/gi
        ];

        document.querySelectorAll('script:not([src])').forEach(function(script) {
            const content = script.textContent || '';
            if (!content) return;

            patterns.forEach(function(pattern) {
                let match;
                while ((match = pattern.exec(content)) !== null) {
                    let url = match[1] || match[0];
                    url = url.replace(/^['"]|['"]$/g, '').trim();
                    if (url && !url.startsWith('http')) {
                        if (url.startsWith('//')) {
                            url = window.location.protocol + url;
                        } else if (url.startsWith('/')) {
                            url = window.location.origin + url;
                        }
                    }
                    if (url && mediaExtRE.test(url)) {
                        reportMediaUrl(url);
                    }
                }
            });
        });

        C.log(TAG, '内联 Script 扫描完成');
    }

    // ==================== 层 9: Worker 拦截（可选） ====================
    function hookWorker() {
        if (typeof Worker === 'undefined') return;

        const _Worker = Worker;

        try {
            self.Worker = function(scriptURL, options) {
                try {
                    // 尝试同步获取 worker 脚本并注入嗅探代码
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', scriptURL, false);
                    xhr.send();
                    if (xhr.status === 200) {
                        const blob = new Blob([
                            '(' + hookWorkerContent.toString() + ')();\n',
                            xhr.response
                        ], { type: 'text/javascript' });
                        const worker = new _Worker(URL.createObjectURL(blob), options);
                        worker.addEventListener('message', function(event) {
                            if (event.data && event.data._catCatchMedia) {
                                reportM3u8Text(event.data.m3u8Text, event.data.source);
                            }
                        });
                        return worker;
                    }
                } catch (e) {
                    // 同步 XHR 失败，降级到原始 Worker
                }
                return new _Worker(scriptURL, options);
            };
            spoofToString(self.Worker, _Worker);

            C.log(TAG, 'Worker Hook 已启用');
        } catch (e) {
            C.log(TAG, 'Worker Hook 失败（降级）: ' + e.message);
        }
    }

    // Worker 内注入的嗅探代码
    function hookWorkerContent() {
        const _fetch = self.fetch;
        if (_fetch) {
            self.fetch = function(input, init) {
                return _fetch.apply(this, arguments).then(function(response) {
                    const clone = response.clone();
                    response.text().then(function(text) {
                        if (text && text.substring(0, 7).toUpperCase() === '#EXTM3U') {
                            try {
                                self.postMessage({
                                    _catCatchMedia: true,
                                    m3u8Text: text,
                                    source: (typeof input === 'object' ? input.url : input) || ''
                                });
                            } catch (e) {}
                        }
                    }).catch(function() {});
                    return clone;
                });
            };
        }
    }

    // ==================== 初始化 ====================
    function init() {
        C.log(TAG, 'Script loaded (CatCatch Mode)');
        if (C.hasBridge()) {
            try { window['SnifferBridge'].onScriptLoaded('CatCatch'); } catch (e) {}
        }

        // 安装所有 hook
        hookXHR();
        hookFetch();
        hookJSONParse();
        hookEncodeAPI();
        hookArrayJoin();
        hookStringFromCharCode();
        hookStringIndexOf();
        hookWorker();

        // DOM 扫描（复用公共模块）
        setTimeout(function() {
            scanInlineScripts();
            C.deepScan(TAG, DURATION.MIN_SECONDS);
            C.observeVideoElements(TAG, DURATION.MIN_SECONDS);
        }, DURATION.INIT_DELAY_MS);

        // 多次延迟扫描
        setTimeout(function() { C.deepScan(TAG, DURATION.MIN_SECONDS); }, 5000);
        setTimeout(function() { C.deepScan(TAG, DURATION.MIN_SECONDS); }, 10000);
    }

    init();
})();
