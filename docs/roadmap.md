# Roadmap — Plataforma de Duplicata Escritural

Este documento acompanha as entregas incrementais da plataforma experimental de Duplicata Escritural, sob a perspectiva do Banco DUP, um Agente Financiador simulado.

O progresso é registrado com base nas implementações conhecidas. Uma funcionalidade implementada ainda pode depender de Code Review, execução dos testes e homologação técnica para ser considerada concluída.

**Última atualização de referência:** 13/11/2026.

## Estado atual

**Entrega em andamento: 3.3 — Integração HTTP entre o Banco DUP e a Signatária.**

| Entrega | Descrição                                 | Situação                          |
| ------- | ----------------------------------------- | --------------------------------- |
| 0       | Fundação técnica e decisões arquiteturais | Concluída                         |
| 1       | Recebimento de Escrituração Manual        | Implementada                      |
| 2       | Processamento assíncrono de Escrituração  | Implementada                      |
| 3.1     | Identidades e autorização de consulta     | Implementada                      |
| 3.2     | Consulta local protegida de recebíveis    | Implementada; revisão a confirmar |
| 3.3     | Integração HTTP do Banco DUP              | Em andamento                      |
| 3.4     | Validação vertical do fluxo               | Planejada                         |

---

## Entrega 0 — Fundação técnica

Estruturação inicial do projeto e definição dos princípios arquiteturais.

- [x] Definir os objetivos e os limites da plataforma.
- [x] Organizar o monorepo.
- [x] Definir a nomenclatura dos componentes.
- [x] Estabelecer as fronteiras arquiteturais.
- [x] Registrar decisões arquiteturais em ADRs.
- [x] Definir a estratégia inicial de testes.
- [x] Criar a documentação de entrada do projeto.

**Decisão relevante:** conforme o ADR 0003, a aplicação `duplicata-escritural-signataria-api` reúne as capacidades de Escrituração e Registro, preservando a separação lógica entre suas responsabilidades.

## Entrega 1 — Recebimento de Escrituração Manual

**Aplicação:** `duplicata-escritural-signataria-api`

Implementação do recebimento durável de solicitações de Escrituração Manual.

Capacidades implementadas:

- Recebimento de solicitações por API REST.
- Consulta do processamento por identificador.
- Persistência em PostgreSQL.
- Controle transacional de idempotência.
- Autenticação local do Sacador.
- Migrations com Flyway.
- Validações e tratamento de erros.

**Limitação:** nesta entrega, receber uma solicitação não representa a criação efetiva de uma Duplicata.

## Entrega 2 — Processamento assíncrono de Escrituração

**Aplicação:** `duplicata-escritural-signataria-api`

Evolução do recebimento durável para processamento assíncrono interno.

Capacidades implementadas:

- Processamento assíncrono com estado persistido.
- Controle de tentativas e reprocessamento.
- Recuperação de processamentos pendentes.
- Criação de Duplicatas locais simuladas.
- Controle de concorrência e integridade transacional.
- Persistência dos resultados.
- Habilitação explícita do agendador.

**Limitação:** a criação local não equivale a Registro regulatório, geração de IUD ou confirmação de disponibilidade financeira.

## Entrega 3 — Consulta autorizada de recebíveis

**Objetivo:** permitir que o Banco DUP solicite autorização ao Sacador e consulte recebíveis disponíveis no simulador da Signatária para análise preliminar.

Esta entrega estabelece a primeira integração funcional entre duas aplicações independentes.

### 3.1 — Identidades e autorização

**Situação: implementada.**

- [x] Diferenciar as identidades do Sacador e do Financiador.
- [x] Implementar solicitação de autorização.
- [x] Implementar decisão do Sacador.
- [x] Implementar consulta e revogação.
- [x] Persistir o estado da autorização.
- [x] Restringir operações conforme o participante autenticado.

### 3.2 — Consulta local protegida

**Situação: implementada; encerramento da revisão técnica a confirmar.**

- [x] Disponibilizar consulta paginada de Duplicatas locais.
- [x] Restringir a consulta ao Financiador autorizado.
- [x] Validar estado e vigência da autorização.
- [x] Preservar isolamento por Sacador e CNPJ completo.
- [x] Retornar projeção mínima dos títulos.
- [x] Manter a propriedade da entidade Duplicata na Escrituração.
- [x] Revalidar a autorização em cada consulta.
- [x] Confirmar aprovação final do Code Review e dos testes.

