package io.apitally.common;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.apitally.common.dto.ExceptionDto;
import io.apitally.common.dto.Header;
import io.apitally.common.dto.Request;
import io.apitally.common.dto.RequestLogItem;
import io.apitally.common.dto.Response;

public class RequestLogger {
    private static final Logger logger = LoggerFactory.getLogger(RequestLogger.class);

    private static final int MAX_BODY_SIZE = 50_000; // 50 KB (uncompressed)
    private static final int MAX_FILE_SIZE = 1_000_000; // 1 MB (compressed)
    private static final int MAX_FILES = 50;
    private static final int MAX_PENDING_WRITES = 100;
    private static final byte[] BODY_TOO_LARGE = "<body too large>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY_MASKED = "<masked>".getBytes(StandardCharsets.UTF_8);
    private static final String MASKED = "******";
    public static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList("application/json", "text/plain");
    private static final Pattern JSON_CONTENT_TYPE_PATTERN = Pattern.compile("\\bjson\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> EXCLUDE_PATH_PATTERNS = Arrays.asList(
            "/_?healthz?$",
            "/_?health[_-]?checks?$",
            "/_?heart[_-]?beats?$",
            "/ping$",
            "/ready$",
            "/live$");
    private static final List<String> EXCLUDE_USER_AGENT_PATTERNS = Arrays.asList(
            "health[-_ ]?check",
            "microsoft-azure-application-lb",
            "googlehc",
            "kube-probe");
    private static final List<String> MASK_QUERY_PARAM_PATTERNS = Arrays.asList(
            "auth",
            "api-?key",
            "secret",
            "token",
            "password",
            "pwd");
    private static final List<String> MASK_HEADER_PATTERNS = Arrays.asList(
            "auth",
            "api-?key",
            "secret",
            "token",
            "cookie");
    private static final List<String> MASK_BODY_FIELD_PATTERNS = Arrays.asList(
            "password",
            "pwd",
            "token",
            "secret",
            "auth",
            "card[-_ ]?number",
            "ccv",
            "ssn");
    private static final int MAINTAIN_INTERVAL_SECONDS = 1;

    private final RequestLoggingConfig config;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock;
    private final Deque<RequestLogItem> pendingWrites;
    private final Deque<TempGzipFile> files;
    private TempGzipFile currentFile;
    private boolean enabled;
    private Long suspendUntil;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> maintainTask;

    private final List<Pattern> compiledPathExcludePatterns;
    private final List<Pattern> compiledUserAgentExcludePatterns;
    private final List<Pattern> compiledQueryParamMaskPatterns;
    private final List<Pattern> compiledHeaderMaskPatterns;
    private final List<Pattern> compiledBodyFieldMaskPatterns;

    public RequestLogger(RequestLoggingConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();
        this.pendingWrites = new ConcurrentLinkedDeque<>();
        this.files = new ConcurrentLinkedDeque<>();
        this.enabled = config.isEnabled();

        this.compiledPathExcludePatterns = compilePatterns(EXCLUDE_PATH_PATTERNS, config.getPathExcludePatterns());
        this.compiledUserAgentExcludePatterns = compilePatterns(EXCLUDE_USER_AGENT_PATTERNS, null);
        this.compiledQueryParamMaskPatterns = compilePatterns(MASK_QUERY_PARAM_PATTERNS,
                config.getQueryParamMaskPatterns());
        this.compiledHeaderMaskPatterns = compilePatterns(MASK_HEADER_PATTERNS, config.getHeaderMaskPatterns());
        this.compiledBodyFieldMaskPatterns = compilePatterns(MASK_BODY_FIELD_PATTERNS,
                config.getBodyFieldMaskPatterns());

        if (enabled) {
            startMaintenance();
        }
    }

    private static List<Pattern> compilePatterns(List<String> defaultPatterns, List<String> additionalPatterns) {
        List<String> patterns = new ArrayList<>(defaultPatterns);
        if (additionalPatterns != null) {
            patterns.addAll(additionalPatterns);
        }
        return patterns.stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
    }

    public RequestLoggingConfig getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSuspendUntil(long timestamp) {
        this.suspendUntil = timestamp;
    }

