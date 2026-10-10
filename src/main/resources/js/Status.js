// 服务状态面板：nginx 进程/版本/时长 + 宿主机 CPU/内存/磁盘 + 面板信息，15s 自动刷新
var REFRESH_SECONDS = 15;

function fmtBytes(n) {
    if (n === null || n === undefined) { return '-'; }
    var units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var v = n, i = 0;
    while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
    return v.toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

function fmtUptime(seconds) {
    if (!seconds && seconds !== 0) { return '-'; }
    var d = Math.floor(seconds / 86400);
    var h = Math.floor(seconds % 86400 / 3600);
    var m = Math.floor(seconds % 3600 / 60);
    var s = seconds % 60;
    return (d > 0 ? d + ' 天 ' : '') + h + ':' + String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
}

$(document).ready(function () {

    function load() {
        $.get('/api/status', function (data) {
            var n = data.nginx || {};
            var running = n.running === true;

            $('#nginxRunning').text(running ? '● Nginx 运行中' : '○ Nginx 未运行/未探测到');

            var rows = [];
            function row(k, v) {
                rows.push('<tr><td style="color:#cfd8dc;">' + escapeHtml(k) + '</td><td class="right-align">' + escapeHtml(v) + '</td></tr>');
            }
            row('版本', n.version || '-');
            row('master PID', (n.pid && n.pid > 0) ? n.pid : '-');
            row('master 运行时长', data.masterUptime || '-');
            row('配置目录', n.confDir || '-');
            row('执行模式', n.execMode + (n.container ? '（容器 ' + n.container + '）' : ''));
            if (n.lastTestTime) {
                row('最近校验', formatTime(n.lastTestTime) + (n.lastTestOk === true ? '（通过）' : '（未通过）'));
            } else {
                row('最近校验', '本会话未执行过');
            }
            $('#nginxStatusBody').html(rows.join(''));

            // nginx -V 完整版本
            $('#nginxFullVersion').text(data.fullVersion || '-');

            // 进程列表
            var procRows = [];
            (data.processes || []).forEach(function (p) {
                procRows.push(`<li class="collection-item" style="padding:4px 12px;">PID ${p.pid} — ${escapeHtml(p.command || '')}`
                    + (p.startedAt ? ` <span class="grey-text" style="font-size:0.75rem;">启动于 ${formatTime(p.startedAt)}</span>` : '')
                    + '</li>');
            });
            if (procRows.length === 0) {
                procRows.push('<li class="collection-item grey-text">'
                    + (n.execMode === 'docker' ? 'docker 模式：进程在容器内，宿主机不可见' : '未探测到 nginx 进程') + '</li>');
            }
            $('#procList').html('<ul class="collection">' + procRows.join('') + '</ul>');

            // CPU / 内存
            if (data.cpuLoad >= 0) {
                $('#cpuBar').css('width', Math.min(100, data.cpuLoad) + '%');
                $('#cpuText').text(data.cpuLoad + ' %');
            } else {
                $('#cpuBar').css('width', '0%');
                $('#cpuText').text('不可用');
            }
            if (data.memTotal) {
                var pct = Math.round(data.memUsed * 100 / data.memTotal);
                $('#memBar').css('width', pct + '%');
                $('#memText').text(fmtBytes(data.memUsed) + ' / ' + fmtBytes(data.memTotal) + '（' + pct + '%）');
            } else {
                $('#memBar').css('width', '0%');
                $('#memText').text('不可用');
            }
            $('#hostDetail').html('系统: ' + escapeHtml(data.osName || '-') + '<br/>'
                + 'Java: ' + escapeHtml(data.jvmVersion || '-'));

            // 磁盘
            var diskHtml = [];
            (data.disks || []).forEach(function (d) {
                var pct = Math.min(100, d.usedPercent || 0);
                var color = pct > 90 ? 'red' : (pct > 80 ? 'orange' : 'teal');
                diskHtml.push('<div style="margin:8px 0;">'
                    + '<span style="font-size:0.8rem;">' + escapeHtml(d.mount) + '　'
                    + fmtBytes(d.totalBytes - d.freeBytes) + ' / ' + fmtBytes(d.totalBytes) + '（' + pct + '%）</span>'
                    + '<div class="progress" style="margin:2px 0;"><div class="determinate ' + color + '" style="width:' + pct + '%;"></div></div>'
                    + '</div>');
            });
            $('#diskList').html(diskHtml.length ? diskHtml.join('') : '<span class="grey-text">无数据</span>');

            // 面板
            $('#panelDetail').html('面板运行时长: ' + fmtUptime(data.panelUptimeSeconds) + '<br/>'
                + 'JVM 堆: ' + fmtBytes(data.jvmHeapUsed) + ' / ' + fmtBytes(data.jvmHeapMax));

            $('#statusRefreshHint').text('每 ' + REFRESH_SECONDS + ' 秒自动刷新，最近: ' + formatTime(Date.now()));
        }).fail(function (xhr) {
            apiError(xhr);
        });
    }

    // 本页面独有的运行时长刷新钩子由 Console 不共享，Status 页持续刷新
    setInterval(load, REFRESH_SECONDS * 1000);
    // 进程列表容器
    var hint = $('#nginxFullVersion').parent();
    $('#nginxFullVersion').after('<div id="procList" style="margin-top:8px;"></div>');
    load();
});