A consulta não representa uma Agenda regulatória completa nem comprova disponibilidade para financiamento.

### 3.3 — Integração HTTP Banco DUP–Signatária

**Situação: em implementação pelo Codex.**

**Aplicações envolvidas:**

- `duplicata-escritural-financiamento-api`
- `duplicata-escritural-signataria-api`

Escopo:

- [ ] Implementar a integração HTTP síncrona.
- [ ] Introduzir o `GatewayRegistradora` como porta de integração.
- [ ] Implementar o adapter HTTP usando Spring `RestClient`.
- [ ] Configurar autenticação técnica entre as aplicações.
- [ ] Permitir solicitação de autorização a partir do Banco DUP.
- [ ] Consultar o estado da autorização.
- [ ] Consultar os recebíveis locais autorizados.
- [ ] Configurar timeouts.
- [ ] Tratar falhas de integração e traduzir erros.
- [ ] Preservar a separação entre os modelos das aplicações.
- [ ] Implementar testes automatizados da integração.

**Restrições:**

O Banco DUP não acessa diretamente o banco de dados da Signatária e não assume a propriedade dos títulos.

Esta entrega não contempla concessão de crédito, Kafka, MCP, Spring AI, retry automático ou circuit breaker.

**Critério de conclusão:** o Banco DUP utiliza as capacidades autorizadas da Signatária por HTTP, com autenticação, contratos e tratamento de falhas verificados.

### 3.4 — Validação vertical

**Situação: planejada.**

- [ ] Executar ambas as aplicações integradas.
- [ ] Validar solicitação de autorização pelo Banco DUP.
- [ ] Validar aprovação pelo Sacador.
- [ ] Consultar recebíveis autorizados pelo Banco DUP.
- [ ] Validar recusa, revogação e expiração.
- [ ] Testar isolamento entre participantes.
- [ ] Executar regressões das entregas anteriores.
- [ ] Documentar a execução local completa.

**Critério de conclusão da Entrega 3:** fluxo demonstrável de ponta a ponta entre Banco DUP e Signatária, com controles de acesso verificados e documentação reproduzível.

---

## Evoluções previstas após a Entrega 3

As próximas capacidades ainda não possuem numeração definitiva de entrega. Sua prioridade será revisada conforme o estado da plataforma.

### Domínio de financiamento

- Elegibilidade determinística.
- Análise de exposição por sacado.
- Seleção de carteira.
- Simulação financeira.
- Precificação e limites.
- Ciclo de vida das operações.
- Tratamento de concorrência e idempotência.

### Resiliência e observabilidade

- Métricas e rastreamento distribuído.
- Políticas de retry quando justificadas.
- Circuit breaker quando necessário.
- Testes de degradação e indisponibilidade.

### Eventos e consistência distribuída

- Eventos relevantes de domínio.
- Kafka e consumidores.
- Transactional Outbox.
- Tratamento de duplicidade.
- Reprocessamento e consistência eventual.

### MCP Server

**Componente previsto:** `duplicata-escritural-financiamento-mcp`

- Exposição de capacidades de negócio como ferramentas MCP.
- Consultas de informações e simulações.
- Validação de entradas e resultados.

### Agente de financiamento

**Componente previsto:** `duplicata-escritural-agente-financiamento`

- Interpretação de solicitações em linguagem natural.
- Integração com MCP usando Spring AI.
- Orquestração de ferramentas.
- Geração de recomendações fundamentadas.

O agente não será responsável pelos cálculos financeiros críticos.

### Consulta regulatória com RAG

**Componente futuro:** `duplicata-escritural-regulacao-mcp`

- Recuperação de documentação regulatória pública.
- Consultas contextualizadas.
- Referências às fontes utilizadas nas respostas.

---

## Critérios de conclusão

Uma entrega somente deverá ser marcada como concluída após:

1. Implementação do escopo aprovado.
2. Code Review concluído.
3. Build e testes relevantes aprovados.
4. Validação dos critérios de aceite.
5. Documentação atualizada.
6. Commit correspondente versionado.

Os estados deste roadmap devem acompanhar o código e as validações, e não apenas o planejamento.

## Princípios de engenharia

- Preservar fronteiras de domínio claras.
- Introduzir tecnologias somente quando justificadas.
- Evitar overengineering.
- Manter lógica financeira crítica determinística.
- Registrar decisões arquiteturais e trade-offs.
- Distinguir implementação local simulada de capacidades regulatórias reais.
- Evoluir o projeto por entregas funcionais verificáveis.
