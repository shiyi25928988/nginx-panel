// 配置文件编辑页：文件树、CodeMirror 编辑器、保存流水线 / 校验 / 重载 / 删除 / 新建
var editor = null;           // CodeMirror 实例或 null（textarea 模式）
var currentPath = null;      // 当前打开的相对路径
var currentEditable = false; // 当前文件是否可编辑
var dirty = false;

function getContent() {
    return editor ? editor.getValue() : $('#editorFallback').val();
}

function setContent(text) {
    if (editor) { editor.setValue(text); editor.clearHistory(); }
    else { $('#editorFallback').val(text); }
    dirty = false;
}

$(document).ready(function () {

    // ---- 上传替换（整体覆盖/新建，密码二次确认）----
    $('.modal').modal();
    var uploadContent = null;

    $('#btnUploadOpen').click(function (e) {
        e.preventDefault();
        $('#uploadFileInput').val('');
        $('.file-path').val('');
        uploadContent = null;
        $('#uploadPreview').text('请先选择本地 .conf 文件');
        $('#uploadPath').val(currentPath || 'nginx.conf');
        $('#uploadReason').val('');
        $('#uploadPassword').val('');
        $('#uploadModal').modal('open');
        setTimeout(function () { M.updateTextFields(); }, 50);
    });

    $('#uploadFileInput').change(function () {
        var file = this.files && this.files[0];
        if (!file) { return; }
        if (file.size > 2 * 1024 * 1024) {
            M.toast({ html: '文件超过 2MB，不支持上传' });
            $(this).val('');
            return;
        }
        if (!/\.(conf|types)$/i.test(file.name)) {
            M.toast({ html: '只允许 .conf / .types 文件' });
            $(this).val('');
            return;
        }
        var reader = new FileReader();
        reader.onload = function () {
            uploadContent = reader.result;
            var lines = String(uploadContent).split('\n');
            var preview = lines.slice(0, 60).join('\n')
                + (lines.length > 60 ? '\n...(共 ' + lines.length + ' 行)' : '');
            $('#uploadPreview').text(preview);
            // 未手动改过目标路径时，默认用文件名（放 conf.d）或当前文件
        };
        reader.readAsText(file, 'UTF-8');
    });

    $('#btnUploadConfirm').click(function (e) {
        e.preventDefault();
        var path = $('#uploadPath').val() ? $('#uploadPath').val().trim() : '';
        if (!path || !/\.(conf|types)$/i.test(path)) {
            M.toast({ html: '目标文件必须是 .conf / .types' });
            return;
        }
        if (uploadContent === null) {
            M.toast({ html: '请先选择本地文件' });
            return;
        }
        var password = $('#uploadPassword').val();
        if (!password) { M.toast({ html: '请输入管理员密码确认' }); return; }
        $.ajax({
            url: '/api/file/upload', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                path: path,
                content: uploadContent,
                password: password,
                reason: $('#uploadReason').val(),
                reloadAfter: $('#autoReload').is(':checked')
            }), dataType: 'json',
            success: function (r) {
                $('#uploadModal').modal('close');
                showOutput(r.testOutput || '');
                if (r.success) { M.toast({ html: r.message }); }
                else { M.toast({ html: failHtml(r.message, r.testOutput), displayLength: 9000 }); }
                if (r.success) {
                    dirty = false;
                    currentPath = path;
                    setContent(uploadContent);
                    $('#editorTitle').text(path);
                    loadFiles();
                }
            },
            error: apiError
        });
    });

    // ---- CodeMirror 初始化（加载失败自动降级 textarea；高亮用自研 nginx-shell 模式）----
    function initEditor() {
        if (typeof CodeMirror !== 'undefined') {
            var modeName = CodeMirror.modes['nginx-shell'] ? 'nginx-shell' : 'text/plain';
            editor = CodeMirror(document.getElementById('editor'), {
                mode: modeName,
                theme: 'material-darker',
                lineNumbers: true,
                indentUnit: 4,
                lineWrapping: true,
                value: ''
            });
            editor.on('change', function () { dirtyChanged(); });
            $('#editor').show();
            $('#editorFallback').hide();
        } else {
            editor = null;
            $('#editorFallback').show().on('input', function () { dirtyChanged(); });
            $('#editor').hide();
        }
    }

    // ---- 编辑器放大/还原（铺满整页；工具栏其它按钮与输出面板隐藏，放大按钮自身保留）----
    var zoomed = false;
    $('#btnEditorZoom').click(function (e) {
        e.preventDefault();
        zoomed = !zoomed;
        var card = $('#btnEditorZoom').closest('.card');
        card.toggleClass('editor-fullscreen', zoomed);
        card.find('.editorToolbar .btn, .editorToolbar .btn-flat').not('#btnEditorZoom')
            .toggleClass('hide-on-fullscreen', zoomed);
        card.find('#outputPanel').toggleClass('hide-on-fullscreen', zoomed);
        $(this).text(zoomed ? '还原' : '放大');
        if (editor) { setTimeout(function () { editor.refresh(); }, 60); }
        else { $('#editorFallback').toggleClass('fullscreen-textarea', zoomed); }
    });

    function dirtyChanged() {
        dirty = true;
        if (currentPath) {
            var mark = dirty ? '（未保存）' : '';
            $('#editorTitle').text(currentPath + mark);
        }
    }

    initEditor();

    // ---- 文件列表 ----
    function loadFiles() {
        $.get('/api/files', function (nodes) {
            var sel = $('#fileSelect').empty();
            sel.append('<option value="" disabled selected>选择文件…</option>');
            (nodes || []).forEach(function (n) {
                if (n.directory) {
                    sel.append($('<option/>').attr('value', n.path)
                        .text('📁 ' + n.path).attr('disabled', true).css('color', '#999'));
                } else {
                    var flag = n.editable ? '📄 ' : '🔒 ';
                    sel.append($('<option/>').attr('value', n.path).text(flag + n.path));
                }
            });
            // 默认打开主配置 nginx.conf（带 ?file= 参数时优先）
            var preselect = new URLSearchParams(location.search).get('file');
            if (!preselect) {
                var hasMain = (nodes || []).some(function (n) {
                    return !n.directory && n.path === 'nginx.conf';
                });
                if (hasMain) { preselect = 'nginx.conf'; }
            }
            if (preselect) { sel.val(preselect); openFile(preselect); }
        }).fail(apiError);
    }

    function openFile(path) {
        $.get('/api/file', { path: path }, function (f) {
            currentPath = f.path;
            currentEditable = f.editable;
            setContent(f.content);
            var mark = f.editable ? '' : '（只读）';
            $('#editorTitle').text(f.path + mark);
            if (editor) { editor.setOption('readOnly', !f.editable); }
            else { $('#editorFallback').attr('readonly', !f.editable); }
            $('#outputPanel').hide();
            dirty = false;
        }).fail(function (xhr) {
            apiError(xhr);
        });
    }

    $('#fileSelect').change(function () {
        var path = $(this).val();
        if (!path) { return; }
        if (dirty && !confirm('当前文件有未保存的修改，确定切换？')) { return; }
        openFile(path);
    });

    // ---- 新建文件 ----
    $('#btnNewFile').click(function () {
        var path = prompt('新建文件相对路径（如 conf.d/demo.conf）:', 'conf.d/');
        if (!path || !path.trim()) { return; }
        path = path.trim();
        if (!/\.(conf)$/.test(path.toLowerCase())) {
            M.toast({ html: '新文件必须是 .conf 文件' });
            return;
        }
        if (dirty && !confirm('当前文件有未保存的修改，确定切换？')) { return; }
        currentPath = path;
        currentEditable = true;
        setContent('');
        $('#editorTitle').text(path + '（新建，保存时创建）');
        M.toast({ html: '填写内容后点"保存并校验"完成创建' });
    });

    // ---- 保存（后端流水线：写入→nginx -t→失败回滚→记录版本）----
    $('#btnSaveFile').click(function () {
        if (!currentPath) { M.toast({ html: '请先选择或新建文件' }); return; }
        if (!currentEditable) { M.toast({ html: '该文件只读' }); return; }
        $.ajax({
            url: '/api/file/save', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                path: currentPath,
                content: getContent(),
                reason: $('#saveReason').val(),
                reloadAfter: $('#autoReload').is(':checked')
            }), dataType: 'json',
            success: function (r) {
                var fullOut = (r.testOutput || '') + (r.reloadOutput ? '\n---\n' + r.reloadOutput : '');
                showOutput(fullOut);
                if (r.success) {
                    M.toast({ html: r.message });
                } else {
                    M.toast({ html: failHtml(r.message, r.testOutput), displayLength: 9000 });
                }
                if (r.success) {
                    dirty = false;
                    $('#editorTitle').text(currentPath);
                    $('#saveReason').val('');
                }
            },
            error: apiError
        });
    });

    // ---- 单独校验（nginx -t，不写入）----
    $('#btnTestOnly').click(function () {
        $.post('/api/nginx/test', function (r) {
            showOutput(r.output || '');
            if (r.exitCode === 0) { M.toast({ html: '校验通过' }); }
            else { M.toast({ html: failHtml('nginx -t 校验失败', r.output), displayLength: 9000 }); }
        }, 'json').fail(apiError);
    });

    // ---- 校验当前内容：编辑器未保存内容在真实配置树上下文里校验（临时替换文件，不落盘）----
    $('#btnValidateMain').click(function () {
        if (!currentPath) {
            // 未打开文件：退化为校验磁盘上整套配置
            $.post('/api/nginx/test', function (r) {
                showOutput(r.output || '');
                M.toast({ html: r.exitCode === 0 ? '校验通过' : failHtml('校验失败', r.output), displayLength: 9000 });
            }, 'json').fail(apiError);
            return;
        }
        if (!currentEditable) {
            M.toast({ html: '该文件为只读，仅支持校验磁盘整套配置' });
            return;
        }
        // 编辑任何文件（nginx.conf / conf.d 片段 / 新建文件）都适用：服务端临时把候选内容
        // 放进该文件位置跑整树 nginx -t，用完还原，磁盘不落盘
        $.ajax({
            url: '/api/file/validate', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                path: currentPath,
                content: getContent(),
                password: null,
                reason: null,
                reloadAfter: false
            }), dataType: 'json',
            success: function (r) {
                showOutput(r.testOutput || '');
                M.toast({ html: r.message });
            },
            error: apiError
        });
    });

    // ---- 重载 ----
    $('#btnReload').click(function () {
        $.post('/api/nginx/reload', function (r) {
            showOutput(r.output || '');
            if (r.exitCode === 0) { M.toast({ html: '重载成功' }); }
            else { M.toast({ html: failHtml('重载失败', r.output), displayLength: 9000 }); }
        }, 'json').fail(apiError);
    });

    // ---- 删除 ----
    $('#btnDelete').click(function () {
        if (!currentPath) { M.toast({ html: '请先选择文件' }); return; }
        if (!confirm('确认删除 ' + currentPath + '？删除后执行 nginx -t，失败会自动恢复。')) { return; }
        $.post('/api/file/delete', { path: currentPath }, function () {
            M.toast({ html: '已删除' });
            currentPath = null;
            $('#editorTitle').text('未选择文件');
            loadFiles();
        }, 'json').fail(apiError);
    });

    // ---- 跳转历史 ----
    $('#btnHistory').click(function () {
        if (!currentPath) { M.toast({ html: '请先选择文件' }); return; }
        window.location.href = '/page/history?file=' + encodeURIComponent(currentPath);
    });

    function showOutput(text) {
        if (!text) { $('#outputPanel').hide(); return; }
        $('#outputPanel').text(text).show();
    }

    // ===== 失败 toast：结论一行 + nginx 原始错误行（[emerg] 等红色，转义防 XSS）=====
    function escHtml(s) {
        return String(s).replace(/&/g, '&').replace(/</g, '<').replace(/>/g, '>').replace(/"/g, '"');
    }
    function failHtml(message, testOutput) {
        var html = '<div style="font-weight:600;">' + escHtml(message) + '</div>';
        if (testOutput) {
            var lines = String(testOutput).split('\n').filter(function (l) {
                return l.trim() && l.indexOf('configuration file') === -1;
            });
            var shown = lines.slice(0, 4); // 关键错误通常在前几行（[emerg] 行）
            html += '<div style="font-family:monospace;font-size:0.72rem;margin-top:6px;'
                 +  'white-space:pre-wrap;text-align:left;max-width:520px;">'
                 +  shown.map(function (l) {
                     var hot = /\[emerg\]|\[alert\]|\[crit\]|failed/i.test(l);
                     return '<div style="' + (hot ? 'color:#ffcdd2;' : 'color:#eceff1;') + '">'
                          + escHtml(l) + '</div>';
                 }).join('')
                 +  '</div>';
            if (lines.length > shown.length) {
                html += '<div style="opacity:0.7;font-size:0.7rem;">…共 ' + lines.length
                     +  ' 行，完整输出见下方黑色面板</div>';
            }
        }
        return html;
    }

    loadFiles();
});
