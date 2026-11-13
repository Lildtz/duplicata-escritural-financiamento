# Execução local da Signatária

Execute os comandos a partir da raiz do repositório `duplicata-escritural-financiamento`.
O nome lógico do projeto Gradle continua `duplicata-escritural-plataforma`.

`duplicata-escritural-signataria-api` é a aplicação existente, anteriormente chamada
`duplicata-escritural-escrituradora-api`. A renomeação prevista no ADR 0003 preserva
o comportamento de bootstrap, Actuator e persistência. Escrituração implementa
recebimento, consulta e processamento assíncrono com criação local de Duplicata.
O agendador está desabilitado por padrão; sua configuração está no guia abaixo.
O [guia de Escrituração Manual](../../docs/escrituracao-manual.md) descreve o contrato,
o cadastro local do Sacador e o provisionamento de tokens sem segredos versionados.
Registro e IUD regulatório continuam planejados. CONCLUIDO representa somente a criação local.
`duplicata-escritural-interoperabilidade-api` é uma aplicação futura.

Inicie o PostgreSQL com Docker disponível:

```powershell
docker compose -f infra/local/docker-compose.yml up -d postgres-escrituradora
.\gradlew.bat :duplicata-escritural-signataria-api:bootRun
```

Sem perfil `local`, as rotas de Escrituração ficam bloqueadas. Essa primeira
inicialização permite aplicar as migrations antes do provisionamento descrito no guia.

A aplicação continua na porta 8081, com health check em
`http://localhost:8081/actuator/health` e exposição de Actuator `health,info`.

Os identificadores de infraestrutura permanecem inalterados:

* banco, usuário padrão e schema: `escrituradora`;
* variáveis: `ESCRITURADORA_DB_HOST`, `ESCRITURADORA_DB_PORT`,
  `ESCRITURADORA_DB_NAME`, `ESCRITURADORA_DB_USERNAME` e `ESCRITURADORA_DB_PASSWORD`;
* serviço Compose: `postgres-escrituradora`;
* volume Compose: `postgres-escrituradora-data`;
* histórico Flyway: `escrituradora.flyway_schema_history`;
* Hibernate: `ddl-auto: validate`, com schema padrão `escrituradora`.

Valores de conexão e credenciais existentes foram preservados. A renomeação não
exige migração física do banco, recriação do serviço ou remoção de volumes.

Para validar o projeto:

```powershell
.\gradlew.bat projects
.\gradlew.bat clean build
```

O teste `SignatariaApplicationTests` utiliza Testcontainers com PostgreSQL
`postgres:18-alpine`, verifica a inicialização do contexto Spring, a conexão,
o histórico Flyway no schema `escrituradora` e executa `flyway.validate()`.
Os testes HTTP também usam PostgreSQL descartável e tokens aleatórios gerados
durante a execução. Eles requerem Docker e não utilizam o banco persistente do Compose.

Reimporte o projeto Gradle no IntelliJ após a renomeação e atualize eventuais
configurações locais de execução para `br.com.duplicataescritural.signataria.SignatariaApplication`.
Os metadados gerados em `.idea/` não devem ser editados ou versionados por esta refatoração.

## Validação vertical Banco DUP–Signatária (Entrega 3.4)

Com Java 21 e Docker disponíveis, execute na raiz:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:test --tests '*FluxoConsultaAutorizadaVerticalTests'
```

O teste gera e inicia o JAR real da Signatária com perfil `local`, porta dinâmica,
PostgreSQL descartável, Flyway e agendador habilitado. O Banco DUP usa seu contexto
Spring e `RestClient`, sem servidor HTTP de entrada. A fixture provisiona apenas
Sacadores e credenciais efêmeras; duplicatas e autorizações são criadas pelos
contratos HTTP reais. Não requer `bootRun`, Compose ou tokens configurados manualmente.
Os dados persistentes locais não são utilizados. Contextos, processo e container
são encerrados ao final; logs temporários são preservados apenas em falha.

O fluxo inclui Escrituração → processamento CONCLUIDO → autorização PENDENTE →
aprovação pelo Sacador → ATIVA → consulta paginada. Há verificações de isolamento,
recusa, revogação entre páginas, expiração, idempotência e paginação inválida.
A suíte também é executada por `.\gradlew.bat clean build`.
Veja [pré-condições, sequência e limitações](../../docs/autorizacao-consulta.md#entrega-34--validação-vertical).
A aprovação técnica final da 3.4 foi registrada em 13/11/2026; o versionamento autorizado está em `27e950e`.

## OpenAPI e Swagger UI (Entrega 4.1)

Com PostgreSQL ativo e o arquivo ignorado de identidades locais provisionado conforme
o [guia de autorização](../../docs/autorizacao-consulta.md#identidades-e-provisionamento-local), inicie:

```powershell
.\gradlew.bat :duplicata-escritural-signataria-api:bootRun "--args=--spring.profiles.active=local --spring.config.additional-location=optional:file:./.env.signataria.local.yml"
```

`bootRun` usa o diretório do subprojeto: nesse comando, o arquivo ignorado de
identidades deve estar em `duplicata-escritural-signataria-api/.env.signataria.local.yml`.
Não versione o arquivo nem credenciais. A URL e o provisionamento PostgreSQL seguem
as configurações anteriores; nenhum cadastro ou token padrão é acrescentado.

Acesse `http://localhost:8081/swagger-ui.html` (redireciona para a UI) e
`http://localhost:8081/v3/api-docs` (JSON gerado). Esses caminhos e os recursos da
UI são públicos somente em `local`. Os caminhos de negócio continuam protegidos.
No **Authorize**, informe apenas o token opaco fictício do participante, sem `Bearer `.
Não há JWT, emissor de tokens ou autorização implícita pelo Swagger. Fora de `local`,
OpenAPI e UI ficam desabilitados e bloqueados pela segurança.

Teste a documentação e o bloqueio sem perfil local, sem depender do Compose:

```powershell
.\gradlew.bat :duplicata-escritural-signataria-api:test --tests '*OpenApiHttpTests' --tests '*SignatariaApplicationTests'
```

Os testes requerem Docker e usam PostgreSQL/Testcontainers, portas dinâmicas e
identidades aleatórias. Consulte [contratos, limites e evidências](../../docs/escrituracao-manual.md#openapi-3-e-swagger-ui--entrega-41).
