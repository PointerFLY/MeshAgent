package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.consumer.ConsumerAgent;
import com.alibaba.dubbo.performance.demo.agent.provider.ProviderAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        IAgent agent = null;
        switch (Options.AGENT_TYPE) {
            case "provider-small":
                agent = new ProviderAgent(1);
                break;
            case "provider-medium":
                agent = new ProviderAgent(2);
                break;
            case "provider-large":
                agent = new ProviderAgent(3);
                break;
            case "consumer":
                agent = new ConsumerAgent();
                break;
            default:
                LOGGER.error("VM option agent type must be set as provider or consumer.");
                System.exit(1);
        }

        agent.start();
    }
}
