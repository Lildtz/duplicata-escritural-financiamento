# Roadmap — Plataforma de Duplicata Escritural

Este documento acompanha as entregas incrementais da plataforma experimental de Duplicata Escritural, sob a perspectiva do Banco DUP, um Agente Financiador simulado.

O progresso é registrado com base nas implementações conhecidas. Uma funcionalidade implementada ainda pode depender de Code Review, execução dos testes e homologação técnica para ser considerada concluída.

**Última atualização de referência:** 13/11/2026.

## Estado atual

**Última entrega: 3.3 — aprovação técnica final registrada em 13/11/2026; commit de encerramento autorizado.**

| Entrega | Descrição                                 | Situação                          |
| ------- | ----------------------------------------- | --------------------------------- |
| 0       | Fundação técnica e decisões arquiteturais | Concluída                         |
| 1       | Recebimento de Escrituração Manual        | Implementada                      |
| 2       | Processamento assíncrono de Escrituração  | Implementada                      |
| 3.1     | Identidades e autorização de consulta     | Implementada                      |
| 3.2     | Consulta local protegida de recebíveis    | Aprovação técnica retrospectiva em 13/11/2026 |
| 3.3     | Integração HTTP do Banco DUP              | Aprovação técnica final em 13/11/2026 |
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

**Situação: implementada; aprovação técnica retrospectiva registrada em 13/11/2026, sem defeitos funcionais bloqueantes.**

- [x] Disponibilizar consulta paginada de Duplicatas locais.
- [x] Restringir a consulta ao Financiador autorizado.
- [x] Validar estado e vigência da autorização.
- [x] Preservar isolamento por Sacador e CNPJ completo.
- [x] Retornar projeção mínima dos títulos.
- [x] Manter a propriedade da entidade Duplicata na Escrituração.
- [x] Revalidar a autorização em cada consulta.
- [x] Confirmar build e testes relevantes.
- [x] Realizar revisão retrospectiva da implementação associada a `eba5d38`.
- [x] Registrar aceite técnico retrospectivo da 3.2 em 13/11/2026.

A consulta não representa uma Agenda regulatória completa nem comprova disponibilidade para financiamento.

Na preparação da 3.3, o build completo confirmou novamente os 163 testes anteriores,
incluindo PostgreSQL/Testcontainers. A revisão corretiva verificou o histórico Git
disponível: `eba5d38` registra a implementação da 3.2; `556528a` introduziu o roadmap
com aprovação marcada e, simultaneamente, revisão a confirmar. Não foi encontrado
registro inequívoco de aprovação em commits, tags, notas Git ou documentos locais.
O checklist foi corrigido para separar testes aprovados de aprovação formal pendente.
A revisão retrospectiva foi realizada nesta sessão em 13/11/2026 e examinou segurança,
autorização por Financiador, paginação, isolamento, falhas, transações, contratos,
testes e ADRs. Não encontrou defeitos funcionais bloqueantes na 3.2 e emitiu parecer
favorável à aprovação atual. Isso não comprova aprovação histórica anterior.
O aceite técnico retrospectivo foi autorizado e registrado em 13/11/2026,
sem retroagir sua data. Nenhuma aprovação histórica foi presumida, o commit
`eba5d38` foi preservado e nenhum comportamento funcional da 3.2 foi alterado.

### 3.3 — Integração HTTP Banco DUP–Signatária

**Situação: implementada e validada; aprovação técnica final registrada em 13/11/2026, após Code Review e revisão corretiva favoráveis.**

**Aplicações envolvidas:**

- `duplicata-escritural-financiamento-api`
- `duplicata-escritural-signataria-api`

Escopo:

- [x] Implementar a integração HTTP síncrona.
- [x] Introduzir o `GatewayRegistradora` como porta de integração.
- [x] Implementar o adapter HTTP usando Spring `RestClient`.
- [x] Configurar autenticação técnica entre as aplicações.
- [x] Permitir solicitação de autorização a partir do Banco DUP.
- [x] Consultar o estado da autorização.
- [x] Consultar os recebíveis locais autorizados.
- [x] Configurar timeouts.
- [x] Tratar falhas de integração e traduzir erros.
- [x] Preservar a separação entre os modelos das aplicações.
- [x] Implementar testes automatizados da integração.
- [x] Atualizar README e guia técnico com contratos, configuração e limitações.
- [x] Avaliar favoravelmente a implementação e a revisão corretiva da 3.3.
- [x] Registrar aprovação técnica final em 13/11/2026.
- [x] Autorizar o commit único de encerramento da 3.3, sem push.

**Evidências técnicas:** casos de uso em `financiamento.consultarecebiveis.aplicacao`,
porta e modelos próprios em `dominio`, adapter/configuração em
`infraestrutura.integracao.signataria`. São consumidos os três contratos existentes
(solicitação de autorização, consulta de autorização e página de Duplicatas).
A credencial Bearer identifica exclusivamente o Financiador provisionado na Signatária;
não é repassada identidade de participante por parâmetro ou header arbitrário.

Testes do Financiamento usam Mockito, MockRestServiceServer e HTTP descartável do JDK,
sem novas dependências. Cobrem sucesso, paginação, vazio, negação após revogação/expiração,
404 genérico, token rejeitado, falhas HTTP/transporte, resposta incompatível, bootstrap,
configuração, timeout real e recusa de redirecionamento. As regressões da Signatária
permanecem em PostgreSQL/Testcontainers. Comando de validação: `.\gradlew.bat clean build`.
Resultados consolidados e procedimentos estão no [guia técnico](autorizacao-consulta.md#entrega-33--integração-http-do-banco-dup).

A revisão corretiva preservou o código funcional: o POST remoto retorna 201 tanto
na criação quanto na repetição idempotente válida; conflito mantém 409. Testes do
adapter e da Signatária reforçam esses casos e a ausência de retry da aplicação.
HTTP 429 não é previsto pelo contrato atual e permanece `RESPOSTA_INCOMPATIVEL`.
A identidade continua definida pelo Bearer provisionado, com a conferência operacional
do vínculo token/Financiador explicitada no guia. A avaliação favorável da implementação
e o aceite técnico da revisão corretiva da 3.3 foram informados nesta sessão.
A aprovação técnica final da 3.3 foi autorizada e registrada em 13/11/2026.
O commit de encerramento foi autorizado separadamente; seu registro e identificador
são verificáveis no histórico Git. A aprovação técnica não substitui esse registro.
A aprovação retrospectiva da 3.2 está registrada separadamente, sem inventar aceite anterior.

**Restrições:**

O Banco DUP não acessa diretamente o banco de dados da Signatária e não assume a propriedade dos títulos.

Esta entrega não contempla concessão de crédito, Kafka, MCP, Spring AI, retry automático ou circuit breaker.
Não foram criados endpoints de entrada no Banco DUP: os casos de uso são beans Spring
disponíveis no perfil `local`. A referência `consultadaEm` é local, pois o contrato
da 3.2 não publica instante de consulta. Não há snapshot entre páginas.
O fluxo real entre as duas aplicações não foi executado nesta entrega; permanece na 3.4.

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
