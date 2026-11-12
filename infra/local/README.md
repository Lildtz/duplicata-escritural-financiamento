# Execução local da Signatária

Execute os comandos a partir da raiz do repositório `duplicata-escritural-financiamento`.
O nome lógico do projeto Gradle continua `duplicata-escritural-plataforma`.

`duplicata-escritural-signataria-api` é a aplicação existente, anteriormente chamada
`duplicata-escritural-escrituradora-api`. A renomeação prevista no ADR 0003 preserva
o comportamento de bootstrap, Actuator e persistência. A primeira entrega de
Escrituração implementa recebimento e consulta de solicitações, sempre `PENDENTE`.
O [guia de Escrituração Manual](../../docs/escrituracao-manual.md) descreve o contrato,
o cadastro local do Sacador e o provisionamento de tokens sem segredos versionados.
Execução da escrituração, Duplicata e Registro continuam planejados.
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
