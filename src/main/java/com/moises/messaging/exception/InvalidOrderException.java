package com.moises.messaging.exception;

/**
 * Excecao lancada pelo consumer quando o payload da mensagem viola uma
 * regra de negocio que nao pode ser corrigida por retry (ex.: quantidade
 * invalida, SKU vazio).
 *
 * Diferente de uma falha transitoria (que dispara o RETRY configurado no
 * DefaultErrorHandler), esta excecao e classificada como NAO recuperavel
 * na configuracao do Kafka (ver KafkaConfig), fazendo a mensagem ser
 * roteada imediatamente para a DLQ, sem gastar tentativas de retry.
 */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
