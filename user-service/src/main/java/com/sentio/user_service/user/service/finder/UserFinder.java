package com.sentio.user_service.user.service.finder;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.EntityFinder;
import com.sentio.user_service.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** UserFinder interface. */
public interface UserFinder extends EntityFinder<User, Long> {

    Page<User> search(String email, Boolean includeDeleted, Pageable pageable);

    User findByIdIncludingDeleted(long userId) throws ResourceNotFoundException;

    User findByEmail(String email) throws ResourceNotFoundException;

    User findByEmail(String email, String errorMessage) throws ResourceNotFoundException;
}
