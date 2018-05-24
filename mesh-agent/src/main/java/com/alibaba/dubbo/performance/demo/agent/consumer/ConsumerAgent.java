package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.IAgent;
import com.alibaba.dubbo.performance.demo.agent.Options;
import com.alibaba.dubbo.performance.demo.agent.EtcdManager;
import com.alibaba.dubbo.performance.demo.agent.Main;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ConsumerAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private EtcdManager etcdManager = new EtcdManager();
    private List<InetSocketAddress> endpoints = etcdManager.findServices();
    private List<Channel> channels = new ArrayList<>();
    private EventLoopGroup clientGroup = new NioEventLoopGroup();

    @Override
    public void start() {
        connectToProviderAgents();
        startServer();
    }

    private void connectToProviderAgents() {
        try {
            Bootstrap b = new Bootstrap();
            b.group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(Options.HTTP_MAX_CONTENT_LENGTH));
                            p.addLast(new ConsumerHttpClientHandler());
                        }
                    });
            for (InetSocketAddress endpoint: endpoints) {
                ChannelFuture f = b.connect(endpoint).sync();
                f.channel().closeFuture().addListener((ChannelFuture future) -> {
                    LOGGER.error("One channel to provider was closed.");
                    // TODO: Reconnect logic if closed unexpectedly?
                });
                channels.add(f.channel());
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Connect to provider agent failed.");
            System.exit(1);
        }
    }

    private void startServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(4);
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
                            p.addLast(new ConsumerHttpServerHandler());
                        }
                    });

            Channel ch = b.bind(Options.SERVER_PORT).sync().channel();
            ch.closeFuture().sync();
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
