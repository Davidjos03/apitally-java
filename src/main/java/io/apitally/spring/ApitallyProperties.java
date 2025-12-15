package io.apitally.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.apitally.common.RequestLoggingCallbacks;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@ConfigurationProperties(prefix = "apitally")
@Validated
public class ApitallyProperties {
    private static final Logger logger = LoggerFactory.getLogger(ApitallyProperties.class);

    @NotNull(message = "Client ID must be set")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "Client ID must be a valid UUID")
    private String clientId;
    @Pattern(regexp = "^[\\w-]{1,32}$", message = "Env must be 1-32 characters long and contain only word characters and hyphens")
    private String env = "default";
    private RequestLoggingConfig requestLogging = new RequestLoggingConfig();

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public RequestLoggingConfig getRequestLogging() {
        return requestLogging;
    }

    public void setRequestLogging(RequestLoggingConfig requestLogging) {
        this.requestLogging = requestLogging;
    }

    public static class RequestLoggingConfig extends io.apitally.common.RequestLoggingConfig {
        @Pattern(regexp = "^[\\w.]+$", message = "Callbacks class must be a valid class name")
        private String callbacksClass;

        public String getCallbacksClass() {
            return callbacksClass;
        }

        public void setCallbacksClass(String callbacksClass) {
            this.callbacksClass = callbacksClass;
            if (callbacksClass != null) {
                try {
                    Class<?> clazz = Class.forName(callbacksClass);
                    if (RequestLoggingCallbacks.class.isAssignableFrom(clazz)) {
                        setCallbacks((RequestLoggingCallbacks) clazz.getDeclaredConstructor().newInstance());
                    }
                } catch (ReflectiveOperationException e) {
                    logger.error("Failed to initialize request logging callbacks", e);
                    setCallbacks(null);
                }
            } else {
                setCallbacks(null);
            }
        }
    }
}
