package com.sentio.user_service.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * requireMembership/requireOwnership throw ResourceNotFoundException instead of returning a boolean
 * - deliberately, so a caller from another organization gets 404 (via GlobalExceptionHandler)
 * rather than 403. A boolean @PreAuthorize check can only ever produce 403 on denial, which is
 * exactly the "resource exists but you're not allowed" leak this class exists to avoid (see
 * SEN-16).
 */
@ExtendWith(MockitoExtension.class)
class OrganizationSecurityTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationSecurity organizationSecurity;

    private User user(long id) {
        User u = User.builder().email("user@sentio.dev").build();
        u.setId(id);
        return u;
    }

    private OrganizationMember membership(User user, OrgRole role) {
        Organization org =
                Organization.builder().name("Acme Legal").slug("acme").build();
        org.setId(10L);
        return OrganizationMember.builder()
                .user(user)
                .organization(org)
                .role(role)
                .isDefault(true)
                .build();
    }

    @Nested
    /** RequireMembership class. */
    class RequireMembership {

        @Test
        void memberOfTheOrg_returnsTheMembership() {
            User user = user(1L);
            Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
            OrganizationMember membership = membership(user, OrgRole.LAWYER);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            assertThat(organizationSecurity.requireMembership(10L, auth)).isEqualTo(membership);
        }

        @Test
        void notAMemberOfTheOrgAtAll_throwsResourceNotFoundException() {
            User user = user(1L);
            Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationSecurity.requireMembership(10L, auth))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void nonSecurityUserPrincipal_throwsResourceNotFoundException() {
            Authentication auth = new TestingAuthenticationToken("not-a-security-user", null);

            assertThatThrownBy(() -> organizationSecurity.requireMembership(10L, auth))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    /** RequireOwnership class. */
    class RequireOwnership {

        @Test
        void ownerOfTheOrg_returnsTheMembership() {
            User user = user(1L);
            Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
            OrganizationMember membership = membership(user, OrgRole.OWNER);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                    .thenReturn(Optional.of(membership));

            assertThatCode(() -> organizationSecurity.requireOwnership(10L, auth))
                    .doesNotThrowAnyException();
        }

        // 404, not 403: a LAWYER hitting an OWNER-only endpoint must not learn that the
        // resource exists behind a permission wall - it should look identical to the
        // resource not existing at all.
        @Test
        void nonOwnerMemberOfTheOrg_throwsResourceNotFoundException() {
            User user = user(1L);
            Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                    .thenReturn(Optional.of(membership(user, OrgRole.LAWYER)));

            assertThatThrownBy(() -> organizationSecurity.requireOwnership(10L, auth))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void notAMemberOfTheOrgAtAll_throwsResourceNotFoundException() {
            User user = user(1L);
            Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
            when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationSecurity.requireOwnership(10L, auth))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
