package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.Options;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

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
