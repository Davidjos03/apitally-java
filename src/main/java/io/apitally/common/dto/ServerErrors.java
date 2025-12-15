package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ServerErrors extends ServerError {
    private final int errorCount;

    public ServerErrors(String consumer, String method, String path, String type, String message,
            StackTraceElement[] stackTrace, int errorCount) {
        super(consumer, method, path, type, message, stackTrace);
        this.errorCount = errorCount;
    }

    @JsonProperty("error_count")
    public int getErrorCount() {
        return errorCount;
    }
}
