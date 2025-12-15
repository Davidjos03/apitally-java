package io.apitally.common.dto;

public class ExceptionDto {
    private String type;
    private String message;
    private String stackTrace;

    public ExceptionDto(Exception exception) {
        this.type = exception.getClass().getSimpleName();
        this.message = ServerError.truncateMessage(exception.getMessage());
        this.stackTrace = ServerError.truncateStackTrace(exception.getStackTrace());
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getStackTrace() {
        return stackTrace;
    }
}
