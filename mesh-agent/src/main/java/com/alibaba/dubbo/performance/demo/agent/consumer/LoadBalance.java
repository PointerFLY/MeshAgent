package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.Endpoint;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class LoadBalance {

    private List<Integer> weights;
    private Random random = new Random();

    LoadBalance(List<Endpoint> endpoints) {
        weights = endpoints.stream().map(Endpoint::getWeight).collect(Collectors.toList());
    }

    public int nextIndex() {
        int index = random.nextInt(weights.size());
        return index;
    }
}
