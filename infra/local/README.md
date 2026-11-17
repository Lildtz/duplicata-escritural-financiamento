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

## Observabilidade (Entrega 4.2)

Os mesmos comandos de inicialização produzem logs JSON com `service`, `traceId`
e `spanId` quando há contexto. Procure o mesmo trace nas duas aplicações para
acompanhar uma consulta. O worker usa `processamentoId`, sem continuidade do trace
do recebimento. Não é necessário instalar um coletor.

Na Signatária `local`, `/actuator/metrics` e `/actuator/metrics/{nome}` exigem Bearer
de Sacador ou Financiador provisionado. O Banco DUP mantém apenas `health,info`
expostos por HTTP. Sampling, exportação opcional de traces, nomes dos medidores e
comandos de teste estão no [guia de observabilidade](../../docs/observabilidade.md).

## Prometheus — exposição local (4.4.1 aprovada em Code Review)

A 4.4.1 está aprovada tecnicamente e versionada em `a2dde54` na feature branch
`feat/4.4-monitoramento`, ainda não integrada à `main`. A Entrega 4.4 permanece incompleta.

O perfil adicional `monitoramento` mantém as portas 8080/8081, força bind em
`127.0.0.1` e acrescenta somente `GET /actuator/prometheus`, com Basic operacional
independente dos participantes. Use `local,monitoramento` e provisione as
credenciais privadas de cada aplicação conforme o
[guia de observabilidade](../../docs/observabilidade.md#exposição-prometheus-local--entrega-441).
O perfil `local` isolado continua funcionando sem essas credenciais.

O Compose existente permanece somente com PostgreSQL. O Compose complementar
abaixo acrescenta a coleta da 4.4.2, aprovada tecnicamente em Code Review na
feature branch, ainda não integrada à `main`; Grafana pertence à 4.4.3 planejada.

## Coleta Prometheus — Entrega 4.4.2

**Aprovada tecnicamente em Code Review na feature branch, ainda não integrada à `main`.**
Requer Docker Desktop em Linux
containers, Engine disponível e as duas aplicações executadas no Windows com
`local,monitoramento`. O arquivo `docker-compose.monitoramento.yml` é um projeto
Compose separado (`duplicata-monitoramento`): não combine seus arquivos com `-f`
nem use seus comandos para administrar o PostgreSQL.

### Verificar o acesso antes de iniciar a coleta

```powershell
docker context show
docker info --format '{{.OSType}}'
docker version
Get-NetTCPConnection -State Listen -LocalPort 8080,8081 | Select-Object LocalAddress,LocalPort
```

O modo deve ser `linux`; os dois listeners devem ser `127.0.0.1`. Provisione
PostgreSQL, participantes Bearer, usuário/hash operacional e inicie as duas
aplicações conforme os guias anteriores. Valide pelo Windows HTTP 401 sem Basic
e 200 com a credencial própria de cada aplicação, com conteúdo Prometheus válido.
Não envie credenciais na primeira sonda do container:

```powershell
foreach ($porta in 8080,8081) {
  "GET /actuator/prometheus HTTP/1.1`r`nHost: host.docker.internal:$porta`r`nConnection: close`r`n`r`n" |
    docker run --rm -i --read-only --cap-drop ALL --security-opt no-new-privileges --user 65534:65534 --entrypoint /bin/busybox postgres:18-alpine nc -w 5 host.docker.internal $porta
}
```

Esse cliente TCP descartável não usa proxy nem recebe segredos. Cada resposta
deve ser HTTP 401 com `WWW-Authenticate: Basic realm="metricas"`. Confira também
DNS (`busybox nslookup host.docker.internal`), o corpo/caminho e a chegada da
requisição à JVM correta (contador HTTP 401 ou logs privados). `localhost` no
container não é o Windows. Em falha, investigue DNS, TCP, proxy e Docker Desktop;
interrompa a coleta para revisão arquitetural se não houver acesso seguro.
Não amplie o bind, abra firewall, crie proxy na LAN ou desative autenticação.

### Provisionar arquivos privados

O Prometheus precisa da **senha original**, correspondente ao BCrypt externo da
aplicação. Gere pares independentes pelo procedimento da
[4.4.1](../../docs/observabilidade.md#geração-de-credenciais-locais).
Os quatro arquivos abaixo não têm exemplos funcionais nem valores padrão.
Confira a exclusão do Git **antes de criá-los**:

```powershell
git check-ignore infra/local/.env.monitoramento/financiamento.usuario infra/local/.env.monitoramento/financiamento.senha infra/local/.env.monitoramento/signataria.usuario infra/local/.env.monitoramento/signataria.senha
```

Os quatro caminhos devem aparecer na saída (regra existente `.env.*`). Pare se
algum não estiver ignorado. Em sessão PowerShell privada, sem transcrição:

```powershell
$pastaMetricas = Join-Path (Get-Location) 'infra/local/.env.monitoramento'
$destinosMetricas = @(
  $pastaMetricas
  (Join-Path $pastaMetricas 'financiamento.usuario')
  (Join-Path $pastaMetricas 'financiamento.senha')
  (Join-Path $pastaMetricas 'signataria.usuario')
  (Join-Path $pastaMetricas 'signataria.senha')
)
foreach ($destino in $destinosMetricas) {
  if (Test-Path -LiteralPath $destino -ErrorAction Stop) {
    throw 'Provisionamento inicial abortado: pasta ou arquivo privado ja existe. Use o procedimento de rotacao deliberada.'
  }
}
New-Item -ItemType Directory -Path $pastaMetricas -ErrorAction Stop | Out-Null
$identidadeLocal = [Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls $pastaMetricas /inheritance:r /grant:r ($identidadeLocal + ':(OI)(CI)F') 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel restringir a pasta privada' }
foreach ($aplicacao in 'financiamento','signataria') {
  $credencialMetricas = Get-Credential -Message "Credencial operacional de $aplicacao"
  [IO.File]::WriteAllText((Join-Path $pastaMetricas "$aplicacao.usuario"), $credencialMetricas.UserName, [Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText((Join-Path $pastaMetricas "$aplicacao.senha"), $credencialMetricas.GetNetworkCredential().Password, [Text.UTF8Encoding]::new($false))
}
Remove-Variable credencialMetricas
```

O provisionamento inicial aborta antes de criar ou gravar qualquer destino se
a pasta privada ou algum dos quatro arquivos já existir. Não remova essa guarda
para reutilizar o exemplo como rotação.

**Rotação deliberada:** planeje separadamente a atualização da senha do job e do
hash BCrypt externo da respectiva aplicação, preservando ACL e acesso somente
leitura no container. Reinicie a aplicação com o novo hash, confirme o retorno
do target a `UP` e mantenha a outra credencial independente. Não execute o
procedimento inicial sobre um provisionamento existente.

Não use `Get-Content`, `cat` ou inspeção de
ambiente para publicar esses arquivos. Não coloque senhas em comandos, URLs,
logs compartilhados, commits ou relatórios. Esses secrets Compose são arquivos
locais em texto claro, montados somente para leitura; não são um cofre criptografado.

### Validar, iniciar e consultar

```powershell
docker compose -f infra/local/docker-compose.monitoramento.yml config --quiet
docker compose -f infra/local/docker-compose.monitoramento.yml run --rm --no-deps --entrypoint /bin/promtool prometheus check config /etc/prometheus/prometheus.yml
docker compose -f infra/local/docker-compose.monitoramento.yml up -d
curl.exe --noproxy '*' --fail http://127.0.0.1:9090/-/ready
curl.exe --noproxy '*' --fail http://127.0.0.1:9090/api/v1/targets
curl.exe --noproxy '*' --fail --get --data-urlencode 'query=up' http://127.0.0.1:9090/api/v1/query
```

Use `http://127.0.0.1:9090/targets` ou a API: `banco-dup` e `signataria` devem
ter `health=up`, `lastError` vazio e `up=1`. Aguarde até dois intervalos de coleta
após inicialização/recuperação. Não exponha a UI/API do Prometheus à LAN: ela não
possui autenticação neste ambiente local.

| Configuração | Valor |
|---|---|
| Imagem oficial fixa | `prom/prometheus:v3.13.5` |
| Processo | UID/GID `65534:65534`, sem privilégios/capabilities adicionais |
| Publicação no Windows | `127.0.0.1:9090:9090` |
| Rede | Bridge própria `duplicata-monitoramento_monitoramento` |
| Volume de séries | `duplicata-monitoramento_prometheus-data` → `/prometheus` |
| Configuração somente leitura | `infra/local/prometheus/prometheus.yml` → `/etc/prometheus/prometheus.yml` |
| Coleta / timeout | 15s / 5s |
| Jobs / targets | `banco-dup` → `host.docker.internal:8080`; `signataria` → `host.docker.internal:8081` |
| Caminho | `/actuator/prometheus`, sem seguir redirecionamentos |
| Basic por job | `username_file` e `password_file` próprios em `/run/secrets/` |
| Labels estáticos | `aplicacao` e `ambiente=local`, além de `job`/`instance` |

O processo não precisa de root para ler os arquivos; `promtool` deve passar
executado pelo próprio serviço. No Windows, a projeção de permissões de bind
mounts é do Docker Desktop; valide novamente ao trocar de ambiente. O servidor
Prometheus escuta na rede interna do container; somente a publicação no host é
loopback. Isso não torna a rede Docker uma fronteira contra administradores do host.

Para parar temporariamente ou remover somente o container/rede de monitoramento:

```powershell
docker compose -f infra/local/docker-compose.monitoramento.yml stop prometheus
docker compose -f infra/local/docker-compose.monitoramento.yml start prometheus
# Alternativa: remove container/rede, preservando as series no volume
docker compose -f infra/local/docker-compose.monitoramento.yml down
```

Não use `down -v`: isso apaga o histórico de séries. O PostgreSQL e seu volume
não são alterados por esses comandos. Encerre as aplicações em seus terminais
separadamente. O build e os dez testes verticais não dependem deste Compose nem
de arquivos de credenciais operacionais. Consulte
[métricas, testes negativos e limitações](../../docs/observabilidade.md#coleta-prometheus--entrega-442).
