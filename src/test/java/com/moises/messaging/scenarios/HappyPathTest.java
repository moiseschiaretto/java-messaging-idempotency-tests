package com.moises.messaging.scenarios;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.entity.Order;
import com.moises.messaging.entity.OrderStatus;
import com.moises.messaging.support.AbstractIntegrationTest;
import com.moises.messaging.support.MessageTestHelper;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Categoria 1 de 6 — Happy Path.
 *
 * Cobre o fluxo mais simples possivel: publicar um pedido valido e
 * confirmar que ele foi consumido do Kafka e persistido corretamente no
 * banco, com todos os campos batendo com o que foi enviado.
 *
 * Esta e a categoria mais simples e serve como fundacao — se ela nao
 * passar, nenhuma das outras cinco categorias (idempotencia,
 * reprocessamento, DLQ, cache, volume) vai funcionar, ja que todas
 * dependem do mesmo fluxo basico estar correto.
 */
@Epic("Mensageria - Anymarket/DB1")
@Feature("Happy Path")
class HappyPathTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Deve publicar, consumir e persistir um pedido novo com sucesso")
    @Story("Pedido novo")
    @Description("Publica um pedido valido via API e confirma que foi persistido no banco com status RECEIVED")
    void devePersistirPedidoNovoComSucesso() {
        // Arrange
        String idempotencyKey = MessageTestHelper.newIdempotencyKey("happy-path-pedido");
        OrderRequest request = MessageTestHelper.validOrderRequest(idempotencyKey);

        // Act
        messageTestHelper.publish(request);

        // Assert
        Order persisted = messageTestHelper.awaitPersisted(idempotencyKey);

        assertThat(persisted).isNotNull();
        assertThat(persisted.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(persisted.getCustomerId()).isEqualTo(request.getCustomerId());
        assertThat(persisted.getProductSku()).isEqualTo(request.getProductSku());
        assertThat(persisted.getQuantity()).isEqualTo(request.getQuantity());
        assertThat(persisted.getUnitPrice()).isEqualByComparingTo(request.getUnitPrice());
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(persisted.getId()).isNotBlank();
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve publicar, consumir e persistir uma atualizacao de estoque com sucesso")
    @Story("Atualizacao de estoque")
    @Description("Publica um pedido representando atualizacao de estoque (quantidade maior) e confirma persistencia correta")
    void devePersistirAtualizacaoDeEstoqueComSucesso() {
        // Arrange — simula um cenario de reposicao de estoque, com
        // quantidade maior e SKU diferente do teste anterior, alinhado
        // ao dominio de e-commerce da Anymarket (catalogo/estoque)
        String idempotencyKey = MessageTestHelper.newIdempotencyKey("happy-path-estoque");
        OrderRequest request = new OrderRequest(
                idempotencyKey, "cliente-teste", "SKU-ESTOQUE-042", 150, new BigDecimal("12.50"));

        // Act
        messageTestHelper.publish(request);

        // Assert
        Order persisted = messageTestHelper.awaitPersisted(idempotencyKey);

        assertThat(persisted).isNotNull();
        assertThat(persisted.getProductSku()).isEqualTo("SKU-ESTOQUE-042");
        assertThat(persisted.getQuantity()).isEqualTo(150);
        assertThat(persisted.getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }
}
