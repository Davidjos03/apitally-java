package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Path {
    private final String method;
    private final String path;

    public Path(String method, String path) {
        this.method = method;
        this.path = path;
    }

    @JsonProperty("method")
    public String getMethod() {
        return method;
    }

    @JsonProperty("path")
    public String getPath() {
        return path;
    }
}
