# Plataforma de Financiamento de Duplicatas Escriturais

[![CI](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/workflows/ci.yml?query=branch%3Amain)

Plataforma experimental para explorar o financiamento de Duplicatas Escriturais no contexto do sistema financeiro brasileiro, sob a perspectiva de um **Agente Financiador**.

O projeto utiliza Java e Spring Boot para explorar sistemas transacionais e integração distribuída. Spring AI e as capacidades de inteligência artificial permanecem planejadas.

## Objetivo

Construir uma plataforma que permita simular operações de financiamento de duplicatas escriturais, desde a consulta de títulos e avaliação de elegibilidade até a seleção de carteiras, simulação financeira e recomendação de financiamento.

Além do domínio financeiro, o projeto busca demonstrar decisões arquiteturais fundamentadas, tratamento de falhas, consistência de dados, resiliência e integração de agentes de IA com serviços transacionais.

## Arquitetura proposta

A plataforma adota uma abordagem híbrida, combinando modularidade interna com componentes independentes quando houver justificativa arquitetural.

| Componente | Responsabilidade |
|---|---|
| `duplicata-escritural-financiamento-api` | Banco DUP: solicitação/consulta de autorização e consulta paginada de Duplicatas por HTTP; crédito, elegibilidade e simulação planejados |
| `duplicata-escritural-signataria-api` | Simulador externo: Escrituração local, autorização e consulta protegida; Registro regulatório planejado |
| `duplicata-escritural-financiamento-mcp` | Planejado: disponibilização de capacidades de negócio via MCP |
| `duplicata-escritural-agente-financiamento` | Planejado: interpretação de solicitações e orquestração de ferramentas por IA |

A arquitetura será implementada incrementalmente, evitando a criação de componentes sem necessidade concreta.

Na Entrega 3.3, o fluxo implementado é:

```text
Banco DUP: ServicoConsultaRecebiveis → GatewayRegistradora
                                      → adapter RestClient → HTTP → Signatária
```

Cada aplicação possui modelos próprios. O Banco DUP não acessa o PostgreSQL da Signatária, não replica autorizações e não executa roteamento ou interoperabilidade. O ADR 0003 mantém esses limites; os ADRs anteriores são preservados como histórico.

## Princípio fundamental

**A inteligência artificial não executa regras financeiras críticas.**

O agente de IA atua na interpretação, no planejamento, na utilização de ferramentas e na elaboração de recomendações.

Elegibilidade, cálculos financeiros, limites, validações, consistência transacional e alterações de estado permanecem sob responsabilidade de código determinístico.

## Tecnologias previstas

- Java e Spring Boot
- Spring AI, MCP e Tool Calling
- PostgreSQL
- Kafka e arquitetura orientada a eventos
- APIs REST
- DDD e arquitetura hexagonal
- Transactional Outbox e idempotência
- Observabilidade e testes automatizados

A presença de uma tecnologia nesta lista não significa que ela já esteja implementada.

## Estado do projeto

**Fase de referência: Entrega 4.4 integrada à `main` pelo [PR #3](https://github.com/Lildtz/duplicata-escritural-financiamento/pull/3), merge `42be1338dce4cf305318779ce7ccf54b5bbd1796`, com [CI pós-merge aprovado](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/runs/44280078489): 324 testes Gradle e seis testes PromQL.** A 4.3 permanece integrada em `482b7f5`; a 4.2 em `54357e8`, a 4.1 em `03290c8`, a 3.4 em `27e950e` e a 3.3 em `04081ca`.

Os três incrementos da 4.4 foram aprovados em revisão consolidada; a validação
visual dos dashboards foi realizada manualmente pelo responsável. A branch
histórica foi `feat/4.4-monitoramento`, com a 4.4.1 versionada em `a2dde54`.
A **4.5 é uma entrega independente, em andamento na branch `feat/4.5-cd`**:
o empacotamento Docker da **4.5.1 está aprovado tecnicamente em Code Review,
sem achados bloqueantes**, e foi versionado no commit local `49c4c51`. As duas
imagens foram construídas e validadas localmente. A **4.5.2 está aprovada
tecnicamente em Code Review**, incluindo a remediação com Boot 4.1.2,
com único commit local autorizado e sem publicação; a 4.5.3 permanece planejada.
A 4.5 ainda não está concluída nem integrada à `main`.

As Entregas 1, 2, 3.1 e 3.2 estão implementadas. A 3.2 recebeu aprovação técnica retrospectiva em 13/11/2026, sem evidência de aceite histórico anterior, conforme o [roadmap](docs/roadmap.md). A 3.4 exercita o fluxo completo do simulador, preservando as fronteiras entre as aplicações.

| Frente | Situação documentada |
|---|---|
| Definição do domínio e objetivos | Concepção inicial estabelecida |
| Desenho arquitetural | Fronteiras documentadas nos [ADRs](docs/adr/README.md) |
| API de financiamento | Integração HTTP implementada; aprovação técnica final da 3.3 em 13/11/2026 |
| Simulador de Signatária | Escrituração e consulta local autorizada implementadas |
| MCP Server e agente de IA | Planejados |
| Integração assíncrona com Kafka | Evolução prevista |

## Execução e testes

Baseline: Java 21, Spring Boot 4.1.x e Gradle Wrapper 9.8. Docker é necessário para os testes PostgreSQL/Testcontainers. Execute na raiz:

```powershell
.\gradlew.bat clean build
git diff --check
git status --short
```

A execução e o provisionamento da Signatária estão no [guia local](infra/local/README.md) e no [guia de autorização](docs/autorizacao-consulta.md#identidades-e-provisionamento-local). Ela utiliza a porta 8081; o Banco DUP utiliza 8080.

A integração exige o perfil `local` em ambas as aplicações e um Bearer cujo hash esteja provisionado na Signatária com o `financiador-id` do Banco DUP. O token permanece somente no ambiente do Financiamento; não existe credencial padrão ou identidade por CNPJ em header.

O provisionamento define a identidade utilizada: um token válido de outro Financiador fará a aplicação atuar como aquele participante. Antes de consultar títulos, confira a associação do hash e o `financiadorId` retornado pela solicitação/consulta de autorização, conforme o [guia de provisionamento](docs/autorizacao-consulta.md#conexão-e-identidade-local). Essa conferência não substitui a autorização remota em cada página.

| Variável do Financiamento | Padrão / requisito |
|---|---|
| `SIGNATARIA_URL_BASE` | `http://localhost:8081`; HTTPS ou HTTP em loopback, sem caminho/query/credenciais |
| `SIGNATARIA_TOKEN` | Obrigatória no perfil `local`; token do Financiador beneficiário |
| `SIGNATARIA_TIMEOUT_CONEXAO` | `2s` |
| `SIGNATARIA_TIMEOUT_LEITURA` | `5s` |

Com o token provisionado de forma privada na variável de ambiente `SIGNATARIA_TOKEN`:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:bootRun "--args=--spring.profiles.active=local"
```

Sem perfil `local`, o Banco DUP mantém apenas o bootstrap/Actuator, sem ativar a integração. Configuração local ausente ou inválida impede a inicialização. Health checks: `http://localhost:8080/actuator/health` e `http://localhost:8081/actuator/health`.

Os casos de uso são beans de aplicação, sem novos endpoints no Banco DUP nesta entrega. O adapter consome POST `/api/v1/autorizacoes-consulta`, GET `/api/v1/autorizacoes-consulta/{id}` e GET `/api/v1/autorizacoes-consulta/{id}/duplicatas?pagina=0&tamanho=50`. Aprovação continua sendo responsabilidade do Sacador na Signatária.

Consulte [contratos, falhas e limites da integração 3.3](docs/autorizacao-consulta.md#entrega-33--integração-http-do-banco-dup). A paginação é repassada por chamada, sem cache ou retry da aplicação. A autorização é revalidada remotamente em cada página. `valorNominal` não é saldo financiável; `consultadaEm` é uma referência local, sem snapshot entre páginas. O escopo é `LOCAL_SIMULADO`, sem equivalência com Agenda regulatória, Registro, IUD ou disponibilidade financeira.

Os testes da 3.3 exercitam o adapter com respostas simuladas e HTTP descartável do JDK, incluindo configuração, Bearer, falhas e timeouts. A 3.4 acrescenta a Signatária real em outro processo e PostgreSQL descartável, com migrations, segurança e agendador ativos. O Banco DUP usa seu contexto Spring no perfil `local`, sem servidor de entrada, e chama o `RestClient` real.

Para executar somente o fluxo vertical, com Java 21 e Docker disponíveis:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:test --tests '*FluxoConsultaAutorizadaVerticalTests'
```

O Wrapper gera o JAR da Signatária; o teste provisiona participantes, tokens aleatórios e portas dinâmicas, recebe Escriturações Manuais e aguarda seu processamento antes de solicitar/aprovar autorizações e consultar páginas. Não requer Compose, configuração manual de credenciais ou dados preexistentes. A suíte também integra o `clean build`. Veja [sequência, cenários e limites da validação vertical](docs/autorizacao-consulta.md#entrega-34--validação-vertical).

## OpenAPI e Swagger UI locais

Na Signatária com perfil `local` e identidades provisionadas conforme o [guia local](infra/local/README.md), acesse `http://localhost:8081/swagger-ui.html` ou `http://localhost:8081/v3/api-docs`. A documentação é pública somente nesse perfil; os endpoints de negócio continuam exigindo Bearer opaco e os papéis corretos. Em **Authorize**, informe apenas um token fictício provisionado pelo operador, sem o prefixo `Bearer`.

O JSON é gerado dos controllers/DTOs existentes, sem especificação estática versionada. O Banco DUP não recebe springdoc nem endpoints novos. Consulte [contratos, erros, segurança e testes da documentação](docs/escrituracao-manual.md#openapi-3-e-swagger-ui--entrega-41).

## Observabilidade fundamental

As duas aplicações produzem logs JSON Logstash com serviço e, quando há contexto,
`traceId`/`spanId`. O Banco DUP usa o builder autoconfigurado do `RestClient` para
propagar W3C até a Signatária. Observations medem operações e classificam falhas;
transições do worker são registradas após commit e correlacionadas pelo ID do
processamento, sem continuidade automática do trace através do banco/agendador.

Em `local`, a Signatária disponibiliza `/actuator/metrics` somente com Bearer de
participante provisionado. O Banco DUP mantém `health,info` por HTTP; seus testes
usam `MeterRegistry`. Não é necessário instalar coletor: exportação OTLP de traces
não tem endpoint padrão e envio de métricas OTLP está desabilitado. Sampling é
configurável por `TRACING_SAMPLING_PROBABILITY` (local `1.0`, demais perfis `0.1`).

Veja [métricas, segurança, configuração OTLP e testes de observabilidade](docs/observabilidade.md).
A suíte vertical reutiliza a infraestrutura da 3.4 e um receptor OTLP efêmero para
verificar os spans das duas JVMs. A coleta Prometheus da 4.4.2 está aprovada em Code Review;
os dashboards e indicadores da 4.4.3 estão aprovados tecnicamente, com inspeção visual
manual confirmada pelo responsável.

Validação final da 4.2: 277 testes aprovados, incluindo dez verticais, sem falhas, erros
ou ignorados. Três execuções independentes adicionais do teste OTLP passaram. Foram
aprovados os ajustes de robustez do diagnóstico JDBC e da espera de spans no
Awaitility. Na 4.2, nenhum backend externo de monitoramento foi validado. A entrega
4.4 está integrada à `main` pelo PR #3, com CI pós-merge aprovado. A 4.5 foi
iniciada separadamente; publicação e implantação continuam planejadas.

## Exposição Prometheus local — Entrega 4.4.1

Aprovada tecnicamente e em Code Review, versionada inicialmente em `a2dde54` e
integrada à `main` pelo PR #3. As duas aplicações acrescentam
`GET /actuator/prometheus` somente com `local,monitoramento`, bind em
`127.0.0.1` e HTTP Basic operacional exclusivo.
Cada aplicação exige usuário e hash BCrypt externos, independentes dos Bearers
de Sacador/Financiador. Sem monitoramento, não são exigidas credenciais operacionais;
`health,info`, Swagger e `/actuator/metrics` preservam suas regras anteriores.

Consulte [ativação, geração privada de credenciais e testes](docs/observabilidade.md#exposição-prometheus-local--entrega-441).
Compose e scrape são acrescentados separadamente na 4.4.2, descrita abaixo.
A 4.4.1 não acrescentou Grafana, buckets ou SLOs.

Última validação local da 4.4.1: `clean build --console=plain` aprovado em
**2min43s**, com **322 testes** (124 do Banco DUP e 198 da Signatária), incluindo
os **10 testes verticais**, com zero falhas, erros ou ignorados. Foram adicionados
**45 testes** desde o baseline de 277.

O bypass por remapeamento do Actuator foi reproduzido e sua correção validada com
HTTP real nas duas aplicações. Configurações incompatíveis de rotas e de servidor
de gerenciamento são rejeitadas na inicialização do monitoramento.
O incremento foi aprovado na branch `feat/4.4-monitoramento` e posteriormente
integrado à `main` pelo PR #3.

## Coleta Prometheus — Entrega 4.4.2

**Aprovada tecnicamente em Code Review e integrada à `main` pelo PR #3.** O Compose
complementar [docker-compose.monitoramento.yml](infra/local/docker-compose.monitoramento.yml)
executa somente `prom/prometheus:v3.13.5`, publica `127.0.0.1:9090` e mantém as
séries em volume próprio. O Compose PostgreSQL permanece independente e inalterado.

A prova inicial com container Linux descartável alcançou ambas as aplicações por
`host.docker.internal:8080/8081`, recebendo HTTP 401 com desafio Basic, sem enviar
credenciais. As aplicações permaneceram em `127.0.0.1`, com a segurança da 4.4.1.
Essa conectividade foi comprovada no Docker Desktop local; deve ser revalidada em
outro ambiente, sem ampliar o bind nem abrir firewall.

Os jobs `banco-dup` e `signataria` coletam `/actuator/prometheus` a cada 15s, com
timeout de 5s e credenciais independentes em arquivos privados ignorados pelo Git.
Ambos ficaram `UP`; foram coletadas métricas JVM, HTTP, JDBC e operações reais,
incluindo uma Escrituração concluída pelo worker e aumento de contador após consulta.

Siga o [guia de infraestrutura](infra/local/README.md#coleta-prometheus--entrega-442)
para provisionar os arquivos privados, validar e iniciar o Compose. Veja
[evidências, consultas e limites](docs/observabilidade.md#coleta-prometheus--entrega-442).
O build continua independente do Prometheus; Grafana, dashboards e SLOs não fazem
parte deste incremento.

Validação da 4.4.2: `clean build --console=plain` aprovado em **3min**, com o
Prometheus parado: **322/322 testes** (124 do Banco DUP e 198 da Signatária),
incluindo os **10 verticais**, sem falhas, erros ou ignorados.

## Grafana e confiabilidade — Entrega 4.4.3

**Aprovada tecnicamente após inspeção visual manual pelo responsável e integrada
à `main` pelo PR #3.** O Compose de
monitoramento acrescenta Grafana OSS **13.3.1** em `127.0.0.1:3000`, com volume
próprio e senha administrativa aleatória em secret local ignorado pelo Git.
Acesso anônimo e cadastro público estão desabilitados; senha ausente, caminho
como diretório ou arquivo vazio/em branco impedem a inicialização.

Dois dashboards são provisionados automaticamente na pasta **Duplicata local**:
**Saúde e integração** e **Operações de Duplicatas**. O datasource de UID
`duplicata-prometheus` consulta `http://prometheus:9090` pela rede Docker.
Histogramas seletivos dos dois timers de operação permitem p95 de consultas
bem-sucedidas, somente com pelo menos 20 amostras em 15min. Nenhum timer é duplicado.

As referências experimentais são 99% de sucesso técnico por camada e p95 de
consulta até 2s. Credencial de integração recusada conta como falha técnica;
rejeições classificadas ficam separadas. Sem tráfego não aparece sucesso de 100%.
`up` mede coleta; transições contam eventos após commit, sem representar estoque.
Não há alertas ou promessa de disponibilidade de produção.

Veja [provisionamento privado, acesso e operação](infra/local/README.md#grafana--entrega-443)
e [definições, PromQL e evidências](docs/observabilidade.md#grafana-e-confiabilidade--entrega-443).

Validação da 4.4.3: build completo aprovado em **4min25s**, com Prometheus e
Grafana parados. **324/324 testes** (125 do Banco DUP e 199 da Signatária),
incluindo os **10 verticais**, zero falhas, erros ou ignorados. Seis casos PromQL
adicionais passaram no `promtool`; os dois dashboards foram verificados pela API
com dados reais. O responsável confirmou pessoalmente o funcionamento dos dois
dashboards. Essa validação visual é manual; o Computer Use não realizou a inspeção.
A revisão consolidada local aprovou os três incrementos da 4.4. Os seis testes
PromQL passaram novamente e foram integrados ao job obrigatório `Build e testes`,
sem alterar seu nome ou identidade. O histórico de 324 testes Gradle foi preservado;
o build não foi repetido nesta mudança restrita ao workflow e à documentação.
Posteriormente, o PR #3 integrou a entrega em `42be1338`; o CI pós-merge aprovou
324 testes Gradle e seis testes PromQL, conforme o registro do estado atual.

## Empacotamento Docker — Entrega 4.5.1

Dois Dockerfiles multi-stage constroem as aplicações com o Gradle Wrapper e
Java 21, com bases Temurin fixadas por digest. As imagens finais usam JRE 21 e
UID/GID `10001:10001`, sem ferramentas de build ou perfis ativados implicitamente.
O contexto restrito por `.dockerignore` exclui arquivos privados e saídas locais.

Execute os builds na raiz:

```powershell
docker build -f duplicata-escritural-financiamento-api/Dockerfile -t duplicata-escritural-financiamento-api:4.5.1-local .
docker build -f duplicata-escritural-signataria-api/Dockerfile -t duplicata-escritural-signataria-api:4.5.1-local .
```

Veja [decisões, configuração externa, execução em loopback e validação](docs/continuous-delivery.md).
O guia também registra a restrição de HTTP em loopback na integração, a dependência
PostgreSQL, o fluxo de publicação da 4.5.2 e o planejamento da 4.5.3. A 4.5 não está concluída; não há
publicação no GHCR ou deploy nesta etapa.

A **4.5.1 foi aprovada tecnicamente em Code Review**, sem achados bloqueantes.
Foram aprovados 324 testes Gradle e seis PromQL; health, segurança básica e
graceful shutdown foram verificados. Os JARs recompilados tiveram SHA-256
idêntico aos das imagens, que permanecem exclusivamente locais. As ressalvas
sobre CVEs, ARM64, autenticação local e ausência de teste completo do fluxo
financeiro pelos containers permanecem no guia de CD. O registro da aprovação
e o único commit local resultaram em `49c4c51`. O workflow separado da 4.5.2
publicará somente após CI de push aprovado para SHA pertencente à `main`, com
tag por SHA completo, digest registrado e bloqueio de vulnerabilidades críticas.
A 4.5.2 está aprovada tecnicamente em Code Review: nenhuma imagem foi publicada,
o workflow remoto ainda não foi validado e a 4.5.3 continua planejada. Detalhes de
permissões, reexecução, scan e limitações estão no guia de CD. O primeiro scan
detectou quatro CRITICAL em Tomcat 11.0.24 por imagem, além de HIGH em Jackson.
A remediação coordenada com **Spring Boot 4.1.2** confirmou Tomcat 11.0.27 e
Jackson 3.1.7/2.21.7 nos artefatos, mantendo 324 testes Gradle e seis PromQL
aprovados. Os novos scans têm **zero CRITICAL/HIGH**, e a guarda passou nas duas
imagens. Permanecem 47 MEDIUM/13 LOW por imagem, incluindo OpenTelemetry
CVE-2026-118259; isso não equivale a ausência de vulnerabilidades ou aceite de
produção. O aceite técnico inclui 324 testes Gradle, seis PromQL, 23 testes
Python (também aprovados em execução independente pelo responsável), dois builds
Docker e smoke tests aprovados. Um único commit local foi autorizado; publicação
remota e funcionamento real do workflow/GHCR continuam não validados. A avaliação
de OpenTelemetry para eventual implantação permanece pendente. O histórico e os
riscos estão no guia de CD; a Entrega 4.5 não está concluída nem integrada à `main`.

## Integração contínua e contribuições

O workflow [CI](.github/workflows/ci.yml) executa em PRs direcionados à
`main`, pushes em `main` e execução manual. O job **Build e testes** usa Ubuntu
24.04, Temurin 21 e o Gradle Wrapper, executando a suíte completa com Docker e
Testcontainers, sem excluir os testes verticais ou exigir coletor externo.
Relatórios XML/HTML são preservados como artefatos mesmo após falha do build,
quando disponíveis, e os totais JUnit são apresentados no resumo da execução.

O GitHub Flow foi adotado conforme o [guia de contribuição](CONTRIBUTING.md):
feature branches por entrega, PR para `main`, CI, Code Review e aceite técnico
antes do merge. O [ruleset `27240150`](https://github.com/Lildtz/duplicata-escritural-financiamento/rules/27240150)
está ativo na `main`, exigindo PR, check **Build e testes** do GitHub Actions e
branch atualizada, com zero aprovações obrigatórias, sem bypass e com force push
e exclusão bloqueados.

O [PR #1](https://github.com/Lildtz/duplicata-escritural-financiamento/pull/1)
foi integrado por squash em `482b7f5`. O [CI pós-merge na `main`](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/runs/43530852930)
aprovou 277 testes (101 do Financiamento e 176 da Signatária), incluindo os dez
verticais, sem falhas, erros ou ignorados. Os [relatórios JUnit e HTML](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/runs/43530852930/artifacts/14087297417)
foram publicados na execução, com retenção de sete dias.

Foi validada uma execução pós-merge; a estabilidade futura será acompanhada pelas
execuções recorrentes do CI. O PR #3 integrou posteriormente a 4.4 e seu CI
pós-merge aprovou 324 testes Gradle e seis testes PromQL. A 4.5 segue em andamento.

## Evolução do projeto
O desenvolvimento segue entregas incrementais, priorizando funcionalidades verificáveis, testes automatizados e decisões arquiteturais justificadas.

Cada entrega relevante deve manter atualizados o estado dos componentes, as instruções de execução e a documentação técnica.

Consulte o [Roadmap do Projeto](docs/roadmap.md) para acompanhar
as fases planejadas, os entregáveis e o progresso da implementação.

## Referências e limitações

O domínio será fundamentado, sempre que possível, em documentação pública e oficial, incluindo legislação brasileira e materiais do Banco Central, da B3 e da Núclea.

**Este é um projeto independente, educacional e experimental.** Não possui integração real ou vínculo institucional com essas entidades. Interfaces externas serão representadas por mocks e simuladores desenvolvidos com base em informações públicas.
