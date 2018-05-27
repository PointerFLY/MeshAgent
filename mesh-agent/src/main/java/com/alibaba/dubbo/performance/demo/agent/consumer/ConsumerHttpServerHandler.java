package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@ChannelHandler.Sharable
public class ConsumerHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @FunctionalInterface
    public interface ReadNewRequestHandler {
        void handle(FullHttpRequest request, Channel channel);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerHttpServerHandler.class);

    private List<Channel> channels = new ArrayList<>();

    private ReadNewRequestHandler handler;

    public List<Channel> getChannels() {
        return channels;
    }

    public void setReadNewRequestHandler(ReadNewRequestHandler handler) {
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        channels.add(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        channels.remove(ctx.channel());
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
