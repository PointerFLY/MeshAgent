package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCounted;

public class RefResponse implements ReferenceCounted {

    private int refCount = 1;

    protected void destroy() {

    }

    @Override
    public int refCnt() {
        return refCount;
    }

    @Override
    public ReferenceCounted touch() {
        return this;
    }

    @Override
    public ReferenceCounted touch(Object hint) {
        return this;
    }

    @Override
    public ReferenceCounted retain() {
        refCount += 1;
        return this;
    }

    @Override
    public ReferenceCounted retain(int increment) {
        refCount += increment;
        return this;
    }

    @Override
    public boolean release(int decrement) {
        if (refCount == 0) {
            return false;
        }

        refCount -= decrement;
        if (refCount < 0) {
            refCount = 0;
            destroy();
            return true;
        }

        return false;
    }

    @Override
    public boolean release() {
        if (refCount == 0) {
            return false;
        }

        refCount -= 1;
        if (refCount == 0) {
            destroy();
            return true;
        }
        return false;
    }
}
