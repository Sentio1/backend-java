package com.sentio.shared.security;

import com.lisovskyi.security.autoconfigure.security.SecurityUtils;
import com.lisovskyi.web.error.autoconfigure.standard.ForbiddenOperationException;
import com.sentio.shared.entity.id.user.UserId;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (UserId.class.equals(parameter.getParameterType())
                        || Long.class.equals(parameter.getParameterType())
                        || long.class.equals(parameter.getParameterType()));
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Long rawId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ForbiddenOperationException("Request is missing user context"));

        if (UserId.class.equals(parameter.getParameterType())) {
            return UserId.of(rawId);
        }

        return rawId;
    }
}
