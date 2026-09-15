package com.sentio.shared.web;

import java.net.URI;

import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class LocationUtility {

    private LocationUtility() {
        throw new UnsupportedOperationException();
    }

    public static URI buildLocation(@NonNull Object id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQuery(null)
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }

    public static URI buildLocationFromPathTemplate(@NonNull String pathTemplate, Object... uriVariables) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQuery(null)
                .path(pathTemplate)
                .buildAndExpand(uriVariables)
                .toUri();
    }

    public static <T> ResponseEntity<T> createdWithLocation(@NonNull Object id, T body) {
        return ResponseEntity.created(buildLocation(id)).body(body);
    }

    public static ResponseEntity<Void> createdWithLocation(@NonNull Object id) {
        return ResponseEntity.created(buildLocation(id)).build();
    }
}
