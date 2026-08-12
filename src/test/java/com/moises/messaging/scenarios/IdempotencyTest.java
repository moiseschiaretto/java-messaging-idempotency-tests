package com.moises.messaging.scenarios;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.entity.Order;
import com.moises.messaging.support.AbstractIntegrationTest;
import com.moises.messaging.support.MessageTestHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Epic("Mensageria - Anymarket/DB1")
@Feature("Idempotencia")
class IdempotencyTest extends AbstractIntegrationTest {

    @Test
    void devePersistirApenasUmaVezMesmoComMensagemDuplicada() {
        String idempotencyKey = MessageTestHelper.newIdempotencyKey("idempotency-duplicada");
        OrderRequest orderRequest = MessageTestHelper.validOrderRequest(idempotencyKey);

        messageTestHelper.publish(orderRequest);
        Order original = messageTestHelper.awaitPersisted(idempotencyKey);
        long totalAntes = orderRepository.count();

        messageTestHelper.publishDuplicate(orderRequest);

        await()
                .pollDelay(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    long totalDepois = orderRepository.count();
                    assertThat(totalDepois)
                            .as("O total de pedidos nao deveria aumentar ao reenviar a mesma idempotencyKey")
                            .isEqualTo(totalAntes);
                });

        Order aposDuplicata = orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertThat(aposDuplicata.getId())
                .as("O ID do registro original nao deveria mudar")
                .isEqualTo(original.getId());
    }
}