package com.sentio.shared.entity.id.organization;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record OrganizationId(@JsonValue long id) implements EntityId {

    public OrganizationId {
        EntityId.requirePositive(id, "Organization id");
    }

    @JsonCreator
    public static OrganizationId of(long id) {
        return new OrganizationId(id);
    }
}
