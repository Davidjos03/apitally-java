package io.apitally.common.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class Requests {
    private final String consumer;
    private final String method;
    private final String path;
    private final int statusCode;
    private final int requestCount;
    private final long requestSizeSum;
    private final long responseSizeSum;
    private final Map<Integer, Integer> responseTimes;
    private final Map<Integer, Integer> requestSizes;
    private final Map<Integer, Integer> responseSizes;

    public Requests(String consumer, String method, String path, int statusCode, int requestCount,
            long requestSizeSum, long responseSizeSum, Map<Integer, Integer> responseTimes,
            Map<Integer, Integer> requestSizes, Map<Integer, Integer> responseSizes) {
        this.consumer = consumer;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.requestCount = requestCount;
        this.requestSizeSum = requestSizeSum;
        this.responseSizeSum = responseSizeSum;
        this.responseTimes = responseTimes;
        this.requestSizes = requestSizes;
        this.responseSizes = responseSizes;
    }

    @JsonProperty("consumer")
    public String getConsumer() {
        return consumer;
    }

    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    @JsonProperty("status_code")
    public int getStatusCode() {
        return statusCode;
    }

    @JsonProperty("request_count")
    public int getRequestCount() {
        return requestCount;
    }

    @JsonProperty("request_size_sum")
    public long getRequestSizeSum() {
        return requestSizeSum;
    }

    @JsonProperty("response_size_sum")
    public long getResponseSizeSum() {
        return responseSizeSum;
    }

    @JsonProperty("response_times")
    public Map<Integer, Integer> getResponseTimes() {
        return responseTimes;
    }

    @JsonProperty("request_sizes")
    public Map<Integer, Integer> getRequestSizes() {
        return requestSizes;
    }

    @JsonProperty("response_sizes")
    public Map<Integer, Integer> getResponseSizes() {
        return responseSizes;
    }
}
