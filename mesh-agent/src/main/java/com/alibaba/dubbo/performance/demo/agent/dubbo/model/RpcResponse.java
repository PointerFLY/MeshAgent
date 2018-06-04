package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import io.netty.buffer.ByteBuf;

public class RpcResponse extends RefResponse {

    private int requestId;
    private ByteBuf bytes;

    protected void destroy() {
        bytes.release();
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public ByteBuf getBytes() {
        return bytes;
    }

    public void setBytes(ByteBuf bytes) {
        this.bytes = bytes;
    }
}
