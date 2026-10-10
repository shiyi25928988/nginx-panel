package yi.shi.plinth.http;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.utils.JsonUtils;

import java.io.IOException;

public final class HttpReqHelper {

    /**
     * 读取请求体并按指定类型反序列化。
     * 使用 getContentLengthLong 避免 int 溢出；当 Content-Length 未知（如 chunked 传输）时
     * 按流完整读取，而非直接丢弃请求体。
     *
     * @param clazz
     * @return
     * @throws IOException
     */
    public static Object getRequestPostBody(Class<?> clazz) throws IOException {
        HttpServletRequest request = ServletHelper.getRequest();
        // 容器可能对 application/x-www-form-urlencoded 等类型预解析并消费掉请求体流，
        // 此时再读 getInputStream 只会拿到全 0 占位字节（Jackson 表现为 CTRL-CHAR 错误）。
        // 与其给出晦涩解析错误，不如前置校验 Content-Type 快速失败。
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            throw new IllegalArgumentException("请求 Content-Type 必须为 application/json");
        }
        long contentLength = request.getContentLengthLong();
        byte[] buffer;
        if (contentLength < 0) {
            // Content-Length 未知（chunked 传输等），按流完整读取
            buffer = IOUtils.toByteArray(request.getInputStream());
        } else {
            buffer = new byte[(int) contentLength];
            for (int i = 0; i < contentLength; ) {
                int readlen = request.getInputStream().read(buffer, i, (int) (contentLength - i));
                if (readlen == -1) {
                    break;
                }
                i += readlen;
            }
        }
        if (buffer.length == 0) {
            return null;
        }
        return JsonUtils.fromJson(buffer, clazz);
    }
}
