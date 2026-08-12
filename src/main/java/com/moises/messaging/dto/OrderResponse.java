package com.moises.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Resposta retornada pelo endpoint REST assim que a mensagem e publicada
 * no Kafka (HTTP 202 Accepted). Nao significa que o pedido ja foi
 * processado — apenas que a solicitacao foi aceita para processamento
 * assincrono. O status real do pedido so existe apos o consumer processar
 * a mensagem.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {

    private String idempotencyKey;
    private String message;
}
