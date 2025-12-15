package io.apitally.common.dto;

import java.util.Arrays;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RequestResponseBase {
    private Header[] headers;
    private Long size;
    private byte[] body;

    public RequestResponseBase(Header[] headers, Long size, byte[] body) {
        this.headers = headers;
        this.size = size;
        this.body = body;
    }

    @JsonIgnore
    public Header[] getHeaders() {
        return headers;
    }

    @JsonProperty("headers")
    public String[][] getHeadersForJson() {
        return Arrays.stream(headers)
                .map(header -> new String[] { header.getName(), header.getValue() })
                .toArray(String[][]::new);
    }

    public void setHeaders(Header[] headers) {
        this.headers = headers;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    @JsonIgnore
    public byte[] getBody() {
        return body;
    }

    @JsonProperty("body")
    public String getBase64EncodedBody() {
        return body != null ? Base64.getEncoder().encodeToString(body) : null;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
