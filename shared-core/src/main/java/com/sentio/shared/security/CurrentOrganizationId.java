package com.sentio.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller {@code Long} parameter to the caller's organization, read from the JWT's
 * {@code org_id} claim - see {@link OrganizationContext}. Throws
 * {@link com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException} (403) if the
 * request carries no such claim, so handler methods never see a null/spoofable value.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentOrganizationId {}
