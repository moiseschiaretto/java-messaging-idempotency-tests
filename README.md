# java-messaging-idempotency-tests

Testes de integração para cenários de **mensageria (Kafka)**, **cache (Redis)** e **persistência (PostgreSQL/Hibernate)** — cobrindo idempotência, reprocessamento e Dead Letter Queue (DLQ), com Spring Boot.

![Testes](https://img.shields.io/badge/testes-7%20passando-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.8.0-231F20)

## 🚀 Como Começar

```bash
# 1. Clone o repositório
git clone https://github.com/moiseschiaretto/java-messaging-idempotency-tests.git
cd java-messaging-idempotency-tests

# 2. Suba a infraestrutura (Kafka, PostgreSQL, Redis)
docker compose up -d

# 3. Confirme que os 3 containers estão saudáveis
docker ps
# Esperado: messaging-kafka, messaging-postgres e messaging-redis, todos "healthy"

# 4. Rode os testes (recomendado)
mvn test allure:serve   # roda os testes e já abre o relatório visual no navegador

# Ou execute separadamente:
mvn test                 # só executa os testes
mvn allure:serve         # só gera/abre o relatório (precisa ter rodado os testes antes)
```

> Pré-requisitos: JDK 17, Maven e Docker Desktop instalados.

## 🔌 Testando a conexão com cada serviço (opcional)

Antes de rodar os testes, é possível confirmar manualmente que cada serviço da infraestrutura está respondendo:

```bash
# PostgreSQL
docker exec messaging-postgres pg_isready -U messaging_user -d messaging_db
# Esperado: "accepting connections"

# Redis
docker exec messaging-redis redis-cli ping
# Esperado: "PONG"

# Kafka
docker exec messaging-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
# Esperado: lista das APIs suportadas pelo broker
```

## Relatório Allure

```bash
mvn test allure:serve   # roda os testes + gera + abre o relatório no navegador
```

> ⚠️ **Não abra o relatório do Allure direto pelo navegador via `file://`.** Assim como outras ferramentas de relatório baseadas em JS, ele carrega os dados via requisições que o navegador bloqueia por segurança quando o HTML é aberto localmente sem servidor. Use sempre `mvn allure:serve` (ou `mvn test allure:serve`), que já sobe o servidor local e abre o relatório completo.

Comandos individuais, se preferir rodar por etapas:

```bash
mvn test                          # roda os testes e gera os dados brutos em target/allure-results/
mvn allure:report                 # gera o HTML estático em target/site/allure-maven-plugin/
mvn allure:serve                  # sobe um servidor local e abre o relatório já pronto
```

## Verificando o cache Redis após os testes (opcional)

```bash
docker exec messaging-redis redis-cli KEYS "*"
```

## Encerrando o ambiente

```bash
docker compose down
```

---

## 📸 Evidências de execução

> Cada imagem abaixo vem acompanhada do comando (ou ação) exato que a gerou, para permitir reprodução independente.

**Estrutura do projeto no IntelliJ**

```
Project View do IntelliJ IDEA (painel lateral esquerdo)
```

![Estrutura do projeto](docs/evidence/00-estrutura-projeto.png)

**Infraestrutura Docker de pé (Kafka, PostgreSQL, Redis)**

```bash
docker compose up -d
```

![Docker Compose up](docs/evidence/01-docker-compose-up.png)

```bash
docker ps
```

![Docker ps — 3 containers healthy](docs/evidence/02-docker-ps-healthy.png)

**Teste individual de conexão com cada serviço**

```bash
docker exec messaging-postgres pg_isready -U messaging_user -d messaging_db
docker exec messaging-redis redis-cli ping
docker exec messaging-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

![Testes individuais de conexão](docs/evidence/03-docker-testes-individuais.png)

**Persistência confirmada no PostgreSQL (tabela `orders`)**

```sql
SELECT * FROM orders ORDER BY created_at DESC;
```

![Tabela orders no PostgreSQL](docs/evidence/04-postgresql-orders.png)

**Cache populado no Redis após a execução dos testes**

```bash
docker exec messaging-redis redis-cli KEYS "*"
```

![Chaves no Redis](docs/evidence/05-redis-keys.png)

**Execução de cada categoria de teste**

*Happy Path*
```bash
mvn test -Dtest=HappyPathTest
```
![Happy Path](docs/evidence/06-happypathtest.png)

*Idempotência*
```bash
mvn test -Dtest=IdempotencyTest
```
![Idempotência](docs/evidence/07-idempotencytest.png)

*Reprocessamento*
```bash
mvn test -Dtest=ReprocessingTest
```
![Reprocessamento](docs/evidence/08-reprocessingtest.png)

*Dead Letter Queue*
```bash
mvn test -Dtest=DlqTest
```
![Dead Letter Queue](docs/evidence/09-dlqtest.png)

*Consistência de Cache*
```bash
mvn test -Dtest=CacheConsistencyTest
```
![Consistência de Cache](docs/evidence/10-cacheconsistencytest.png)

*Consistência sob Volume*
```bash
mvn test -Dtest=VolumeConsistencyTest
```
![Consistência sob Volume](docs/evidence/11-volumeconsistencytest.png)

**Relatório Allure consolidado — todas as 6 suítes juntas (7 casos de teste, 100% de sucesso)**

> `HappyPathTest` contém 2 métodos de teste; as outras 5 suítes têm 1 cada — por isso o total de **casos de teste** (7) é maior que o total de **suítes/categorias** (6).

```bash
mvn test allure:serve
```

![Allure — Visão geral](docs/evidence/12-allure-overview.png)
![Allure — Suítes detalhadas](docs/evidence/13-allure-suites.png)
![Allure — Gráficos](docs/evidence/14-allure-graphs.png)

---

## Por que estes cenários específicos?

Cada teste deste projeto foi desenhado para provar, na prática, um comportamento que é comum — e crítico — em sistemas baseados em mensageria assíncrona:

**Idempotência.** Em qualquer sistema distribuído, uma mensagem pode ser entregue mais de uma vez (retry de rede, reprocessamento manual, falha momentânea do consumer). Sem controle de idempotência, isso gera pedidos duplicados, cobranças duplicadas, estoque incorreto. O teste `IdempotencyTest` publica a mesma mensagem duas vezes e confirma que o sistema processa apenas a primeira, ignorando a segunda sem erro.

**Reprocessamento.** Falhas transitórias acontecem (concorrência no banco, timeout momentâneo). Um sistema robusto tenta de novo automaticamente, sem intervenção manual, e sem duplicar o resultado quando a nova tentativa é bem-sucedida. O teste `ReprocessingTest` simula uma corrida de concorrência real e confirma que o mecanismo de retry do Kafka resolve o conflito sozinho.

**Dead Letter Queue (DLQ).** Nem toda mensagem inválida se corrige tentando de novo — um payload malformado continuará malformado. Em vez de travar a fila inteira ou entrar em loop de tentativas inúteis, mensagens assim são desviadas para um tópico separado (a DLQ), para investigação posterior, sem bloquear o processamento das mensagens válidas. O teste `DlqTest` confirma esse desvio.

**Consistência de cache.** Um cache desatualizado (stale) é pior do que não ter cache — o sistema passa a responder com dado errado. O teste `CacheConsistencyTest` confirma que, a cada escrita no banco, o cache Redis é atualizado de forma consistente com o dado real.

**Consistência sob volume.** Um sistema pode funcionar bem com 1 mensagem e falhar silenciosamente com 200 (perda de mensagens, deadlocks, timeouts). O teste `VolumeConsistencyTest` publica um lote de 200 mensagens simultâneas e confirma que todas — sem exceção — são processadas e persistidas corretamente.

## Stack

- **Java 17** + **Spring Boot 3.3.4**
- **Apache Kafka** (modo KRaft, sem Zookeeper) — mensageria
- **PostgreSQL** + **Hibernate/JPA** — persistência
- **Redis** — cache (estratégia cache-aside com invalidação)
- **Spring Actuator + Micrometer** — observabilidade (métricas de mensagens processadas/ignoradas)
- **JUnit 5**, **AssertJ**, **Awaitility** — testes de integração
- **Allure Framework** — relatórios de teste
- **Docker Compose** — orquestração da infraestrutura local

## Estrutura

```
src/
  main/java/com/moises/messaging/
    config/       # configuração de Kafka (retry/DLQ) e Redis (serialização)
    controller/   # endpoint REST de entrada (POST /orders)
    dto/          # objetos de requisição/resposta
    entity/       # entidade Order (JPA)
    exception/    # exceções de domínio
    repository/   # acesso a dados (Spring Data JPA)
    service/      # regras de negócio: publicação, consumo, cache
  test/java/com/moises/messaging/
    scenarios/    # as 6 classes de teste, uma por cenário
    support/      # infraestrutura de apoio compartilhada entre os testes
docker-compose.yml
pom.xml
docs/evidence/    # capturas de tela da execução e dos relatórios
```

## Mapa mental do projeto

Visão consolidada do fluxo assíncrono completo: a requisição HTTP entra pelo `OrderController`, é publicada no Kafka pelo `OrderProducerService`, e processada de forma assíncrona pelo `OrderConsumerService` — que decide entre três caminhos (payload inválido → DLQ sem retry; duplicata → ignorada; pedido novo → persistido no PostgreSQL e cacheado no Redis). Em paralelo, os testes automatizados usam Testcontainers para subir instâncias isoladas e efêmeras de Kafka e PostgreSQL a cada execução — nunca a infraestrutura do `docker-compose.yml`, que serve apenas para rodar a aplicação manualmente.

<img src="docs/evidence/15-mapa-mental-projeto.png" alt="Mapa mental do projeto java-messaging-idempotency-tests" width="900">

## Cobertura de cenários

**6 categorias de teste**, cobrindo o ciclo completo de uma mensagem: publicação, consumo, validação, idempotência, retry, DLQ, persistência e cache — o conjunto de competências técnicas mais exigido em vagas de plataforma/enabling para times de e-commerce e integração de sistemas.
