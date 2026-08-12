package com.moises.messaging.controller;

import com.moises.messaging.dto.OrderRequest;
import com.moises.messaging.dto.OrderResponse;
import com.moises.messaging.service.OrderProducerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de entrada do fluxo assincrono.
 *
 * Importante (esclarecido no plano do projeto): este NAO e uma integracao
 * com uma API publica externa — e a propria aplicacao construida aqui.
 * O endpoint apenas aceita a requisicao e publica a mensagem no Kafka;
 * ele nao processa o pedido de forma sincrona. Por isso retorna 202
 * Accepted, nao 200 ou 201 — o processamento real acontece de forma
 * assincrona no OrderConsumerService.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProducerService orderProducerService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest orderRequest) {
        orderProducerService.publish(orderRequest);

        OrderResponse response = new OrderResponse(
                orderRequest.getIdempotencyKey(),
                "Pedido aceito para processamento assincrono"
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
