package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.consumer.ConsumerHttpClientHandler;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;

@ChannelHandler.Sharable
public class ProviderDubboClientHandler extends SimpleChannelInboundHandler<RpcResponse> {

    @FunctionalInterface
    public interface ReadNewResponseHandler {
        void handle(RpcResponse response);
    }

    private ReadNewResponseHandler handler;

    public void setReadNewResponseHandler(ReadNewResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        handler.handle(response);
    }
}
