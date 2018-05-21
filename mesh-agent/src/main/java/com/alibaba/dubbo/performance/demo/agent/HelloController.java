package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClient;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

@RestController
public class HelloController {

    private Logger logger = LoggerFactory.getLogger(HelloController.class);
    
    private IRegistry registry = new EtcdRegistry(System.getProperty("etcd.url"));
    private AsyncHttpClient asyncHttpClient = org.asynchttpclient.Dsl.asyncHttpClient();

    private RpcClient rpcClient = new RpcClient(registry);
    private Random random = new Random();
    private List<Endpoint> endpoints = null;
    private Object lock = new Object();

    @RequestMapping(value = "")
    public DeferredResult<String> invoke(@RequestParam("interface") String interfaceName,
                         @RequestParam("method") String method,
                         @RequestParam("parameterTypesString") String parameterTypesString,
                         @RequestParam("parameter") String parameter) throws Exception {
        String type = System.getProperty("type");   // 获取type参数
        if ("consumer".equals(type)){
            return consumer(interfaceName,method,parameterTypesString,parameter);
        } else if ("provider".equals(type)){
            return provider(interfaceName, method, parameterTypesString, parameter);
        } else {
            DeferredResult<String> result = new DeferredResult<>();
            result.setResult("Environment variable type is needed to set to provider or consumer.");
            return result;
        }
    }

    public DeferredResult<String> provider(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {
        DeferredResult<String> result = new DeferredResult<>();
        rpcClient.invoke(result::setResult, interfaceName,method,parameterTypesString,parameter);
        return result;
    }

    public DeferredResult<String> consumer(String interfaceName,String method,String parameterTypesString,String parameter) throws Exception {
        if (null == endpoints) {
            synchronized (lock) {
                if (null == endpoints) {
                    endpoints = registry.find("com.alibaba.dubbo.performance.demo.provider.IHelloService");
                }
            }
        }

        // 简单的负载均衡，随机取一个
        Endpoint endpoint = endpoints.get(random.nextInt(endpoints.size()));

        String url = "http://" + endpoint.getHost() + ":" + endpoint.getPort();

        org.asynchttpclient.Request request = org.asynchttpclient.Dsl.post(url)
                .addFormParam("interface", interfaceName)
                .addFormParam("method", method)
                .addFormParam("parameterTypesString", parameterTypesString)
                .addFormParam("parameter", parameter)
                .build();

        ListenableFuture<org.asynchttpclient.Response> responseFuture = asyncHttpClient.executeRequest(request);
        DeferredResult<String> result = new DeferredResult<>();

        Runnable callback = () -> {
            try {
                String value = responseFuture.get().getResponseBody();
                result.setResult(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        responseFuture.addListener(callback, null);

        return result;
    }
}
