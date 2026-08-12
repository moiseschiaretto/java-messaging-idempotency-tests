package com.moises.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe de entrada da aplicacao.
 *
 * Este projeto simula um servico de processamento de pedidos e estoque
 * (contexto de e-commerce, inspirado no dominio da vaga DB1/Anymarket),
 * expondo um endpoint REST que publica mensagens no Kafka para
 * processamento assincrono, com persistencia via Hibernate e cache via
 * Redis.
 *
 * O foco deste projeto e a AUTOMACAO DE TESTES sobre os cenarios dificeis
 * dessa stack (idempotencia, reprocessamento, DLQ, consistencia de cache e
 * consistencia sob volume) — a aplicacao em si e propositalmente simples,
 * apenas o suficiente para sustentar esses cenarios de forma realista.
 */
@SpringBootApplication
public class MessagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingApplication.class, args);
    }
}
