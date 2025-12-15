package io.apitally.common;

import java.util.ArrayList;
import java.util.List;

public class RequestLoggingConfig {
    private boolean enabled = false;
    private boolean queryParamsIncluded = true;
    private boolean requestHeadersIncluded = false;
    private boolean requestBodyIncluded = false;
    private boolean responseHeadersIncluded = true;
    private boolean responseBodyIncluded = false;
    private boolean exceptionIncluded = true;
    private List<String> queryParamMaskPatterns = new ArrayList<>();
    private List<String> headerMaskPatterns = new ArrayList<>();
    private List<String> bodyFieldMaskPatterns = new ArrayList<>();
    private List<String> pathExcludePatterns = new ArrayList<>();
    private RequestLoggingCallbacks callbacks;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isQueryParamsIncluded() {
        return queryParamsIncluded;
    }

    public void setQueryParamsIncluded(boolean queryParamsIncluded) {
        this.queryParamsIncluded = queryParamsIncluded;
    }

    public boolean isRequestHeadersIncluded() {
        return requestHeadersIncluded;
    }

    public void setRequestHeadersIncluded(boolean requestHeadersIncluded) {
        this.requestHeadersIncluded = requestHeadersIncluded;
    }

    public boolean isRequestBodyIncluded() {
        return requestBodyIncluded;
    }

    public void setRequestBodyIncluded(boolean requestBodyIncluded) {
        this.requestBodyIncluded = requestBodyIncluded;
    }

    public boolean isResponseHeadersIncluded() {
        return responseHeadersIncluded;
    }

    public void setResponseHeadersIncluded(boolean responseHeadersIncluded) {
        this.responseHeadersIncluded = responseHeadersIncluded;
    }

    public boolean isResponseBodyIncluded() {
        return responseBodyIncluded;
    }

    public void setResponseBodyIncluded(boolean responseBodyIncluded) {
        this.responseBodyIncluded = responseBodyIncluded;
    }

    public boolean isExceptionIncluded() {
        return exceptionIncluded;
    }

    public void setExceptionIncluded(boolean exceptionIncluded) {
        this.exceptionIncluded = exceptionIncluded;
    }

    public List<String> getQueryParamMaskPatterns() {
        return queryParamMaskPatterns;
    }

    public void setQueryParamMaskPatterns(List<String> queryParamMaskPatterns) {
        this.queryParamMaskPatterns = queryParamMaskPatterns;
    }

    public List<String> getHeaderMaskPatterns() {
        return headerMaskPatterns;
    }

    public void setHeaderMaskPatterns(List<String> headerMaskPatterns) {
        this.headerMaskPatterns = headerMaskPatterns;
    }

    public List<String> getBodyFieldMaskPatterns() {
        return bodyFieldMaskPatterns;
    }

    public void setBodyFieldMaskPatterns(List<String> bodyFieldMaskPatterns) {
        this.bodyFieldMaskPatterns = bodyFieldMaskPatterns;
    }

    public List<String> getPathExcludePatterns() {
        return pathExcludePatterns;
    }

    public void setPathExcludePatterns(List<String> pathExcludePatterns) {
        this.pathExcludePatterns = pathExcludePatterns;
    }

    public RequestLoggingCallbacks getCallbacks() {
        return callbacks;
    }

    protected void setCallbacks(RequestLoggingCallbacks callbacks) {
        this.callbacks = callbacks;
    }
}
