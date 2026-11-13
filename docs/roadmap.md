# Roadmap — Plataforma de Duplicata Escritural

Este documento acompanha as entregas incrementais da plataforma experimental de Duplicata Escritural, sob a perspectiva do Banco DUP, um Agente Financiador simulado.

O progresso é registrado com base nas implementações conhecidas. Uma funcionalidade implementada ainda pode depender de Code Review, execução dos testes e homologação técnica para ser considerada concluída.

**Última atualização de referência:** 13/11/2026.

## Estado atual

**Entrega 4.2 — Observabilidade fundamental aprovada tecnicamente em 13/11/2026, com Code Review concluído favoravelmente; versionamento pendente de autorização explícita.** A 4.1 foi aprovada tecnicamente em 13/11/2026 e versionada isoladamente em `03290c8`. A 3.4 foi encerrada e versionada em `27e950e`, após aprovação técnica em 13/11/2026.

| Entrega | Descrição                                 | Situação                          |
| ------- | ----------------------------------------- | --------------------------------- |
| 0       | Fundação técnica e decisões arquiteturais | Concluída                         |
| 1       | Recebimento de Escrituração Manual        | Implementada                      |
| 2       | Processamento assíncrono de Escrituração  | Implementada                      |
| 3.1     | Identidades e autorização de consulta     | Implementada                      |
| 3.2     | Consulta local protegida de recebíveis    | Aprovação técnica retrospectiva em 13/11/2026 |
| 3.3     | Integração HTTP do Banco DUP              | Aprovação técnica final em 13/11/2026; versionada em `04081ca` |
| 3.4     | Validação vertical do fluxo               | Aprovação técnica final em 13/11/2026; versionada em `27e950e` |
| 4.1     | OpenAPI e Swagger UI                      | Aprovada tecnicamente em 13/11/2026; versionada em `03290c8` |
| 4.2     | Observabilidade                          | Aprovação técnica final em 13/11/2026; versionamento pendente de autorização explícita |
| 4.3     | CI com GitHub Actions                     | Planejada |
| 4.4     | Monitoramento                            | Planejada |
| 4.5     | CD                                       | Planejada; estratégia de deploy a definir |

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

**Situação: validação vertical aprovada tecnicamente em 13/11/2026 e versionada em `27e950e`, após autorização explícita.**

- [x] Executar ambas as aplicações integradas.
- [x] Validar solicitação de autorização pelo Banco DUP.
- [x] Validar aprovação pelo Sacador.
- [x] Consultar recebíveis autorizados pelo Banco DUP.
- [x] Validar recusa, revogação e expiração.
- [x] Testar isolamento entre participantes.
- [x] Executar regressões das entregas anteriores.
- [x] Documentar a execução automatizada completa em ambiente descartável.
- [x] Registrar aprovação técnica final do Code Review da 3.4 em 13/11/2026.

**Evidências:** `FluxoConsultaAutorizadaVerticalTests` executa nove casos com
PostgreSQL `postgres:18-alpine`/Testcontainers e V1–V3 aplicadas pela Signatária real.
O teste do Banco DUP depende apenas do JAR executável da Signatária, iniciado em
processo separado, com HTTP em porta dinâmica, perfil `local`, Bearers provisionados
e agendador real. O contexto Spring do Banco DUP obtém `ServicoConsultaRecebiveis`
e utiliza a configuração existente de `GatewayRegistradoraHttp`/`RestClient`.

