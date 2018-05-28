package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.EtcdManager;
import com.alibaba.dubbo.performance.demo.agent.IAgent;
import com.alibaba.dubbo.performance.demo.agent.Options;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcEncoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.JsonUtils;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Request;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcInvocation;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostStandardRequestDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ProviderAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderAgent.class);

    private EtcdManager etcdManager = new EtcdManager();
    private ProviderHttpServerHandler serverHandler = new ProviderHttpServerHandler();
    private ProviderDubboClientHandler clientHandler = new ProviderDubboClientHandler();
    private Channel clientChannel;
    private Channel serverChannel() { return serverHandler.getChannel(); }
    private EventLoopGroup clientGroup = new NioEventLoopGroup(1);

    @Override
    public void start() {
        serverHandler.setReadNewRequestHandler((request, channel) -> {
            HttpPostStandardRequestDecoder decoder = new HttpPostStandardRequestDecoder(request);
            Attribute methodAttr = (Attribute)decoder.getBodyHttpData("method");
            Attribute interfaceAttr = (Attribute)decoder.getBodyHttpData("interface");
            Attribute paramTypesAttr = (Attribute)decoder.getBodyHttpData("parameterTypesString");
            Attribute paramsAttr = (Attribute)decoder.getBodyHttpData("parameter");

            RpcInvocation invocation = new RpcInvocation();
            try {
                invocation.setMethodName(methodAttr.getValue());
                invocation.setAttachment("path", interfaceAttr.getValue());
                invocation.setParameterTypes(paramTypesAttr.getValue());    // Dubbo内部用"Ljava/lang/String"来表示参数类型是String
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));
                JsonUtils.writeObject(paramsAttr.getValue(), writer);
                invocation.setArguments(out.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            } finally {
                decoder.destroy();
            }

            Request dubboRequest = new Request();
            dubboRequest.setId(request.headers().getInt(Options.REQUEST_ID_KEY));
            dubboRequest.setVersion("2.0.0");
            dubboRequest.setTwoWay(true);
            dubboRequest.setData(invocation);

            clientChannel.writeAndFlush(dubboRequest);
        });
        clientHandler.setReadNewResponseHandler((response) -> {
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(response.getBytes()));
            httpResponse.headers().set("content-type", "text/plain");
            httpResponse.headers().setInt("content-length", httpResponse.content().readableBytes());
            httpResponse.headers().set(Options.REQUEST_ID_KEY, response.getRequestId());
            serverChannel().writeAndFlush(httpResponse);
        });

        connectToProvider();
        startServer();
    }

    private void connectToProvider() {
        try {
            Bootstrap b = new Bootstrap();
            b.group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new DubboRpcEncoder());
                            p.addLast(new DubboRpcDecoder());
                            p.addLast(clientHandler);
                        }
                    });
            ChannelFuture f = b.connect("127.0.0.1", Options.PROVIDER_PORT).sync();
            f.channel().closeFuture().addListener(future ->  {
                    LOGGER.error("One channel to provider was closed.");
                    // TODO: Reconnect logic if closed unexpectedly?
            });
            clientChannel = f.channel();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Connect to provider failed");
            System.exit(1);
        }
    }

    private void startServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(1);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(Options.HTTP_MAX_CONTENT_LENGTH));
                            p.addLast(serverHandler);
                        }
                    });
            Channel channel = b.bind(Options.SERVER_PORT).sync().channel();
            etcdManager.registerService();
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOGGER.error("Start server failed.");
            System.exit(1);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
