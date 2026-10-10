$(document).ready(function () {
    // 登录
    $('#loginForm').submit(function (event) {
        event.preventDefault();
        var username = $('#loginUsername').val();
        var password = $('#loginPassword').val();
        $.ajax({
            url: '/user/login',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({username: username, password: password}),
            success: function (response) {
                M.toast({html: '登录成功'});
                setTimeout(function () { window.location.href = '/page/index'; }, 500);
            },
            error: function (xhr) {
                apiError(xhr);
            }
        });
    });
});
