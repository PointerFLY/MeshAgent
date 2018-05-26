package com.alibaba.dubbo.performance.demo.agent.consumer;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;

@ChannelHandler.Sharable
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
