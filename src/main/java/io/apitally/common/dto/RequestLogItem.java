package io.apitally.common.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RequestLogItem extends BaseDto {
    private final String uuid;
    private final Request request;
    private final Response response;
    private final ExceptionDto exception;

    public RequestLogItem(Request request, Response response, ExceptionDto exception) {
        this.uuid = UUID.randomUUID().toString();
        this.request = request;
        this.response = response;
        this.exception = exception;
    }

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }

    @JsonProperty("request")
    public Request getRequest() {
        return request;
    }

    @JsonProperty("response")
    public Response getResponse() {
        return response;
    }

    @JsonProperty("exception")
    public ExceptionDto getException() {
        return exception;
    }
}
