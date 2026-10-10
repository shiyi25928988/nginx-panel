// 证书管理页：清单渲染、替换弹层（文件读取+密码确认）、自签生成
var newCertPem = null;
var newKeyPem = null;

function fmtDate(iso) {
    if (!iso) { return '-'; }
    return String(iso).replace('T', ' ').substring(0, 19);
}

$(document).ready(function () {
    $('.modal').modal();

    function loadCerts() {
        $.get('/api/certs', function (list) {
            var body = $('#certTableBody').empty();
            (list || []).forEach(function (c) {
                var status = !c.certExists
                    ? '<span class="grey-text">文件缺失</span>'
                    : (c.expired ? '<span class="red-text">已过期</span>' : '<span class="green-text">正常</span>');
                var match = c.keyMatch === true ? '<span class="green-text">匹配</span>'
                    : (c.keyMatch === false ? '<span class="red-text">不匹配!</span>' : '<span class="grey-text">未知</span>');
                var domains = (c.domains && c.domains.length) ? c.domains.join(', ') : '-';
                body.append($('<tr/>')
                    .append($('<td/>').html(status))
                    .append($('<td/>').text(domains).css('font-size', '0.8rem'))
                    .append($('<td/>').text(c.issuer || '-').css('font-size', '0.8rem'))
                    .append($('<td/>').text(c.certExists ? fmtDate(c.notAfter) : '-'))
                    .append($('<td/>').html(dayBadge(c)))
                    .append($('<td/>').text(c.certPath).css('font-size', '0.7rem'))
                    .append($('<td/>').html((c.keyPath || '-') + ' ' + match).css('font-size', '0.7rem'))
                    .append($('<td/>').text(c.certExists ? (c.keyAlg || '-') : '-')));
            });
            if (!list || list.length === 0) {
                body.append('<tr><td colspan="8" class="grey-text">当前配置中没有引用任何 ssl_certificate 指令</td></tr>');
            }
            fillCertSelect(list || []);
        }).fail(apiError);
    }

    function dayBadge(c) {
        if (!c.certExists) { return '<span class="grey-text">-</span>'; }
        if (c.daysLeft === null || c.daysLeft === undefined) {
            // 解析失败场景，subject 里有具体原因
            return '<span class="grey-text">-</span>';
        }
        if (c.expired) { return '<span class="red-text">已过期</span>'; }
        var icon = c.daysLeft < 7 ? '⚠️' : (c.daysLeft < 30 ? '🟠' : '✅');
        return icon + ' ' + c.daysLeft + ' 天';
    }

    var certInventory = []; // 清单缓存（替换提交时校验目标受管状态）
    function fillCertSelect(list) {
        certInventory = list || [];
        var sel = $('#certPathSelect').empty();
        list.forEach(function (c) {
            if (c.certExists) {
                var label = c.managed
                    ? c.certPath + '（' + (c.domains || []).join(',') + '）'
                    : c.certPath + '（' + (c.domains || []).join(',') + '，系统/外部路径，不可面板替换）';
                var opt = $('<option/>').attr('value', c.certPath).text(label);
                if (!c.managed) { opt.attr('disabled', true); }
                sel.append(opt);
            }
        });
        setTimeout(function () { M.updateTextFields(); sel.formSelect(); }, 50);
    }

    // ---- 替换弹层 ----
    $('#btnCertReplace').click(function (e) {
        e.preventDefault();
        newCertPem = null;
        newKeyPem = null;
        $('#certFileInput').val('');
        $('#keyFileInput').val('');
        $('.file-path').val('');
        $('#certReason').val('');
        $('#certPassword').val('');
        $('#certModal').modal('open');
        M.updateTextFields();
    });

    $('#certFileInput').change(function () {
        var file = this.files && this.files[0];
        if (!file) { return; }
        if (file.size > 2 * 1024 * 1024) { M.toast({ html: '证书文件超过 2MB' }); $(this).val(''); return; }
        var reader = new FileReader();
        reader.onload = function () {
            newCertPem = reader.result;
            if (!/BEGIN CERTIFICATE/.test(String(newCertPem))) {
                M.toast({ html: '警告: 未检测到 BEGIN CERTIFICATE 结构' });
            }
        };
        reader.readAsText(file, 'UTF-8');
    });

    $('#keyFileInput').change(function () {
        var file = this.files && this.files[0];
        if (!file) { newKeyPem = null; return; }
        var reader = new FileReader();
        reader.onload = function () {
            newKeyPem = reader.result;
            if (/ENCRYPTED PRIVATE KEY|Proc-Type: 4,ENCRYPTED/.test(String(newKeyPem))) {
                M.toast({ html: '不支持带口令的私钥，请导出无口令 PEM' });
                newKeyPem = null;
            }
        };
        reader.readAsText(file, 'UTF-8');
    });

    $('#btnCertConfirm').click(function (e) {
        e.preventDefault();
        var certPath = $('#certPathSelect').val();
        if (!certPath) { M.toast({ html: '请选择目标证书路径' }); return; }
        var targetCert = certInventory.filter(function (c) { return c.certPath === certPath; })[0];
        if (targetCert && targetCert.managed === false) {
            M.toast({ html: '该证书位于系统/外部路径，不允许面板替换；请修改配置 ssl_certificate 指向面板证书目录（cert.dir）' });
            return;
        }
        if (!newCertPem) { M.toast({ html: '请选择新的证书文件' }); return; }
        var password = $('#certPassword').val();
        if (!password) { M.toast({ html: '请输入管理员密码确认' }); return; }
        $.ajax({
            url: '/api/certs/replace', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                certPath: certPath,
                certPem: newCertPem,
                keyPem: newKeyPem,
                password: password,
                reason: $('#certReason').val(),
                reloadAfter: $('#certAutoReload').is(':checked')
            }), dataType: 'json',
            success: function (r) {
                $('#certModal').modal('close');
                M.toast({ html: r.message });
                loadCerts();
            },
            error: apiError
        });
    });

    // ---- 自签生成 ----
    $('#btnGenCert').click(function (e) {
        e.preventDefault();
        var name = $('#genName').val() && $('#genName').val().trim();
        if (!name) { M.toast({ html: '请输入文件名' }); return; }
        $.ajax({
            url: '/api/certs/generate', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                name: name,
                cn: $('#genCn').val(),
                sans: $('#genSans').val(),
                days: parseInt($('#genDays').val(), 10) || 3650,
                password: $('#genPassword').val(),
                reason: '自签生成 ' + name
            }), dataType: 'json',
            success: function (r) {
                M.toast({ html: r.message });
                loadCerts();
            },
            error: apiError
        });
    });

    $('#btnCertRefresh').click(loadCerts);
    loadCerts();
});
