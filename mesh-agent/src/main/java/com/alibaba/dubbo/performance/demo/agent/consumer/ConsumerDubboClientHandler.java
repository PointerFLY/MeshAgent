package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class ConsumerDubboClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    private ReadNewResponseHandler handler;

    public void setReadNewResponseHandler(ReadNewResponseHandler handler) {
        this.handler = handler;
    }

    @FunctionalInterface
    public interface ReadNewResponseHandler {
        void handle(RpcResponse response);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        handler.handle(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
