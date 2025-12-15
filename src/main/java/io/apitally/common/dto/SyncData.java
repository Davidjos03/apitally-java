package io.apitally.common.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SyncData extends BaseDto {
    private final double timestamp;
    private final UUID instanceUuid;
    private final UUID messageUuid;
    private final List<Requests> requests;
    private final List<ValidationErrors> validationErrors;
    private final List<ServerErrors> serverErrors;
    private final List<Consumer> consumers;

    public SyncData(UUID instanceUuid, List<Requests> requests, List<ValidationErrors> validationErrors,
            List<ServerErrors> serverErrors, List<Consumer> consumers) {
        this.timestamp = System.currentTimeMillis() / 1000.0;
        this.instanceUuid = instanceUuid;
        this.messageUuid = UUID.randomUUID();
        this.requests = requests;
        this.validationErrors = validationErrors;
        this.serverErrors = serverErrors;
        this.consumers = consumers;
    }

    @JsonProperty("timestamp")
    public double getTimestamp() {
        return timestamp;
    }

    @JsonProperty("instance_uuid")
    public UUID getInstanceUuid() {
        return instanceUuid;
    }

    @JsonProperty("message_uuid")
    public UUID getMessageUuid() {
        return messageUuid;
    }

    @JsonProperty("requests")
    public List<Requests> getRequests() {
        return requests;
    }

    @JsonProperty("validation_errors")
    public List<ValidationErrors> getValidationErrors() {
        return validationErrors;
    }

    @JsonProperty("server_errors")
    public List<ServerErrors> getServerErrors() {
        return serverErrors;
    }

    @JsonProperty("consumers")
    public List<Consumer> getConsumers() {
        return consumers;
    }

    @JsonIgnore
    public double getAgeInSeconds() {
        return System.currentTimeMillis() / 1000.0 - timestamp;
    }
}
