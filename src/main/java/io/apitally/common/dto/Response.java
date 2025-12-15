package io.apitally.common.dto;

public class Response extends RequestResponseBase {
    private final int statusCode;
    private final double responseTime;

    public Response(int statusCode, double responseTime, Header[] headers, Long size, byte[] body) {
        super(headers, size, body);
        this.statusCode = statusCode;
        this.responseTime = responseTime;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public double getResponseTime() {
        return responseTime;
    }
}
