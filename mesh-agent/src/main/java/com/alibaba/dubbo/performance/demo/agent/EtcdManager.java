package com.alibaba.dubbo.performance.demo.agent;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.KV;
import com.coreos.jetcd.Lease;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.kv.GetResponse;
import com.coreos.jetcd.options.GetOption;
import com.coreos.jetcd.options.PutOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class EtcdManager {

    private final String ROOT_PATH = "dubbomesh";
    private final String SERVICE_NAME = "com.alibaba.dubbo.performance.demo.provider.IHelloService";
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdManager.class);

    private Lease leaseClient;
    private KV kvClient;
    private long leaseId;

    public EtcdManager() {
        Client client = Client.builder().endpoints(Options.ETCD_URL).build();
        leaseClient = client.getLeaseClient();
        kvClient = client.getKVClient();
        try {
            leaseId = leaseClient.grant(30).get().getID();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Get lease from etcd failed.");
            System.exit(1);
        }
        leaseClient.keepAlive(leaseId);
    }

    public void registerService() {
        try {
            String port = System.getProperty("server.port");
            String hostIp = InetAddress.getLocalHost().getHostAddress();
            String strKey = String.format("/%s/%s/%s:%s", ROOT_PATH, SERVICE_NAME, hostIp, port);
            ByteSequence key = ByteSequence.fromString(strKey);
            ByteSequence value = ByteSequence.fromString("");

            kvClient.put(key, value, PutOption.newBuilder().withLeaseId(leaseId).build()).get();
            LOGGER.info("Register a new service at:" + strKey);
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Failed to register etcd service.");
            System.exit(1);
        }
    }

    public List<InetSocketAddress> findServices() {
        String strPrefix = String.format("/%s/%s", ROOT_PATH, SERVICE_NAME);
        ByteSequence prefix = ByteSequence.fromString(strPrefix);
        GetResponse response = null;
        try {
            response = kvClient.get(prefix, GetOption.newBuilder().withPrefix(prefix).build()).get();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Fail to find service from etcd.");
            System.exit(1);
        }

        List<InetSocketAddress> endpoints = new ArrayList<>();
        for (com.coreos.jetcd.data.KeyValue kv: response.getKvs()) {
            String strKey = kv.getKey().toStringUtf8();
            int index = strKey.lastIndexOf("/");
            String strEndpoint = strKey.substring(index + 1, strKey.length());

            String[] splits = strEndpoint.split(":");
            String host = splits[0];
            int port = Integer.valueOf(splits[1]);

            endpoints.add(new InetSocketAddress(host, port));
        }

        if (endpoints.size() == 0) {
            LOGGER.error("Endpoint is empty, check provider service registry.");
            System.exit(1);
        }

        return endpoints;
    }
}
