package io.apitally.common.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StartupData extends BaseDto {
    private final UUID instanceUuid;
    private final UUID messageUuid;
    private final List<Path> paths;
    private final Map<String, String> versions;
    private final String client;

    public StartupData(UUID instanceUuid, List<Path> paths, Map<String, String> versions, String client) {
        this.instanceUuid = instanceUuid;
        this.messageUuid = UUID.randomUUID();
        this.paths = paths;
        this.versions = versions;
        this.client = client;
    }

    @JsonProperty("instance_uuid")
    public UUID getInstanceUuid() {
        return instanceUuid;
    }

    @JsonProperty("message_uuid")
    public UUID getMessageUuid() {
        return messageUuid;
    }

    @JsonProperty("paths")
    public List<Path> getPaths() {
        return paths;
    }

    @JsonProperty("versions")
    public Map<String, String> getVersions() {
        return versions;
    }

    @JsonProperty("client")
    public String getClient() {
        return client;
    }
}
