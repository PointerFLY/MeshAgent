package com.alibaba.dubbo.performance.demo.agent.provider;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class ProviderHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @FunctionalInterface
    public interface ReadNewRequestHandler {
        void handle(FullHttpRequest request, Channel channel);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderHttpServerHandler.class);

    private ReadNewRequestHandler handler;

    private Channel channel;

    public void setReadNewRequestHandler(ReadNewRequestHandler handler) {
        this.handler = handler;
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channel = ctx.channel();
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
