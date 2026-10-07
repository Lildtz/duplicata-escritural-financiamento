---

name: architecture-review
description: Revise decisões arquiteturais relevantes antes de implementá-las. Use ao introduzir novos serviços, módulos, dependências, bancos, filas, Kafka, caches, integrações externas, padrões distribuídos, MCP, Agents, novas fronteiras ou mudanças significativas na arquitetura. Questione necessidade, alternativas, trade-offs, consistência com o domínio e risco de overengineering.
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

# Architecture Review

Use esta skill sempre que uma mudança ultrapassar implementação local e alterar a arquitetura, fronteiras ou dependências relevantes do sistema.

Leia o `AGENTS.md` antes da análise.

O objetivo não é tornar a arquitetura mais sofisticada.

O objetivo é garantir que cada decisão relevante tenha uma justificativa concreta, seja simples o suficiente e possa ser defendida tecnicamente.

## Princípio principal

Toda decisão arquitetural deve responder claramente:

> Qual problema real isso resolve?

Se não existir uma resposta concreta, provavelmente a mudança não deve ser feita ainda.

Não aceite como justificativa:

* "é uma best practice";
* "é mais escalável";
* "podemos precisar no futuro";
* "arquitetura moderna usa";
* "fica melhor no portfólio";
* "microsserviços são mais profissionais";
* "Kafka é mais robusto".

Essas afirmações podem fazer parte de uma decisão, mas não substituem um problema concreto.

---

# Processo de revisão

## 1. Defina o problema

Antes de discutir tecnologia, descreva o problema em termos de comportamento, domínio ou operação.

Exemplos válidos:

* duas operações podem tentar utilizar a mesma duplicata;
* precisamos publicar um evento depois de confirmar uma transação;
* a escrituradora pode ficar indisponível;
* múltiplos consumidores precisam reagir ao financiamento contratado;
* determinada capacidade precisa de deploy independente.

Evite começar com:

```text
Precisamos usar Kafka.
```

Prefira:

```text
Precisamos desacoplar o processamento posterior da contratação e permitir múltiplos consumidores independentes.
```

Tecnologia vem depois do problema.

---

# 2. Identifique as restrições

Considere apenas restrições reais.

Exemplos:

* consistência transacional;
* volumetria;
* latência;
* disponibilidade;
* concorrência;
* integração externa;
* isolamento;
* segurança;
* auditabilidade;
* deploy independente;
* evolução independente.

Não invente requisitos de escala ou disponibilidade.

Se volumetria ou SLA não forem conhecidos, declare isso explicitamente.

---

# 3. Avalie alternativas

Para decisões relevantes, considere pelo menos:

```text
Opção mais simples
vs.
Opção mais sofisticada
```

Quando aplicável, avalie também uma terceira alternativa.

Exemplo:

```text
Problema:
processar tarefas após contratação.

Alternativa A:
processamento síncrono.

Alternativa B:
processamento assíncrono interno.

Alternativa C:
evento Kafka.
```

Não escolha automaticamente a alternativa tecnicamente mais poderosa.

---

# 4. Avalie trade-offs

Para cada alternativa relevante, considere:

* complexidade;
* consistência;
* acoplamento;
* operação;
* observabilidade;
* custo de manutenção;
* testabilidade;
* escalabilidade;
* tolerância a falhas;
* conhecimento necessário;
* impacto no desenvolvimento local.

Não transforme trade-offs em listas genéricas.

Relacione-os ao problema atual.

---

# 5. Escolha a menor solução suficiente

Prefira a alternativa mais simples que:

* resolve o problema corretamente;
* preserva requisitos relevantes;
* não cria risco desnecessário;
* permite evolução futura sem grandes bloqueios.

Não implemente capacidade futura antecipadamente apenas porque existe possibilidade de precisar dela.

YAGNI deve ser aplicado por padrão.

---

# Revisões específicas

## Microsserviços

Antes de criar um novo serviço, pergunte:

* precisa de deploy independente?
* precisa de escala independente?
* possui responsabilidade claramente distinta?
* possui dados e invariantes próprios?
* possui ciclo de vida independente?
* representa sistema externo?
* existe necessidade operacional concreta de separação?

Se a resposta for predominantemente não, prefira módulo interno.

Exemplo:

