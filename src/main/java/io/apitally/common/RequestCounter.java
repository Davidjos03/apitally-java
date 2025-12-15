package io.apitally.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.apitally.common.dto.Requests;

public class RequestCounter {
    private final Map<String, Integer> requestCounts;
    private final Map<String, Long> requestSizeSums;
    private final Map<String, Long> responseSizeSums;
    private final Map<String, Map<Integer, Integer>> responseTimes;
    private final Map<String, Map<Integer, Integer>> requestSizes;
    private final Map<String, Map<Integer, Integer>> responseSizes;

    public RequestCounter() {
        this.requestCounts = new ConcurrentHashMap<>();
        this.requestSizeSums = new ConcurrentHashMap<>();
        this.responseSizeSums = new ConcurrentHashMap<>();
        this.responseTimes = new ConcurrentHashMap<>();
        this.requestSizes = new ConcurrentHashMap<>();
        this.responseSizes = new ConcurrentHashMap<>();
    }

    public void addRequest(String consumer, String method, String path, int statusCode, long responseTime,
            long requestSize, long responseSize) {
        String key = String.join("|",
                consumer,
                method.toUpperCase(),
                path,
                String.valueOf(statusCode));

        // Increment request count
        requestCounts.merge(key, 1, Integer::sum);

        // Add response time (rounded to nearest 10ms)
        responseTimes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        Map<Integer, Integer> responseTimeMap = responseTimes.get(key);
        int responseTimeMsBin = (int) (Math.floor(responseTime / 10.0) * 10);
        responseTimeMap.merge(responseTimeMsBin, 1, Integer::sum);

        // Add request size (rounded down to nearest KB)
        if (requestSize >= 0) {
            requestSizeSums.merge(key, requestSize, Long::sum);
            requestSizes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            Map<Integer, Integer> requestSizeMap = requestSizes.get(key);
            int requestSizeKbBin = (int) Math.floor(requestSize / 1000.0);
            requestSizeMap.merge(requestSizeKbBin, 1, Integer::sum);
        }

        // Add response size (rounded down to nearest KB)
        if (responseSize >= 0) {
            responseSizeSums.merge(key, responseSize, Long::sum);
            responseSizes.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
            Map<Integer, Integer> responseSizeMap = responseSizes.get(key);
            int responseSizeKbBin = (int) Math.floor(responseSize / 1000.0);
            responseSizeMap.merge(responseSizeKbBin, 1, Integer::sum);
        }
    }

    public List<Requests> getAndResetRequests() {
        List<Requests> data = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : requestCounts.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split("\\|");
            String consumer = parts[0].isEmpty() ? null : parts[0];
            String method = parts[1];
            String path = parts[2];
            int statusCode = Integer.parseInt(parts[3]);

            Map<Integer, Integer> responseTimeMap = responseTimes.getOrDefault(key, new ConcurrentHashMap<>());
            Map<Integer, Integer> requestSizeMap = requestSizes.getOrDefault(key, new ConcurrentHashMap<>());
            Map<Integer, Integer> responseSizeMap = responseSizes.getOrDefault(key, new ConcurrentHashMap<>());

            Requests item = new Requests(
                    consumer,
                    method,
                    path,
                    statusCode,
                    entry.getValue(),
                    requestSizeSums.getOrDefault(key, 0L),
                    responseSizeSums.getOrDefault(key, 0L),
                    responseTimeMap,
                    requestSizeMap,
                    responseSizeMap);
            data.add(item);
        }

        // Reset all counters
        requestCounts.clear();
        requestSizeSums.clear();
        responseSizeSums.clear();
        responseTimes.clear();
        requestSizes.clear();
        responseSizes.clear();

        return data;
    }
}
