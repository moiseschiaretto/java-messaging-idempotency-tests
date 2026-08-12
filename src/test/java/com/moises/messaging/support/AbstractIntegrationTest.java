package com.moises.messaging.support;

import com.moises.messaging.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected OrderRepository orderRepository;

    protected MessageTestHelper messageTestHelper;

    @BeforeEach
    void setUpMessageTestHelper() {
        messageTestHelper = new MessageTestHelper(restTemplate, orderRepository);
    }
}