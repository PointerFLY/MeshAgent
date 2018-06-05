package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import io.netty.buffer.ByteBuf;

public class RpcResponse extends Ref {

    private long requestId;
    private ByteBuf bytes;

    protected void destroy() {
        bytes.release();
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public ByteBuf getBytes() {
        return bytes;
    }

    public void setBytes(ByteBuf bytes) {
        this.bytes = bytes;
    }
}
