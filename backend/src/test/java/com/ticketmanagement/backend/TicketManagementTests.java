package com.ticketmanagement.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.seed.enabled=false")
class TicketManagementTests {

    @Test
    void contextLoads() {
    }

}
