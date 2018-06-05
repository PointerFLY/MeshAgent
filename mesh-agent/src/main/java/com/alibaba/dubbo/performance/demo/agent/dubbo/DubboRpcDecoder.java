package com.alibaba.dubbo.performance.demo.agent.dubbo;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class DubboRpcDecoder extends ByteToMessageDecoder {

    private static final int HEADER_LENGTH = 16;

    private long requestId;
    private int dataLength = 0;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        do {
            if (dataLength == 0) {
                if (in.readableBytes() < HEADER_LENGTH) {
                    break;
                }

                in.readerIndex(4);
                requestId = in.readLong();
                dataLength = in.readInt();
                in.discardReadBytes();
            } else {
                if (in.readableBytes() < dataLength) {
                    break;
                }

                in.readerIndex(2);
                ByteBuf data = in.readBytes(dataLength - 3);

                in.readerIndex(in.readerIndex() + 1);
                in.discardReadBytes();
                dataLength = 0;

                RpcResponse response = new RpcResponse();
                response.setRequestId(requestId);
                response.setBytes(data);
                out.add(response);
            }
        } while (in.isReadable());
    }
}
