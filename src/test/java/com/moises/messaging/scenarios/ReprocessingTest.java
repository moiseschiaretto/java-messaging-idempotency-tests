package com.moises.messaging.scenarios;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.support.AbstractIntegrationTest;
import com.moises.messaging.support.MessageTestHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Mensageria - Anymarket/DB1")
@Feature("Reprocessamento")
class ReprocessingTest extends AbstractIntegrationTest {

    @Test
    void deveReprocessarComSucessoAposFalhaDeConcorrenciaNoConstraintUnico() throws InterruptedException {
        String idempotencyKey = MessageTestHelper.newIdempotencyKey("reprocessing-concorrencia");
        OrderRequest orderRequest = MessageTestHelper.validOrderRequest(idempotencyKey);

        // Publica a MESMA chave duas vezes o mais proximo possivel no tempo,
        // forcando uma corrida: os dois consumers fazem a checagem de
        // idempotencia antes que qualquer um tenha persistido, entao os
        // dois tentam inserir — o segundo bate no constraint unico do banco,
        // recebe uma excecao retryable (nao e InvalidOrderException) e o
        // DefaultErrorHandler aciona o retry automatico (3 tentativas,
        // 1s de intervalo, conforme application.yml).
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Runnable publishTask = () -> {
            try {
                startLatch.await();
                messageTestHelper.publish(orderRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        executor.submit(publishTask);
        executor.submit(publishTask);
        startLatch.countDown();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Aguarda o suficiente para as 3 tentativas de retry (1s cada)
        // esgotarem, caso o reprocessamento demore para se resolver.
        messageTestHelper.awaitPersisted(idempotencyKey);

        long total = orderRepository.count();
        assertThat(orderRepository.existsByIdempotencyKey(idempotencyKey)).isTrue();

        // Mesmo com a corrida e o retry, o resultado final e UM UNICO
        // registro — prova de que o reprocessamento se autocorrigiu sem
        // gerar duplicata.
        long countComEssaChave = orderRepository.findByIdempotencyKey(idempotencyKey).stream().count();
        assertThat(countComEssaChave)
                .as("Apos o reprocessamento, deve existir exatamente 1 registro para essa idempotencyKey")
                .isEqualTo(1);
    }
}