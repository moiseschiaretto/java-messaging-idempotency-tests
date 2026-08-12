package com.moises.messaging.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Payload recebido pelo endpoint REST de entrada (POST /orders).
 *
 * A validacao aqui (@NotBlank, @Min, etc.) e o que permite o cenario de
 * teste "DLQ": um payload que fere essas regras e propositalmente enviado
 * para forcar o roteamento para a dead-letter queue durante o
 * processamento assincrono.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    /**
     * Chave de idempotencia. Quem publica a mensagem e responsavel por
     * gerar essa chave de forma unica por operacao de negocio (nao por
     * tentativa de envio) — e assim que retries de rede nao geram pedidos
     * duplicados.
     */
    @NotBlank(message = "idempotencyKey e obrigatorio")
    private String idempotencyKey;

    @NotBlank(message = "customerId e obrigatorio")
    private String customerId;

    @NotBlank(message = "productSku e obrigatorio")
    private String productSku;

    @NotNull(message = "quantity e obrigatorio")
    @Min(value = 1, message = "quantity deve ser maior que zero")
    private Integer quantity;

    @NotNull(message = "unitPrice e obrigatorio")
    @DecimalMin(value = "0.01", message = "unitPrice deve ser maior que zero")
    private BigDecimal unitPrice;
}
