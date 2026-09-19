package com.sentio.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller {@code Long} parameter to the authenticated caller's user id (the JWT's
 * {@code sub} claim, via {@code SecurityUtils.getCurrentUserId()}). Throws
 * {@link com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException} (403) if the
 * request isn't authenticated, so handler methods never see a null/spoofable value.
 *
 * <p>Prefer {@code @CurrentUser} from the security starter when the full principal is needed
 * (e.g. user-service, which loads a real profile) - this is for services like core-service that
 * only ever need the bare id to stamp on a {@code created_by}/{@code updated_by} column.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {}
