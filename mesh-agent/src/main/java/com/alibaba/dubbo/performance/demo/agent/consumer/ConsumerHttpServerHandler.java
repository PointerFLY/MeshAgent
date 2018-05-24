package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.io.IOException;

public class ConsumerHttpServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        FullHttpRequest request = (FullHttpRequest)msg;
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
        InterfaceHttpData data = decoder.getBodyHttpData("interface");

        if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            try {
                String value = attribute.getValue();
                System.out.println("interface:" + value);
            } catch (IOException e) {

            }
        }

        HttpRequest req = (HttpRequest) msg;
//
//        boolean keepAlive = HttpUtil.isKeepAlive(req);
//        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(CONTENT));
//        response.headers().set(CONTENT_TYPE, "text/plain");
//        response.headers().setInt(CONTENT_LENGTH, response.content().readableBytes());
//
//        if (!keepAlive) {
//            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
//        } else {
//            response.headers().set(CONNECTION, KEEP_ALIVE);
//            ctx.write(response);
//        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
