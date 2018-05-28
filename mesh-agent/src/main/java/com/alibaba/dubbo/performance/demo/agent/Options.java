package com.alibaba.dubbo.performance.demo.agent;

public class Options {

    public static final String AGENT_TYPE = System.getProperty("type");
    public static final int SERVER_PORT = Integer.valueOf(System.getProperty("server.port"));
    public static final int PROVIDER_PORT;
    public static final String ETCD_URL = System.getProperty("etcd.url");

    public static final int HTTP_MAX_CONTENT_LENGTH = 65536;
    public static final String REQUEST_ID_KEY = "request-id";

    static {
        String strPort = System.getProperty("dubbo.protocol.port");
        PROVIDER_PORT = strPort != null ? Integer.valueOf(strPort) : 0;
    }
}
