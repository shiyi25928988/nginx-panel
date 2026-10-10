// 网络拓扑页：分层 SVG 渲染 + 悬停高亮 + 节点详情 + 筛选
(function () {
    var g = null;      // /api/topology 数据
    var filterText = '';

    function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }

    // ---------- 加载 ----------
    function load() {
        $('#topoHint').text('解析中…');
        $.get('/api/topology', function (data) {
            g = data;
            var hint = [
                'servers: ' + g.servers.length,
                'upstreams: ' + g.upstreams.length,
                'backends: ' + g.backends.length,
                'files: ' + g.filesScanned
            ].join(' | ');
            $('#topoHint').text(hint);
            render();
        }).fail(apiError);
    }

    // ---------- 布局 ----------
    function columns() {
        var col0 = [{ id: 'C:web', kind: 'client', lines: ['访问方', 'Client'] }];
        var col1 = (g.servers || []).map(function (s) {
            return { id: s.id, kind: 'server', lines: [s.domains ? s.domains.slice(0, 2).join(', ') : '_'],
                     sub: s.listen.join(', ') + (s.ssl ? ' SSL' : ''), obj: s };
        });
        var col2 = [];
        (g.upstreams || []).forEach(function (u) {
            col2.push({ id: u.id, kind: 'upstream', lines: [u.name, (u.servers || []).length + ' backends'], obj: u });
        });
        // 直连 backend（不在 upstream 成员里、且被直连边指向的）
        var memberSet = {};
        (g.upstreams || []).forEach(function (u) {
            (u.servers || []).forEach(function (s) {
                memberSet[u.id + '→' + beOfMember(s)] = 1;
            });
        });
        (g.edges || []).forEach(function (e) {
            if (e.from.indexOf('S:') === 0 && e.to.indexOf('http') === 0) {
                col2.push({ id: e.to, kind: 'backend', lines: [e.to.replace(/^https?:\/\//, '')] });
            }
        });
        var col3 = [];
        (g.backends || []).forEach(function (b) {
            col3.push({ id: b.id, kind: 'backend', lines: [b.id.replace(/^https?:\/\//, '')], obj: b });
        });
        // 静态目录节点并入 col2（显示 root 目录路径，而不是 server id）
        (g.edges || []).forEach(function (e) {
            if (e.to.indexOf('T:') === 0) {
                var rootPath = e.to.substring(2);
                col2.push({ id: e.to, kind: 'static',
                    lines: [rootPath.length > 34 ? '…' + rootPath.substring(rootPath.length - 32) : rootPath],
                    sub: '静态目录' });
            }
        });
        return [
            { title: '客户端', nodes: col0 },
            { title: 'Server（监听/域名）', nodes: col1 },
            { title: 'Upstream / 直连后端 / 静态目录', nodes: col2 },
            { title: '后端（Backend）', nodes: col3 }
        ];
    }

    function beOfMember(m) {
        var host = m.split(' ')[0];
        if (host.indexOf(':') === -1) { host = host + ':80'; }
        return 'http://' + host;
    }

    // ---------- 渲染 ----------
    var nodeBox = {}; // id → {x,y,w,h,kind,lines}
    var selectedId = null;

    function render() {
        if (!g) { return; }
        var cols = columns();
        var rowH = 74, nodeW = 240, nodeH = 54, layerW = 290, topPad = 40;
        var maxRows = Math.max.apply(null, cols.map(function (c) { return Math.max(1, c.nodes.length); }));
        var H = topPad * 2 + maxRows * rowH;
        var W = 80 + cols.length * (nodeW + 60);
        var svg = [];
        svg.push('<svg class="topo-svg" width="100%" height="' + H + '" viewBox="0 0 ' + W + ' ' + H + '">');

        // 列标题
        cols.forEach(function (c, i) {
            svg.push('<text x="' + (60 + i * (nodeW + 60)) + '" y="22" class="elabel">' + esc(c.title) + '</text>');
        });

        // 节点
        cols.forEach(function (c, ci) {
            var x = 60 + ci * (nodeW + 60);
            c.nodes.forEach(function (n, ri) {
                var y = topPad + ri * rowH + (maxRows * rowH - c.nodes.length * rowH) / 2;
                nodeBox[n.id] = { x: x, y: y, w: nodeW, h: nodeH, kind: n.kind, lines: n.lines, obj: n.obj };
                svg.push('<g class="node ' + n.kind + '" data-id="' + esc(n.id) + '">' +
                    '<rect x="' + x + '" y="' + y + '" width="' + nodeW + '" height="' + nodeH + '"/>' +
                    '<text x="' + (x + 12) + '" y="' + (y + 22) + '">' + esc((n.lines || [])[0] || '') + '</text>' +
                    '<text x="' + (x + 12) + '" y="' + (y + 42) + '" opacity="0.75">' + esc(n.sub || (n.lines || [])[1] || '') + '</text>' +
                    '</g>');
            });
        });

        // 边：源节点右缘 → 目标左缘；层差自动中途折中
        (g.edges || []).forEach(function (e) {
            var a = nodeBox[e.from], b = nodeBox[e.to];
            if (!a || !b) { return; }
            var x1 = a.x + nodeW, y1 = a.y + nodeH / 2;
            var x2 = b.x, y2 = b.y + nodeH / 2;
            var mx = (x1 + x2) / 2;
            var d = 'M' + x1 + ',' + y1 + ' C' + mx + ',' + y1 + ' ' + mx + ',' + y2 + ' ' + x2 + ',' + y2;
            svg.push('<path class="edge" data-from="' + esc(e.from) + '" data-to="' + esc(e.to) + '" d="' + d + '"/>');
            if (e.label) {
                svg.push('<text class="elabel" x="' + ((x1 + x2) / 2) + '" y="' + ((y1 + y2) / 2 - 5) + '" text-anchor="middle">' + esc(e.label) + '</text>');
            }
        });

        svg.push('</svg>');
        $('#topoView').html(svg.join(''));
        bindHover();
        applyFilter();
        bindClicks();
    }

    function bindHover() {
        $('.topo-svg .node').hover(function () {
            var id = $(this).data('id');
            $('.topo-svg .edge').each(function () {
                var $e = $(this);
                var hot = $e.data('from') === id || $e.data('to') === id;
                $e.toggleClass('hot', hot);
                $e.toggleClass('dim', !hot);
            });
            $('.topo-svg .node').each(function () {
                var $n = $(this);
                var nid = $n.data('id');
                var connected = nid === id;
                $('.topo-svg .edge').each(function () {
                    var $e = $(this);
                    if ($e.hasClass('hot')) {
                        if ($e.data('from') === nid || $e.data('to') === nid) { connected = true; }
                    }
                });
                $n.toggleClass('dim', !connected);
            });
        }, restore);
    }

    function restore() {
        $('.topo-svg .edge').removeClass('hot').removeClass('dim');
        $('.topo-svg .node').removeClass('dim');
        if (selectedId) {
            $('.topo-svg .node[data-id="' + cssEscape(selectedId) + '"]').addClass('sel');
        }
        applyFilter();
    }

    function bindClicks() {
        $('.topo-svg .node').click(function () {
            var id = $(this).data('id');
            selectedId = id;
            $('.topo-svg .node').removeClass('sel');
            $(this).addClass('sel');
            showDetail(id);
        });
    }

    function showDetail(id) {
        var html = [];
        if (id.indexOf('S:') === 0) {
            var s = (g.servers || []).filter(function (x) { return x.id === id; })[0];
            if (s) {
                html.push('<b>' + esc(id) + '</b> <span class="grey-text">(' + esc(s.file || '') + ')</span><br/>');
                html.push('监听: ' + (s.listen || []).map(function (l) { return chip(l); }).join(' ') + '<br/>');
                html.push('域名: ' + (s.domains || []).map(function (d) { return chip(d); }).join(' '));
                if (s.locations && s.locations.length) {
                    html.push('<br/>Locations:<br/>');
                    s.locations.forEach(function (l) {
                        html.push(chip(l.path + ' → ' + l.type + ' ' + l.routeTarget));
                    });
                }
            }
        } else if (id.indexOf('U:') === 0) {
            var u = (g.upstreams || []).filter(function (x) { return x.id === id; })[0];
            if (u) {
                html.push('<b>upstream ' + esc(u.name) + '</b> <span class="grey-text">(' + esc(u.file || '') + (u.headerInput ? '，' + esc(u.headerInput) : '') + ')</span><br/>');
                html.push((u.servers || []).map(function (m) { return chip(m); }).join(' '));
            }
        } else if (id.indexOf('B') === 0 || id.indexOf('http') === 0) {
            var b = (g.backends || []).filter(function (x) { return x.id === id; })[0];
            if (b) {
                html.push('<b>Backend</b> ' + chip(b.id) + '<br/>scheme: ' + chip(b.scheme) + (b.from ? '<br/>首次引用: ' + esc(b.from) : ''));
            }
        } else if (id.indexOf('T:') === 0) {
            html.push('<b>Static</b> ' + chip(id.substring(2)));
        } else {
            html.push('<b>Client</b><br/>访问方（浏览器/app）经监听端口连入 nginx。');
        }
        $('#topoDetail').html(html.join(''));
    }

    function chip(text) { return '<span class="chip">' + esc(text) + '</span>'; }

    // ---------- 筛选 ----------
    function applyFilter() {
        if (!g || !filterText) {
            $('.topo-svg .node[data-dimmed]').removeClass('dim');
            return;
        }
        var ft = filterText.toLowerCase();
        var nodeIds = {};
        Object.keys(nodeBox).forEach(function (id) {
            var n = nodeBox[id];
            var hay = JSON.stringify([id, n.lines, n.obj]);
            nodeIds[id] = hay.toLowerCase().indexOf(ft) !== -1;
        });
        // 通过筛选节点的边链路也保留
        var pass = nodeIds;
        for (var i = 0; i < 3; i++) {
            (g.edges || []).forEach(function (e) {
                if (pass[e.from] && pass[e.to] === false && edgeCarriesFilter(e)) { pass[e.to] = true; }
                if (pass[e.to] === false && pass[e.from] ? false : pass[e.from] && pass[e.to] === false) { }
            });
        }
        (g.edges || []).forEach(function (e) {
            if (pass[e.from] && (pass[e.to] || edgeRfrom(e))) { pass[e.to] = true; }
        });
        (g.edges || []).forEach(function (e) {
            if (pass[e.to] && (pass[e.from] || edgeRfrom(e))) { pass[e.from] = true; }
        });
        $('.topo-svg .node').each(function () {
            var $n = $(this);
            var nid = $n.data('id');
            $n.toggleClass('dim', !pass[nid]);
        });
    }

    function edgeRfrom(e) { return false; }
    function edgeCarriesFilter(e) { return false; }

    // ---------- 事件 ----------
    $(document).ready(function () {
        $('#topoFilter').on('input', function () {
            filterText = $(this).val().trim();
            applyFilter();
        });
        $('#btnTopoRefresh').click(function (e) { e.preventDefault(); load(); });
        load();
    });

    function cssEscape(s) { return String(s).replace(/:/g, '\\:').replace(/\\/g, '\\\\').replace(/ /g, '\\ '); }
})();
