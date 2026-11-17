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

## Exposição Prometheus local — Entrega 4.4.1

**Situação: 4.4.1 aprovada tecnicamente e em Code Review na feature branch
`feat/4.4-monitoramento`, ainda não integrada à `main`. A Entrega 4.4 permanece incompleta.**

As duas aplicações usam `micrometer-registry-prometheus`, com versão gerenciada
pelo Spring Boot 4.1.1. O registry exporta os medidores da 4.2, sem novos contadores,
buckets ou exportação OTLP adicional. O build não exige Prometheus/Grafana.

### Perfis e credenciais externas

Ative explicitamente `local,monitoramento`. O perfil de monitoramento:

- mantém as portas 8080 (Banco DUP) e 8081 (Signatária);
- exige `local` e bind em `127.0.0.1`; override amplo de endereço é rejeitado;
- exige `management.endpoints.web.base-path=/actuator` e
  `management.endpoints.web.path-mapping.prometheus=prometheus` (valores padrão);
  remapeamentos são rejeitados na inicialização;
- não aceita definir `management.server.port` ou `management.server.address`,
  mesmo com valores iguais aos principais; o gerenciamento usa o servidor existente;
- habilita somente o endpoint adicional `/actuator/prometheus`;
- falha na inicialização se faltar usuário/hash ou se a configuração for inválida;
- aceita usuário de 3–64 caracteres ASCII: letras, números, ponto, hífen e sublinhado,
  começando por letra ou número;
- aceita hash BCrypt `$2a$`, `$2b$` ou `$2y$`, com custo de 10 a 14.
  O limite superior mantém o custo de autenticação local controlado.

| Aplicação | Variável externa | Propriedade equivalente |
|---|---|---|
| Banco DUP | `FINANCIAMENTO_METRICAS_USUARIO` | `financiamento.monitoramento.usuario` |
| Banco DUP | `FINANCIAMENTO_METRICAS_SENHA_HASH` | `financiamento.monitoramento.senha-hash` |
| Signatária | `SIGNATARIA_METRICAS_USUARIO` | `signataria.monitoramento.usuario` |
| Signatária | `SIGNATARIA_METRICAS_SENHA_HASH` | `signataria.monitoramento.senha-hash` |

Não existem valores padrão de credenciais. Use usuário e senha diferentes em cada
aplicação. Carregue as variáveis de forma privada no ambiente do processo, ou use
um YAML externo via `SPRING_CONFIG_ADDITIONAL_LOCATION`. Arquivos `.env.*` são
ignorados pelo Git; não são carregados automaticamente pelo Spring.
Não passe senha/hash nos argumentos do Gradle nem versione sua saída.

As identidades Bearer e o token de integração continuam necessários para os casos
de uso de negócio. O operador Basic não é Sacador nem Financiador; seu provider de
autenticação existe apenas dentro da cadeia Prometheus.

### Geração de credenciais locais

Após resolver as dependências pelo Wrapper, use o BCrypt já fornecido pelo Spring
Security, sem instalar ferramenta ou adicionar script ao projeto. No PowerShell:

```powershell
$cryptoJar = Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/org.springframework.security/spring-security-crypto/7.1.1" -Recurse -Filter 'spring-security-crypto-7.1.1.jar' | Select-Object -First 1 -ExpandProperty FullName
jshell --class-path "$cryptoJar"
```

Na sessão local do JShell:

```java
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCrypt;
byte[] bytes = new byte[32];
new SecureRandom().nextBytes(bytes);
String senha = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
String hash = BCrypt.hashpw(senha, BCrypt.gensalt(10));
System.out.println("Senha aleatoria: " + senha);
System.out.println("Hash BCrypt correspondente: " + hash);
```

A senha e o hash exibidos são privados e temporários. Guarde o hash na configuração
externa da aplicação e a senha apenas no cliente operacional; não reutilize Bearers.
Não copie senhas, hashes ou saídas privadas dessa sessão para commits, logs
compartilhados ou relatórios.
Repita a geração para a outra aplicação. Os testes automatizados geram seus
próprios segredos aleatórios; não há senhas reais ou hashes fixos nos testes.

