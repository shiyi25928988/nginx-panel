package yi.shi.plinth.ngx;

/**
 * Nginx Shell 业务异常：消息直接透传给前端 toast。
 */
public class NginxPanelException extends RuntimeException {

    public NginxPanelException(String message) {
        super(message);
    }

    public NginxPanelException(String message, Throwable cause) {
        super(message, cause);
    }
}