    public void logRequest(Request request, Response response, Exception exception) {
        if (!enabled || suspendUntil != null && suspendUntil > System.currentTimeMillis()) {
            return;
        }

        try {
            String userAgent = findHeader(request.getHeaders(), "user-agent");
            if (shouldExcludePath(request.getPath()) || shouldExcludeUserAgent(userAgent)) {
                return;
            }
            if (config.getCallbacks() != null && config.getCallbacks().shouldExclude(request, response)) {
                return;
            }

            if (!config.isRequestBodyIncluded() || !hasSupportedContentType(request.getHeaders())) {
                request.setBody(null);
            }
            if (!config.isResponseBodyIncluded() || !hasSupportedContentType(response.getHeaders())) {
                response.setBody(null);
            }

            ExceptionDto exceptionDto = null;
            if (exception != null && config.isExceptionIncluded()) {
                exceptionDto = new ExceptionDto(exception);
            }

            RequestLogItem item = new RequestLogItem(request, response, exceptionDto);
            pendingWrites.add(item);

            if (pendingWrites.size() > MAX_PENDING_WRITES) {
                pendingWrites.poll();
            }
        } catch (Exception e) {
            logger.error("Error while logging request", e);
        }
    }

    private void applyMasking(RequestLogItem item) {
        Request request = item.getRequest();
        Response response = item.getResponse();

        if (request.getBody() != null) {
            // Apply user-provided masking callback for request body
            if (config.getCallbacks() != null) {
                byte[] maskedBody = config.getCallbacks().maskRequestBody(request);
                request.setBody(maskedBody != null ? maskedBody : BODY_MASKED);
            }

            if (request.getBody().length > MAX_BODY_SIZE) {
                request.setBody(BODY_TOO_LARGE);
            }

            // Mask request body fields (if JSON)
            if (!Arrays.equals(request.getBody(), BODY_TOO_LARGE) && !Arrays.equals(request.getBody(), BODY_MASKED)
                    && hasJsonContentType(request.getHeaders())) {
                request.setBody(maskJsonBody(request.getBody()));
            }
        }

        if (response.getBody() != null) {
            // Apply user-provided masking callback for response body
            if (config.getCallbacks() != null) {
                byte[] maskedBody = config.getCallbacks().maskResponseBody(request, response);
                response.setBody(maskedBody != null ? maskedBody : BODY_MASKED);
            }

            if (response.getBody().length > MAX_BODY_SIZE) {
                response.setBody(BODY_TOO_LARGE);
            }

            // Mask response body fields (if JSON)
            if (!Arrays.equals(response.getBody(), BODY_TOO_LARGE) && !Arrays.equals(response.getBody(), BODY_MASKED)
                    && hasJsonContentType(response.getHeaders())) {
                response.setBody(maskJsonBody(response.getBody()));
            }
        }

        // Process headers
        request.setHeaders(
                config.isRequestHeadersIncluded()
                        ? maskHeaders(request.getHeaders()).toArray(new Header[0])
                        : new Header[0]);
        response.setHeaders(
                config.isResponseHeadersIncluded()
                        ? maskHeaders(response.getHeaders()).toArray(new Header[0])
                        : new Header[0]);

        // Process query params and URL
        if (request.getUrl() != null) {
            try {
                URL url = new URL(request.getUrl());
                String query = url.getQuery();
                if (!config.isQueryParamsIncluded()) {
                    query = null;
                } else if (query != null) {
                    query = maskQueryParams(query);
                }
                request.setUrl(new java.net.URL(url.getProtocol(), url.getHost(), url.getPort(),
                        url.getPath() + (query != null ? "?" + query : "")).toString());
            } catch (MalformedURLException e) {
                // Keep original URL if malformed
            }
        }
    }