Depois do provisionamento privado e da configuração Bearer já documentada:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:bootRun "--args=--spring.profiles.active=local,monitoramento"
.\gradlew.bat :duplicata-escritural-signataria-api:bootRun "--args=--spring.profiles.active=local,monitoramento"
```

Execute cada aplicação em seu terminal. A Signatária continua exigindo PostgreSQL
e o arquivo externo de participantes; siga o [guia local](../infra/local/README.md).
Para inspecionar o scrape sem gravar a senha no histórico:

```powershell
$credencialMetricas = Get-Credential -Message 'Credencial operacional da aplicacao'
Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/prometheus' -Headers @{
  Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(
    $credencialMetricas.UserName + ':' + $credencialMetricas.GetNetworkCredential().Password))
}
Remove-Variable credencialMetricas
```

Use 8081 e a outra credencial para a Signatária. HTTP Basic não cifra o tráfego;
este perfil serve exclusivamente à máquina local. Não publique na LAN.

### Contrato de acesso e compatibilidade

| Caminho | Com monitoramento | Sem monitoramento |
|---|---|---|
| `GET /actuator/prometheus` | Basic correto: 200; ausente/incorreto/Bearer: 401 | Não exposto |
| Outros métodos em `/actuator/prometheus` | Basic correto: 403 | Não expostos |
| `/actuator/health`, `/actuator/info` | Públicos como antes | Públicos como antes |
| Signatária `/actuator/metrics[/nome]` | Mantém Bearer de participante em local | Mantém regra da 4.2 |
| Signatária APIs de negócio | Mantêm Bearer e papéis; Basic recebe 401 | Regras anteriores |
| Swagger da Signatária | Público em local | Regra da 4.1 |
| `env`, `beans`, `configprops`, `heapdump`, `loggers` | Não expostos/liberados | Não expostos/liberados |

A cadeia Prometheus tem precedência (`@Order(1)`), permite apenas GET no caminho
exato e não executa `FiltroTokenLocal`. Usa Basic nativo, BCrypt e sessão
`STATELESS`. Não cria login ou usuário padrão.
No Banco DUP, a cadeia complementar preserva a descoberta do Actuator,
`health,info` e os 404 de rotas inexistentes. Não autentica Basic globalmente.
A configuração de filtros depende de contexto servlet; os testes verticais continuam
iniciando o Banco DUP sem servidor HTTP e sem credenciais operacionais.

### Restrição de rotas e servidor de gerenciamento

A revisão de segurança reproduziu, antes da correção, o seguinte comportamento
com HTTP real em `local,monitoramento`:

| Configuração externa | Banco DUP sem Basic | Signatária sem Basic |
|---|---|---|
| Base `/manage`: `/manage/prometheus` | 200 com métricas | 401 |
| Mapeamento `scrape`: `/actuator/scrape` | 404 | 401 |
| Base `/actuator/health` ou mapeamento `health/prometheus` | 200 com métricas | 200 com métricas |
| Porta separada, caminho padrão, endereço de teste em loopback | 401; Basic correto: 200 | 401; Basic correto: 200 |
| Somente `management.server.address`, sem porta separada | Falha nativa de inicialização | Falha nativa de inicialização |

Com porta separada, ambas aceitaram scrape autenticado em `127.0.0.2`, embora o
servidor principal usasse `127.0.0.1`. No Banco DUP, combinar esse servidor com
base `/manage` também retornou métricas sem Basic. As sondas ficaram exclusivamente
em loopback; não foi aberto listener na LAN nem comprovada exposição por ela.

A correção rejeita configurações incompatíveis no construtor de segurança do
monitoramento, antes de servir HTTP, sem alterar as cadeias Bearer ou HTTP geral.
Os testes iniciam as aplicações reais e verificam a falha controlada e a recusa de
conexão nas portas principal e de gerenciamento. A restrição não se aplica a
perfis sem `monitoramento`.

O matcher oficial `EndpointRequest` acompanha os caminhos efetivos do Actuator,
mas não restringe porta/endereço. Como esta entrega exige um caminho fixo e o
servidor existente, rejeitar overrides mantém o contrato menor que aceitar rotas
dinâmicas e ainda validar o servidor separadamente. O matcher fixo só é usado
depois dessa validação.

Após a correção, o `clean build --console=plain` passou em 2min43s: **322 testes**
(124 do Banco DUP e 198 da Signatária), incluindo os dez verticais, sem falhas,
erros ou ignorados. O ajuste acrescentou 20 casos sobre os 302 anteriores:
oito cenários de inicialização/HTTP e duas propriedades isoladas por aplicação.
As regressões afetadas passaram com 124 testes do Banco DUP e 84 da seleção
HTTP/configuração da Signatária. `git diff --check` aprovado; a 4.4.1 foi aprovada
tecnicamente e em Code Review na feature branch, ainda não integrada à `main`.

### Nomes exportados

Os nomes Micrometer são convertidos para nomes Prometheus. As operações devem
ser executadas antes de procurar os medidores criados sob demanda:

| Medidor | Exportação |
|---|---|
| `financiamento.registradora.operacao` | `financiamento_registradora_operacao_seconds_count/sum/max` |
| `signataria.operacao` | `signataria_operacao_seconds_count/sum/max` |
| `signataria.escrituracao.transicoes` | `signataria_escrituracao_transicoes_total` |
| HTTP | `http_client_requests_seconds_count`, `http_server_requests_seconds_count` |
| JVM/threads | `jvm_memory_used_bytes`, `jvm_threads_live_threads` |
| JDBC/Hikari (Signatária) | `jdbc_connections_max`, `hikaricp_connections` |

Exemplos sanitizados ilustrativos; não representam contagem financeira:

```text
financiamento_registradora_operacao_seconds_count{error="none",motivo="nenhum",operacao="consultar_duplicatas",resultado="sucesso"} 1
signataria_escrituracao_transicoes_total{estado="CONCLUIDO",motivo="nenhum"} 1
```

Os labels próprios existentes continuam finitos. Não há IDs, CNPJ, Bearers,
senhas, hashes, Idempotency-Key ou valores financeiros nos labels próprios.
Nenhum medidor adicional duplica os timers ou as transições da 4.2.

### Verificação e próxima etapa

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:test --tests '*MonitoramentoHttpTests' --tests '*ConfiguracaoMetricasTests' :duplicata-escritural-signataria-api:test --tests '*MonitoramentoHttpTests' --tests '*ConfiguracaoMetricasTests'
.\gradlew.bat clean build --console=plain
git diff --check
```

