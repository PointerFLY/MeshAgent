package com.alibaba.dubbo.performance.demo.agent.provider;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class ProviderDubboServerHandler extends ChannelInboundHandlerAdapter {

    @FunctionalInterface
    public interface ReadNewRequestHandler {
        void handle(ByteBuf byteBuf, Channel channel);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderDubboServerHandler.class);

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
    public void channelRead(ChannelHandlerContext ctx, Object byteBuf) {
        handler.handle((ByteBuf)byteBuf, ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
