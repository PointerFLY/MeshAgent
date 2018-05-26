package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.consumer.ConsumerHttpServerHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ProviderHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @FunctionalInterface
    public interface ReadNewRequestHandler {
        void handle(FullHttpRequest request, Channel channel);
    }

    private ReadNewRequestHandler handler;

    private Channel channel;

    public void setReadNewRequestHandler(ReadNewRequestHandler handler) {
        this.handler = handler;
    }

    public Channel getChannel() {
        return channel;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderHttpServerHandler.class);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        handler.handle(request, ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
