package com.sentio.user_service.user.service.finder;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.AbstractEntityFinder;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** UserFinderImpl class. */
public class UserFinderImpl extends AbstractEntityFinder<User, Long> implements UserFinder {

    private final UserRepository userRepository;

    @Override
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<User> search(String email, Boolean includeDeleted, Pageable pageable) {
        return userRepository.search(email, includeDeleted, pageable);
    }

    public @NonNull User findByIdIncludingDeleted(long userId) throws ResourceNotFoundException {
        return findBy(userId, "id", userRepository::findByIdIncludingDeleted);
    }

    @Override
    public @NonNull User findByEmail(String email) throws ResourceNotFoundException {
        return findBy(email, "email", userRepository::findByEmail);
    }

    @Override
    public @NonNull User findByEmail(String email, String errorMessage) throws ResourceNotFoundException {
        return findBy(email, userRepository::findByEmail, errorMessage);
    }

    @Override
    protected JpaRepository<User, Long> getRepository() {
        return userRepository;
    }

    @Override
    protected String getEntityName() {
        return "User";
    }
}
