package com.sentio.user_service.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.repository.OrganizationRepository;
import com.sentio.user_service.organization.service.OrganizationService;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.repository.UserRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserServiceConcurrencyIT {

    @Autowired
    private UserService userService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private User owner1;
    private User owner2;
    private Organization org;
    private OrganizationMember member1;
    private OrganizationMember member2;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        owner1 = User.builder()
                .email("owner1@sentio.dev")
                .password("hash")
                .build();
        owner2 = User.builder()
                .email("owner2@sentio.dev")
                .password("hash")
                .build();
        userRepository.saveAllAndFlush(List.of(owner1, owner2));

        org = new Organization();
        org.setName("Concurrency Test Org");
        org.setSlug("concurrency-test-org");
        organizationRepository.saveAndFlush(org);

        member1 = OrganizationMember.builder()
                .user(owner1)
                .organization(org)
                .role(OrgRole.OWNER)
                .isDefault(true)
                .build();
        member2 = OrganizationMember.builder()
                .user(owner2)
                .organization(org)
                .role(OrgRole.OWNER)
                .isDefault(true)
                .build();
        organizationMemberRepository.saveAllAndFlush(List.of(member1, member2));

        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        organizationMemberRepository.deleteAll(List.of(member1, member2));
        organizationRepository.delete(org);
        userRepository.deleteAll(List.of(owner1, owner2));
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void concurrentDeletionAndRoleChange_onlyOneSucceedsAndTheOtherFails() throws InterruptedException {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "pass", 
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        context.setAuthentication(auth);

        Callable<Void> demoteOwner2 = () -> {
            SecurityContextHolder.setContext(context);
            bothReady.countDown();
            go.await();
            organizationService.patchRoleForMember(org.getId(), owner2.getId(), OrgRole.LAWYER);
            return null;
        };

        Callable<Void> deleteOwner1 = () -> {
            SecurityContextHolder.setContext(context);
            bothReady.countDown();
            go.await();
            userService.deleteUser(owner1.getId(), null);
            return null;
        };

        Future<Void> future1 = executorService.submit(demoteOwner2);
        Future<Void> future2 = executorService.submit(deleteOwner1);

        bothReady.await();
        go.countDown();

        int succeeded = 0;
        int failed = 0;

        try {
            future1.get(10, TimeUnit.SECONDS);
            succeeded++;
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
            failed++;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            future2.get(10, TimeUnit.SECONDS);
            succeeded++;
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
            failed++;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(failed).isEqualTo(1);

        long ownerCount = organizationMemberRepository.countByOrganizationIdAndRole(org.getId(), OrgRole.OWNER);
        assertThat(ownerCount).isEqualTo(1);
    }
}
