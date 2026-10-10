// 个人资料页：当前管理员信息 + 修改密码
$(document).ready(function () {

    $.get('/user/current', function (u) {
        $('#profileTitle').text('当前管理员: ' + (u ? u.username : ''));
        if (u) {
            $('#profileDetail').html('角色: ' + escapeHtml(u.roles || '') + '<br/>创建时间: ' + formatTime(u.createTime));
        }
    }).fail(apiError);

    $('#passwordForm').submit(function (e) {
        e.preventDefault();
        var oldPwd = $('#oldPassword').val();
        var newPwd = $('#newPassword').val();
        var newPwd2 = $('#newPassword2').val();
        if (!oldPwd || !newPwd) { M.toast({ html: '请填写完整' }); return; }
        if (newPwd.length < 8) { M.toast({ html: '新密码至少 8 位' }); return; }
        if (newPwd !== newPwd2) { M.toast({ html: '两次新密码不一致' }); return; }
        $.ajax({
            url: '/user/password', method: 'POST', contentType: 'application/json; charset=UTF-8',
            data: JSON.stringify({
                oldPassword: oldPwd, newPassword: newPwd
            }), dataType: 'json',
            success: function () {
                M.toast({ html: '密码已修改（其它已登录会话将失效）' });
                $('#passwordForm')[0].reset();
                setTimeout(function () { window.location.href = '/page/login'; }, 1500);
            },
            error: apiError
        });
    });
});