São criadas três Duplicatas por Escrituração Manual para o Sacador principal,
mais uma de outro Sacador para verificar isolamento. Também há um Sacador sem
títulos e dois Financiadores. Foram verificados PENDENTE/ATIVA, recusa, revogação
entre páginas, expiração real, 404 genérico, Bearer inválido, decisão por Sacador
alheio, paginação, precisão decimal, repetição 201/Location e conflito 409.
Não houve defeito funcional confirmado nem alteração de produção ou migrations.
Comando e detalhes estão no [guia da 3.4](autorizacao-consulta.md#entrega-34--validação-vertical).

O primeiro ensaio passou em 8/9 casos; a asserção que comparava integralmente
dois Problems 404 foi corrigida para respeitar `instance`, que contém o caminho
público solicitado. A execução específica seguinte passou em 9/9 casos.
Em 13/11/2026, `.\gradlew.bat clean build` passou em 1min43s: 263 testes,
sendo 100 do Financiamento (incluindo os nove verticais) e 163 da Signatária,
sem falhas, erros ou ignorados. `git diff --check` também passou.

Após os ajustes finais, o JAR da Signatária passou a ser input rastreável da tarefa
de teste e a expiração manteve relógio real, com margem de 30s/45s. A prova
incremental confirmou `UP-TO-DATE` sem mudanças e reexecução ao alterar somente
o conteúdo do JAR. O build completo seguinte passou em 2min38s, novamente com
263 testes aprovados, incluindo os nove verticais, sem falhas, erros ou ignorados.
A aprovação técnica final foi registrada em 13/11/2026. Após autorização explícita,
o commit `27e950e` versionou exclusivamente os sete arquivos da 3.4, sem alterações da 4.1 e sem push.

**Limites:** o Banco DUP é iniciado sem servidor HTTP, pois os casos de uso não
possuem endpoints de entrada. A comunicação de saída é HTTP real. O teste exige
Docker e Java 21; não acessa o banco persistente do Compose nem infraestruturas
regulatórias. A validação não implica elegibilidade, saldo financiável, Registro,
IUD ou interoperabilidade. Nenhuma próxima entrega foi iniciada.

**Critério de conclusão da Entrega 3:** fluxo demonstrável de ponta a ponta entre Banco DUP e Signatária, com controles de acesso verificados e documentação reproduzível.

---

## Entrega 4.1 — OpenAPI 3 e Swagger UI

**Situação: implementada e validada por HTTP; aprovada tecnicamente em 13/11/2026, com Code Review encerrado favoravelmente. Versionada em `03290c8` após autorização explícita.**

- [x] Separar a 4.1 do commit de encerramento da 3.4.
- [x] Adotar springdoc 3.2.0 somente na Signatária, compatível com Spring Boot 4.1.x.
- [x] Gerar OpenAPI das sete operações HTTP existentes, com DTOs reais e Bearer opaco.
- [x] Expor documentação somente no perfil `local`, mantendo filtros e papéis de negócio.
- [x] Validar JSON, parâmetros, schemas, respostas e recursos da UI por HTTP real.
- [x] Executar regressões HTTP afetadas.
- [x] Executar e registrar o build completo.
- [x] Concluir documentação e revisão do diff.
- [x] Encerrar o Code Review com parecer técnico final favorável em 13/11/2026.
- [x] Registrar aprovação técnica da 4.1 em 13/11/2026.

Swagger usa os caminhos padrão `/swagger-ui.html` e `/v3/api-docs`, sem documentação
de Actuator ou novos endpoints de negócio. Fora de `local`, OpenAPI/UI ficam
desabilitados e as rotas documentais permanecem bloqueadas. Não há alteração em
domínio, persistência ou autenticação. A verificação encontrou uma inconsistência
documental anterior: UUID malformado no GET de processamento retorna 422 pelo
handler existente; o guia foi corrigido sem alterar a regra ou o tratamento de erro.
Procedimentos e evidências estão no [guia técnico](escrituracao-manual.md#openapi-3-e-swagger-ui--entrega-41).
Em 13/11/2026, os testes específicos com regressões HTTP passaram em 61/61 casos.
O build final `.\gradlew.bat clean build` passou em 2min24s: 270 testes (100 do
Financiamento e 170 da Signatária), sem falhas, erros ou ignorados. O JSON gerado,
os quatro grupos e os recursos da UI foram verificados por HTTP, com filtros ativos.
A Signatária também iniciou como JAR em ambiente descartável; a inspeção visual
foi interrompida pelo Computer Use por não conseguir determinar a URL atual do
Chrome no Windows com confiança para aplicar sua política. Não se declara inspeção
visual realizada. Processo e container descartáveis foram encerrados.
Naquela entrega, observabilidade/monitoramento permaneciam futuros.

Na verificação final dos contratos, os testes HTTP confirmaram 401 por Bearer
ausente/inválido e 403 por papel inadequado com `application/problem+json;charset=UTF-8`;
o 403 de autorização própria sem permissão usa `application/problem+json`.
Os media types coincidem com o OpenAPI, sem alteração nos filtros, handlers ou
componentes documentais. As asserções preservam os corpos distintos e o desafio
`WWW-Authenticate: Bearer` somente no 401. Os 61 testes HTTP afetados passaram;
o novo build completo passou em 2min04s, com 270 testes (100 do Financiamento e
170 da Signatária), sem falhas, erros ou ignorados. A aprovação técnica foi registrada
em 13/11/2026, preservando a limitação da inspeção visual não realizada e a evidência
dos testes HTTP de Swagger UI aprovados. Após autorização explícita, o commit
`03290c828f751f838407e314f6fe2e27da137091` encerrou a 4.1 isoladamente no histórico.

## Entrega 4.2 — Observabilidade fundamental

**Situação: implementada e validada; aprovação técnica final em 13/11/2026, com Code Review concluído favoravelmente. Versionamento pendente de autorização explícita.**

- [x] Confirmar a 4.1 encerrada em commit independente e árvore limpa antes de iniciar.
- [x] Adotar o starter OpenTelemetry gerenciado pelo Boot nas duas aplicações.
- [x] Produzir JSON Logstash com serviço e contexto de trace/span, sem credenciais.
- [x] Usar builder RestClient autoconfigurado, preservando Bearer, timeouts e semântica de falhas.
- [x] Verificar W3C e a cadeia de spans entre JVMs por receptor OTLP efêmero de teste.
- [x] Verificar métricas HTTP/JVM, operações com tags finitas e transições após commit.
- [x] Comprovar worker real, rollback sem falsa conclusão e correlação por processamento.
- [x] Expor métricas da Signatária somente em local com autenticação; demais endpoints sensíveis bloqueados.
- [x] Não exigir backend de telemetria para inicialização ou testes.
- [x] Concluir build completo e revisão final do diff.
- [x] Aprovar os ajustes de robustez dos testes JDBC e Awaitility.
- [x] Aprovar três execuções independentes do teste OTLP.
- [x] Encerrar Code Review e registrar aprovação técnica final em 13/11/2026.
- [ ] Versionar após autorização.

A validação específica passou com 29 testes do Banco DUP e 44 da Signatária,
sem falhas, erros ou ignorados. O novo caso vertical verifica `traceparent`, bit
sampled, trace compartilhado, spans distintos e relações pai/filho, incluindo
spans intermediários nativos do Spring Security. Os logs JSON são verificados
com IDs de contexto, serviço e ausência dos tokens/hashes efêmeros. O teste real
do worker não conta uma conclusão revertida e observa a nova tentativa confirmada.

Os ensaios iniciais identificaram a necessidade do starter RestClient do Boot 4,
do appender estruturado no Logback existente e de desabilitar exportação OTLP de
métricas por padrão. Duas asserções de tracing foram ajustadas aos flags W3C
válidos e aos spans intermediários efetivamente produzidos. Não foi necessário
alterar regra de negócio, segurança de negócio, transação, migration ou ADR.
Procedimentos, métricas, sampling/exportação e limites: [guia de observabilidade](observabilidade.md).

A regressão HTTP e a suíte completa do Banco DUP passaram em 1min43s. Na validação
inicial, `.\gradlew.bat clean build --console=plain` passou em 2min24s, com 277 testes:
101 do Financiamento (dez verticais: nove anteriores e um de observabilidade) e
176 da Signatária. Zero falhas, erros e ignorados. O build não exigiu coletor externo;
os relatórios não contêm avisos de falha de exportação OTLP. `git diff --check`
passou. O diff contém somente instrumentação, configuração, testes e documentação.

A revisão final removeu a exigência de exatamente três eventos JDBC, preservando
JSON válido, metadados, códigos/SQLState permitidos e substituição segura de mensagens.
Também corrigiu a intermitência de chegada dos spans: as buscas por raiz e servidor
agora verificam `Optional` por asserção antes do acesso, permitindo novas tentativas
pelo Awaitility dentro dos mesmos 15s, sem `Thread.sleep` ou alteração do receptor.
As três execuções independentes do teste OTLP, com `--rerun-tasks` e sem mudanças
entre elas, passaram em 1min, 43s e 41s. A suíte vertical completa passou com dez
testes em 1min03s. O último `clean build --console=plain` passou em 2min21s: 277
testes (101 do Financiamento e 176 da Signatária), zero falhas, erros e ignorados.
Essas correções e evidências foram aprovadas tecnicamente em 13/11/2026; o Code
Review está encerrado favoravelmente. Apenas o versionamento aguarda autorização
explícita. Nenhum backend externo de monitoramento foi validado.

O processamento não persiste contexto de trace: a correlação com o recebimento
usa o ID técnico. Não há novos spans SQL/JDBC, retry da integração, coletor
obrigatório, dashboards ou monitoramento centralizado. AGENTS e ADRs preservados.

## Planejamento restante da Fase 4

Sequência aprovada; as entregas abaixo permanecem planejadas e não foram iniciadas.

### 4.3 — CI com GitHub Actions

- Java 21 e Gradle Wrapper.
- Testcontainers, testes verticais e relatórios de testes.
- Status checks.

### 4.4 — Monitoramento

- Prometheus e Grafana.
- Dashboards e SLIs/SLOs.

### 4.5 — CD

- Empacotamento e publicação.
- Estratégia de deploy a definir.

## Evoluções previstas

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

- Expansão das métricas e do rastreamento fundamental da 4.2 quando houver novos casos de uso.
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
