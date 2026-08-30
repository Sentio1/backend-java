package com.sentio.shared.entity.id.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record ClientId(@JsonValue long id) implements EntityId {

    public ClientId {
        EntityId.requirePositive(id, "Client id");
    }

    @JsonCreator
    public static ClientId of(long id) {
        return new ClientId(id);
    }
}
