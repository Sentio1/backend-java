package com.sentio.shared.entity;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import java.util.Optional;
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
            throws ResourceNotFoundException {
        if (value == null) {
            throw new IllegalArgumentException("Parameter for " + getEntityName() + "Finder cannot be null");
        }

        return finder.apply(value).orElseThrow(() -> new ResourceNotFoundException(errorMessage));
    }

    protected <Value> @NonNull Entity findBy(Value value, String fieldName, Function<Value, Optional<Entity>> finder)
            throws ResourceNotFoundException {
        if (value == null) {
            throw new IllegalArgumentException("Parameter for " + getEntityName() + "Finder cannot be null");
        }

        return finder.apply(value).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), fieldName, value));
    }
}
