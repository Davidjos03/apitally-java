package io.apitally.spring.app;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TestException extends RuntimeException {

    public TestException(String message) {
        super(message);
    }
}
