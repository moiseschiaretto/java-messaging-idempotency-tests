package com.moises.messaging.repository;

import com.moises.messaging.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para a entidade Order.
 *
 * O metodo findByIdempotencyKey e o que sustenta o cenario de idempotencia:
 * antes de persistir uma nova mensagem, o consumer consulta se ja existe
 * um pedido com aquela chave. Se existir, a mensagem e descartada sem
 * gerar duplicidade.
 */
public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
