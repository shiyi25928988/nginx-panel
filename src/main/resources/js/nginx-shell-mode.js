// Nginx Shell 定制 CodeMirror nginx 模式
// 解决 webjar 自带 mode/nginx.js 的误报问题：路径/glob（/etc/nginx/*.conf）、
// 正则 location 等无法被其词法器识别的 token 会整段标红（cm-error）。
// 本模式的底线是：任何未识别 character 一律按普通文本渲染，绝不打 error。
(function () {
    'use strict';
    if (typeof CodeMirror === 'undefined') {
        return;
    }

    var directives = /^(?:user|worker_processes|worker_cpu_affinity|worker_rlimit_nofile|pid|error_log|daemon|env|include
|events|http|server|location|upstream|map|split_clients|geo|if|set|break|return|rewrite|try_files|root|alias|index|autoindex\
|client_max_body_size|client_body_buffer_size|default_type|types_hash_max_size|listen|server_name|proxy_pass|proxy_set_header\
|proxy_http_version|proxy_connect_timeout|proxy_send_timeout|proxy_read_timeout|proxy_buffering|proxy_next_upstream\
|fastcgi_pass|fastcgi_param|uwsgi_pass|grpc_pass|ssl_certificate|ssl_certificate_key|ssl_protocols|ssl_ciphers\
|ssl_prefer_server_ciphers|ssl_session_cache|ssl_session_timeout|access_log|log_format|error_page|add_header|expires\
|sendfile|tcp_nopush|tcp_nodelay|keepalive_timeout|keepalive_requests|gzip|gzip_types|gzip_comp_level|gzip_min_length\
|gzip_vary|gzip_proxied|limit_req|limit_req_zone|limit_conn|limit_conn_zone|charset|server_tokens|types|resolver\
|real_ip_header|real_ip_recursive|set_real_ip_from|deny|allow|auth_basic|auth_basic_user_file|satisfy|autoindex\
|proxy_cache_path|proxy_cache|proxy_cache_valid|variables_hash_max_size)$/;
    // location/upstream 内专用
    var blockWords = /^(?:least_conn|ip_hash|hash|keepalive|hash_init|zone|sticky|least_time)$/;
    var atoms = /^(?:on|off|true|false|default_server|http|https|unix|all|deprecated|md5|sha1|combined|main|build)$/;
    var fileNameLike = /\.(?:conf|types|log|pem|crt|key|cer|csr|htpasswd|html|xml|json|pid)$/i;

    function mode() {
        return {
            startState: function () {},
            copyState: function (s) { return s; },
            token: function (stream) {
                if (stream.eatSpace()) { return null; }
                // 注释
                if (stream.match(/^#.*/)) { return 'comment'; }
                // 受支持的字符串
                if (stream.match(/^"(?:[^"\\]|\\.)*"?/)) { return 'string'; }
                if (stream.match(/^'(?:[^'\\]|\\.)*'?/)) { return 'string'; }
                // 变量 $remote_addr
                if (stream.eat('$')) { stream.eatWhile(/[\w]/); return 'variable-2'; }
                // 大括号（nginx 块结构）
                if (stream.match(/^[{};]/)) { return 'metal'; }
                // 数字 + 体积/时间单位（768、10m、60s、1d）
                if (stream.match(/^[+-]?\d/)) {
                    stream.eatWhile(/[\w.%]/);
                    return 'number';
                }
                // 正则 location（~ / ~* 如 /\.conf$/）
                if (stream.match(/^~\*?\s*/) || stream.match(/^\^~/)) {
                    // 正则内容吃到行尾
                    if (!stream.eol()) { stream.skipToEnd(); }
                    return 'string-2';
                }
                // 词法单元：指令/参数/路径/glob。字符集故意宽泛：字母数字 + _-. /* 等路径符号
                if (stream.match(/^[A-Za-z_][\w\-.\/\*]*/)) {
                    var w = stream.current();
                    if (directives.test(w)) { return 'keyword'; }
                    if (blockWords.test(w)) { return 'atom'; }
                    if (atoms.test(w)) { return 'atom'; }
                    if (fileNameLike.test(w)) { return 'string'; }
                    if (w.indexOf('/') !== -1 && (w.charAt(0) === '/' || w.indexOf('*') !== -1)) {
                        // 绝对路径或含 glob 的路径整体当字符串渲染
                        return 'string';
                    }
                    return null;
                }
                // 任何剩余字符按普通文本走，不打 error（本早模式的错误全部为误报）
                stream.next();
                return null;
            },
            fold: 'brace',
            indent: function () { return 0; }
        };
    }

    CodeMirror.defineMode('nginx-shell', mode);
    // 与旧名兼容（防止页面在其它入口按 mode:'nginx' 使用）
    CodeMirror.defineMIME('text/x-nginx-conf', 'nginx-shell');
})();