`MotorElegibilidade` não deve virar microsserviço apenas porque possui responsabilidade própria.

`duplicata-escritural-escrituradora-api` é um serviço separado porque representa uma infraestrutura externa ao financiamento.

---

# Módulos

Criar um módulo é mais barato que criar um serviço, mas também deve possuir responsabilidade real.

Não crie antecipadamente:

```text
eligibility
pricing
portfolio
contract
risk
exposure
```

apenas porque eles aparecem no roadmap.

Crie módulos quando casos de uso reais surgirem.

---

# Interfaces

Questione interfaces com uma única implementação.

Uma interface pode ser válida mesmo com uma implementação quando representa uma fronteira real.

Exemplo válido:

```text
GatewayEscrituradora
```

porque desacopla domínio de financiamento de uma integração externa.

Exemplo potencialmente desnecessário:

```text
DuplicataService
DuplicataServiceImpl
```

quando não existe fronteira, variação ou necessidade concreta de abstração.

Não aplique regras mecânicas.

---

# Banco de dados

Antes de adicionar novo banco, schema ou datastore, pergunte:

* qual tipo de dado será armazenado?
* qual requisito não é atendido pelo banco existente?
* precisamos de consistência transacional?
* precisamos de busca especializada?
* precisamos de retenção ou isolamento diferente?

Não introduza:

* MongoDB;
* Redis;
* Elasticsearch;
* banco vetorial;

apenas para demonstrar tecnologia.

---

# Cache

Antes de introduzir cache, identifique:

* problema de latência;
* custo da consulta;
* frequência de leitura;
* tolerância a dados desatualizados;
* estratégia de invalidação.

Se não existe problema mensurável, não introduza cache.

---

# Kafka e mensageria

Antes de Kafka, determine se há necessidade real de:

* processamento assíncrono;
* múltiplos consumidores;
* desacoplamento temporal;
* eventos de domínio;
* integração entre sistemas;
* absorção de picos.

Se uma chamada síncrona simples resolve corretamente o problema atual, prefira síncrono.

Ao aprovar Kafka, revisar também:

* produtor;
* evento;
* consumidores;
* ordering;
* partition key;
* at-least-once;
* idempotência;
* retries;
* DLQ;
* observabilidade;
* schema evolution.

Kafka não elimina problemas de consistência.

---

# Transactional Outbox

Considere Outbox quando uma operação precisar garantir:

```text
mudança no banco
+
publicação de evento
```

sem risco de dual write inconsistente.

Não crie Outbox se ainda não existe publicação transacional de eventos.

---

# Retry

Antes de adicionar retry, responda:

* qual falha será repetida?
* ela é transitória?
* a operação é idempotente?
* existe backoff?
* qual limite de tentativas?
* o retry pode amplificar uma falha?

Retry não é solução padrão para toda integração.

---

# Circuit Breaker

Antes de Circuit Breaker, determine:

* existe dependência remota?
* falhas repetidas podem degradar o sistema?
* existe comportamento adequado quando o circuito abre?
* existe fallback real?

Não adicione Resilience4j apenas para mostrar circuit breaker no projeto.

---

# Consistência

Para mudanças distribuídas, declare explicitamente:

* onde existe consistência forte;
* onde existe consistência eventual;
* quais invariantes não podem ser violadas;
* como falhas parciais são tratadas.

Não use consistência eventual para simplificar artificialmente uma regra que exige atomicidade.

---

# Concorrência

Se houver disputa pelo mesmo recurso, revise:

* constraint no banco;
* optimistic locking;
* pessimistic locking;
* isolamento;
* operação atômica;
* idempotência.

Prefira mecanismos simples e confiáveis.

Não use locks distribuídos sem necessidade demonstrada.

---

# DDD

Antes de introduzir:

* Aggregate;
* Domain Event;
* Value Object;
* Domain Service;
* Repository;

verifique se o conceito expressa algo relevante no domínio.

Não transforme DDD em estrutura cerimonial.

Use linguagem do negócio e preserve invariantes reais.

---

# Spring / Frameworks

Antes de adicionar um starter ou framework:

1. identifique o problema;
2. verifique se a stack existente já resolve;
3. avalie impacto;
4. evite framework para uma necessidade mínima.

