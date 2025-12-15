package io.apitally.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.apitally.common.dto.ValidationError;
import io.apitally.common.dto.ValidationErrors;

public class ValidationErrorCounter {
    private final Map<String, Integer> errorCounts;
    private final Map<String, ValidationError> errorDetails;

    public ValidationErrorCounter() {
        this.errorCounts = new ConcurrentHashMap<>();
        this.errorDetails = new ConcurrentHashMap<>();
    }

    public void addValidationError(String consumer, String method, String path, String loc, String msg, String type) {
        ValidationError validationError = new ValidationError(consumer, method, path, loc, msg, type);
        String key = getKey(validationError);
        errorDetails.putIfAbsent(key, validationError);
        errorCounts.merge(key, 1, Integer::sum);
    }

    public List<ValidationErrors> getAndResetValidationErrors() {
        List<ValidationErrors> data = new ArrayList<>();
        errorCounts.forEach((key, count) -> {
            ValidationError error = errorDetails.get(key);
            if (error != null) {
                data.add(new ValidationErrors(error.getConsumer(), error.getMethod(), error.getPath(), error.getLoc(),
                        error.getMsg(), error.getType(), count));
            }
        });
        errorCounts.clear();
        errorDetails.clear();
        return data;
    }

    private String getKey(ValidationError error) {
        String hashInput = String.join("|",
                error.getConsumer() != null ? error.getConsumer() : "",
                error.getMethod().toUpperCase(),
                error.getPath(),
                String.join(".", error.getLoc()),
                error.getMsg().trim(),
                error.getType());
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(hashInput.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
