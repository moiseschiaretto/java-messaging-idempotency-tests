package com.moises.messaging.service;

import com.moises.messaging.dto.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Responsavel por publicar a mensagem de pedido no Kafka.
 *
 * A chave da mensagem (segundo parametro do send) e a propria
 * idempotencyKey — isso garante que mensagens com a mesma chave sempre
 * vao para a mesma particao, preservando a ordem de chegada entre
 * tentativas duplicadas da mesma operacao.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducerService {

    private static final org.slf4j.Logger evidenceLog =
            org.slf4j.LoggerFactory.getLogger("com.moises.messaging.evidence");

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${messaging.kafka.topic-orders}")
    private String topic;

    public void publish(OrderRequest orderRequest) {
        log.info("Publicando pedido no topico [{}] com idempotencyKey=[{}]",
                topic, orderRequest.getIdempotencyKey());

        kafkaTemplate.send(topic, orderRequest.getIdempotencyKey(), orderRequest);

        // Log estruturado de evidencia: registra o payload publicado, para
        // servir de prova nos cenarios de idempotencia e DLQ durante os testes.
        evidenceLog.info("PUBLISHED topic={} idempotencyKey={} payload={}",
                topic, orderRequest.getIdempotencyKey(), orderRequest);
    }
}
