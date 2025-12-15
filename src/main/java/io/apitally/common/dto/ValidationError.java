package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ValidationError extends BaseDto {
    private final String consumer;
    private final String method;
    private final String path;
    private final String loc;
    private final String msg;
    private final String type;

    public ValidationError(String consumer, String method, String path, String loc, String msg, String type) {
        this.consumer = consumer;
        this.method = method;
        this.path = path;
        this.loc = loc;
        this.msg = msg;
        this.type = type;
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

    @JsonIgnore
    public String getLoc() {
        return loc;
    }

    @JsonProperty("loc")
    public String[] getLocSplit() {
        return loc.split("\\.");
    }

    @JsonProperty("msg")
    public String getMsg() {
        return msg;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }
}
