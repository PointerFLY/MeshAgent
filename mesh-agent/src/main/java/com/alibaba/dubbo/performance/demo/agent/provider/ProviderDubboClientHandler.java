package com.alibaba.dubbo.performance.demo.agent.provider;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@ChannelHandler.Sharable
public class ProviderDubboClientHandler extends ChannelInboundHandlerAdapter{

    @FunctionalInterface
    public interface ReadNewResponseHandler {
        void handle(ByteBuf byteBuf);
    }

    private ReadNewResponseHandler handler;

    public void setReadNewResponseHandler(ReadNewResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object byteBuf) {
        handler.handle((ByteBuf)byteBuf);
    }
}
