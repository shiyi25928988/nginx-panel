// 配置模板页：卡片列表、变量表单、预览、生成并校验
var currentTemplate = null;

$(document).ready(function () {
    $('.modal').modal();

    function loadTemplates() {
        $.get('/api/templates', function (list) {
            var wrap = $('#templateCards').empty();
            (list || []).forEach(function (t) {
                var card = $('<div class="col s12 m6 l4"><div class="card"><div class="card-content">' +
                    '<span class="card-title">' + escapeHtml(t.title) + '</span>' +
                    '<p class="grey-text">' + escapeHtml(t.description) + '</p>' +
                    '<p style="font-size:0.8rem;">默认文件: <code>' + escapeHtml(t.targetFile) + '</code></p>' +
                    '</div><div class="card-action"><a href="#" class="btn blue" style="text-transform:none;">应用模板</a>' +
                    '<a href="#" class="previewLink" style="margin-left:8px;">查看模板</a></div></div></div>');
                card.find('.btn').click(function () { openApply(t); });
                card.find('.previewLink').click(function (e) {
                    e.preventDefault();
                    openApply(t, true);
                });
                wrap.append(card);
            });
            if (!list || list.length === 0) {
                wrap.append('<div class="col s12 grey-text">没有模板</div>');
            }
        }).fail(apiError);
    }

    function openApply(t, previewOnly) {
        currentTemplate = t;
        $('#applyTitle').text('应用模板: ' + t.title);
        $('#applyPath').val(t.targetFile);
        $('#applyReason').val('');
        $.get('/api/template', { name: t.name }, function (d) {
            renderVarInputs(d.variables, d.raw);
            updatePreview();
            $('#applyModal').modal('open');
            if (previewOnly) {
                M.toast({ html: '填写变量后点"生成并校验"保存' });
            }
        }).fail(apiError);
    }

    function renderVarInputs(variables, raw) {
        var box = $('#applyVars').empty();
        (variables || []).forEach(function (v) {
            var id = 'var_' + v;
            var input = $('<div class="input-field"/>').append(
                $('<input/>').attr({ id: id, name: v, type: 'text' })
                    .addClass('templateVar').on('input', updatePreview),
                $('<label/>').attr('for', id).text('变量 {{' + v + '}}')
            );
            box.append(input);
        });
        box.data('variables', variables || []);
        box.data('raw', raw);
        setTimeout(function () { M.updateTextFields(); }, 50);
    }

    function collectVars() {
        var vars = {};
        $('.templateVar').each(function () {
            vars[$(this).attr('name')] = $(this).val();
        });
        return vars;
    }

    function updatePreview() {
        var box = $('#applyVars');
        var raw = box.data('raw') || '';
        var vars = collectVars();
        var preview = raw;
        var missing = [];
        (box.data('variables') || []).forEach(function (v) {
            var re = new RegExp('\\{\\{' + v + '\\}\\}', 'g');
            var value = vars[v];
            if (!value) { missing.push(v); value = '【未填】'; }
            preview = preview.replace(re, value);
        });
        $('#applyPreview').text(preview + (missing.length ? '\n\n(尚未填写的变量: ' + missing.join(', ') + ')' : ''));
    }

    $('#btnApplyConfirm').click(function () {
        if (!currentTemplate) { return; }
        var path = $('#applyPath').val();
        if (!path || !/\.conf$/.test(path.toLowerCase())) {
            M.toast({ html: '目标文件必须是 .conf' });
            return;
        }
        var vars = collectVars();
        var missing = ($('#applyVars').data('variables') || []).filter(function (v) { return !vars[v]; });
        if (missing.length > 0) {
            M.toast({ html: '请填写变量: ' + missing.join(', ') });
            return;
        }
        var autoReload = confirm('生成成功后自动重载配置？');
        $.ajax({
            url: '/api/template/apply', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                template: currentTemplate.name,
                path: path,
                vars: vars,
                reason: $('#applyReason').val() || ('应用模板 ' + currentTemplate.title),
                reloadAfter: autoReload
            }), dataType: 'json',
            success: function (r) {
                $('#applyModal').modal('close');
                M.toast({ html: r.message });
                if (r.success || /已撤销/.test(r.message)) {
                    window.location.href = '/page/config?file=' + encodeURIComponent(path);
                }
            },
            error: apiError
        });
    });

    loadTemplates();
});
