package com.sentio.user_service.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class HttpRequestUtils {

    private HttpRequestUtils() {
        throw new UnsupportedOperationException();
    }

    public static String getClientIP(final HttpServletRequest request) {
        String headerValue = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(headerValue)) {
            String[] ipAddresses = headerValue.split(",");
            return ipAddresses[ipAddresses.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
