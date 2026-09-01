package com.sentio.user_service.user.service.finder;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.shared.entity.finder.EntityFinder;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** UserFinder interface. */
public interface UserFinder extends EntityFinder<User, Long> {

    Page<User> search(String email, Boolean includeDeleted, Pageable pageable);

    User findByIdIncludingDeleted(long userId) throws ResourceNotFoundException;

    User findByEmail(String email) throws ResourceNotFoundException;

    User findByEmail(String email, String errorMessage) throws ResourceNotFoundException;

    // Optional-варіант поряд із findByEmail (throwing): для сценаріїв, де 404 не мапиться
    // напряму на кінцевий виняток (напр. AuthService.login перетворює "нема такого юзера" на
    // UnauthorizedException, а не ResourceNotFoundException) - Optional.orElseThrow(...) чистіший
    // за try/catch навколо кидаючого варіанту. Той самий підхід, що й
    // RefreshTokenFinder.findByTokenHashOptional.
    Optional<User> findByEmailOptional(String email);

    boolean existsByEmail(String email);

    long countByPlatformRole(PlatformRole platformRole);
}
