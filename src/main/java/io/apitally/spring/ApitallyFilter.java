package io.apitally.spring;

import java.io.IOException;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import io.apitally.common.ApitallyClient;
import io.apitally.common.ConsumerRegistry;
import io.apitally.common.RequestLogger;
import io.apitally.common.dto.Consumer;
import io.apitally.common.dto.Header;
import io.apitally.common.dto.Request;
import io.apitally.common.dto.Response;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

public class ApitallyFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(ApitallyFilter.class);

    private final ApitallyClient client;

    public ApitallyFilter(ApitallyClient client) {
        this.client = client;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!client.isEnabled() || request.getMethod().equals("OPTIONS")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestContentType = request.getContentType();
        final boolean shouldCacheRequest = client.requestLogger.getConfig().isEnabled()
                && client.requestLogger.getConfig().isRequestBodyIncluded()
                && requestContentType != null
                && RequestLogger.ALLOWED_CONTENT_TYPES.stream()
                        .anyMatch(allowedContentType -> requestContentType.startsWith(allowedContentType));
        final boolean shouldCacheResponse = client.requestLogger.getConfig().isEnabled()
                && client.requestLogger.getConfig().isResponseBodyIncluded();
        ContentCachingRequestWrapper cachingRequest = shouldCacheRequest
                ? new ContentCachingRequestWrapper(request)
                : null;
        ContentCachingResponseWrapper cachingResponse = shouldCacheResponse
                ? new ContentCachingResponseWrapper(response)
                : null;
        CountingResponseWrapper countingResponse = cachingResponse == null
                ? new CountingResponseWrapper(response)
                : null;

        Exception exception = null;
        final long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(
                    cachingRequest != null ? cachingRequest : request,
                    cachingResponse != null ? cachingResponse : countingResponse);
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            final long responseTimeInMillis = System.currentTimeMillis() - startTime;
            final String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

            // Get request and response body
            final byte[] requestBody = cachingRequest != null ? cachingRequest.getContentAsByteArray() : new byte[0];
            final byte[] responseBody = cachingResponse != null ? cachingResponse.getContentAsByteArray() : new byte[0];
            if (cachingResponse != null) {
                cachingResponse.copyBodyToResponse();
            }

            try {
                // Register consumer and get consumer identifier
                final Consumer consumer = ConsumerRegistry.consumerFromObject(request.getAttribute("apitallyConsumer"));
                client.consumerRegistry.addOrUpdateConsumer(consumer);
                final String consumerIdentifier = consumer != null ? consumer.getIdentifier() : "";

                // Get captured exception
                Object capturedException = request.getAttribute("apitallyCapturedException");
                if (exception == null && capturedException != null && capturedException instanceof Exception) {
                    exception = (Exception) capturedException;
                }

                // Add request to counter
                final long requestContentLength = request.getContentLengthLong();
                final long requestSize = requestContentLength >= 0 ? requestContentLength
                        : cachingRequest != null ? requestBody.length : -1;
                final long responseContentLength = getResponseContentLength(response);
                final long responseSize = responseContentLength >= 0 ? responseContentLength
                        : cachingResponse != null ? responseBody.length
                                : countingResponse != null ? countingResponse.getByteCount() : -1;
                client.requestCounter.addRequest(
                        consumerIdentifier, request.getMethod(), path, response.getStatus(), responseTimeInMillis,
                        requestSize, responseSize);

                // Log request
                if (client.requestLogger.isEnabled()) {
                    final Header[] requestHeaders = Collections.list(request.getHeaderNames()).stream()
                            .flatMap(name -> Collections.list(request.getHeaders(name)).stream()
                                    .map(value -> new Header(name, value)))
                            .toArray(Header[]::new);
                    final Header[] responseHeaders = response.getHeaderNames().stream()
                            .flatMap(name -> response.getHeaders(name).stream()
                                    .map(value -> new Header(name, value)))
                            .toArray(Header[]::new);

                    client.requestLogger.logRequest(
                            new Request(startTime / 1000.0, consumerIdentifier, request.getMethod(), path,
                                    request.getRequestURL().toString(), requestHeaders, requestSize, requestBody),
                            new Response(response.getStatus(), responseTimeInMillis / 1000.0, responseHeaders,
                                    responseSize, responseBody),
                            exception);
                }

                // Add validation error to counter
                if (response.getStatus() >= 400 && response.getStatus() < 500) {
                    if (capturedException instanceof ConstraintViolationException e) {
                        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
                            client.validationErrorCounter.addValidationError(
                                    consumerIdentifier, request.getMethod(), path,
                                    violation.getPropertyPath().toString(), violation.getMessage(),
                                    violation.getConstraintDescriptor().getAnnotation().annotationType()
                                            .getSimpleName());
                        }
                    } else if (capturedException instanceof MethodArgumentNotValidException e) {
                        for (FieldError error : e.getBindingResult().getFieldErrors()) {
                            client.validationErrorCounter.addValidationError(
                                    consumerIdentifier, request.getMethod(), path,
                                    error.getObjectName() + "." + error.getField(), error.getDefaultMessage(),
                                    error.getCode());
                        }
                    }
                }

                // Add server error to counter
                if (response.getStatus() == 500 && exception != null) {
                    client.serverErrorCounter.addServerError(consumerIdentifier, request.getMethod(), path, exception);
                }
            } catch (Exception e) {
                logger.error("Error in Apitally filter", e);
            }
        }
    }

    private static long getResponseContentLength(HttpServletResponse response) {
        String contentLength = response.getHeader("content-length");
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
            }
        }
        return -1L;
    }

    private static class CountingResponseWrapper extends HttpServletResponseWrapper {
        private CountingServletOutputStream countingStream;

        public CountingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (countingStream == null) {
                countingStream = new CountingServletOutputStream(super.getOutputStream());
            }
            return countingStream;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (countingStream != null) {
                countingStream.flush();
            }
            super.flushBuffer();
        }

        public long getByteCount() {
            return countingStream != null ? countingStream.getByteCount() : 0;
        }
    }

    private static class CountingServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream outputStream;
        private long byteCount;

        public CountingServletOutputStream(ServletOutputStream outputStream) {
            this.outputStream = outputStream;
            this.byteCount = 0;
        }

        @Override
        public boolean isReady() {
            return outputStream.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            outputStream.setWriteListener(writeListener);
        }

        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
            byteCount++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            outputStream.write(b);
            byteCount += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            outputStream.write(b, off, len);
            byteCount += len;
        }

        @Override
        public void flush() throws IOException {
            outputStream.flush();
        }

        @Override
        public void close() throws IOException {
            outputStream.close();
        }

        public long getByteCount() {
            return byteCount;
        }
    }
}
