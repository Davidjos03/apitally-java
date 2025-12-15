package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apitally.common.dto.Header;
import io.apitally.common.dto.Request;
import io.apitally.common.dto.Response;

public class RequestLoggerTest {

    private RequestLogger requestLogger;
    private RequestLoggingConfig requestLoggingConfig;

    @BeforeEach
    void setUp() {
        requestLoggingConfig = new RequestLoggingConfig();
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setQueryParamsIncluded(true);
        requestLoggingConfig.setRequestHeadersIncluded(true);
        requestLoggingConfig.setRequestBodyIncluded(true);
        requestLoggingConfig.setResponseHeadersIncluded(true);
        requestLoggingConfig.setResponseBodyIncluded(true);
        requestLogger = new RequestLogger(requestLoggingConfig);
    }

    @AfterEach
    void tearDown() {
        requestLogger.close();
    }

    @Test
    void testEndToEnd() {
        Header[] requestHeaders = new Header[] {
                new Header("User-Agent", "Test"),
        };
        Header[] responseHeaders = new Header[] {
                new Header("Content-Type", "application/json"),
        };
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                "tester",
                "GET",
                "/items",
                "http://test/items",
                requestHeaders,
                0L,
                new byte[0]);
        Response response = new Response(
                200,
                0.123,
                responseHeaders,
                13L,
                "{\"items\": []}".getBytes());
        Exception exception = new Exception("test");
        requestLogger.logRequest(request, response, exception);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);

        JsonNode jsonNode = items[0];
        assertEquals("GET", jsonNode.get("request").get("method").asText());
        assertEquals("/items", jsonNode.get("request").get("path").asText());
        assertEquals("http://test/items", jsonNode.get("request").get("url").asText());
        assertFalse(jsonNode.get("request").has("body"));
        assertEquals(200, jsonNode.get("response").get("statusCode").asInt());
        assertEquals(0.123, jsonNode.get("response").get("responseTime").asDouble(), 0.001);
        assertEquals("{\"items\":[]}",
                new String(Base64.getDecoder()
                        .decode(jsonNode.get("response").get("body").asText())));

        JsonNode requestHeadersNode = jsonNode.get("request").get("headers");
        assertTrue(requestHeadersNode.isArray());
        assertEquals(1, requestHeadersNode.size());
        assertEquals("User-Agent", requestHeadersNode.get(0).get(0).asText());
        assertEquals("Test", requestHeadersNode.get(0).get(1).asText());

        JsonNode responseHeadersNode = jsonNode.get("response").get("headers");
        assertTrue(responseHeadersNode.isArray());
        assertEquals(1, responseHeadersNode.size());
        assertEquals("Content-Type", responseHeadersNode.get(0).get(0).asText());
        assertEquals("application/json", responseHeadersNode.get(0).get(1).asText());

        JsonNode exceptionNode = jsonNode.get("exception");
        assertNotNull(exceptionNode);
        assertEquals("Exception", exceptionNode.get("type").asText());
        assertEquals("test", exceptionNode.get("message").asText());
        assertTrue(exceptionNode.get("stackTrace").asText().contains("test"));

        requestLogger.clear();

        items = getLoggedItems(requestLogger);
        assertEquals(0, items.length);
    }

    @Test
    void testExcludeBasedOnOptions() {
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setQueryParamsIncluded(false);
        requestLoggingConfig.setRequestHeadersIncluded(false);
        requestLoggingConfig.setRequestBodyIncluded(false);
        requestLoggingConfig.setResponseHeadersIncluded(false);
        requestLoggingConfig.setResponseBodyIncluded(false);
        requestLogger = new RequestLogger(requestLoggingConfig);

        Header[] requestHeaders = new Header[] {
                new Header("Content-Type", "application/json"),
        };
        Header[] responseHeaders = new Header[] {
                new Header("Content-Type", "application/json"),
        };
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                "tester",
                "POST",
                "/items",
                "http://test/items?token=my-secret-token",
                requestHeaders,
                16L,
                "{\"key\": \"value\"}".getBytes());
        Response response = new Response(
                200,
                0.123,
                responseHeaders,
                16L,
                "{\"key\": \"value\"}".getBytes());

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);
        assertEquals("http://test/items", items[0].get("request").get("url").asText());
        assertFalse(items[0].get("request").has("headers"));
        assertFalse(items[0].get("request").has("body"));
        assertFalse(items[0].get("response").has("headers"));
        assertFalse(items[0].get("response").has("body"));
    }

    @Test
    void testExcludeBasedOnCallback() {
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setCallbacks(new RequestLoggingCallbacks() {
            @Override
            public boolean shouldExclude(Request request, Response response) {
                return request.getConsumer() != null && request.getConsumer().contains("tester");
            }
        });
        requestLogger = new RequestLogger(requestLoggingConfig);

        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                "tester",
                "GET",
                "/items",
                "http://test/items",
                new Header[0],
                0L,
                new byte[0]);
        Response response = new Response(
                200,
                0.123,
                new Header[0],
                13L,
                "{\"items\": []}".getBytes());

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(0, items.length);
    }

    @Test
    void testExcludeBasedOnPath() {
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                null,
                "GET",
                "/healthz",
                "http://test/healthz",
                new Header[0],
                0L,
                new byte[0]);
        Response response = new Response(
                200,
                0.123,
                new Header[0],
                17L,
                "{\"healthy\": true}".getBytes());

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(0, items.length);
    }

    @Test
    void testExcludeBasedOnUserAgent() {
        Header[] requestHeaders = new Header[] {
                new Header("User-Agent", "ELB-HealthChecker/2.0"),
        };
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                null,
                "GET",
                "/",
                "http://test/",
                requestHeaders,
                0L,
                new byte[0]);
        Response response = new Response(
                200,
                0,
                new Header[0],
                0L,
                new byte[0]);

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(0, items.length);
    }

    @Test
    void testMaskHeaders() {
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setRequestHeadersIncluded(true);
        requestLoggingConfig.setHeaderMaskPatterns(List.of("(?i)test"));
        requestLogger = new RequestLogger(requestLoggingConfig);

        Header[] requestHeaders = new Header[] {
                new Header("Accept", "text/plain"),
                new Header("Authorization", "Bearer 123456"),
                new Header("X-Test", "123456"),
        };
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                null,
                "GET",
                "/test",
                "http://localhost:8000/test?foo=bar",
                requestHeaders,
                0L,
                new byte[0]);
        Response response = new Response(200, 0, new Header[0], 0L, new byte[0]);

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);
        JsonNode requestHeadersNode = items[0].get("request").get("headers");

        // Convert headers array to a map for easier testing
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        for (JsonNode header : requestHeadersNode) {
            headers.put(header.get(0).asText(), header.get(1).asText());
        }

        assertEquals("text/plain", headers.get("Accept"));
        assertEquals("******", headers.get("Authorization"));
        assertEquals("******", headers.get("X-Test"));
    }

    @Test
    void testMaskQueryParams() {
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setQueryParamsIncluded(true);
        requestLoggingConfig.setQueryParamMaskPatterns(List.of("(?i)test"));
        requestLogger = new RequestLogger(requestLoggingConfig);

        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                null,
                "GET",
                "/test",
                "http://localhost/test?secret=123456&test=123456&other=abcdef",
                new Header[0],
                0L,
                new byte[0]);
        Response response = new Response(200, 0, new Header[0], 0L, new byte[0]);

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);
        String url = items[0].get("request").get("url").asText();
        assertTrue(url.contains("secret=******"));
        assertTrue(url.contains("test=******"));
        assertTrue(url.contains("other=abcdef"));
    }

    @Test
    void testMaskBodyUsingCallback() {
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setRequestBodyIncluded(true);
        requestLoggingConfig.setResponseBodyIncluded(true);
        requestLoggingConfig.setCallbacks(new RequestLoggingCallbacks() {
            @Override
            public byte[] maskRequestBody(Request request) {
                if ("/test".equals(request.getPath())) {
                    return null;
                }
                return request.getBody();
            }

            @Override
            public byte[] maskResponseBody(Request request, Response response) {
                if ("/test".equals(request.getPath())) {
                    return null;
                }
                return response.getBody();
            }
        });
        requestLogger = new RequestLogger(requestLoggingConfig);

        Header[] requestHeaders = new Header[] {
                new Header("Content-Type", "application/json"),
        };
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                null,
                "GET",
                "/test",
                "http://test/test",
                requestHeaders,
                4L,
                "test".getBytes());
        Response response = new Response(
                200,
                0,
                new Header[] { new Header("Content-Type", "application/json") },
                4L,
                "test".getBytes());

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);
        String requestBody = new String(Base64.getDecoder()
                .decode(items[0].get("request").get("body").asText()));
        assertEquals("<masked>", requestBody);

        String responseBody = new String(Base64.getDecoder()
                .decode(items[0].get("response").get("body").asText()));
        assertEquals("<masked>", responseBody);
    }

    @Test
    void testMaskBodyFields() {
        requestLoggingConfig.setEnabled(true);
        requestLoggingConfig.setRequestBodyIncluded(true);
        requestLoggingConfig.setResponseBodyIncluded(true);
        requestLoggingConfig.setBodyFieldMaskPatterns(List.of("(?i)custom"));
        requestLogger = new RequestLogger(requestLoggingConfig);

        String requestBodyJson = "{\"username\":\"john_doe\",\"password\":\"secret123\",\"token\":\"abc123\",\"custom\":\"xyz789\",\"user_id\":42,\"api_key\":123,\"normal_field\":\"value\",\"nested\":{\"password\":\"nested_secret\",\"count\":5,\"deeper\":{\"auth\":\"deep_token\"}},\"array\":[{\"password\":\"array_secret\",\"id\":1},{\"normal\":\"text\",\"token\":\"array_token\"}]}";
        String responseBodyJson = "{\"status\":\"success\",\"secret\":\"response_secret\",\"data\":{\"pwd\":\"response_pwd\"}}";

        Header[] requestHeaders = new Header[] {
                new Header("Content-Type", "application/json"),
        };
        Request request = new Request(
                System.currentTimeMillis() / 1000.0,
                null,
                "POST",
                "/test",
                "http://localhost:8000/test?foo=bar",
                requestHeaders,
                (long) requestBodyJson.getBytes().length,
                requestBodyJson.getBytes());
        Response response = new Response(
                200,
                0.1,
                new Header[] { new Header("Content-Type", "application/json") },
                (long) responseBodyJson.getBytes().length,
                responseBodyJson.getBytes());

        requestLogger.logRequest(request, response, null);

        JsonNode[] items = getLoggedItems(requestLogger);
        assertEquals(1, items.length);

        String reqBodyBase64 = items[0].get("request").get("body").asText();
        JsonNode reqBody;
        String respBodyBase64 = items[0].get("response").get("body").asText();
        JsonNode respBody;

        try {
            ObjectMapper mapper = new ObjectMapper();
            reqBody = mapper.readTree(new String(Base64.getDecoder().decode(reqBodyBase64)));
            respBody = mapper.readTree(new String(Base64.getDecoder().decode(respBodyBase64)));
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON bodies", e);
        }

        // Test fields that should be masked
        assertEquals("******", reqBody.get("password").asText());
        assertEquals("******", reqBody.get("token").asText());
        assertEquals("******", reqBody.get("custom").asText());
        assertEquals("******", reqBody.get("nested").get("password").asText());
        assertEquals("******", reqBody.get("nested").get("deeper").get("auth").asText());
        assertEquals("******", reqBody.get("array").get(0).get("password").asText());
        assertEquals("******", reqBody.get("array").get(1).get("token").asText());
        assertEquals("******", respBody.get("secret").asText());
        assertEquals("******", respBody.get("data").get("pwd").asText());

        // Test fields that should NOT be masked
        assertEquals("john_doe", reqBody.get("username").asText());
        assertEquals(42, reqBody.get("user_id").asInt());
        assertEquals(123, reqBody.get("api_key").asInt());
        assertEquals("value", reqBody.get("normal_field").asText());
        assertEquals(5, reqBody.get("nested").get("count").asInt());
        assertEquals(1, reqBody.get("array").get(0).get("id").asInt());
        assertEquals("text", reqBody.get("array").get(1).get("normal").asText());
        assertEquals("success", respBody.get("status").asText());
    }

    private JsonNode[] getLoggedItems(RequestLogger requestLogger) {
        requestLogger.maintain();
        requestLogger.rotateFile();

        TempGzipFile logFile = requestLogger.getFile();
        if (logFile == null) {
            return new JsonNode[0];
        }

        try {
            List<String> lines = logFile.readDecompressedLines();
            JsonNode[] items = new JsonNode[lines.size()];
            ObjectMapper objectMapper = new ObjectMapper();

            for (int i = 0; i < lines.size(); i++) {
                items[i] = objectMapper.readTree(lines.get(i));
            }

            return items;
        } catch (IOException e) {
            throw new AssertionError("Failed to read gzipped file", e);
        }
    }
}
