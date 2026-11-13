# Observabilidade fundamental — Entrega 4.2

As duas aplicações usam Actuator, Micrometer Metrics/Observation/Tracing e o starter
OpenTelemetry gerenciado pelo Spring Boot 4.1.1. Instrumentação não exige exportação:
não é necessário instalar um coletor, Prometheus ou uma plataforma de visualização.
Não há novos endpoints de negócio, tabelas, mudanças de autorização ou retry da integração.

## Logs e correlação

O console usa JSON Logstash nativo do Boot (`logging.structured.format.console=logstash`).
Cada evento inclui `@timestamp`, `level`, `service`, `logger_name` e `message`.
Com contexto ativo, o MDC acrescenta `traceId` e `spanId`; bootstrap pode não tê-los.
Operações acrescentam `operacao`, `resultado` e `motivo`, com valores finitos.
Quando a operação retorna autorização, `estadoAutorizacao` informa seu estado
persistido após o serviço, sem IDs dos participantes ou dados cadastrais. Isso
permite distinguir decisão ATIVA/RECUSADA. Repetição de POST continua devolvendo
a representação HTTP original PENDENTE, independentemente desse estado atual.

Exemplo **ilustrativo, com IDs fictícios; não é evidência de execução**:

```json
{"@timestamp":"2026-11-13T12:00:00-03:00","level":"INFO","service":"duplicata-escritural-financiamento-api","logger_name":"br.com.duplicataescritural.financiamento.consultarecebiveis.aplicacao.ServicoConsultaRecebiveis","message":"Operação da registradora finalizada","traceId":"11111111111111111111111111111111","spanId":"2222222222222222","operacao":"consultar_duplicatas","resultado":"sucesso","motivo":"nenhum"}
```

Para acompanhar uma consulta, localize o evento do Banco DUP e procure o mesmo
`traceId` nos logs da Signatária. O `spanId` muda a cada etapa. O Banco DUP cria uma
Observation para solicitar autorização, consultar autorização e consultar uma página.
Seu `RestClient.Builder` autoconfigurado propaga W3C `traceparent` automaticamente.
A Signatária cria um span servidor e Observations das operações; Spring Security
também pode acrescentar spans intermediários. Bearer, timeouts, ausência de redirects,
tratamento de erros e revalidação por página permanecem como na 3.3.

Logs operacionais não incluem payloads, CNPJ, valores, tokens, hashes ou causas de
exceções. Erros das Observations explícitas recebem apenas classificação segura.
Stack traces são excluídos do JSON pelo Boot; o diagnóstico JDBC mantém a lista
permitida anterior de códigos numéricos/SQLState, agora serializada pelo `JsonWriter`
do Boot. Outras mensagens JDBC são substituídas por texto fixo, sem throwable.
Não habilite logging de corpos/cabeçalhos HTTP, SQL ou parâmetros financeiros.
Essas restrições deliberadamente reduzem detalhes de exceções nos logs.

## Processamento assíncrono

O recebimento registra `processamentoId` depois do retorno do serviço transacional,
inclusive quando a chamada é repetição idempotente. O agendador continua usando o
worker persistido e as transações T1/T2/T3 existentes. A tentativa executada possui
Observation própria; uma consulta de fila vazia não cria uma Observation de execução.

As transições registram `processamentoId`, `tentativa`, `estado` e código seguro em
`motivo` **após commit**, por `TransactionSynchronization.afterCommit`:

- `EM_PROCESSAMENTO`: reserva confirmada; a tentativa informa novas execuções.
- `CONCLUIDO`: Duplicata local e conclusão confirmadas na mesma transação.
- `REJEITADO`: rejeição de negócio confirmada.
- `PENDENTE`: falha transitória/reserva expirada com nova tentativa prevista.
- `FALHA_TECNICA`: falha permanente ou esgotamento das tentativas existentes.

Rollback não emite uma transição confirmada. O ID correlaciona recebimento, reserva,
execução e recuperação. O trace HTTP **não é persistido nem continuado pelo banco**.
Reserva, execução e registro posterior de falha podem ter contextos distintos ou
nenhum contexto ativo; somente eventos com contexto recebem IDs de trace/span.
Os sinais são diagnóstico operacional, não auditoria financeira transacional.

## Métricas

