package com.alibaba.dubbo.performance.demo.agent.dubbo.model;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

public class RpcFuture implements Future<Object> {

    private static ThreadPoolTaskExecutor executor;

    static {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setThreadNamePrefix("default_task_executor_thread");
        executor.initialize();
    }

    private CountDownLatch latch = new CountDownLatch(1);

    private Runnable listener;

    private RpcResponse response;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public Object get() throws InterruptedException {
         //boolean b = latch.await(100, TimeUnit.MICROSECONDS);
        latch.await();
        try {
            return response.getBytes();
        }catch (Exception e){
            e.printStackTrace();
        }
        return "Error";
    }

    public void addListener(Runnable listener) {
        this.listener = listener;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException {
        boolean b = latch.await(timeout,unit);
        return response.getBytes();
    }

    public void done(RpcResponse response){
        this.response = response;
        try {
            executor.execute(listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
        latch.countDown();
    }
}
