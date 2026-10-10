// 控制台首页：状态、快捷操作、最近改动与最近日志
$(document).ready(function () {

    function loadStatus() {
        $.get('/api/nginx/status', function (s) {
            var running = s.running === true;
            $('#statusRunning').text(running ? '● Nginx 运行中' : '○ Nginx 未运行/未探测到');
            var detail = [];
            if (s.version) { detail.push('版本: ' + escapeHtml(s.version)); }
            detail.push('配置目录: ' + escapeHtml(s.confDir));
            detail.push('执行模式: ' + escapeHtml(s.execMode) + (s.container ? '（容器 ' + escapeHtml(s.container) + '）' : ''));
            if (s.execMode === 'native') {
                detail.push('PID: ' + (s.pid && s.pid > 0 ? s.pid : '未探测到'));
            }
            if (s.lastTestTime) {
                detail.push('最近校验: ' + formatTime(s.lastTestTime) + (s.lastTestOk === true ? '（通过）' : '（未通过）'));
            }
            $('#statusDetail').html(detail.join('<br/>'));
        }).fail(function (xhr) { apiError(xhr); $('#statusRunning').text('状态获取失败'); });
    }

    function loadRecentLogs() {
        $.get('/api/logs', { page: 1, size: 5 }, function (data) {
            var body = $('#recentLogsBody').empty();
            (data.list || []).forEach(function (l) {
                var cls = l.result === 'SUCCESS' ? 'green-text' : 'red-text';
                body.append($('<tr/>')
                    .append($('<td/>').text(formatTime(l.createTime)))
                    .append($('<td/>').text(actionName(l.action)))
                    .append($('<td/>').append($('<span/>').addClass(cls).text(l.result))));
            });
            if (!data.list || data.list.length === 0) {
                body.append('<tr><td colspan="3" class="grey-text">暂无日志</td></tr>');
            }
        }).fail(function (xhr) { apiError(xhr); });
    }

    function loadVersions() {
        // 最近的版本改动直接从 /api/history 无法跨文件查询，由后端 /api/version/recent 提供
        $.get('/api/version/recent', { limit: 5 }, function (list) {
            var body = $('#recentVersionsBody').empty();
            (list || []).forEach(function (v) {
                body.append($('<tr>')
                    .append($('<td/>').text(formatTime(v.createTime)))
                    .append($('<td/>').append($('<a/>').text(v.filePath)
                        .attr('href', '/page/config?file=' + encodeURIComponent(v.filePath))))
                    .append($('<td/>').text('v' + v.versionNo))
                    .append($('<td/>').text(v.editor))
                    .append($('<td/>').text(v.reason || '')));
            });
            if (!list || list.length === 0) {
                body.append('<tr><td colspan="5" class="grey-text">暂无改动</td></tr>');
            }
        }).fail(function (xhr) { apiError(xhr); });
    }

    $('#btnTest').click(function () {
        $.post('/api/nginx/test', function (r) {
            var ok = r.exitCode === 0;
            M.toast({ html: ok ? '校验通过：' + (r.output || 'successful') : '校验失败（详见日志）' });
        }).fail(apiError);
    });

    $('#btnReload').click(function () {
        $.post('/api/nginx/reload', function (r) {
            var ok = r.exitCode === 0;
            M.toast({ html: ok ? '重载成功' : '重载失败：' + (r.output || '') });
        }).fail(apiError);
    });

    function actionName(a) {
        return a;
    }

    loadStatus();
    loadRecentLogs();
    loadVersions();
});
