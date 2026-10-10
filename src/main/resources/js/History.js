// 版本历史页：按文件分页查看版本、diff 对比、回滚
var historyPage_ = 1;
var historySize = 10;
var historyPath = null;
var lastDiffVersionId = null;

$(document).ready(function () {
    $('.modal').modal();

    function loadFileOptions(preselect) {
        $.get('/api/files', function (nodes) {
            var sel = $('#historyFileSelect').empty();
            sel.append('<option value="" disabled>选择文件…</option>');
            (nodes || []).forEach(function (n) {
                if (!n.directory && n.editable) {
                    sel.append($('<option/>').attr('value', n.path).text(n.path));
                }
            });
            var fromQuery = new URLSearchParams(location.search).get('file');
            if (fromQuery) { sel.val(fromQuery); $('#btnLoadHistory').click(); }
        }).fail(apiError);
    }

    function loadHistory() {
        historyPath = $('#historyFileSelect').val();
        if (!historyPath) { M.toast({ html: '请先选择文件' }); return; }
        $('#historyFilePath').text('文件: ' + historyPath);
        $.get('/api/history', { path: historyPath, page: historyPage_, size: historySize }, function (data) {
            var body = $('#historyTableBody').empty();
            (data.list || []).forEach(function (v) {
                // 列表接口不回传内容（体积），删除动作以 MD5 常量标记
                var deleted = v.md5 === 'DELETED';
                var tr = $('<tr/>')
                    .append($('<td/>').text('v' + v.versionNo + (deleted ? '（删除）' : '')))
                    .append($('<td/>').text(formatTime(v.createTime)))
                    .append($('<td/>').text(v.editor))
                    .append($('<td/>').text(v.reason || ''))
                    .append($('<td/>').text(v.md5 || '').css('font-size', '0.75rem'));
                var ops = $('<td/>');
                if (!deleted) {
                    ops.append($('<a href="#" class="green-text">查看/Diff</a>')
                        .click(function (e) { e.preventDefault(); showDiff(v.id); }));
                } else {
                    ops.append($('<span class="grey-text">已删除</span>'));
                }
                tr.append(ops);
                body.append(tr);
            });
            if (!data.list || data.list.length === 0) {
                body.append('<tr><td colspan="6" class="grey-text">该文件还没有版本记录</td></tr>');
            }
            var totalPage = Math.max(1, Math.ceil(data.total / historySize));
            $('#historyPageInfo').text(historyPage_ + ' / ' + totalPage + '（共 ' + data.total + ' 条）');
        }).fail(apiError);
    }

    // ---- diff：默认展示"该版本修改前 → 修改后"；可切换与当前磁盘对比 ----
    var diffVersion = null;   // 当前 diff 的版本对象（含 prevContent/content）
    var diffVsCurrent = false; // 对比目标：false=修改前→修改后；true=该版本→当前磁盘
    var diskContent = '';

    function showDiff(versionId) {
        lastDiffVersionId = versionId;
        diffVsCurrent = false;
        $.when(
            $.get('/api/history/version', { id: versionId }),
            $.get('/api/file', { path: historyPath })
        ).then(function (versionResp, fileResp) {
            diffVersion = versionResp[0];
            diskContent = (fileResp[0] && fileResp[0].content) || '';
            $('#diffModal').modal('open');
            renderDiffView();
        }, function (xhr) { apiError(xhr[0] || xhr); });
    }

    function renderDiffView() {
        if (!diffVersion) { return; }
        if (diffVsCurrent) {
            $('#diffTitle').text('Diff: ' + historyPath + '（v' + diffVersion.versionNo + ' → 当前磁盘）');
            $('#diffBody').html(renderDiff(diffVersion.content || '', diskContent));
        } else {
            var prev = diffVersion.prevContent;
            var what = prev ? '（修改前 → 修改后）' : '（新建文件）';
            $('#diffTitle').text('Diff: ' + historyPath + ' ' + what);
            $('#diffBody').html(renderDiff(prev || '', diffVersion.content || ''));
        }
        $('#diffToggle').text(diffVsCurrent ? '对比：该版本修改前后' : '对比：与当前磁盘');
        $('#diffMeta').text('版本时间: ' + formatTime(diffVersion.createTime)
            + '   操作人: ' + diffVersion.editor
            + '   原因: ' + (diffVersion.reason || ''));
    }

    $('#diffToggle').click(function (e) {
        e.preventDefault();
        diffVsCurrent = !diffVsCurrent;
        renderDiffView();
    });

    $('#btnLoadHistory').click(function () { historyPage_ = 1; loadHistory(); });
    $('#btnHistoryPrev').click(function () { if (historyPage_ > 1) { historyPage_--; loadHistory(); } });
    $('#btnHistoryNext').click(function () { historyPage_++; loadHistory(); });

    loadFileOptions();
});

