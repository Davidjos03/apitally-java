package io.apitally.spring.app;

import io.apitally.common.dto.Request;
import io.apitally.common.dto.Response;

public class RequestLoggingCallbacks implements io.apitally.common.RequestLoggingCallbacks {

    @Override
    public boolean shouldExclude(Request request, Response response) {
        return true;
    }
}
