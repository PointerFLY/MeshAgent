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
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Reference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumerAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerAgent.class);
    private static final String REQUEST_ID_KEY = "request-id";

    private EtcdManager etcdManager = new EtcdManager();
    private List<Channel> clientChannels = new ArrayList<>();
    private List<Channel> serverChannels() { return serverHandler.getChannels(); }
    private EventLoopGroup clientGroup = new NioEventLoopGroup();
    private Random random = new Random();
    private ConsumerHttpClientHandler clientHandler = new ConsumerHttpClientHandler();
    private ConsumerHttpServerHandler serverHandler = new ConsumerHttpServerHandler();
    private AtomicLong atomicLong = new AtomicLong();
    private ConcurrentHashMap<String, Channel> map = new ConcurrentHashMap<>();

    @Override
    public void start() {
        serverHandler.setReadNewRequestHandler((request, channel) -> {
            long requestId = atomicLong.getAndIncrement();
            request.headers().set(REQUEST_ID_KEY, requestId);
            request.headers().set("host", "127.0.0.1:30002");
            map.put(String.valueOf(requestId), channel);

            Channel clientChannel = clientChannels.get(random.nextInt(clientChannels.size()));
            ReferenceCountUtil.retain(request);
            clientChannel.writeAndFlush(request);
        });
        clientHandler.setReadNewResponseHandler((response) -> {
            String id = response.headers().get(REQUEST_ID_KEY);
            Channel channel = map.get(id);
            ReferenceCountUtil.retain(response);
            channel.writeAndFlush(response);
        });

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
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(Options.HTTP_MAX_CONTENT_LENGTH));
                            p.addLast(clientHandler);
                        }
                    });

            List<InetSocketAddress> endpoints = etcdManager.findServices();
            for (InetSocketAddress endpoint: endpoints) {
                ChannelFuture f = b.connect(endpoint).sync();
                f.channel().closeFuture().addListener((future) -> {
                    LOGGER.error("One channel to provider closed: " + future.cause().toString());;
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
        EventLoopGroup workerGroup = new NioEventLoopGroup();
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
