package no.haagensoftware.contentice.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import no.haagensoftware.contentice.spi.StoragePlugin;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created with IntelliJ IDEA.
 * User: joahaage
 * Date: 17.11.13
 * Time: 12:24
 * To change this template use File | Settings | File Templates.
 */
public abstract class ContenticeHandler extends SimpleChannelInboundHandler<FullHttpRequest> implements ContenticeParameterMap {
    private static final Logger logger = Logger.getLogger(ContenticeHandler.class.getName());
    private Map<String, String> parameterMap;
    private StoragePlugin storage;

    @Override
    public String getParameter(String key) {
        String value = null;

        if (parameterMap != null) {
            value = parameterMap.get(key);
        }

        return value;
    }

    @Override
    public void setParameterMap(Map<String, String> parameterMap) {
        this.parameterMap = parameterMap;
    }

    public void setStorage(StoragePlugin storage) {
        this.storage = storage;
    }

    public StoragePlugin getStorage() {
        return storage;
    }

    public String getUri(FullHttpRequest fullHttpRequest) {
        return fullHttpRequest.getUri();
    }

    public String getCookieValue(FullHttpRequest fullHttpRequest, String cookieName) {
        String cookieValue = null;

        HttpHeaders httpHeaders = fullHttpRequest.headers();
        String value = httpHeaders.get("Cookie");
        logger.info("cookie header: \n" + value);
        if (value != null) {

            Set<Cookie> cookies = CookieDecoder.decode(value);
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    cookieValue = cookie.getValue();
                    break;
                }
            }
        }

        return cookieValue;
    }

    public boolean isPut(FullHttpRequest fullHttpRequest) {
        HttpMethod method = fullHttpRequest.getMethod();
        return method == HttpMethod.PUT;
    }

    public boolean isPost(FullHttpRequest fullHttpRequest) {
        HttpMethod method = fullHttpRequest.getMethod();
        return method == HttpMethod.POST;
    }

    public boolean isGet(FullHttpRequest fullHttpRequest) {
        HttpMethod method = fullHttpRequest.getMethod();
        return method == HttpMethod.GET;
    }

    public boolean isDelete(FullHttpRequest fullHttpRequest) {
        HttpMethod method = fullHttpRequest.getMethod();
        return method == HttpMethod.DELETE;
    }

    public String getHttpMessageContent(FullHttpRequest fullHttpRequest) {
        String requestContent = null;
        ByteBuf content = fullHttpRequest.content();
        if (content.isReadable()) {
            requestContent = content.toString(CharsetUtil.UTF_8);
        }
        return requestContent;
    }

    public void handleIncomingRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {

    }

    public void writeContentsToBuffer(ChannelHandlerContext ctx, String responseText, String contentType) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(responseText, CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, contentType + "; charset=UTF-8");

        ctx.write(response);
        ctx.flush();
    }

    public void write404ToBuffer(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, Unpooled.copiedBuffer("404 Not Found", CharsetUtil.UTF_8));
        ctx.write(response);
        ctx.flush();
    }

    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status.toString(), CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.write(response);
        ctx.flush();
    }
}