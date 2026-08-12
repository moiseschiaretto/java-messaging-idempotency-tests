package com.moises.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moises.messaging.entity.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configura o RedisTemplate usado pelo OrderCacheService.
 *
 * Chaves como String (legivel no redis-cli, facilita debug manual durante o
 * desenvolvimento) e valores serializados em JSON, para armazenar o objeto
 * Order de forma legivel e sem depender de serializacao binaria Java.
 *
 * NOTA (ajuste feito apos falha detectada em teste): a versao original usava
 * GenericJackson2JsonRedisSerializer com um ObjectMapper customizado (para
 * suportar o campo Instant via JavaTimeModule). Isso causava um bug real:
 * o GenericJackson2JsonRedisSerializer so grava metadado de tipo (@class)
 * automaticamente quando usa seu ObjectMapper interno padrao — ao receber
 * um ObjectMapper customizado, esse metadado deixa de ser gravado, e a
 * leitura do cache passa a devolver um LinkedHashMap generico em vez de um
 * objeto Order (ClassCastException). A correcao usa um serializer TIPADO
 * (Jackson2JsonRedisSerializer<Order>), que ja sabe reconstruir o tipo
 * correto sem depender de metadado extra no JSON.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Order> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Order> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper objectMapper = new ObjectMapper();
        // Necessario para serializar/desserializar o campo Instant (createdAt)
        objectMapper.registerModule(new JavaTimeModule());

        Jackson2JsonRedisSerializer<Order> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Order.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }
}