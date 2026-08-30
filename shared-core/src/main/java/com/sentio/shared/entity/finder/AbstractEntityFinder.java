package com.sentio.shared.entity.finder;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** AbstractEntityFinder class. */
public abstract class AbstractEntityFinder<Entity, ID> implements EntityFinder<Entity, ID> {

    protected abstract JpaRepository<Entity, ID> getRepository();

    protected abstract String getEntityName();

    @Override
    public Page<Entity> findAll(Pageable pageable) {
        return getRepository().findAll(pageable);
    }

    @Override
    public Entity findById(ID id) throws ResourceNotFoundException {
        return getRepository().findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), "id", id));
    }

    @Override
    public Optional<Entity> findByIdOptional(ID id) {
        return getRepository().findById(id);
    }

    @Override
    public boolean existsById(ID id) {
        return getRepository().existsById(id);
    }

    protected <Value> @NonNull Entity findBy(Value value, Function<Value, Optional<Entity>> finder, String errorMessage)
            throws IllegalArgumentException, ResourceNotFoundException {
        if (value == null) {
            throw new IllegalArgumentException("Parameter for " + getEntityName() + "Finder cannot be null");
        }

        return finder.apply(value).orElseThrow(() -> new ResourceNotFoundException(errorMessage));
    }

    protected <Value> @NonNull Entity findBy(Value value, String fieldName, Function<Value, Optional<Entity>> finder)
            throws IllegalArgumentException, ResourceNotFoundException {
        if (value == null) {
            throw new IllegalArgumentException("Parameter for " + getEntityName() + "Finder cannot be null");
        }

        return finder.apply(value).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), fieldName, value));
    }

    protected <Value, Value2> @NonNull Entity findBy(Value value, Value2 value2, BiFunction<Value, Value2, Optional<Entity>> finder)
        throws IllegalArgumentException, ResourceNotFoundException {
        if (value == null || value2 == null) {
            throw new IllegalArgumentException("Parameter for " + getEntityName() + "Finder cannot be null");
        }

        return finder.apply(value, value2).orElseThrow(() -> new ResourceNotFoundException(getEntityName() + " instance not found: " + value + ", " + value2));
    }
}
