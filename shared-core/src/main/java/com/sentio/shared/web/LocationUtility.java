package com.sentio.shared.web;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class LocationUtility {

    private LocationUtility() {
        throw new UnsupportedOperationException();
    }

    public static URI buildLocation(Object id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

    public static <T> ResponseEntity<T> createdWithLocation(Object id, T body) {
        return ResponseEntity.created(buildLocation(id)).body(body);
    }

    public static ResponseEntity<Void> createdWithLocation(Object id) {
        return ResponseEntity.created(buildLocation(id)).build();
    }
}
