package com.sentio.user_service.user;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.OrganizationMemberRepository;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserContextResponse findUserById(long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        OrganizationMember membership = organizationMemberRepository.findByUserIdAndIsDefaultTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationMember", "userId", id));

        return userMapper.toResponse(user, membership);
    }
}