Não introduza dependências "caso sejam úteis futuramente".

---

# MCP

Antes de criar uma Tool MCP, pergunte:

> Isso representa uma capacidade útil para um Agent?

Evite expor diretamente cada endpoint REST como Tool.

Prefira capacidades como:

```text
consultarAgenda
verificarElegibilidade
simularFinanciamento
consultarExposicaoPorSacado
```

em vez de Tools que apenas espelham CRUD técnico.

O MCP Server é uma interface orientada a agentes, não simplesmente outro API Gateway.

---

# Agents e LLMs

Nunca aprove usar LLM para executar diretamente:

* cálculo financeiro;
* elegibilidade;
* limite;
* pricing;
* concentração;
* transição crítica de estado;
* integridade.

LLM pode:

* interpretar;
* planejar;
* selecionar Tools;
* explicar;
* recomendar.

Código determinístico deve executar regras críticas.

---

# Tecnologias de IA

Antes de introduzir:

* RAG;
* vector database;
* memória;
* múltiplos Agents;
* Agent-to-Agent;
* workflows autônomos;

identifique um caso de uso concreto.

Não transforme uma tarefa determinística em problema de IA.

---

# Segurança arquitetural

Para novas integrações, considere:

* autenticação;
* autorização;
* secrets;
* exposição de dados;
* logging;
* dados financeiros;
* dados pessoais.

Nunca comprometa segurança apenas para simplificar uma demonstração.

---

# Operação

Para novos componentes executáveis, avalie o custo operacional adicional:

* novo deploy;
* nova porta;
* configuração;
* health check;
* logs;
* métricas;
* tracing;
* pipeline;
* banco;
* secrets;
* monitoramento.

Um novo serviço possui custo mesmo em um projeto de portfólio.

---

# Formato da revisão

Para decisões relevantes, produza:

## Problema

Descreva o problema real.

## Alternativas

Liste as opções razoáveis.

## Decisão recomendada

Escolha uma opção.

## Justificativa

Explique por que ela atende melhor ao estado atual do projeto.

## Trade-offs

Declare os custos e limitações relevantes.

## Consequências

Explique o que muda agora e quais possibilidades permanecem abertas.

## Complexidade evitada

Quando relevante, registre explicitamente o que decidiu NÃO introduzir.

Exemplo:

```text
Problema
Precisamos consultar dados mantidos pela escrituradora.

Alternativas
1. acessar diretamente o banco da escrituradora;
2. criar API HTTP;
3. publicar todos os dados via Kafka.

Decisão
Integração HTTP síncrona.

Justificativa
A consulta precisa de resposta imediata e a escrituradora representa um sistema externo. Não existe necessidade atual de replicação assíncrona desses dados.

Trade-offs
A disponibilidade do fluxo passa a depender parcialmente da escrituradora.

Consequências
Precisaremos futuramente tratar timeout, observabilidade e falhas dessa integração.

Complexidade evitada
Kafka, replicação de dados e consistência eventual não são necessários neste momento.
```

---

# Relação com implementation-strategy

`implementation-strategy` analisa como implementar uma tarefa.

`architecture-review` analisa se uma decisão arquitetural relevante deve existir.

Fluxo:

```text
implementation-strategy
        ↓
detecta decisão arquitetural relevante
        ↓
architecture-review
        ↓
decisão
        ↓
implementação
```

---

# Relação com Ponytail

Ponytail e esta skill possuem objetivos complementares.

Ponytail pergunta:

> A implementação está mais complexa do que deveria?

Esta skill pergunta:

> A arquitetura escolhida faz sentido para o problema e para o domínio?

Ponytail pode sugerir simplificação.

Não aceite automaticamente uma simplificação que destrua uma fronteira arquitetural legítima.

Toda alteração estrutural relevante continua sujeita às regras deste arquivo e do `AGENTS.md`.

---

# Regra final

Não aprove arquitetura pela sofisticação da solução.

A melhor solução para este projeto é aquela que:

* resolve um problema real;
* possui justificativa clara;
* mantém o domínio compreensível;
* minimiza complexidade desnecessária;
* permite explicar os trade-offs;
* pode ser defendida tecnicamente em uma entrevista.

Se uma solução não conseguir responder claramente "por que isso existe?", não a aprove.
