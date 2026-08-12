package com.moises.messaging.service;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.entity.Order;
import com.moises.messaging.exception.InvalidOrderException;
import com.moises.messaging.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer do topico de pedidos. Concentra a logica de negocio que os
 * testes da Fase 3 exercitam:
 *
 *   - Validacao do payload (dispara InvalidOrderException -> DLQ imediata)
 *   - Checagem de idempotencia (findByIdempotencyKey antes de persistir)
 *   - Persistencia via Hibernate/JPA
 *   - Atualizacao do cache Redis apos a escrita (evita cache desatualizado)
 *   - Metricas de observabilidade (mensagens processadas/ignoradas/falhas)
 *
 * O ack-mode manual (configurado em application.yml) exige a chamada
 * explicita de acknowledgment.acknowledge() — so confirmamos o consumo ao
 * Kafka DEPOIS que a transacao de persistencia foi concluida com sucesso.
 * Isso evita perder mensagens caso a aplicacao caia entre o consumo e a
 * gravacao no banco.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderConsumerService {

    private static final org.slf4j.Logger evidenceLog =
            org.slf4j.LoggerFactory.getLogger("com.moises.messaging.evidence");

    private final OrderRepository orderRepository;
    private final OrderCacheService orderCacheService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "${messaging.kafka.topic-orders}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(OrderRequest orderRequest, Acknowledgment acknowledgment) {

        log.info("Mensagem recebida: idempotencyKey=[{}]", orderRequest.getIdempotencyKey());

        validate(orderRequest);

        // ---- Checagem de idempotencia ----
        // Se ja existe um pedido com essa chave, a mensagem e uma duplicata
        // (reenvio por retry de rede, por exemplo) e deve ser IGNORADA sem
        // gerar um novo registro nem alterar o existente.
        if (orderRepository.existsByIdempotencyKey(orderRequest.getIdempotencyKey())) {
            log.warn("Mensagem duplicada detectada e ignorada: idempotencyKey=[{}]",
                    orderRequest.getIdempotencyKey());
            evidenceLog.info("DUPLICATE_IGNORED idempotencyKey={}", orderRequest.getIdempotencyKey());
            incrementCounter("messaging.orders.duplicates.ignored");
            acknowledgment.acknowledge();
            return;
        }

        Order order = Order.builder()
                .idempotencyKey(orderRequest.getIdempotencyKey())
                .customerId(orderRequest.getCustomerId())
                .productSku(orderRequest.getProductSku())
                .quantity(orderRequest.getQuantity())
                .unitPrice(orderRequest.getUnitPrice())
                .build();

        Order saved = orderRepository.save(order);

        // Atualiza o cache imediatamente apos a escrita no banco, para que
        // uma leitura subsequente nunca retorne um valor desatualizado.
        orderCacheService.put(saved);

        evidenceLog.info("PERSISTED idempotencyKey={} orderId={} status={}",
                saved.getIdempotencyKey(), saved.getId(), saved.getStatus());

        incrementCounter("messaging.orders.processed");

        acknowledgment.acknowledge();

        log.info("Pedido processado com sucesso: id=[{}], idempotencyKey=[{}]",
                saved.getId(), saved.getIdempotencyKey());
    }

    /**
     * Validacao de negocio que nao pode ser corrigida por retry. Se
     * falhar aqui, a excecao e classificada como NAO recuperavel (ver
     * KafkaConfig) e a mensagem vai direto para a DLQ.
     */
    private void validate(OrderRequest orderRequest) {
        if (orderRequest.getQuantity() == null || orderRequest.getQuantity() <= 0) {
            throw new InvalidOrderException(
                    "Quantidade invalida para o pedido com idempotencyKey=" + orderRequest.getIdempotencyKey());
        }
        if (orderRequest.getProductSku() == null || orderRequest.getProductSku().isBlank()) {
            throw new InvalidOrderException(
                    "productSku ausente para o pedido com idempotencyKey=" + orderRequest.getIdempotencyKey());
        }
    }

    private void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .description("Contador de eventos do fluxo de processamento de pedidos")
                .register(meterRegistry)
                .increment();
    }
}
