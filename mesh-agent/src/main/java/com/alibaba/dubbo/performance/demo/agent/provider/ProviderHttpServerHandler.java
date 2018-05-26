package com.alibaba.dubbo.performance.demo.agent.provider;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class ProviderHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderHttpServerHandler.class);

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
        InterfaceHttpData data = decoder.getBodyHttpData("parameter");

        if (data.getHttpDataType() != InterfaceHttpData.HttpDataType.Attribute) {
            LOGGER.error("Provider received incorrect http request.");
            System.exit(1);
        }

        String paramStr = null;
        Attribute attribute = (Attribute)data;
        try {
            paramStr = attribute.getValue();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        String hashStr = String.valueOf(paramStr.hashCode());
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(hashStr.getBytes()));
        response.headers().set("Content-Type", "text/plain");
        response.headers().setInt("Content-Length", response.content().readableBytes());
        response.headers().set("request-id", request.headers().get("request-id"));
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