Os testes HTTP usam servidor real, credenciais efêmeras e, na Signatária,
PostgreSQL/Testcontainers e worker real. Conferem exportação, isolamento Basic/Bearer,
métodos negados, endpoints sensíveis, ausência de segredos e compatibilidade local.

Validação inicial em 17/11/2026: 13 testes específicos do Banco DUP e 12 da
Signatária aprovados. A regressão passou com 114 testes do Banco DUP (incluindo os
dez verticais) e 74 na seleção HTTP/configuração da Signatária. O
`clean build --console=plain` passou em 3min12s: **302 testes**, sendo 114 do
Banco DUP e 188 da Signatária, sem falhas, erros ou ignorados. São 25 testes novos
sobre o baseline de 277. Não havia Prometheus/Grafana em execução.
`git diff --check` aprovado. Naquela validação inicial, ainda não havia commit,
push, PR ou aceite final da 4.4.

A 4.4.2 deverá validar o acesso de dentro do Docker Desktop usando
`host.docker.internal:8080/8081`. `localhost` no container não aponta para o Windows.
O bind em loopback não comprova essa conectividade. Não ampliar o bind para
contornar falha sem revisar a fronteira de segurança. Compose, scrape, Grafana,
dashboards, alertas e SLIs/SLOs permanecem fora da 4.4.1.

Referências: [acesso/exposição do Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
e [cadeias de segurança](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html).
