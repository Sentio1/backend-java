package com.sentio.user_service.identity.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.identity.auth.dto.request.RegistrationRequest;
import com.sentio.user_service.identity.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.identity.organization.internal.controller.dto.CreateOrganizationRequest;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization.UpdateOrganizationRequest;
import com.sentio.user_service.identity.organization.internal.controller.dto.organization_invite.OrganizationInviteRequest;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.internal.repository.OrganizationMemberRepository;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
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
import tools.jackson.databind.ObjectMapper;

/**
 * SEN-16's central isolation requirement: a caller from organization A must get 404 - not 403 - on
 * any org-scoped endpoint for organization B. 403 would confirm the resource exists, which leaks
 * that another org's data is there; 404 makes it indistinguishable from a nonexistent org. Every
 * org-mutating endpoint routes through OrganizationSecurity.requireMembership/requireOwnership (see
 * those classes) instead of a boolean @PreAuthorize expression specifically so this is achievable -
 * a boolean check can only ever deny with 403.
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

    @Autowired
    private UserRepository userRepository;

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

    private RegistrationRequest registrationRequest(String email) {
        return new RegistrationRequest(email, "Password123!", "Password123!", null, "Doe", "Owner", null);
    }

    private Session register(String email) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationRequest(email))))
                .andReturn()
                .getResponse();
        for (String h : response.getHeaders("Set-Cookie")) {
            System.out.println("DEBUG Set-Cookie: " + h);
        }
        return new Session(
                response.getCookie("access_token").getValue(),
                response.getCookie("XSRF-TOKEN").getValue());
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, Session session) {
        return builder.cookie(
                        new Cookie("access_token", session.accessToken()),
                        new Cookie("XSRF-TOKEN", session.xsrfToken()))
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    // Registration is always org-less now - reaching an "owner with an org"
    // starting state needs the separate onboarding step.
    private Session registerOwner(String email, String orgName) throws Exception {
        Session session = register(email);
        MockHttpServletResponse response = mockMvc.perform(authenticated(post("/organizations"), session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(orgName, null, null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        // The XSRF-TOKEN cookie is only reissued when it actually changes - same as a
        // real browser, which just keeps sending its existing cookie when the server
        // doesn't tell it otherwise. No new Set-Cookie here means the original is still
        // valid, so fall back to it instead of assuming one is always present.
        Cookie freshXsrf = response.getCookie("XSRF-TOKEN");
        return new Session(
                response.getCookie("access_token").getValue(),
                freshXsrf != null ? freshXsrf.getValue() : session.xsrfToken());
    }

    @BeforeEach
    void setUp() throws Exception {
        orgASession = registerOwner("owner-a@sentio.dev", "Org A");
        long ownerAId = userRepository.findByEmail("owner-a@sentio.dev").orElseThrow().getId();
        var memberA = organizationMemberRepository.findAll().stream()
                .filter(m -> m.getUserId() == ownerAId)
                .findFirst()
                .orElseThrow();
        orgAId = memberA.getOrganization().getId();

        registerOwner("owner-b@sentio.dev", "Org B");
        long ownerBId = userRepository.findByEmail("owner-b@sentio.dev").orElseThrow().getId();
        var memberB = organizationMemberRepository.findAll().stream()
                .filter(m -> m.getUserId() == ownerBId)
                .findFirst()
                .orElseThrow();
        orgBId = memberB.getOrganization().getId();
        orgBOwnerId = ownerBId;
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
        mockMvc.perform(authenticated(
                                patch("/organizations/" + orgBId + "/members/" + orgBOwnerId + "/role"), orgASession)
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
                        .content(objectMapper.writeValueAsString(
                                new OrganizationInviteRequest("intruder@sentio.dev", OrgRole.LAWYER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerOfOrgA_listInvitesOfOrgB_returns404NotForbidden() throws Exception {
        mockMvc.perform(authenticated(get("/organizations/" + orgBId + "/invites"), orgASession))
                .andExpect(status().isNotFound());
    }
}
