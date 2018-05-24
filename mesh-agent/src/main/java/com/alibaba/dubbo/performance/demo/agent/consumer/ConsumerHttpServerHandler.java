package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.Main;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

import java.io.IOException;
import java.util.logging.Logger;

public class ConsumerHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ConsumerHttpServerHandler.class);

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
        InterfaceHttpData data = decoder.getBodyHttpData("parameter");

        if (data.getHttpDataType() != InterfaceHttpData.HttpDataType.Attribute) {
            LOGGER.error("Consumer received incorrect http request.");
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
        response.headers().set("Connection", "Keep-Alive");
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
