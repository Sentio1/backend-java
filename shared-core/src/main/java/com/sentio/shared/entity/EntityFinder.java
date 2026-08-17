package com.sentio.shared.entity;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** EntityFinder interface. */
public interface EntityFinder<T, ID> {

    Page<T> findAll(Pageable pageable);

    T findById(ID id);

    Optional<T> findByIdOptional(ID id);

    boolean existsById(ID id);
}
