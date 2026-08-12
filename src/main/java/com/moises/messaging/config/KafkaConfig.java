package com.moises.messaging.config;

import com.moises.messaging.exception.InvalidOrderException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuracao central do tratamento de erros do Kafka.
 *
 * Aqui esta a decisao tecnica registrada no plano: usar o
 * {@link DefaultErrorHandler} do Spring Kafka (abordagem moderna) em vez de
 * capturar excecoes manualmente dentro do @KafkaListener. As vantagens:
 *
 *   1) Politica de retry declarativa (FixedBackOff), sem escrever loop
 *      de tentativas na mao;
 *   2) Roteamento automatico para a DLQ via DeadLetterPublishingRecoverer,
 *      apos esgotar as tentativas;
 *   3) Classificacao de excecoes: InvalidOrderException e tratada como
 *      NAO recuperavel — vai direto para a DLQ, sem gastar retries, porque
 *      um payload invalido nunca vai se corrigir sozinho tentando de novo.
 */
@Configuration
public class KafkaConfig {

    @Value("${messaging.kafka.max-retry-attempts}")
    private int maxRetryAttempts;

    @Value("${messaging.kafka.retry-backoff-ms}")
    private long retryBackoffMs;

    /**
     * Define o handler de erro usado por todos os @KafkaListener da
     * aplicacao. O KafkaOperations injetado aqui e o mesmo usado pelo
     * DeadLetterPublishingRecoverer para publicar a mensagem original
     * (mais o cabecalho com o motivo da falha) no topico de DLQ.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {

        // Publica a mensagem que falhou definitivamente no topico
        // "{topico-original}.DLQ" — convencao padrao do Spring Kafka.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + ".DLQ",
                        record.partition()
                )
        );

        // maxRetryAttempts tentativas adicionais, espacadas por
        // retryBackoffMs milissegundos, antes de acionar o recoverer.
        FixedBackOff backOff = new FixedBackOff(retryBackoffMs, maxRetryAttempts);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // InvalidOrderException nunca deve ser tentada de novo — payload
        // invalido nao se corrige sozinho. Vai direto para a DLQ.
        errorHandler.addNotRetryableExceptions(InvalidOrderException.class);

        return errorHandler;
    }
}
