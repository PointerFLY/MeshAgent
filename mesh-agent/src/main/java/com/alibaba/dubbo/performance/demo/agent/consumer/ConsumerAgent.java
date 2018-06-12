package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.EtcdManager;
import com.alibaba.dubbo.performance.demo.agent.IAgent;
import com.alibaba.dubbo.performance.demo.agent.Options;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcEncoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.JsonUtils;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcRequest;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcInvocation;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ConsumerAgent implements IAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerAgent.class);

    private EtcdManager etcdManager = new EtcdManager();
    private List<Channel> clientChannels;
    private List<Endpoint> endpoints;
    private List<Channel> serverChannels() { return serverHandler.getChannels(); }
    private EventLoopGroup clientGroup = Options.isLinux ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
    private EventLoopGroup workerGroup = Options.isLinux ? new EpollEventLoopGroup(3) : new NioEventLoopGroup(3);
    private ConsumerDubboClientHandler clientHandler = new ConsumerDubboClientHandler();
    private ConsumerHttpServerHandler serverHandler = new ConsumerHttpServerHandler();
    private long requestId = 0;
    private Map<Long, Channel> map = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private LoadBalance loadBalance;

    @Override
    public void start() {
        LOGGER.info(String.format("isLinux: %s", Options.isLinux));
        serverHandler.setReadNewRequestHandler((request, channel) -> {
            connectToProviderAgentsIfNeeded();

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

            long id;
            int index;
            synchronized (lock) {
                id = requestId;
                requestId += 1;
                index = loadBalance.nextIndex();
            }

            map.put(id, channel);

            RpcRequest rpcRequest = new RpcRequest();
            rpcRequest.setVersion("2.0.0");
            rpcRequest.setTwoWay(true);
            rpcRequest.setData(invocation);
            rpcRequest.setId(id);

            Channel clientChannel = clientChannels.get(index);
            clientChannel.writeAndFlush(rpcRequest);
        });
        clientHandler.setReadNewResponseHandler((response) -> {
            long requestId = response.getRequestId();
            response.getBytes().retain();
            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HTTP_1_1, OK, response.getBytes());
            httpResponse.headers().setInt("content-length", response.getBytes().readableBytes());

            Channel channel = map.remove(requestId);
            channel.writeAndFlush(httpResponse);
        });

        startServer();
    }

    private void connectToProviderAgentsIfNeeded() {
        if (clientChannels == null) {
            synchronized (lock) {
                if (clientChannels == null) {
                    connectToProviderAgents();
                }
            }
        }
    }

    private void connectToProviderAgents() {
        endpoints = etcdManager.findServices();
        loadBalance = new LoadBalance(endpoints);
        clientChannels = new ArrayList<>();

        try {
            Bootstrap b = new Bootstrap();
            Class<? extends SocketChannel> clazz = Options.isLinux ? EpollSocketChannel.class : NioSocketChannel.class;
            b.group(clientGroup)
                    .channel(clazz)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new DubboRpcDecoder());
                            p.addLast(new DubboRpcEncoder());
                            p.addLast(clientHandler);
                        }
                    });

            for (Endpoint endpoint: endpoints) {
                ChannelFuture f = b.connect(endpoint.getHost(), endpoint.getPort()).sync();
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
        EventLoopGroup bossGroup = Options.isLinux ? new EpollEventLoopGroup(1) : new NioEventLoopGroup(1);
        try {
            ServerBootstrap b = new ServerBootstrap();
            Class<? extends ServerSocketChannel> clazz = Options.isLinux ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
            b.group(bossGroup, workerGroup)
                    .channel(clazz)
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
