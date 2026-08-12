package com.moises.messaging.entity;

/**
 * Estados possiveis de um pedido ao longo do fluxo de processamento
 * assincrono. Usado tanto pela aplicacao quanto pelos testes, para validar
 * em qual etapa do fluxo o pedido se encontra apos o consumo da mensagem.
 */
public enum OrderStatus {

    /** Mensagem consumida e persistida com sucesso. Estado final do happy path. */
    RECEIVED,

    /** Falhou no processamento e esta em uma das tentativas de retry. */
    RETRYING,

    /** Esgotou as tentativas de retry e foi roteado para a DLQ. */
    FAILED
}
