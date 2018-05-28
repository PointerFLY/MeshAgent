package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.EtcdManager;
import com.alibaba.dubbo.performance.demo.agent.IAgent;
import com.alibaba.dubbo.performance.demo.agent.Options;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerAgent.class);

    private EtcdManager etcdManager = new EtcdManager();
    private List<InetSocketAddress> endpoints = etcdManager.findServices();
    private List<Channel> clientChannels = new ArrayList<>();
    private List<Channel> serverChannels() { return serverHandler.getChannels(); }
    private EventLoopGroup clientGroup = new NioEventLoopGroup(1);
    private Random random = new Random();
    private ConsumerHttpClientHandler clientHandler = new ConsumerHttpClientHandler();
    private ConsumerHttpServerHandler serverHandler = new ConsumerHttpServerHandler();
    private AtomicInteger atomicInteger = new AtomicInteger();
    private ConcurrentHashMap<Integer, Channel> map = new ConcurrentHashMap<>();

    @Override
    public void start() {
        serverHandler.setReadNewRequestHandler((request, channel) -> {
            int index = random.nextInt(clientChannels.size());
            Channel clientChannel = clientChannels.get(index);
            InetSocketAddress endpoint = endpoints.get(index);
            request.headers().set("host", endpoint.getHostString() + ":" + endpoint.getPort());

            int requestId = atomicInteger.getAndIncrement();
            request.headers().set(Options.REQUEST_ID_KEY, requestId);
            map.put(requestId, channel);

            ReferenceCountUtil.retain(request);
            clientChannel.writeAndFlush(request);
        });
        clientHandler.setReadNewResponseHandler((response) -> {
            int requestId = Integer.valueOf(response.headers().get(Options.REQUEST_ID_KEY));
            Channel channel = map.get(requestId);
            ReferenceCountUtil.retain(response);
            channel.writeAndFlush(response);
            map.remove(requestId);
        });

        connectToProviderAgents();
        startServer();
    }

    private int count = 0;

    private void connectToProviderAgents() {
        try {
            Bootstrap b = new Bootstrap();
            b.group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(Options.HTTP_MAX_CONTENT_LENGTH));
                            p.addLast(clientHandler);
                        }
                    });

            for (InetSocketAddress endpoint: endpoints) {
                ChannelFuture f = b.connect(endpoint).sync();
                f.channel().closeFuture().addListener(future -> {
                    LOGGER.error("One channel to provider closed: " + future.cause().toString());
                    System.exit(1);
                    // TODO: Reconnect logic if closed unexpectedly?
                });
                clientChannels.add(f.channel());
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Connect to provider agent failed.");
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
