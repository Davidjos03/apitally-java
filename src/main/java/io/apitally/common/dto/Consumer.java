package io.apitally.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Consumer {
    private final String identifier;
    private final String name;
    private final String group;

    public Consumer(String identifier) {
        this(identifier, null, null);
    }

    public Consumer(String identifier, String name, String group) {
        if (identifier != null) {
            identifier = identifier.trim();
            if (identifier.length() > 128) {
                identifier = identifier.substring(0, 128);
            }
        }
        if (name != null) {
            name = name.trim();
            if (name.length() > 64) {
                name = name.substring(0, 64);
            }
        }
        if (group != null) {
            group = group.trim();
            if (group.length() > 64) {
                group = group.substring(0, 64);
            }
        }
        this.identifier = identifier;
        this.name = name;
        this.group = group;
    }

    @JsonProperty("identifier")
    public String getIdentifier() {
        return identifier;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("group")
    public String getGroup() {
        return group;
    }
}