| Nome Micrometer | Tipo | Dimensões próprias / significado |
|---|---|---|
| `financiamento.registradora.operacao` | Timer de Observation | `operacao`: `solicitar_autorizacao`, `consultar_autorizacao`, `consultar_duplicatas`; `resultado`: `sucesso`, `rejeitado`, `falha`; `motivo`: `nenhum`, enum `FalhaRegistradora.Motivo` ou `ERRO_INTERNO` |
| `signataria.operacao` | Timer de Observation | `operacao`: as três consultas anteriores, `decidir_autorizacao`, `revogar_autorizacao`, `receber_escrituracao`, `executar_escrituracao`; `resultado`: `sucesso`, `rejeitado`, `falha`; `motivo`: `nenhum`, `acesso_negado`, `nao_encontrado`, `conflito`, `parametros`, `negocio`, `infraestrutura`, `interno` |
| `signataria.escrituracao.transicoes` | Counter | `estado` e `motivo` limitados aos enums do processamento; conta transições confirmadas, incluindo retomadas |
| `http.client.requests` | Timer nativo | HTTP de saída do Banco DUP: método, template de URI, status, resultado e exceção |
| `http.server.requests` | Timer nativo | HTTP de entrada na Signatária, incluindo rejeições de autenticação/papel |

Os timers de Observation também recebem a tag técnica `error`. Uma mesma operação
tem uma medição semântica e sua medição HTTP; não existe Counter adicional duplicando
o Timer. Transições e duração de tentativa têm significados diferentes.
Idempotência conta chamadas, não novas autorizações. Durações incluem os custos
efetivamente observados, sem valores fixos esperados nos testes.

Também existem métricas JVM (por exemplo `jvm.memory.used`), threads e pool JDBC da
Signatária. Não há instrumentação SQL/JDBC de spans adicionada nesta entrega.
Nenhuma tag própria usa CNPJ, UUID, Idempotency-Key, token, valor nominal ou número
de documento. O cliente usa templates de URI, inclusive para paginação; parâmetros
concretos não viram labels. Identificadores técnicos aparecem somente nos logs de
processamento e, quando inerentes ao HTTP, nos atributos de alta cardinalidade dos spans.

## Inspeção local e segurança

Na Signatária com perfil `local`, GET `/actuator/metrics` e
`/actuator/metrics/{nome}` exigem Bearer de participante provisionado, Sacador ou
Financiador. Sem Bearer/válido, o resultado é 401. Isso não concede operações de
negócio do outro papel. Exemplo com token fictício já provisionado no ambiente:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8081/actuator/metrics/signataria.operacao' -Headers @{ Authorization = "Bearer $env:SIGNATARIA_TOKEN" }
```

Execute operações antes de consultar seus medidores, registrados sob demanda.
`health` e `info` continuam públicos. Fora de `local`, a exposição permanece
`health,info` e métricas HTTP ficam bloqueadas. `env`, `beans`, `configprops`,
`heapdump` e `loggers` não são expostos/liberados. Swagger permanece público somente
em `local`, sem mudança da segurança de negócio.

O Banco DUP mantém apenas `health,info` por HTTP. Nos testes sem servidor, consulte
seu `MeterRegistry`; não foi criado um controller de métricas ou de casos de uso.
Não publique o perfil local em ambiente compartilhado: ele é um simulador com
provisionamento de identidades pelo operador, conforme o guia de autorização.

## Sampling e OTLP

`TRACING_SAMPLING_PROBABILITY` configura `management.tracing.sampling.probability`:
padrão `1.0` em `local` e `0.1` nos demais perfis. O sampler nativo padrão é
`parent-based-trace-id-ratio`: respeita o contexto pai e aplica a probabilidade às
raízes. Testes verticais fixam explicitamente `1.0` nas duas aplicações.
`management.tracing.propagation.type=w3c` limita a propagação ao padrão W3C.

Nenhum endpoint OTLP de traces ou logs é definido no repositório. Sem endpoint,
o Boot não configura o exporter de traces. `management.otlp.metrics.export.enabled=false`
impede o envio de métricas ao destino padrão do registry OTLP trazido pelo starter.
As métricas continuam disponíveis no registry local/Actuator. Não é preciso um
backend ativo para iniciar ou testar a plataforma.

Se houver um receptor externo provisionado pelo operador, configure por ambiente
`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` com a URL HTTP completa de
traces, incluindo `/v1/traces`. Propriedades nativas adicionais do Boot 4.1.1:

- `management.opentelemetry.tracing.export.otlp.transport`: `http` por padrão.
- `management.opentelemetry.tracing.export.otlp.connect-timeout`: padrão `10s`.
- `management.opentelemetry.tracing.export.otlp.timeout`: padrão `10s`.
- `management.opentelemetry.tracing.export.schedule-delay`: padrão `5s`.
- `management.tracing.export.otlp.enabled=false`: desabilita explicitamente o exporter.

Não coloque segredos em URLs ou arquivos versionados. Cabeçalhos privados de um
receptor, se necessários, pertencem à configuração externa. A entrega prepara a
exportação de traces; não instala nem valida uma plataforma de monitoramento.
Referências oficiais: [tracing do Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
e metadados das dependências 4.1.1 efetivamente resolvidas pelo Wrapper.

## Testes reproduzíveis

Java 21 e Docker disponíveis, na raiz:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:test --tests '*ServicoConsultaRecebiveisTests' --tests '*FluxoConsultaAutorizadaVerticalTests'
.\gradlew.bat :duplicata-escritural-signataria-api:test --tests '*ObservabilidadeTests' --tests '*ProcessamentoEscrituracaoTests' --tests '*OpenApiHttpTests' --tests '*SignatariaApplicationTests'
.\gradlew.bat clean build
git diff --check
```

