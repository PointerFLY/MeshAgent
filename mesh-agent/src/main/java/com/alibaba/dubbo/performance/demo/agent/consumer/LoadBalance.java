package com.alibaba.dubbo.performance.demo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LoadBalance {

    private final List<Integer> weights;
    private List<Integer> roundIndices = new ArrayList<>();
    private int cursor = 0;

    public LoadBalance(List<Endpoint> endpoints) {
        weights = endpoints.stream().map(Endpoint::getWeight).collect(Collectors.toList());
        for (int i = 0; i < weights.size(); i++) {
            int weight = weights.get(i);
            do {
                roundIndices.add(i);
                weight--;
            } while (weight > 0);
        }
    }

    public int nextIndex() {
        cursor++;
        cursor %= roundIndices.size();
        return roundIndices.get(cursor);
    }
}
