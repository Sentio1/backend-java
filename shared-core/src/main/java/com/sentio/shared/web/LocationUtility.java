package com.sentio.shared.web;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

public final class LocationUtility {

    private LocationUtility() {
        throw new UnsupportedOperationException();
    }

    public static URI buildLocation(Object id) {
        return ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
