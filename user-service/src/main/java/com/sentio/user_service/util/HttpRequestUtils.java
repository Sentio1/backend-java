package com.sentio.user_service.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestUtils {

    private HttpRequestUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Never trust X-Forwarded-For (or any other forwarded header) by hand-parsing it
     * here - it's fully client-controlled unless a trusted proxy strips/overwrites it
     * before the request reaches us, and no request-level code can tell the two cases
     * apart. When this service is actually deployed behind a trusted reverse proxy /
     * ingress / load balancer, enable {@code server.forward-headers-strategy: framework}
     * for that environment instead: Spring's ForwardedHeaderFilter then rewrites
     * getRemoteAddr() itself from the forwarded headers, so this method stays correct
     * in both modes without knowing which one it's running in.
     */
    public static String getClientIP(final HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
