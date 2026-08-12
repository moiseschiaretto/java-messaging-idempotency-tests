package com.moises.messaging.support;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.dto.OrderResponse;
import com.moises.messaging.entity.Order;
import com.moises.messaging.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageTestHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageTestHelper.class);
    private static final Logger evidenceLog = LoggerFactory.getLogger("com.moises.messaging.evidence");

    private final TestRestTemplate restTemplate;
    private final OrderRepository orderRepository;

    public MessageTestHelper(TestRestTemplate restTemplate, OrderRepository orderRepository) {
        this.restTemplate = restTemplate;
        this.orderRepository = orderRepository;
    }

    public static String newIdempotencyKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    public static OrderRequest validOrderRequest(String idempotencyKey) {
        return new OrderRequest(idempotencyKey, "cliente-teste", "SKU-TEST-001", 2, new BigDecimal("49.90"));
    }

    public void publish(OrderRequest orderRequest) {
        log.info("Publicando pedido de teste: idempotencyKey=[{}]", orderRequest.getIdempotencyKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> request = new HttpEntity<>(orderRequest, headers);

        ResponseEntity<OrderResponse> response =
                restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "Esperava 202 Accepted ao publicar idempotencyKey=" + orderRequest.getIdempotencyKey());

        evidenceLog.info("TEST_PUBLISHED idempotencyKey={} payload={}",
                orderRequest.getIdempotencyKey(), orderRequest);
    }

    /**
     * Reenvia o mesmo OrderRequest (mesma idempotencyKey) para simular uma
     * duplicata real de rede (ex: retry do cliente apos timeout). A API
     * sempre responde 202 Accepted, mesmo para duplicatas — a rejeicao
     * acontece de forma assincrona, no consumer, nao no controller.
     */
    public void publishDuplicate(OrderRequest orderRequest) {
        log.info("Reenviando pedido (duplicata proposital): idempotencyKey=[{}]", orderRequest.getIdempotencyKey());
        publish(orderRequest);
    }

    public void publishExpectingBadRequest(OrderRequest orderRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> request = new HttpEntity<>(orderRequest, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/orders", request, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Esperava 400 Bad Request para payload invalido");
    }

    public void publishBatch(List<OrderRequest> orderRequests) {
        log.info("Publicando lote de {} pedidos de teste", orderRequests.size());
        orderRequests.forEach(this::publish);
        evidenceLog.info("TEST_BATCH_PUBLISHED total={}", orderRequests.size());
    }

    public Order awaitPersisted(String idempotencyKey) {
        return await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null), o -> o != null);
    }

    public void awaitTotalCount(long expectedCount) {
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(300))
                .until(orderRepository::count, count -> count >= expectedCount);
    }

    public void assertNeverPersisted(String idempotencyKey, Duration waitDuration) {
        try {
            Thread.sleep(waitDuration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        boolean exists = orderRepository.existsByIdempotencyKey(idempotencyKey);
        if (exists) {
            throw new AssertionError(
                    "Esperava que idempotencyKey=" + idempotencyKey + " NUNCA fosse persistida, mas foi encontrada.");
        }
        evidenceLog.info("TEST_ASSERT_NEVER_PERSISTED idempotencyKey={} confirmed=true", idempotencyKey);
    }
}