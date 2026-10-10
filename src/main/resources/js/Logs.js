// Nginx 日志面板：文件自动发现、tail 读取、前端关键字过滤、自动刷新、跟随滚动
var autoScrollOn = true;
var autoRefreshTimer = null;
var allowedFiles = [];

function fmtBytes(n) {
    if (n === null || n === undefined) { return '-'; }
    var units = ['B', 'KB', 'MB', 'GB'];
    var v = n, i = 0;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return v.toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

function loadFileOptions() {
    $.get('/api/nglog/files', function (list) {
        allowedFiles = list || [];
        var sel = $('#logFileSelect').empty();
        if (!allowedFiles.length) {
            sel.append('<option value="" disabled selected>未发现任何日志文件</option>');
            $('#logView').text('配置中没有声明 error_log/access_log，且二进制默认路径不可读。');
            return;
        }
        allowedFiles.forEach(function (f) {
            var label = f.name + '  ' + fmtBytes(f.size);
            sel.append($('<option/>').attr('value', f.path)
                .text((f.exists ? '📄 ' : '⚠️ ') + label));
        });
        // 保持当前选择
        if (sel.data('prev')) { sel.val(sel.data('prev')); }
        loadTail();
    }).fail(apiError);
}

function loadTail() {
    var path = $('#logFileSelect').val();
    if (!path) { return; }
    $('#logFileSelect').data('prev', path);
    var lines = $('#tailLines').val() || 500;
    var filter = $('#logFilter').val();
    var started = Date.now();
    $.get('/api/nglog/tail', { path: path, lines: lines }, function (resp) {
        var text = resp && resp.content ? resp.content : '';
        var view = $('#logView');
        var shown = '';
        var total = String(text).split('\n');
        if (filter) {
            shown = total.filter(function (l) { return l.indexOf(filter) !== -1; }).join('\n');
            if (total.length && shown === '' ) { shown = '(无匹配 ' + total.length + ' 行中的过滤条件: ' + filter + ')'; }
        } else {
            shown = text === '' ? '(空文件或暂无日志)' : text;
        }
        view.text(shown);
        if (autoScrollOn) { view.scrollTop(view[0].scrollHeight); }
        $('#logFetchHint').text('读取 ' + lines + ' 行，耗时 ' + (Date.now() - started) + 'ms · ' + path);
    }).fail(apiError);
}

$(document).ready(function () {
    $('#logFileSelect').change(loadTail);
    $('#tailLines').change(loadTail);
    $('#logFilter').on('input', function () { loadTail(); });

    $('#btnLogRefresh').click(loadTail);

    $('#btnAutoScroll').click(function () {
        autoScrollOn = !autoScrollOn;
        $(this).removeClass('grey darken-1 green');
        $(this).addClass(autoScrollOn ? 'green' : 'grey darken-1');
        M.toast({ html: autoScrollOn ? '已开启跟随滚动' : '已关闭跟随滚动' });
    });

    $('#logAutoRefresh').change(function () {
        if (this.checked) {
            autoRefreshTimer = setInterval(loadTail, 10000);
        } else {
            clearInterval(autoRefreshTimer);
        }
    });

    loadFileOptions();
    setInterval(loadFileOptions, 30000);  // 文件清单随配置变化半分钟同步一次
});