A fixture vertical da 3.4 é reutilizada: Signatária em JAR/JVM independente,
PostgreSQL/Testcontainers, Flyway, agendador e segurança reais; Banco DUP em seu
contexto Spring, sem servidor HTTP. Um receptor HTTP OTLP efêmero em loopback e porta
dinâmica recebe os spans das duas aplicações. Os bindings oficiais
`opentelemetry-proto:1.9.0-alpha` são usados **somente nos testes** para decodificar
o protobuf, sem parser próprio. Não há serviço permanente nem novo executável.

O teste adicional valida header `traceparent` (incluindo o bit sampled, sem exigir
outros flags fixos), IDs e cadeia pai/filho, JSON/MDC/serviço, métricas e cardinalidade,
segredos ausentes nos logs/spans e worker com trace distinto do recebimento.
Os nove cenários verticais anteriores permanecem. O worker também é testado com
rollback real do PostgreSQL: não conta conclusão revertida e registra a conclusão
somente após a tentativa posterior confirmada. Testes de classificação verificam
falhas sem expor mensagens internas ou duplicar medições.

Contextos, processo, receptor e container são encerrados pela fixture. Logs do
JUnit ficam em diretório temporário, preservados somente em falha. O build padrão
não depende de coletor externo. Resultados finais desta entrega estão no [roadmap](roadmap.md).

## Evidências em 13/11/2026

Validação específica: 29 testes do Banco DUP e 44 da Signatária aprovados. Regressões
HTTP e suíte completa do Banco DUP aprovadas. Build inicial
`.\gradlew.bat clean build --console=plain`: 2min24s, 277 testes (101 do Banco DUP,
176 da Signatária), sem falhas, erros ou ignorados. Dez testes verticais aprovados,
incluindo os nove anteriores. `git diff --check` sem erros.

Trecho **real, reduzido aos campos operacionais** do log JSON capturado nesse build:

```json
{"@timestamp":"2026-11-13T15:59:03.8484928-03:00","service":"duplicata-escritural-financiamento-api","level":"INFO","message":"Operação da registradora finalizada","traceId":"3f09917307062d45d01d81562fe31fae","spanId":"7df96804dc6af623","operacao":"consultar_duplicatas","resultado":"sucesso","motivo":"nenhum"}
```

Os relatórios locais são gerados em `build/reports/tests/test/index.html` de cada
subprojeto, com XML em `build/test-results/test`. O teste
`propagaW3cEntreJvmsERegistraLogsMetricasEWorkerSeguros` comprova a propagação e as
relações dos spans recebidos, não apenas a existência das dependências. Nenhum
aviso de falha de exportação OTLP apareceu nos relatórios desse build.

A revisão final aprovou o diagnóstico JDBC sem quantidade fixa de eventos e as
buscas de raiz/servidor OTLP com asserção de presença antes de `orElseThrow`,
permitindo repetição pelo Awaitility dentro dos 15s existentes. As três execuções
independentes do teste OTLP passaram em 1min, 43s e 41s, com `--rerun-tasks` e sem
alterações entre elas. Todos os dez testes verticais passaram em 1min03s. O último
`.\gradlew.bat clean build --console=plain` passou em 2min21s: 277 testes (101 do
Banco DUP e 176 da Signatária), sem falhas, erros ou ignorados.

**Aprovação técnica final da 4.2 em 13/11/2026, com Code Review concluído favoravelmente.**
Versionamento autorizado e registrado em `54357e8`. Nenhum backend externo de
monitoramento foi validado; utilizou-se somente o receptor OTLP efêmero de teste.
No aceite da 4.2, as entregas 4.3, 4.4 e 4.5 ainda estavam planejadas; o estado
atual está no [roadmap](roadmap.md).
