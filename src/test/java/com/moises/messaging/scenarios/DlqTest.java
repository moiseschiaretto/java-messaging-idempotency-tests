package com.moises.messaging.scenarios;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.support.AbstractIntegrationTest;
import com.moises.messaging.support.MessageTestHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Epic("Mensageria - Anymarket/DB1")
@Feature("Dead Letter Queue")
class DlqTest extends AbstractIntegrationTest {

    @Test
    void devePublicarNaDlqQuandoMensagemForInvalida() {
        String idempotencyKey = MessageTestHelper.newIdempotencyKey("dlq-invalido");

        // Payload propositalmente invalido: quantity zero, o que dispara
        // InvalidOrderException dentro do consumer (a validacao do
        // Bean Validation so protege o endpoint REST, entao publicamos
        // direto no Kafka para simular uma mensagem malformada vinda de
        // outro sistema).
        OrderRequest invalidOrder = new OrderRequest(
                idempotencyKey, "cliente-teste", "SKU-TEST-001", 0, new java.math.BigDecimal("49.90"));

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class);
        producerProps.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        KafkaTemplate<String, Object> rawKafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
        rawKafkaTemplate.send("orders-topic", idempotencyKey, invalidOrder);

        // Confirma que o registro NUNCA chega a ser persistido no banco —
        // InvalidOrderException e nao-retryable, entao vai direto para a
        // DLQ sem tentar novamente.
        messageTestHelper.assertNeverPersisted(idempotencyKey, Duration.ofSeconds(5));

        // Confirma que a mensagem realmente chegou no topico de DLQ,
        // lendo diretamente do topico "orders-topic.DLQ".
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-reader-" + idempotencyKey);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> dlqConsumer =
                     new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {

            dlqConsumer.subscribe(Collections.singletonList("orders-topic.DLQ"));

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofMillis(500));
                boolean encontrouNaDlq = false;
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key() != null && record.key().equals(idempotencyKey)) {
                        encontrouNaDlq = true;
                    }
                }
                assertThat(encontrouNaDlq)
                        .as("A mensagem invalida deveria ter chegado ao topico orders-topic.DLQ")
                        .isTrue();
            });
        }
    }
}