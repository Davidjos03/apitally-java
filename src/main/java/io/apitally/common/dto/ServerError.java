package io.apitally.common.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ServerError {
    private static final int MAX_MSG_LENGTH = 2048;
    private static final int MAX_STACKTRACE_LENGTH = 65536;

    private final String consumer;
    private final String method;
    private final String path;
    private final String type;
    private final String message;
    private final StackTraceElement[] stackTrace;
    private final String stackTraceString;

    public ServerError(String consumer, String method, String path, String type, String message,
            StackTraceElement[] stackTrace) {
        this.consumer = consumer;
        this.method = method;
        this.path = path;
        this.type = type;
        this.message = truncateMessage(message);
        this.stackTrace = stackTrace;
        this.stackTraceString = truncateStackTrace(stackTrace);
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

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("msg")
    public String getMessage() {
        return message;
    }

    @JsonIgnore
    public StackTraceElement[] getStackTrace() {
        return stackTrace;
    }

    @JsonProperty("traceback")
    public String getStackTraceString() {
        return stackTraceString;
    }

    public static String truncateMessage(String msg) {
        msg = msg.trim();
        if (msg.length() <= MAX_MSG_LENGTH) {
            return msg;
        }
        String suffix = "... (truncated)";
        int cutoff = MAX_MSG_LENGTH - suffix.length();
        return msg.substring(0, cutoff) + suffix;
    }

    public static String truncateStackTrace(StackTraceElement[] stackTrace) {
        String suffix = "... (truncated) ...";
        int cutoff = MAX_STACKTRACE_LENGTH - suffix.length();
        List<String> truncatedLines = new ArrayList<>();
        int length = 0;
        for (StackTraceElement element : stackTrace) {
            String line = element.toString().trim();
            if (length + line.length() + 1 > cutoff) {
                truncatedLines.add(suffix);
                break;
            }
            truncatedLines.add(line);
            length += line.length() + 1;
        }
        return String.join("\n", truncatedLines);
    }
}
