package com.sentio.user_service.organization;

import tools.jackson.databind.ObjectMapper;
import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.auth.dto.RegistrationRequest;
import com.sentio.user_service.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.organization.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteRequest;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEN-16's central isolation requirement: a caller from organization A must get
 * 404 - not 403 - on any org-scoped endpoint for organization B. 403 would
 * confirm the resource exists, which leaks that another org's data is there;
 * 404 makes it indistinguishable from a nonexistent org. Every org-mutating
 * endpoint routes through OrganizationSecurity.requireMembership/requireOwnership
 * (see those classes) instead of a boolean @PreAuthorize expression specifically
 * so this is achievable - a boolean check can only ever deny with 403.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrganizationIsolationIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    // Same reasoning as AuthControllerIT: this class isn't testing rate limits,
    // so it shouldn't be subject to the shared IP limiter across IT classes.
    @MockitoBean
    private RateLimitingService rateLimitingService;

    // CookieCsrfTokenRepository (double-submit pattern): every response carries a
    // fresh XSRF-TOKEN cookie, and state-changing requests must echo its value back
    // as the X-XSRF-TOKEN header, or CsrfFilter rejects them before anything else runs.
    private record Session(String accessToken, String xsrfToken) {}

    private Session orgASession;
    private long orgAId;
    private long orgBId;
    private long orgBOwnerId;

    private RegistrationRequest ownerRequest(String email, String orgName) {
        return new RegistrationRequest(
                email, "Password123!", "Password123!", null,
                "Doe", "Owner", null,
                orgName, null, null
        );
    }

    private Session register(String email, String orgName) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerRequest(email, orgName))))
                .andReturn().getResponse();
        return new Session(
                response.getCookie("access_token").getValue(),
                response.getCookie("XSRF-TOKEN").getValue()
        );
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, Session session) {
        return builder
                .cookie(new Cookie("access_token", session.accessToken()), new Cookie("XSRF-TOKEN", session.xsrfToken()))
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    @BeforeEach
    void setUp() throws Exception {
        orgASession = register("owner-a@sentio.dev", "Org A");
        var memberA = organizationMemberRepository.findAll().stream()
                .filter(m -> m.getUser().getEmail().equals("owner-a@sentio.dev"))
                .findFirst().orElseThrow();
        orgAId = memberA.getOrganization().getId();

        register("owner-b@sentio.dev", "Org B");
        var memberB = organizationMemberRepository.findAll().stream()
                .filter(m -> m.getUser().getEmail().equals("owner-b@sentio.dev"))
                .findFirst().orElseThrow();
        orgBId = memberB.getOrganization().getId();
        orgBOwnerId = memberB.getUser().getId();
    }

    @Test
    void ownerOfOrgA_canListTheirOwnOrgsMembers() throws Exception {
        mockMvc.perform(authenticated(get("/organizations/" + orgAId + "/members"), orgASession))
                .andExpect(status().isOk());
    }

    @Test
    void ownerOfOrgA_getMembersOfOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(get("/organizations/" + orgBId + "/members"), orgASession))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerOfOrgA_updateOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(put("/organizations/" + orgBId), orgASession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrganizationRequest("Hijacked Name"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerOfOrgA_patchMemberRoleInOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(patch("/organizations/" + orgBId + "/members/" + orgBOwnerId + "/role"), orgASession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(OrgRole.ASSISTANT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerOfOrgA_deleteMemberInOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(delete("/organizations/" + orgBId + "/members/" + orgBOwnerId), orgASession))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerOfOrgA_createInviteInOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(post("/organizations/" + orgBId + "/invites"), orgASession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrganizationInviteRequest("intruder@sentio.dev", OrgRole.LAWYER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerOfOrgA_listInvitesOfOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(get("/organizations/" + orgBId + "/invites"), orgASession))
                .andExpect(status().isNotFound());
    }
}