// ---------- 左右分栏 diff（LCS 对齐，配对成修改行，两侧异色高亮） ----------
// 输出结构：<table> 每行 = 左(旧版本单元格) + 右(新版本单元格)
//   相同行 —— 两侧同色
//   修改行 —— 左红底 + 右绿底（LCS 的删除块与插入块按序配对）
//   纯删除 —— 左红底 + 右空；纯新增 —— 左空 + 右绿底
function renderDiff(oldText, newText) {
    var esc = function (s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); };
    var oldLines = oldText ? String(oldText).split('\n') : [];
    var newLines = newText ? String(newText).split('\n') : [];
    var n = oldLines.length, m = newLines.length;

    // ---- LCS 动态规划（配置文件通常 < 1000 行，可接受）----
    var dp = [];
    for (var i = 0; i <= n; i++) { dp.push(new Array(m + 1).fill(0)); }
    for (var i2 = n - 1; i2 >= 0; i2--) {
        for (var j2 = m - 1; j2 >= 0; j2--) {
            dp[i2][j2] = oldLines[i2] === newLines[j2]
                ? dp[i2 + 1][j2 + 1] + 1
                : Math.max(dp[i2 + 1][j2], dp[i2][j2 + 1]);
        }
    }

    // ---- 生成编辑操作序列 (' Cookie' / 'del' / 'ins') 并配对成块 ----
    var rows = []; // {type:'eq'|'mod'|'del'|'ins', left:'#'|text, leftNo, right:'#'|text, rightNo}
    var dels = [], inss = [];
    function flush() {
        var pairs = Math.min(dels.length, inss.length);
        for (var p = 0; p < pairs; p++) {
            rows.push({ type: 'mod', L: dels[p], R: inss[p] });
        }
        for (var d = pairs; d < dels.length; d++) { rows.push({ type: 'del', L: dels[d] }); }
        for (var q = pairs; q < inss.length; q++) { rows.push({ type: 'ins', R: inss[q] }); }
        dels = []; inss = [];
    }
    var x = 0, y = 0;
    while (x < n && y < m) {
        if (oldLines[x] === newLines[y]) { flush(); rows.push({ type: 'eq', L: oldLines[x], R: newLines[y] }); x++; y++; }
        else if (dp[x + 1][y] >= dp[x][y + 1]) { dels.push(oldLines[x]); x++; }
        else { inss.push(newLines[y]); y++; }
    }
    while (x < n) { dels.push(oldLines[x]); x++; flush(); }
    while (y < m) { inss.push(newLines[y]); y++; flush(); }
    flush();

    if (rows.every(function (r) { return r.type === 'eq'; }) && rows.length > 0) {
        return '<div class="grey-text" style="padding:8px;">内容一致（无差异）</div>';
    }
    if (rows.length === 0) {
        return '<div class="grey-text" style="padding:8px;">（一侧为空 / 无内容）</div>';
    }

    // ---- 渲染双列 ----
    var html = ['<table class="diff-table"><thead><tr>' +
        '<th class="gutter">旧#</th><th>旧版本（- 删除 / 红色）</th>' +
        '<th class="gutter">新#</th><th>新版本（+ 新增 / 绿色）</th></tr></thead><tbody>'];
    var leftNo = 0, rightNo = 0;
    rows.forEach(function (r) {
        var L, R, ln, rn, cls = '';
        if (r.type === 'eq' || r.type === 'mod') {
            leftNo++; rightNo++; ln = leftNo; rn = rightNo;
            L = r.L; R = r.R;
            cls = r.type === 'mod' ? 'diff-left diff-right' : '';
        } else if (r.type === 'del') {
            leftNo++; ln = leftNo; rn = '';
            L = r.L; R = ''; cls = 'diff-left';
        } else {
            rightNo++; ln = ''; rn = rightNo;
            L = ''; R = r.R; cls = 'diff-right';
        }
        html.push('<tr class="' + cls + '">' +
            '<td class="gutter">' + ln + '</td><td class="cell-old">' + esc(L) + '</td>' +
            '<td class="gutter">' + rn + '</td><td class="cell-new">' + esc(R) + '</td></tr>');
    });
    html.push('</tbody></table>');
    return html.join('');
}
