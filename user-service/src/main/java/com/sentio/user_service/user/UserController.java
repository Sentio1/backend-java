package com.sentio.user_service.user;

import com.lisovskyi.security.autoconfigure.security.annotation.CurrentUser;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserContextResponse> me(
            @CurrentUser SecurityUser user
    ) {
        return ResponseEntity.ok(userService.findUserById(user.getId()));
    }
}
