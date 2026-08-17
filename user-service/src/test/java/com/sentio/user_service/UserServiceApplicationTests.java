package com.sentio.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
/** UserServiceApplicationTests class. */
class UserServiceApplicationTests {

    @Test
    void contextLoads() {}
}
