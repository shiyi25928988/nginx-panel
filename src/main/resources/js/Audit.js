// 操作审计日志页：过滤 + 分页
var auditPage_ = 1;
var auditSize = 15;

$(document).ready(function () {

    function loadLogs() {
        var action = $('#auditActionFilter').val() || '';
        $.get('/api/logs', { action: action, page: auditPage_, size: auditSize }, function (data) {
            var body = $('#auditTableBody').empty();
            (data.list || []).forEach(function (l) {
                var resultCls = l.result === 'SUCCESS' ? 'green-text' : 'red-text';
                var detail = (l.detail || '');
                var short = detail.length > 120 ? detail.substring(0, 120) + '…' : detail;
                body.append($('<tr/>')
                    .append($('<td/>').text(formatTime(l.createTime)))
                    .append($('<td/>').text(l.username))
                    .append($('<td/>').text(l.action))
                    .append($('<td/>').text(l.filePath || '').css('font-size', '0.8rem'))
                    .append($('<td/>').append($('<span/>').addClass(resultCls).text(l.result)))
                    .append($('<td/>').attr('title', detail).css('font-size', '0.8rem')
                        .css('max-width', '320px').css('white-space', 'normal').text(short)));
            });
            if (!data.list || data.list.length === 0) {
                body.append('<tr><td colspan="6" class="grey-text">暂无日志</td></tr>');
            }
            var totalPage = Math.max(1, Math.ceil(data.total / auditSize));
            $('#auditPageInfo').text(auditPage_ + ' / ' + totalPage + '（共 ' + data.total + ' 条）');
        }).fail(apiError);
    }

    $('#auditActionFilter').change(function () { auditPage_ = 1; loadLogs(); });
    $('#btnAuditPrev').click(function () { if (auditPage_ > 1) { auditPage_--; loadLogs(); } });
    $('#btnAuditNext').click(function () { auditPage_++; loadLogs(); });

    loadLogs();
});