    public void writeToFile() throws IOException {
        if (!enabled || pendingWrites.isEmpty()) {
            return;
        }
        lock.lock();
        try {
            if (currentFile == null) {
                currentFile = new TempGzipFile();
            }
            RequestLogItem item;
            while ((item = pendingWrites.poll()) != null) {
                applyMasking(item);

                ObjectNode itemNode = objectMapper.createObjectNode();
                itemNode.put("uuid", item.getUuid());
                itemNode.set("request", skipEmptyValues(objectMapper.valueToTree(item.getRequest())));
                itemNode.set("response", skipEmptyValues(objectMapper.valueToTree(item.getResponse())));
                if (item.getException() != null) {
                    itemNode.set("exception", objectMapper.valueToTree(item.getException()));
                }

                String serializedItem = objectMapper.writeValueAsString(itemNode);
                currentFile.writeLine(serializedItem.getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            lock.unlock();
        }
    }

    public TempGzipFile getFile() {
        return files.poll();
    }

    public void retryFileLater(TempGzipFile file) {
        files.addFirst(file);
    }

    public void rotateFile() {
        lock.lock();
        try {
            if (currentFile != null) {
                currentFile.close();
                files.add(currentFile);
                currentFile = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void maintain() {
        try {
            writeToFile();
        } catch (IOException e) {
            // Ignore
        }
        if (currentFile != null && currentFile.getSize() > MAX_FILE_SIZE) {
            rotateFile();
        }
        while (files.size() > MAX_FILES) {
            TempGzipFile file = files.poll();
            if (file != null) {
                file.delete();
            }
        }
        if (suspendUntil != null && suspendUntil < System.currentTimeMillis()) {
            suspendUntil = null;
        }
    }

    public void clear() {
        pendingWrites.clear();
        rotateFile();
        for (TempGzipFile file : files) {
            file.delete();
        }
        files.clear();
    }

    public void close() {
        enabled = false;
        stopMaintenance();
        clear();
    }

    private void startMaintenance() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "apitally-request-logger");
                thread.setDaemon(true);
                return thread;
            });
        }
        maintainTask = scheduler.scheduleAtFixedRate(
                this::maintain,
                0,
                MAINTAIN_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    private void stopMaintenance() {
        if (maintainTask != null) {
            maintainTask.cancel(false);
            maintainTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean shouldExcludePath(String path) {
        if (path == null) {
            return false;
        }
        return compiledPathExcludePatterns.stream()
                .anyMatch(p -> p.matcher(path).find());
    }

    private boolean shouldExcludeUserAgent(String userAgent) {
        return userAgent != null && compiledUserAgentExcludePatterns.stream()
                .anyMatch(p -> p.matcher(userAgent).find());
    }

    private boolean shouldMaskQueryParam(String name) {
        return compiledQueryParamMaskPatterns.stream()
                .anyMatch(p -> p.matcher(name).find());
    }

    private boolean shouldMaskHeader(String name) {
        return compiledHeaderMaskPatterns.stream()
                .anyMatch(p -> p.matcher(name).find());
    }

    private boolean shouldMaskBodyField(String name) {
        return compiledBodyFieldMaskPatterns.stream()
                .anyMatch(p -> p.matcher(name).find());
    }

    private String maskQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }
        StringBuilder result = new StringBuilder();
        String[] pairs = query.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String[] pair = pairs[i].split("=", 2);
            String name = pair[0];
            String value = pair.length > 1 ? pair[1] : "";
            if (i > 0) {
                result.append('&');
            }
            result.append(name).append('=');
            result.append(shouldMaskQueryParam(name) ? MASKED : value);
        }
        return result.toString();
    }

    private List<Header> maskHeaders(Header[] headers) {
        return Arrays.stream(headers)
                .map(header -> new Header(
                        header.getName(),
                        shouldMaskHeader(header.getName()) ? MASKED : header.getValue()))
                .collect(Collectors.toList());
    }

    private byte[] maskJsonBody(byte[] body) {
        try {
            String json = new String(body, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(json);
            maskJsonNode(node);
            return objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return body;
        }
    }

    private void maskJsonNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual() && shouldMaskBodyField(entry.getKey())) {
                    objectNode.put(entry.getKey(), MASKED);
                } else {
                    maskJsonNode(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::maskJsonNode);
        }
    }

    private boolean hasSupportedContentType(Header[] headers) {
        String contentType = findHeader(headers, "content-type");
        return contentType != null && ALLOWED_CONTENT_TYPES.stream()
                .anyMatch(contentType::startsWith);
    }

    private boolean hasJsonContentType(Header[] headers) {
        String contentType = findHeader(headers, "content-type");
        return contentType != null && JSON_CONTENT_TYPE_PATTERN.matcher(contentType).find();
    }

    private String findHeader(Header[] headers, String name) {
        return Arrays.stream(headers)
                .filter(h -> h.getName().toLowerCase().equals(name))
                .map(Header::getValue)
                .findFirst()
                .orElse(null);
    }

    private ObjectNode skipEmptyValues(ObjectNode node) {
        ObjectNode result = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNull()) {
                return;
            }
            if (entry.getValue().isArray() && entry.getValue().size() == 0) {
                return;
            }
            if (entry.getValue().isTextual() && entry.getValue().asText().isEmpty()) {
                return;
            }
            result.set(entry.getKey(), entry.getValue());
        });
        return result;
    }
}
