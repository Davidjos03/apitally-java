package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ValidationErrors extends ValidationError {
    private final int errorCount;

    public ValidationErrors(String consumer, String method, String path, String loc, String msg, String type,
            int errorCount) {
        super(consumer, method, path, loc, msg, type);
        this.errorCount = errorCount;
    }

    @JsonProperty("error_count")
    public int getErrorCount() {
        return errorCount;
    }
}
