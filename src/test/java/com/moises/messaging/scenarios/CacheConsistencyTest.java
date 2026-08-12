package com.moises.messaging.scenarios;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.entity.Order;
import com.moises.messaging.support.AbstractIntegrationTest;
import com.moises.messaging.support.MessageTestHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Mensageria - Anymarket/DB1")
@Feature("Consistencia de Cache")
class CacheConsistencyTest extends AbstractIntegrationTest {

    @Autowired
    private RedisTemplate<String, Order> redisTemplate;

    @Test
    void deveManterCacheConsistenteComOBancoAposEscrita() {
        String idempotencyKey = MessageTestHelper.newIdempotencyKey("cache-consistency");
        OrderRequest orderRequest = MessageTestHelper.validOrderRequest(idempotencyKey);

        messageTestHelper.publish(orderRequest);
        Order persisted = messageTestHelper.awaitPersisted(idempotencyKey);

        // Confirma que o cache foi populado com o MESMO conteudo do banco
        // logo apos a escrita (nunca deve ficar stale).
        String cacheKey = "order:idempotency:" + idempotencyKey;
        Order cached = redisTemplate.opsForValue().get(cacheKey);

        assertThat(cached)
                .as("O cache deveria ter sido populado apos a persistencia no banco")
                .isNotNull();

        assertThat(cached.getId())
                .as("O ID no cache deve ser identico ao ID persistido no banco")
                .isEqualTo(persisted.getId());

        assertThat(cached.getStatus())
                .as("O status no cache deve ser identico ao status persistido no banco")
                .isEqualTo(persisted.getStatus());

        assertThat(cached.getIdempotencyKey())
                .isEqualTo(persisted.getIdempotencyKey());
    }
}