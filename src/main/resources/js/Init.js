$(document).ready(function () {
    $('#busyIndicator').show();
    M.AutoInit();
    $('#busyIndicator').hide();
});

/** 通用工具：统一处理 API 错误提示 */
function apiError(xhr) {
    var msg = xhr && xhr.responseText ? xhr.responseText : '请求失败';
    if (msg && msg.length > 300) { msg = msg.substring(0, 300) + '...'; }
    M.toast({ html: msg });
}

function escapeHtml(text) {
    return String(text == null ? '' : text)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function formatTime(epochMillis) {
    if (!epochMillis) { return '-'; }
    var d = new Date(epochMillis);
    function p(n) { return n < 10 ? '0' + n : n; }
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
        + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
}
