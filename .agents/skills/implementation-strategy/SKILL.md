---

name: implementation-strategy
description: Analise e planeje mudanças de implementação antes de editar código. Use para novas features, persistência, integrações, regras de negócio, novas dependências, mudanças de schema, refatorações relevantes ou qualquer alteração não trivial. Não use para correções óbvias de typo, formatação ou documentação simples.
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

# Implementation Strategy

Use esta skill antes de implementar mudanças não triviais neste repositório.

O objetivo é entender o problema, localizar o menor conjunto de alterações necessárias e evitar implementação prematura ou overengineering.

Leia e respeite o `AGENTS.md` antes de tomar decisões.

## Processo

### 1. Entenda a solicitação

Determine:

* qual problema precisa ser resolvido;
* qual comportamento é esperado;
* quais restrições já existem;
* o que está explicitamente fora do escopo.

Não transforme requisitos futuros em requisitos atuais.

Se uma necessidade não existe agora, não projete antecipadamente para ela sem justificativa concreta.

### 2. Inspecione o código existente

Antes de propor alterações:

* localize os módulos envolvidos;
* leia os arquivos diretamente relacionados;
* procure implementações semelhantes existentes;
* identifique convenções já utilizadas;
* verifique testes existentes;
* verifique configurações e dependências relevantes.

Não proponha nova abstração antes de verificar se já existe uma solução adequada no projeto.

### 3. Identifique a fronteira da mudança

Determine quais aplicações, módulos ou capacidades realmente precisam ser alterados.

Prefira mudanças locais.

Evite alterar componentes não relacionados.

Pergunte:

> Qual é o menor conjunto coerente de arquivos que precisa mudar para resolver o problema corretamente?

### 4. Avalie alternativas

Para decisões que possuam mais de uma abordagem razoável, considere brevemente:

```text
Problema
→ alternativas
→ decisão
→ trade-offs
→ consequências
```

Não crie uma análise extensa quando a decisão for simples.

Considere especialmente:

* solução usando recursos já existentes;
* solução com nova abstração;
* solução com nova dependência;
* solução síncrona versus assíncrona;
* consistência transacional versus eventual;
* módulo interno versus novo serviço.

Prefira a alternativa mais simples que atenda corretamente aos requisitos atuais.

### 5. Questione complexidade adicional

Antes de adicionar qualquer um destes elementos:

* interface;
* camada;
* factory;
* strategy;
* mapper;
* wrapper;
* biblioteca;
* framework;
* cache;
* fila;
* evento;
* novo serviço;
* novo módulo;
* abstração genérica;

responda:

> Qual problema concreto isso resolve agora?

Se não houver uma resposta clara, não adicione.

Não crie extensibilidade especulativa.

### 6. Considere o domínio

Para mudanças relacionadas à Duplicata Escritural ou financiamento:

* preserve a linguagem do domínio;
* mantenha regras financeiras críticas determinísticas;
* não invente regras regulatórias;
* não invente contratos de B3, Núclea ou Banco Central;
* diferencie regra oficial de simplificação do projeto;
* mantenha fronteiras externas explícitas quando forem reais.

Não mova regra financeira para LLM, prompt ou Agent.

### 7. Considere persistência e concorrência quando relevante

Se houver alteração de estado, avalie apenas quando aplicável:

* transação;
* constraints;
* idempotência;
* chamadas repetidas;
* concorrência;
* optimistic/pessimistic locking;
* índices;
* atomicidade.

Não adicione esses mecanismos preventivamente quando o caso de uso não precisar deles.

### 8. Considere integração distribuída quando relevante

Se houver comunicação entre aplicações, avalie:

* contrato;
* timeout;
* comportamento de falha;
* idempotência;
* retry;
* observabilidade.

Não adicione retry ou circuit breaker automaticamente.

Primeiro determine qual falha está sendo tratada e se a operação pode ser repetida com segurança.

### 9. Defina estratégia de testes

Antes da implementação, determine quais verificações serão necessárias.

Escolha entre:

* teste unitário;
* teste de integração;
* Testcontainers;
* teste de contrato;
* execução manual;

de acordo com o comportamento que está sendo alterado.

Não crie teste apenas para aumentar cobertura.

Teste comportamento relevante.

## Saída esperada

Antes de começar uma alteração não trivial, produza uma estratégia curta contendo:

### Objetivo

O que será implementado.

### Estado atual

O que existe hoje e é relevante para a mudança.

### Alterações propostas

Arquivos, módulos ou componentes que provavelmente serão modificados.

### Decisões

Principais decisões técnicas e o motivo.

### Riscos ou trade-offs

Somente riscos concretos relevantes.

### Verificação

Como a implementação será validada.

Exemplo de formato:

```text
Objetivo
Adicionar persistência de duplicatas na escrituradora.

Estado atual
A escrituradora é uma aplicação Spring Boot sem persistência.

Alterações
- adicionar PostgreSQL/JPA/Flyway;
- configurar datasource;
- criar migration inicial;
- modelar persistência de Duplicata;
- adicionar teste de integração.

Decisões
Usar Flyway para evolução explícita do schema.
Usar Testcontainers para validar integração real com PostgreSQL.

Trade-offs
A infraestrutura local fica um pouco mais complexa, mas passamos a testar o comportamento contra o mesmo tipo de banco usado pela aplicação.

Verificação
- testes de integração;
- Gradle build;
- revisão do diff.
```

## Durante a implementação

Depois que a estratégia estiver definida:

* implemente somente o escopo planejado;
* ajuste o plano se descobrir informação nova relevante;
* não faça refatorações laterais;
* não introduza requisitos futuros;
* mantenha o diff pequeno e compreensível.

Se durante a implementação surgir uma decisão arquitetural significativa não prevista, interrompa essa decisão e utilize `architecture-review` antes de seguir.

## Relação com Ponytail

Esta skill decide como abordar a implementação.

Ponytail pode ser utilizado posteriormente para questionar se a implementação resultante ficou mais complexa do que o necessário.

Não utilize Ponytail como substituto desta análise inicial.

Fluxo preferencial:

```text
implementation-strategy
        ↓
implementação
        ↓
testes
        ↓
Ponytail review, quando apropriado
        ↓
code-change-verification
```
