package no.haagensoftware.contentice.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import no.haagensoftware.contentice.data.Domain;
import no.haagensoftware.contentice.spi.AuthenticationPlugin;
import no.haagensoftware.contentice.spi.PostProcessorPlugin;
import no.haagensoftware.contentice.spi.StoragePlugin;
import no.haagensoftware.contentice.util.PluginResolver;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
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
public abstract class ContenticeHandler extends CommonConticiousInboundHandler {
    public String getUri(FullHttpRequest fullHttpRequest) {
        return fullHttpRequest.getUri();
    }

    protected String getQueryString(String url) {
        String queryString = null;

        if (url.contains("?")) {
            queryString = url.substring(url.indexOf("?") + 1, url.length());

            queryString = queryString.replaceAll("%5B", "");
            queryString = queryString.replaceAll("%5D", "");
            queryString = queryString.replaceAll("%40", "@");
        }

        return queryString;
    }

    public String getContentType(FullHttpRequest fullHttpRequest) {
        String contentType = null;

        HttpHeaders httpHeaders = fullHttpRequest.headers();
        contentType = httpHeaders.get("Content-Type");

        return contentType;
    }

    public String getHost(FullHttpRequest fullHttpRequest) {
        String host = null;

        HttpHeaders httpHeaders = fullHttpRequest.headers();
        host = httpHeaders.get("Host");

        if (host.contains(":")) {
            host = host.split(":")[0];
        }

        return host;
    }



    public String getCookieValue(FullHttpRequest fullHttpRequest, String cookieName) {
        String cookieValue = null;

        HttpHeaders httpHeaders = fullHttpRequest.headers();

        String value = httpHeaders.get("Cookie");

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

    public String getHttpMessageContent(FullHttpRequest fullHttpRequest) {
        String requestContent = null;
        ByteBuf content = fullHttpRequest.content();
        if (content.isReadable()) {
            requestContent = content.toString(CharsetUtil.UTF_8);
        }
        return requestContent;
    }

    public void storeContentAsPng(FullHttpRequest fullHttpRequest) throws IOException {
        ByteBuf content = fullHttpRequest.content();

        FileOutputStream out = new FileOutputStream(new File("/Users/jhsmbp/Projects/TeknologihusetWeb/site/uploads/test.png"));

        if (content.isReadable()) {
            content.readBytes(out, content.readableBytes());
        }
    }

    public void handleIncomingRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) throws Exception {

    }

    public void writeContentsToBuffer(ChannelHandlerContext ctx, String responseText, String contentType) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(responseText.getBytes()));
        response.headers().set(CONTENT_TYPE, contentType + "; charset=UTF-8");
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void writeFileToBuffer(ChannelHandlerContext ctx, String path, String contentType) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, contentType);
        //response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);

        File file = new File(path);

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException fnfe) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = 0;
        try {
            fileLength = raf.length();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        //response.headers().set(CONTENT_LENGTH, fileLength);
        response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        ctx.write(response);

        ChannelFuture sendFileFuture = null;
        try {
            sendFileFuture = ctx.write(new ChunkedFile(raf, 0, fileLength, 8192), ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {

                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                    /*if (total < 0) { // total unknown
                        System.err.println("Transfer progress: " + progress);
                    } else {
                        System.err.println("Transfer progress: " + progress + " / " + total);
                    }*/
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws Exception {
                    //System.err.println("Transfer complete.");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        /*if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }*/

        // Write the end marker
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
    }

    public void write404ToBuffer(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, Unpooled.copiedBuffer("404 Not Found", CharsetUtil.UTF_8));
        ctx.write(response);
        ctx.flush();

        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
    }

    public void writeOKToBuffer(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer("", CharsetUtil.UTF_8));
        ctx.write(response);
        ctx.flush();

        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
    }

    public void write404DomainNotFoundToBuffer(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND, Unpooled.copiedBuffer("That domain is not configured in the Conticious administration panel", CharsetUtil.UTF_8));
        ctx.write(response);
        ctx.flush();

        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
    }

    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status.toString(), CharsetUtil.UTF_8));
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.write(response);
        ctx.flush();

        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        lastContentFuture.addListener(ChannelFutureListener.CLOSE);
    }
}
