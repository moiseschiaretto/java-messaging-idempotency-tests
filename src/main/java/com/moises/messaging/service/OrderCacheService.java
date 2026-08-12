package com.moises.messaging.service;

import com.moises.messaging.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Camada de cache do pedido, usando Redis.
 *
 * Estrategia: cache-aside (le do cache; se nao encontrar, busca no banco e
 * popula o cache) com invalidacao explicita na escrita. Essa combinacao e
 * exatamente o cenario "consistencia com Hibernate" citado na vaga: o
 * teste precisa provar que, apos uma escrita no banco, o cache nunca fica
 * com um valor desatualizado (stale).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCacheService {

    private static final String CACHE_KEY_PREFIX = "order:idempotency:";

    private final RedisTemplate<String, Order> redisTemplate;

    @Value("${messaging.cache.order-ttl-seconds}")
    private long ttlSeconds;

    public void put(Order order) {
        String key = buildKey(order.getIdempotencyKey());
        redisTemplate.opsForValue().set(key, order, Duration.ofSeconds(ttlSeconds));
        log.debug("Cache atualizado para a chave [{}]", key);
    }

    public Order get(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        Order cached = redisTemplate.opsForValue().get(key);
        log.debug("Cache {} para a chave [{}]", cached != null ? "HIT" : "MISS", key);
        return cached;
    }

    /**
     * Remove a entrada do cache. Chamado sempre que o pedido correspondente
     * e alterado no banco, para evitar que uma leitura subsequente retorne
     * um valor desatualizado.
     */
    public void invalidate(String idempotencyKey) {
        String key = buildKey(idempotencyKey);
        redisTemplate.delete(key);
        log.debug("Cache invalidado para a chave [{}]", key);
    }

    private String buildKey(String idempotencyKey) {
        return CACHE_KEY_PREFIX + idempotencyKey;
    }
}
