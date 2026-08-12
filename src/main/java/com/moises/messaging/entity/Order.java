package com.moises.messaging.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representa um pedido (Order) processado pelo fluxo de mensageria.
 *
 * O campo {@code idempotencyKey} e o elemento central do cenario de
 * idempotencia exigido pela vaga: cada mensagem publicada carrega essa
 * chave, e a constraint UNIQUE no banco garante — no nivel de persistencia,
 * nao apenas na logica da aplicacao — que a mesma chave nunca gera dois
 * registros. Isso e proposital: idempotencia garantida so pela logica do
 * codigo e fragil (uma corrida de concorrencia pode furar); a constraint no
 * banco e a garantia definitiva.
 */
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_idempotency_key",
                columnNames = "idempotency_key"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * Chave de idempotencia enviada pelo cliente/produtor da mensagem.
     * Mensagens duplicadas (reenviadas por retry de rede, por exemplo)
     * chegam com a MESMA chave — e devem ser identificadas e ignoradas
     * sem duplicar o efeito no banco.
     */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "product_sku", nullable = false, length = 50)
    private String productSku;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Status do pedido no fluxo de processamento. RECEIVED e o estado
     * inicial, assim que a mensagem e consumida e persistida.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = OrderStatus.RECEIVED;
        }
    }
}
