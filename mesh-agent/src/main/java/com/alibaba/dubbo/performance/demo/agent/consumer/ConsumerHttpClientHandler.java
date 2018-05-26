package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.ReferenceCountUtil;

public class ConsumerHttpClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

    private ReadNewResponseHandler handler;

    public void setReadNewResponseHandler(ReadNewResponseHandler handler) {
        this.handler = handler;
    }

    @FunctionalInterface
    public interface ReadNewResponseHandler {
        void handle(FullHttpResponse response);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
        handler.handle(response);
    }
}
