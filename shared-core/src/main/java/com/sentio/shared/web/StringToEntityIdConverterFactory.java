package com.sentio.shared.web;

import com.sentio.shared.entity.id.EntityId;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Component
public class StringToEntityIdConverterFactory implements ConverterFactory<String, EntityId> {

    @Override
    public @NonNull <T extends EntityId> Converter<String, ? extends T> getConverter(@NonNull final Class<T> targetType) {
        return new StringToEntityIdConverter<>(targetType);
    }

    private static final class StringToEntityIdConverter<T extends EntityId> implements Converter<String, T> {

        private final MethodHandle handle;
        private final String targetTypeName;

        StringToEntityIdConverter(Class<T> targetType) {
            this.targetTypeName = targetType.getSimpleName();
            MethodHandle resolvedHandle = null;

            try {
                resolvedHandle = MethodHandles.publicLookup()
                        .findStatic(targetType, "of", MethodType.methodType(targetType, long.class));
            } catch (NoSuchMethodException | IllegalAccessException _) {
            }

            if (resolvedHandle == null) {
                try {
                    resolvedHandle = MethodHandles.publicLookup()
                            .findConstructor(targetType, MethodType.methodType(void.class, long.class));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new IllegalStateException("EntityId " + targetTypeName + " must have of(long) or public constructor(long)", e);
                }
            }

            this.handle = resolvedHandle;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T convert(@NonNull String source) {
            long value = Long.parseLong(source);

            try {
                return (T) handle.invoke(value);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalArgumentException("Failed to instantiate " + targetTypeName + " from value: " + source, t);
            }
        }
    }
}
