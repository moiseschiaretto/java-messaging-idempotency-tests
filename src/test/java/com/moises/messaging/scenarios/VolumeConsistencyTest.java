package com.moises.messaging.scenarios;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.support.AbstractIntegrationTest;
import com.moises.messaging.support.MessageTestHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Mensageria - Anymarket/DB1")
@Feature("Consistencia sob Volume")
class VolumeConsistencyTest extends AbstractIntegrationTest {

    private static final int TOTAL_MENSAGENS = 200;

    @Test
    void devePersistirTodasAsMensagensDeUmLoteSemPerda() {
        long totalAntes = orderRepository.count();

        List<OrderRequest> lote = new ArrayList<>();
        for (int i = 0; i < TOTAL_MENSAGENS; i++) {
            String idempotencyKey = MessageTestHelper.newIdempotencyKey("volume-" + i);
            lote.add(MessageTestHelper.validOrderRequest(idempotencyKey));
        }

        messageTestHelper.publishBatch(lote);

        // Aguarda ate que o total de registros no banco reflita as
        // TOTAL_MENSAGENS novas mensagens, dando tempo do consumer
        // processar o lote inteiro de forma assincrona.
        messageTestHelper.awaitTotalCount(totalAntes + TOTAL_MENSAGENS);

        long totalDepois = orderRepository.count();
        assertThat(totalDepois)
                .as("Todas as %d mensagens do lote deveriam ter sido persistidas, sem perda", TOTAL_MENSAGENS)
                .isEqualTo(totalAntes + TOTAL_MENSAGENS);

        // Confirma tambem que nenhuma das chaves do lote ficou faltando
        // individualmente (nao so a contagem bate, mas cada uma existe).
        for (OrderRequest orderRequest : lote) {
            assertThat(orderRepository.existsByIdempotencyKey(orderRequest.getIdempotencyKey()))
                    .as("A mensagem com idempotencyKey=%s deveria ter sido persistida", orderRequest.getIdempotencyKey())
                    .isTrue();
        }
    }
}