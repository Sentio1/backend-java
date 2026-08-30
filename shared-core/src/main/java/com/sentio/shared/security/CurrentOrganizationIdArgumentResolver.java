package com.sentio.shared.security;

import com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException;
import com.sentio.shared.entity.id.organization.OrganizationId;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentOrganizationIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOrganizationId.class)
                && (OrganizationId.class.equals(parameter.getParameterType())
                        || Long.class.equals(parameter.getParameterType())
                        || long.class.equals(parameter.getParameterType()));
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Long rawId = OrganizationContext.getCurrentOrganizationId()
                .orElseThrow(() -> new ForbiddenOperationException("Request is missing organization context"));

        if (OrganizationId.class.equals(parameter.getParameterType())) {
            return OrganizationId.of(rawId);
        }

        return rawId;
    }
}
