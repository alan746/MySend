package com.mysend.operations;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "operations")
public class OperationsEndpoint {

    private final OperationalMetrics metrics;

    public OperationsEndpoint(OperationalMetrics metrics) {
        this.metrics = metrics;
    }

    @ReadOperation
    public Map<String, Object> operations() {
        return metrics.snapshot();
    }
}
