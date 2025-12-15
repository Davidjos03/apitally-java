package io.apitally.common.dto;

public class Request extends RequestResponseBase {
    private final double timestamp;
    private final String consumer;
    private final String method;
    private final String path;
    private String url;

    public Request(double timestamp, String consumer, String method, String path, String url,
            Header[] headers, Long size, byte[] body) {
        super(headers, size, body);
        this.timestamp = timestamp;
        this.consumer = consumer;
        this.method = method;
        this.path = path;
        this.url = url;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
