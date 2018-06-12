package com.alibaba.dubbo.performance.demo.agent.provider;

import com.alibaba.dubbo.performance.demo.agent.EtcdManager;
import com.alibaba.dubbo.performance.demo.agent.IAgent;
import com.alibaba.dubbo.performance.demo.agent.Options;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProviderAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderAgent.class);

    private EtcdManager etcdManager = new EtcdManager();
    private ProviderDubboServerHandler serverHandler = new ProviderDubboServerHandler();
    private ProviderDubboClientHandler clientHandler = new ProviderDubboClientHandler();
    private Channel clientChannel;
    private Channel serverChannel() { return serverHandler.getChannel(); }
    private EventLoopGroup clientGroup = Options.isLinux ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
    private EventLoopGroup workerGroup = Options.isLinux ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
    private final Object lock = new Object();
    private int weight;

    public ProviderAgent(int weight) {
        this.weight = weight;
    }

    @Override
    public void start() {
        serverHandler.setReadNewRequestHandler((bytebuf, channel) -> {
            connectToProviderIfNeeded();
            clientChannel.writeAndFlush(bytebuf);
        });
        clientHandler.setReadNewResponseHandler((byteBuf) -> {
            serverChannel().writeAndFlush(byteBuf);
        });

        startServer();
    }

    private void connectToProviderIfNeeded() {
        if (clientChannel == null) {
            synchronized (lock) {
                if (clientChannel == null) {
                    connectToProvider();
                }
            }
        }
    }

    private void connectToProvider() {
        try {
            Bootstrap b = new Bootstrap();
            Class<? extends SocketChannel> clazz = Options.isLinux ? EpollSocketChannel.class : NioSocketChannel.class;
            b.group(clientGroup)
                    .channel(clazz)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(clientHandler);
                        }
                    });
            ChannelFuture f = b.connect("127.0.0.1", Options.PROVIDER_PORT).sync();
            f.channel().closeFuture().addListener(future ->  {
                    LOGGER.error("One channel to provider was closed:" + f.cause().toString());
                    // TODO: Reconnect logic if closed unexpectedly?
            });
            clientChannel = f.channel();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Connect to provider failed:" + "127.0.0.1:" + Options.PROVIDER_PORT);
            System.exit(1);
        }
    }

    private void startServer() {
        try {
            ServerBootstrap b = new ServerBootstrap();
            Class<? extends ServerSocketChannel> clazz = Options.isLinux ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
            b.group(workerGroup)
                    .channel(clazz)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(serverHandler);
                        }
                    });
            Channel channel = b.bind(Options.SERVER_PORT).sync().channel();
            etcdManager.registerService(weight);
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOGGER.error("Start server failed.");
            System.exit(1);
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
