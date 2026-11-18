# Entrega 4.5 — Continuous Delivery

Entrega independente na branch `feat/4.5-cd`, criada da `origin/main` em
`42be1338dce4cf305318779ce7ccf54b5bbd1796`. A 4.4 foi integrada pelo
[PR #3](https://github.com/Lildtz/duplicata-escritural-financiamento/pull/3);
o [CI pós-merge](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/runs/44280078489)
foi aprovado. O baseline informado no encerramento é de 324 testes Gradle e
seis testes PromQL. A **4.5.1 está aprovada tecnicamente em Code Review, sem
achados bloqueantes**, e versionada no commit local `49c4c51cb9dfc12f50830d01167a77678ead53c7`.
As duas imagens foram construídas e validadas localmente; permanecem exclusivamente
locais. A 4.5 continua em andamento, não concluída nem integrada à `main`;
A **4.5.2 está aprovada tecnicamente em Code Review**, incluindo a remediação,
com único commit local autorizado, sem execução remota ou
publicação efetiva. Após remediação com Boot 4.1.2, as duas imagens passaram na
guarda de vulnerabilidades, sem CRITICAL/HIGH detectadas nos novos scans;
MEDIUM/LOW permanecem registradas. A 4.5.3 permanece planejada. Não há autorização
para push, PR, merge, publicação ou deploy.

## Estratégia e limites

| Incremento | Decisão | Estado |
|---|---|---|
| 4.5.1 — Empacotamento | Uma imagem por aplicação, build multi-stage com Wrapper, JRE 21 e usuário não root | Aprovada e versionada localmente em `49c4c51` |
| 4.5.2 — Publicação | GitHub Actions → GHCR, após CI de push aprovado na `main` | Aprovada tecnicamente em Code Review; commit local autorizado; sem publicação |
| 4.5.3 — Implantação | Definir destino, custos e operação antes de provisionar | Somente planejamento |

O problema atual é executar as duas aplicações sem depender do JDK ou de um JAR
preexistente na máquina do operador. Foram consideradas três alternativas:

1. Copiar JARs produzidos no host: Dockerfile menor, mas depende de um build
   anterior e pode empacotar artefato desatualizado.
2. Dockerfiles multi-stage por aplicação: o Wrapper compila dentro de um JDK
   fixado e somente o JAR executável passa para o JRE. **Opção escolhida.**
3. Buildpacks ou Dockerfile parametrizado: adicionam convenções/configuração
   desnecessárias para apenas duas aplicações.

Os dois Dockerfiles curtos repetem o setup comum deliberadamente. Isso mantém os
comandos explícitos, sem script genérico, imagem-base própria ou nova dependência.
O contexto é a raiz do monorepo: `settings.gradle.kts` inclui os dois subprojetos,
por isso ambos os build scripts são copiados. Cada imagem copia e compila somente
as fontes de produção da aplicação correspondente.

O checkout Windows pode converter o `gradlew` para CRLF. O build normaliza
somente essa cópia interna para LF antes de chamar `sh ./gradlew`, sem alterar
o Wrapper versionado ou depender de seu bit executável no host.

As tarefas verificadas são `:duplicata-escritural-financiamento-api:bootJar` e
`:duplicata-escritural-signataria-api:bootJar`. Com a versão atual dos subprojetos,
`0.0.1-SNAPSHOT`, produzem respectivamente
`duplicata-escritural-financiamento-api-0.0.1-SNAPSHOT.jar` e
`duplicata-escritural-signataria-api-0.0.1-SNAPSHOT.jar`. O Dockerfile seleciona o
único JAR de `build/libs` gerado por `bootJar` e o renomeia para `/app/app.jar`;
não depende da versão no nome. `bootJar` não executa testes. O CI obrigatório
**Build e testes**, incluindo os verticais e PromQL, continua sendo necessário.

## Bases, repetibilidade e segurança

As bases oficiais Eclipse Temurin são `21.0.13_9-jdk-noble` no build e
`21.0.13_9-jre-noble` no runtime, ambas fixadas por digest de índice OCI nos
Dockerfiles. Java permanece na versão principal 21. A 4.5.1 foi validada com
Spring Boot 4.1.1; a remediação da 4.5.2 atualiza somente a declaração central
para 4.1.2. Dependency management 1.1.7 e Wrapper Gradle 9.8.0 permanecem inalterados.

O build não usa Gradle global, cache/JAR do host ou instalações `apt` variáveis.
As versões de dependências continuam controladas pelos build scripts e BOMs
existentes. Os arquivos de archive são reproduzíveis por padrão no Gradle 9.
Não há dependency locking/verificação de todos os checksums no projeto; também
não se promete igualdade de digest da imagem OCI entre builds (metadados e
proveniência podem variar). O teste de repetibilidade compara o SHA-256 dos JARs.
Builds sem cache precisam de rede para registry, distribuição Gradle e Maven.

O `.dockerignore` admite somente scripts do build, Wrapper e `src/main` das duas
aplicações; exclui `.git`, `.agents`, `.codex`, `.idea`, `.gradle`, `build`, testes,
relatórios, infraestrutura, `.env*` e arquivos privados. Há exclusões adicionais
para arquivos ocultos, `secrets` e chaves/keystores entre as fontes. Isso não
substitui revisar fontes: um segredo escrito em Java/YAML de produção entraria
no JAR. A configuração pública preexistente contém defaults de banco local;
eles não são credenciais de ambiente e devem ser substituídos no runtime.

A inspeção do contexto deve considerar a semântica de reinclusão do Docker:
permitir um diretório pode permitir seus descendentes. As exclusões intermediárias
dos subprojetos e de `src` mantêm somente `src/main` e os build scripts permitidos.

A imagem final acrescenta apenas o JAR ao JRE e um usuário/grupo `app` de UID/GID
`10001:10001`. O JAR é somente leitura. Não há JDK, Wrapper, fontes, testes ou
cache Gradle no runtime. O entrypoint em formato exec executa Java como PID 1,
recebendo SIGTERM diretamente. Não há shell intermediário ou perfil implícito.
O JRE Ubuntu inclui utilitários de sistema; não é uma imagem distroless.
Atualizar digests exige decisão explícita e nova validação de segurança.

Referências: [Docker: multi-stage e fixação de bases](https://docs.docker.com/build/building/best-practices/),
[Gradle: archives reproduzíveis](https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives).

## Build e execução local

Execute na raiz, com Docker Desktop em modo Linux:

```powershell
docker build --progress=plain -f duplicata-escritural-financiamento-api/Dockerfile -t duplicata-escritural-financiamento-api:4.5.1-local .
docker build --progress=plain -f duplicata-escritural-signataria-api/Dockerfile -t duplicata-escritural-signataria-api:4.5.1-local .
```

As tags acima são exclusivamente locais. Nenhuma imagem é publicada nesta etapa.
`EXPOSE` documenta 8080/8081; quem publica portas é o operador. Os exemplos usam
somente `127.0.0.1` no host e portas alternativas para coexistir com as JVMs locais.
O bind interno padrão da aplicação permite o encaminhamento Docker; não sobrescreva
a segurança da 4.4 para habilitar monitoramento em containers.

Banco DUP sem perfil: inicia bootstrap/Actuator, sem integração ou token:

```powershell
docker run -d --name dup-451-banco -p 127.0.0.1:18080:8080 duplicata-escritural-financiamento-api:4.5.1-local
curl.exe --fail http://127.0.0.1:18080/actuator/health
```

Signatária sem perfil: requer PostgreSQL e aplica as migrations existentes;
as APIs de negócio ficam bloqueadas. Use o PostgreSQL local já provisionado
conforme o [guia local](../infra/local/README.md), sem remover volumes.
No Docker Desktop, `localhost` dentro do container é o próprio container;
para banco publicado no host, utilize `host.docker.internal` e a porta real.
Defina privadamente `ESCRITURADORA_DB_USERNAME` e `ESCRITURADORA_DB_PASSWORD`
no ambiente do terminal, além do host, porta e nome do banco escolhido:

```powershell
$env:ESCRITURADORA_DB_HOST = 'host.docker.internal'
# Defina ESCRITURADORA_DB_PORT e ESCRITURADORA_DB_NAME conforme seu banco.
# Usuário e senha devem estar no ambiente privado, sem valores em comandos.
docker run -d --name dup-451-signataria -p 127.0.0.1:18081:8081 --env ESCRITURADORA_DB_HOST --env ESCRITURADORA_DB_PORT --env ESCRITURADORA_DB_NAME --env ESCRITURADORA_DB_USERNAME --env ESCRITURADORA_DB_PASSWORD duplicata-escritural-signataria-api:4.5.1-local
curl.exe --fail http://127.0.0.1:18081/actuator/health
```

O health pode demorar enquanto a JVM inicializa. Aguarde HTTP 200 e JSON
`status=UP`; não considere apenas `docker run` como sucesso. O health da
Signatária depende do banco; o do Banco DUP não prova conexão à Signatária.
Não se acrescentou `HEALTHCHECK` à imagem: a sonda HTTP do ambiente deve consultar
`/actuator/health`, sem instalar ferramenta adicional apenas para essa função.

Em ambos, confira `/actuator/prometheus`, `/actuator/env`, `/actuator/configprops`
e `/actuator/metrics`: não devem retornar 200 sem autorização. Na Signatária sem
perfil, `/v3/api-docs` e `/api/v1/autorizacoes-consulta` também ficam bloqueados.

```powershell
docker image inspect duplicata-escritural-financiamento-api:4.5.1-local --format '{{.Config.User}} {{json .Config.Entrypoint}}'
docker run --rm --network none --entrypoint id duplicata-escritural-financiamento-api:4.5.1-local
docker run --rm --network none --entrypoint java duplicata-escritural-signataria-api:4.5.1-local -version
docker stop --time 30 dup-451-banco dup-451-signataria
docker inspect dup-451-banco dup-451-signataria --format '{{.Name}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}'
docker rm dup-451-banco dup-451-signataria
```

Confirme também os logs de graceful shutdown; exit 137 ou timeout exige
investigação. Remova somente os containers da prova, após encerrados, sem `-v`.
Não use `down -v` nem prune. Credenciais em variáveis são visíveis a quem controla
o daemon Docker; isto é uma prova local em host confiável.

## Perfil local e integração

Nenhum Dockerfile ativa `local` ou `monitoramento`. Quando necessário, passe
`SPRING_PROFILES_ACTIVE=local` em runtime. Na Signatária, monte o arquivo privado
de hashes/identidades conforme o [provisionamento existente](autorizacao-consulta.md#identidades-e-provisionamento-local)
em modo somente leitura, legível pelo UID 10001; aponte
`SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/identidades.yml` para o destino
real do mount. Nunca copie esse arquivo para a imagem. O agendador continua
desabilitado; sua habilitação exige configuração explícita já documentada.

No Banco DUP em `local`, passe `SIGNATARIA_TOKEN` por ambiente e configure
`SIGNATARIA_URL_BASE`, `SIGNATARIA_TIMEOUT_CONEXAO` (default `2s`) e
`SIGNATARIA_TIMEOUT_LEITURA` (default `5s`). O token deve corresponder ao hash e
Financiador provisionados na Signatária. Configuração ausente/inválida impede
o startup, como antes. Não se adicionou nova credencial padrão.

A validação existente de `ConexaoSignataria` exige HTTPS, exceto HTTP em
`localhost`, `127.0.0.1` ou `[::1]`. Logo, `http://signataria:8081` e
`http://host.docker.internal:8081` são inválidos. Não altere essa fronteira para
conectar containers. Uma prova local pode compartilhar o namespace de rede da
Signatária via `--network container:dup-451-signataria`, usando
`SIGNATARIA_URL_BASE=http://127.0.0.1:8081`. Nesse caso, publique **ambas** as portas
loopback ao criar o container da Signatária e não use `-p` no Banco DUP.
Essa topologia é somente para teste local; implantação com redes distintas
precisará de HTTPS e configuração explícita.

O perfil `monitoramento` continua restrito a `local` e `server.address=127.0.0.1`.
Sua coleta em containers exigiria outra avaliação de rede/segurança. O Compose
de monitoramento da 4.4 permanece inalterado e não integra esta prova.

## Validação local da 4.5.1 — 18/11/2026

**Aprovação técnica em Code Review: sem achados bloqueantes.** O aceite preserva
as evidências abaixo: duas imagens construídas e validadas localmente, 324 testes
Gradle e seis PromQL aprovados, health, segurança básica, graceful shutdown e
SHA-256 idêntico dos JARs recompilados. Somente texto de documentação foi ajustado
para registrar a aprovação; builds e testes não foram repetidos. As ressalvas
sobre CVEs, ARM64, autenticação local e ausência de teste completo do fluxo
financeiro pelos containers permanecem válidas.

Execução real no Docker Desktop 4.91.0, Engine 29.8.0, Linux/amd64:

- Os dois comandos `docker build --progress=plain` documentados acima passaram.
  A etapa Gradle sem cache levou **5min53s** no Banco DUP e **6min36s** na
  Signatária, além de download/exportação das bases. Nova execução com o contexto
  final restrito reutilizou as camadas e passou para ambas.
- A tentativa inicial identificou CRLF no Wrapper do checkout Windows. A
  normalização interna para LF corrigiu os dois builds; não se alterou o Wrapper.
- `docker image inspect`, `docker image history` e execução com entrypoints
  `id`, `sh` e `java -version` confirmaram UID/GID 10001, JRE **21.0.13+9**,
  entrypoint Java direto, JAR somente leitura e ausência de `javac`, Gradle,
  `/workspace` e cache Gradle no runtime. Nenhum perfil, token ou variável de
  banco foi incorporado ao ambiente/histórico das imagens.
- Exportação do contexto com `FROM scratch`/`COPY . /` e inspeção dos caminhos:
  **77 arquivos permitidos**, sem Git, arquivos privados, testes ou relatórios.
  Os dois JARs finais foram extraídos e inspecionados: 252/351 entradas,
  manifests Spring Boot 4.1.1 e nenhuma entrada privada/de build/testes detectada.
  A revisão não detectou segredos privados no contexto; os defaults públicos de
  desenvolvimento preexistentes continuam no `application.yml` original.
- Sem perfil explícito, health de ambas em HTTP 200/`UP`. Banco DUP retornou
  404 para `prometheus`, `env`, `configprops` e `metrics`; Signatária retornou
  401 para esses caminhos, OpenAPI e a rota de autorizações consultada sem token.
- Com `local`, as duas imagens iniciaram com health 200/`UP`; a Signatária
  retornou OpenAPI 200, metrics sem Bearer 401 e com Bearer efêmero 200.
  Prometheus permaneceu bloqueado: 401 na Signatária e 404 no Banco DUP.
  O Banco DUP iniciou com token externo e URL HTTP em loopback, compartilhando
  somente para essa prova o namespace de rede da Signatária. Não se executou um
  novo fluxo financeiro pelos containers; a suíte vertical existente passou.
- As portas da prova foram publicadas apenas em `127.0.0.1:18080/18081/18082`.
  O PostgreSQL já ativo `duplicata-442-prova-398ea737`, publicado em loopback
  na porta 15442, recebeu somente um banco isolado da prova. Quatro migrations
  ficaram registradas com sucesso. Esse banco foi removido após os testes;
  nenhum dado anterior ou volume foi removido/alterado pela prova.
- As quatro JVMs (duas aplicações × default/local) encerraram via
  `docker stop --time 30` com **graceful shutdown completo, exit 143 e sem OOM**.
  Exit 143 corresponde ao SIGTERM recebido, sem encerramento forçado por timeout.
  Containers da prova removidos; PostgreSQL, Prometheus e Grafana preexistentes
  permaneceram ativos.
- O `clean build` padrão encontrou o JAR da Signatária bloqueado pela aplicação
  local existente. Sem interrompê-la, repetiu-se o build com um init script
  temporário que direciona `layout.buildDirectory` de cada projeto para
  `.gradle/entrega451-verificacao/<nome do projeto>`. Comando:
  `.\gradlew.bat -I .gradle/entrega451-verificacao.init.gradle clean build --no-daemon --console=plain`.
  Resultado: **4min05s, 324/324 testes** (125 Banco DUP, 199 Signatária),
  incluindo dez verticais, zero falhas, erros ou ignorados. Os **seis testes
  PromQL** passaram novamente em `promtool` 3.13.5, sem rede ou secrets.
- Recompilação limpa dentro dos estágios de build, com `--network none` e
  `clean bootJar --offline --no-daemon --console=plain`: **59s/1min02s**,
  comparação byte a byte via `cmp` aprovada nas duas aplicações. Os hashes
  coincidiram com os JARs extraídos das imagens finais:

| Aplicação | SHA-256 do JAR |
|---|---|
| Banco DUP | `7706eab573e47cb19d3f65eb65b2eadcb88b80cbd12b5197dde59e5a6029b03f` |
| Signatária | `6fd9d49e95a8997e37da9b22967aecc962360e5aeb701ae08e3f4ac4ff6b055a` |

Não foram executados scan de CVEs, build ARM64, publicação, deploy ou rollback
remoto. A prova de health/configuração não substitui validação de produção.
As imagens continuam sujeitas às dependências e à autenticação local existentes.

## 4.5.2 — Publicação aprovada tecnicamente em Code Review

O workflow [publicar-imagens.yml](../.github/workflows/publicar-imagens.yml)
é separado do CI. O job obrigatório `build-test`, nome **Build e testes**, e seu
workflow `CI` permanecem integralmente preservados. A alternativa de adicionar
publicação via `needs` no CI reduziria a validação entre workflows, mas alteraria
o pipeline aprovado e misturaria permissões de publicação com sua manutenção.
O workflow separado mantém essa responsabilidade isolada, ao custo de validar
explicitamente evento, origem e ancestralidade. Não há deploy.

### Autorização e permissões

O único gatilho é `workflow_run`, conclusão do workflow `CI`, filtrado por
`main`. Além do filtro, o job de autorização exige sucesso, evento original
`push`, branch `main` e `head_repository.full_name` exatamente
`Lildtz/duplicata-escritural-financiamento`. Nenhum PR, fork ou execução manual
do CI autoriza publicação. Não há `pull_request_target` nem gatilho manual de CD.

O job sem escrita obtém somente código da `main` confiável, sem persistir
credenciais, e executa os testes das guardas. O script
[publicacao.py](../.github/scripts/publicacao.py) revalida repositório executor,
repositório do evento, ação/status concluídos, workflow `CI` e caminho
`.github/workflows/ci.yml`, origem, branch e SHA hexadecimal completo. Em seguida,
`git merge-base --is-ancestor <SHA> refs/remotes/origin/main`, com histórico
completo, rejeita SHAs inexistentes ou fora do histórico da `main`. Um nome de
branch isolado não concede confiança.

Somente o job dependente, em matriz para duas aplicações, recebe
`contents: read` e `packages: write`. Ele faz checkout do **SHA exato aprovado**,
revalida evento/ancestralidade e confere HEAD antes de autenticar. O workflow
como um todo tem apenas `contents: read`; não há PAT, `id-token: write`,
permissão de escrita no Git ou alteração de rulesets. Checkout e upload-artifact
estão fixados pelos mesmos SHAs completos/versionados já usados no CI.

O `GITHUB_TOKEN` efêmero é enviado ao `docker login` por stdin e ao cliente de
consulta por variável, nunca por argumento, build arg, label ou artefato.
O login fica em diretório temporário próprio e há logout mesmo em falha.
O código não imprime respostas de autenticação; redirecionamentos HTTP são
rejeitados para não encaminhar Authorization a outro destino. Dados externos
não são interpolados em comandos shell; os comandos Git/Docker do script usam
listas de argumentos, sem `shell=True`.

Não há download/execução de artefatos do CI anterior nem restauração de caches
de PR. Build e análise partem do checkout autorizado, em runners efêmeros Ubuntu
24.04. A concorrência é serializada por SHA entre execuções deste workflow,
com `cancel-in-progress: false`; os jobs de aplicações distintas podem executar
em paralelo. A confiança continua dependendo da proteção/revisão da `main` e
dos mantenedores que podem alterar o workflow.

### Imagens, tags e labels

| Aplicação | Nome da imagem |
|---|---|
| Banco DUP | `ghcr.io/lildtz/duplicata-escritural-financiamento-api` |
| Signatária | `ghcr.io/lildtz/duplicata-escritural-signataria-api` |

Única tag: `sha-<SHA Git completo de 40 caracteres>`. Não há `latest`, tag de
branch ou tag semântica automática. Releases exigirão estratégia própria futura.
O SHA identifica o código; o **digest OCI retornado e verificado no GHCR**
identifica o manifesto efetivamente publicado.

O build usa `docker buildx build --platform linux/amd64 --load` com os Dockerfiles
e o contexto restrito aprovados, sem alterações na 4.5.1. Somente essa plataforma
é suportada. `--provenance=false --sbom=false` mantém um manifesto simples de
uma plataforma; não se implementaram assinatura, attestation ou SBOM publicado.
Isso não impede inspecionar as dependências do JAR no scan.

Labels externas ao Dockerfile: `org.opencontainers.image.source` com a URL do
repositório, `org.opencontainers.image.revision` com o SHA e
`org.opencontainers.image.created` com a data do commit em RFC 3339. A data do
commit é uma referência estável de criação da versão, não o horário efetivo do
build. Plataforma, labels e usuário `10001:10001` são verificados antes do scan.
Configuração, tokens e profiles continuam externos à imagem.

### Reexecução idempotente e recuperação parcial

Antes do build, uma consulta autenticada à API de distribuição do GHCR verifica
a tag. **Somente HTTP 404 com NAME_UNKNOWN/MANIFEST_UNKNOWN significa ausência.**
Erros de autenticação, permissão, timeout, rate limit ou respostas incompatíveis
falham fechados. O corpo do manifesto é conferido contra Docker-Content-Digest.

Se a tag existe, baixa-se **por digest** e verifica-se plataforma, usuário e
labels de origem/revisão/data; depois, o mesmo artefato é analisado novamente.
Não se recompila nem se sobrescreve a tag. Labels/plataforma incompatíveis
interrompem a execução, exigindo investigação. Labels não constituem assinatura
nem prova criptográfica de autoria; pressupõem controle dos escritores do pacote.

Se ausente, o fluxo constrói e analisa a imagem. Imediatamente antes do push,
consulta a tag de novo e aborta caso tenha aparecido. Após o push, compara o
digest de configuração remoto com o hash da configuração contida no archive
exato enviado ao scanner. Uma nova consulta confirma o digest antes de registrá-lo.
Rebuilds futuros podem produzir digests diferentes; a tag de SHA existente
permanece vinculada ao artefato original, sem atualização silenciosa de bases.

Os dois pacotes não formam uma transação. Se somente uma imagem for publicada,
reexecutar o workflow do mesmo CI/SHA verifica e reutiliza aquela imagem e tenta
publicar somente a ausente. O mesmo vale para timeout após push aceito ou falha
no registro de evidência. Um erro transitório não apaga tags ou pacotes.

Limite: GHCR não oferece nesta implementação criação condicional atômica de
tag. A concorrência protege execuções deste workflow, mas outro escritor com
privilégios pode disputar o intervalo consulta/push. As tags `sha-*` devem ser
escritas exclusivamente por este fluxo; não se promete imutabilidade contra
administradores ou outros processos. Remediações de uma imagem já publicada
exigem um novo commit e nova tag, nunca mover a anterior.

Por imagem, o resumo e o JSON `*-publicacao.json` registram nome, SHA, tag,
plataforma, digest e URL da execução responsável. Relatório de vulnerabilidades
e registro são preservados por sete dias, sem credenciais. Falha anterior ao
registro pode deixar apenas o relatório; o digest da tag é recuperado na
reexecução. A primeira publicação pode criar pacote privado conforme o padrão
do GitHub; este workflow não altera visibilidade ou permissões do registry.

### Vulnerabilidades e atualização das bases

Trivy **0.76.2**, ferramenta dedicada necessária para inspecionar SO e bibliotecas
Java dentro do bootJar, executa em container fixado por digest. Não se adicionou
dependência Java ou serviço permanente. O scanner recebe somente um archive
somente leitura e um diretório de relatório/cache temporário, sem socket Docker,
token ou configuração de login. Seu custo inclui download da imagem e dos bancos
de vulnerabilidades/identificação Java; os bancos são atualizados no runner,
sem cache compartilhado com PRs.

O JSON mantém todas as severidades, versões instaladas e correções conhecidas.
A guarda exige resultados de SO e dependências do runtime Java. Qualquer
**CRITICAL bloqueia publicação**, inclusive sem correção disponível; falha ou
relatório incompleto também bloqueia. HIGH e demais severidades são reportadas
para revisão; isso não equivale à aprovação automática de risco de produção.
Não há lista de exceções ou ignore de CVEs introduzido nesta etapa.

Os digests Temurin aprovados permanecem fixos. Avaliar novos boletins/releases
e os relatórios antes de publicação/promoção; para atualizar bases, propor novo
commit revisado com novos digests, rebuild, scan e smoke tests. Não se atualiza
base silenciosamente nem se relaxa a guarda para viabilizar publicação.

### Validação sem publicação

```powershell
python -B -m unittest discover -s .github/scripts -p 'test_*.py' -v
docker run --rm --network none --mount "type=bind,source=${PWD},target=/repo,readonly" -w /repo rhysd/actionlint:1.7.12 -color
git diff --check
```

Os testes usam um repositório Git temporário para ancestralidade e respostas
simuladas de registry/Docker para os demais cenários. Não enviam requisições
ao GHCR nem executam push real. `actionlint` valida YAML, expressões e shell.
Para repetir o build sem publicação, copie o comando `docker buildx build` do
workflow, defina APP/SOURCE_SHA/CREATED/IMAGE no ambiente e mantenha `--load`.
Tag local com prefixo `ghcr.io` não significa publicação.

O workflow só passa a receber `workflow_run` quando existir na branch padrão.
A execução remota após integração, autenticação real, primeira criação dos
pacotes e digests retornados pelo GHCR ainda precisam de validação autorizada.
Não há publicação nem aprovação remota comprovada nesta execução local.

### Evidências locais da 4.5.2 — execução 1, antes da remediação, 18/11/2026

Este registro preserva a primeira execução e o bloqueio então identificado.
Os resultados posteriores da remediação estão na execução 2 abaixo.

Branch `feat/4.5-cd`, HEAD preservado em `49c4c51cb9dfc12f50830d01167a77678ead53c7`,
base `42be1338`. Índice/árvore inicialmente limpos. Nenhum commit, push, PR,
pacote GHCR ou deploy foi criado. Dockerfiles, `.dockerignore`, CI obrigatório,
fontes, Gradle e Composes permanecem idênticos ao commit aprovado.

- YAML, expressões e shell dos dois workflows aprovados no **actionlint 1.7.12**.
- **20 testes Python aprovados**, incluindo sucesso de CI de push confiável,
  ancestral antigo da `main`, e rejeições de PR, falha/cancelamento/skipped,
  fork, outro workflow, outro gatilho, branch não confiável, SHA inválido,
  inexistente ou fora da `main`. Há testes de timeout/401/403/429/500, 404
  conhecido, digest corrompido, plataforma/labels/usuário incompatíveis,
  tag concorrente, reuso sem push e recuperação após resposta de push perdida.
- Os builds locais usaram o mesmo comando Buildx do workflow com `--load`,
  `linux/amd64`, tag `sha-49c4c51cb9dfc12f50830d01167a77678ead53c7` e labels OCI.
  Ambos passaram reutilizando as camadas de build já validadas na 4.5.1;
  plataforma, usuário e labels foram conferidos com o próprio script do fluxo.
  Os manifestos **locais**, não digests retornados pelo GHCR, foram
  `sha256:4789991873ea47e9bb6952400bc02d2fb7bd31d93051286c18ada74514beaf4b`
  e `sha256:10483c4b884c01ea4b976e9a5fdc26d0a34c63e979c5aaf2b1068f2aacf56307`.
- O campo `containerimage.config.digest` não apareceu nos metadados locais do
  Buildx 0.37.0. Por isso, a conferência pós-push usa o hash do blob de configuração
  dentro de `docker image save`, lido sem extração. O formato foi validado com
  archive real e fixture; não depende desse campo opcional do Buildx.
- Trivy 0.76.2 analisou os archives das duas imagens, detectando Ubuntu 24.04,
  136 pacotes do SO e as bibliotecas JAR. DB de CVEs atualizada em
  `2026-11-18T07:15:21Z`; DB Java em `2026-11-18T00:58:01Z`. O primeiro scan
  excedeu o timeout padrão durante download/preparação; repetição com **15min**
  passou, e esse timeout está no workflow. Download inicial: aproximadamente
  134 MiB para CVEs e 955 MiB para identificação Java. Não há cache de PR.
- Não se repetiu a suíte Gradle: aplicações, dependências e build scripts não
  mudaram. Permanecem as evidências de 324 testes Gradle, seis PromQL e smoke
  tests da 4.5.1. Esta etapa valida o pipeline localmente, sem atestar execução
  do workflow no GitHub ou comportamento real de autenticação/push no GHCR.

**Resultado de segurança: publicação bloqueada até avaliação dos achados.**
A guarda real retornou **exit 1 para ambas as imagens**, sem executar publicação.

| Imagem | CRITICAL | HIGH | MEDIUM | LOW |
|---|---:|---:|---:|---:|
| Banco DUP | 4 | 5 | 49 | 13 |
| Signatária | 4 | 10 | 51 | 13 |

Em ambas, os pacotes do SO contabilizaram 46 MEDIUM e 13 LOW, sem HIGH/CRITICAL
detectados nesse recorte. Isso não prova ausência geral de CVEs na base/JRE:
o scan depende de catálogo/identificação de pacotes e não substitui boletins
Temurin, triagem de aplicabilidade ou revisão de configurações.

Achados CRITICAL/HIGH do runtime (versões corrigidas indicadas pelo scanner na
mesma linha; não são uma recomendação de atualização automática do BOM):

| CVE | Severidade Trivy | Componente / versão instalada | Correção indicada | Imagens |
|---|---|---|---|---|
| CVE-2026-65182 | CRITICAL | tomcat-embed-core 11.0.24 | 11.0.25 | Ambas |
| CVE-2026-65637 | CRITICAL | tomcat-embed-core 11.0.24 | 11.0.25 | Ambas |
| CVE-2026-65905 | CRITICAL | tomcat-embed-core 11.0.24 | 11.0.25 | Ambas |
| CVE-2026-68525 | CRITICAL | tomcat-embed-core 11.0.24 | 11.0.25 | Ambas |
| CVE-2026-89407 | HIGH | tools.jackson.core:jackson-core 3.1.5 | 3.1.7 | Ambas |
| CVE-2026-89425 | HIGH | tools.jackson.core:jackson-core 3.1.5 | 3.1.7 | Ambas |
| CVE-2026-68497 | HIGH | tools.jackson.core:jackson-databind 3.1.5 | 3.1.6 | Ambas |
| CVE-2026-91776 | HIGH | tools.jackson.core:jackson-databind 3.1.5 | 3.1.7 | Ambas |
| CVE-2026-91777 | HIGH | tools.jackson.core:jackson-databind 3.1.5 | 3.1.7 | Ambas |
| CVE-2026-89407 | HIGH | com.fasterxml.jackson.core:jackson-core 2.21.5 | 2.21.7 | Signatária |
| CVE-2026-89425 | HIGH | com.fasterxml.jackson.core:jackson-core 2.21.5 | 2.21.7 | Signatária |
| CVE-2026-68497 | HIGH | com.fasterxml.jackson.core:jackson-databind 2.21.5 | 2.21.6 | Signatária |
| CVE-2026-91776 | HIGH | com.fasterxml.jackson.core:jackson-databind 2.21.5 | 2.21.7 | Signatária |
| CVE-2026-91777 | HIGH | com.fasterxml.jackson.core:jackson-databind 2.21.5 | 2.21.7 | Signatária |

O [boletim Apache Tomcat](https://tomcat.apache.org/security-11.html) confirma os
quatro CVEs e a correção em 11.0.25, com severidades próprias distintas:
Important para 65182, Moderate para 65637, Low para 65905/68525. Os cenários
envolvem constraints do container, HTTP/2/SNI, DIGEST e FORM, respectivamente.
Isso exige triagem de aplicabilidade; não se afirmou exploração nesta aplicação
e não se criou exceção à guarda CRITICAL. Os mantenedores também publicam os
advisories [Jackson core](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-p6pp-m3f8-5c89)
e [databind](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-WV8Q-QHHJ-9H54).

Os JSONs completos, incluindo MEDIUM/LOW, versões afetadas e correções, ficaram
em `.gradle/cd452/analise/*-vulnerabilidades.json`, ignorados pelo Git. Somente o
resumo dos achados é documentação versionável. Não houve atualização incidental
de Spring Boot/Tomcat/Jackson ou das bases; avaliar correção compatível em mudança
deliberada e repetir o scan antes de aprovar publicação.

Referências de segurança do desenho: [workflow_run](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run),
[uso seguro de Actions](https://docs.github.com/en/actions/reference/security/secure-use),
[GHCR](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
e [API de distribuição](https://distribution.github.io/distribution/spec/api/).

### Remediação — execução 2, 18/11/2026

**Resultado local: CRITICAL e HIGH eliminadas dos dois relatórios; guarda
aprovada, incremento ainda em revisão.** Nenhum commit ou publicação remota.
Os arquivos/imagens/relatórios da execução 1 foram preservados. No início desta
execução, os três arquivos novos do pipeline já estavam staged com o conteúdo
revisado; esse índice foi preservado, sem adicionar as alterações de remediação.

O [anúncio oficial de 23/10/2026](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/)
e as [notas do Boot 4.1.2](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2)
confirmam Tomcat 11.0.27, Jackson BOM 3.1.7 e Jackson 2 BOM 2.21.7.
A [compatibilidade oficial](https://docs.spring.io/spring-boot/system-requirements.html)
inclui Java 21 e Gradle 9. Preferiu-se a atualização coordenada do BOM a overrides
individuais. A única alteração declarativa de dependências é a versão do plugin
Boot no `build.gradle.kts` raiz, de 4.1.1 para 4.1.2. As atualizações transitivas
coordenadas pelo BOM fazem parte desse patch; não se editaram outras versões,
declarações de dependência, configurações, APIs ou regras de domínio.

| Componente | Antes | Resolvido e incorporado após a atualização |
|---|---|---|
| Spring Boot | 4.1.1 | 4.1.2 nas duas aplicações |
| tomcat-embed-core | 11.0.24 | 11.0.27 nas duas aplicações |
| tools.jackson.core:jackson-core/databind | 3.1.5 | 3.1.7 nas duas aplicações |
| com.fasterxml.jackson.core:jackson-core/databind | 2.21.5 | 2.21.7 na Signatária |

Os `dependencyInsight --configuration runtimeClasspath --dependency tomcat-embed-core`
e `--dependency jackson` foram executados em cada subprojeto. Depois, foram
inspecionados os manifests e `BOOT-INF/lib` dos bootJars do build Gradle e das
imagens finais. A tabela reflete os artefatos efetivos, sem depender apenas do
texto da configuração. Os JARs do Banco DUP não incluem Jackson 2.

Regressão completa:

```powershell
.\gradlew.bat -I .gradle/cd452-remediacao/verificacao.init.gradle clean build --no-daemon --console=plain
```

O init script temporário isola saídas em `.gradle/cd452-remediacao/build/<projeto>`,
preservando os JARs em uso e os resultados anteriores. **BUILD SUCCESSFUL em
12min24s: 324/324 testes**, 125 do Banco DUP e 199 da Signatária, zero falhas,
erros ou ignorados. Inclui os dez verticais, HTTP de segurança/autenticação,
observabilidade, persistência e migrations. Quantidade Java inalterada.
Houve avisos CDS e de conexões JDBC já fechadas no encerramento dos contextos
com bancos descartáveis; não houve falhas de teste. Os **seis testes PromQL**
e o **actionlint 1.7.12** também passaram. O CI obrigatório não foi modificado.

As imagens foram reconstruídas com os Dockerfiles e bases fixadas originais,
`--platform linux/amd64 --load --provenance=false --sbom=false`, em tags locais
`:4.5.2-remediacao`. A label revision usa o HEAD com sufixo `-dirty`, pois a
remediação ainda não foi versionada; esses artefatos não são apresentados como
imagens de um SHA aprovado na `main`. Etapas Gradle dos builds frios:
**9min16s / 10min21s**. Nenhuma imagem aprovada foi sobrescrita ou excluída.

As duas imagens confirmaram Temurin **21.0.13+9**, UID/GID **10001:10001**,
linux/amd64, JAR somente leitura, sem JDK/Gradle ou credenciais no ambiente da
imagem. O contexto exportado manteve **77 arquivos permitidos**, sem privados.
Quatro startups, duas aplicações × perfis default/local, retornaram health
HTTP 200/UP. Endpoints sensíveis permaneceram bloqueados; metrics local da
Signatária exigiu Bearer e OpenAPI local retornou 200. Quatro SIGTERMs tiveram
graceful shutdown completo, exit 143 e OOM=false. Somente portas loopback;
PostgreSQL existente com banco isolado da prova, removido ao final, sem remover
volumes ou dados anteriores. Não se repetiu fluxo financeiro completo pelos
containers; os dez testes verticais exercitaram o fluxo real em JVMs separadas.

Trivy **0.76.2**, mesmo digest e bancos válidos de 18/11 da execução anterior,
analisou os archives completos das duas imagens. A guarda retornou **exit 0**
em ambas, com inventários Java de **89 / 135 pacotes**. Nenhum ignore de CVE,
`--ignore-unfixed`, filtro de severidade ou exceção foi introduzido.

| Imagem / execução | CRITICAL | HIGH | MEDIUM | LOW |
|---|---:|---:|---:|---:|
| Banco DUP — antes, 11:12 UTC | 4 | 5 | 49 | 13 |
| Banco DUP — depois, 12:28 UTC | 0 | 0 | 47 | 13 |
| Signatária — antes, 11:13 UTC | 4 | 10 | 51 | 13 |
| Signatária — depois, 12:29 UTC | 0 | 0 | 47 | 13 |

Os nove IDs distintos CRITICAL/HIGH listados na execução 1 não aparecem nos
novos relatórios. Não há outras CRITICAL ou HIGH detectadas. As 46 MEDIUM/13 LOW
de pacotes Ubuntu permanecem; no runtime Java resta **CVE-2026-118259 (MEDIUM)**,
OpenTelemetry API **1.62.0**, correção indicada **1.67.0**. O
[advisory oficial OpenTelemetry](https://github.com/open-telemetry/opentelemetry-java/security/advisories/GHSA-ccrj-gw8r-854m)
descreve custo de CPU excessivo ao extrair `tracestate` W3C malformado. Como o
projeto usa propagação W3C, o risco não foi descartado como irrelevante; exige
avaliação de ingresso, limites de headers e atualização compatível futura.
Não se criou override adicional ou aceite de risco de produção nesta execução.

Robustez pontual: a guarda anterior aceitava qualquer `lang-pkgs`, inclusive
um relatório de npm sem Java. Agora exige `Class=lang-pkgs`, `Type=jar` e
inventário `Packages` não vazio, além da análise de SO. **23 testes Python
passaram: os 20 originais preservados e três novos**, cobrindo npm sem Java,
JAR sem inventário, relatório vazio/ausente/inválido e relatório Java sem CVEs.
Teste real com archive inexistente produziu falha do scanner, **exit 1**.
O workflow não usa `continue-on-error`; a etapa de publicação conserva o
[`success()` implícito do GitHub](https://docs.github.com/en/actions/reference/workflows-and-actions/expressions#status-check-functions),
portanto não executa após falha do scan ou da guarda. Sua estrutura foi preservada.

O limite de **30min** do job foi mantido: build frio observado de até 10min21s,
download/preparação inicial das DBs de aproximadamente 10min na execução 1 e
análise/exportação na ordem de minutos cabem no orçamento, com margem restante.
O timeout específico do scanner continua 15min. Não é garantia sob qualquer
latência; medir a primeira execução remota antes de ajustar limites, mantendo
falha fechada em timeout. Não houve defeito que justificasse reestruturar o job.

Evidências desta execução, ignoradas pelo Git: `.gradle/cd452-remediacao/`, com
`dependencyInsight.txt`, `versoes-bootjar-imagens.json`, `testes-junit.json`,
`comparacao-scans.json`, `scanner-negativo.txt` e `analise/*-vulnerabilidades.json`.
Os resultados anteriores permanecem em `.gradle/cd452/`. Sem publicação no GHCR,
sem execução remota aprovada e sem início da 4.5.3. O patch está pronto para
novo Code Review naquele encerramento; o aceite técnico posterior está registrado
abaixo. A publicação continua sem autorização.

### Aceite técnico após a remediação

O **Code Review final da 4.5.2 foi aprovado**, incluindo o patch completo e a
remediação coordenada com Spring Boot 4.1.2. Os **23 testes Python também foram
executados independentemente pelo responsável e passaram**. O aceite preserva
as evidências locais anteriores: **324 testes Gradle, seis PromQL, 23 Python,
dois builds Docker e smoke tests aprovados**, com health, segurança e graceful
shutdown verificados. Os dois scans Trivy não detectaram CRITICAL/HIGH após a
remediação; **47 MEDIUM e 13 LOW por imagem permanecem reportadas**.

**CVE-2026-118259 em OpenTelemetry continua pendente de avaliação para eventual
implantação**, sem aceite automático de risco de produção. A aprovação autoriza
somente o registro documental e um único commit local da 4.5.2. Publicação remota,
funcionamento real do workflow no GitHub e autenticação/push no GHCR ainda não
foram validados. Os resultados da primeira análise e da remediação permanecem
históricos acima; esta atualização de aceite muda somente documentação, sem
repetir builds ou testes. A Entrega 4.5 continua em andamento, não concluída nem
integrada à `main`; a 4.5.3 permanece planejada.

## 4.5.3 — Implantação planejada

Antes de escolher provedor, definir orçamento mensal (compute, PostgreSQL,
storage, registry/egress, logs e backups), ambiente, TLS, acesso e responsável
operacional. Nenhuma nuvem, servidor, Kubernetes ou credencial remota é criado
nesta execução. A autenticação atual é exclusivamente local/simulada e não
constitui solução pronta de produção.

Implantar as duas imagens **publicadas por digest**, sem recompilar no destino.
Configuração e secrets variam por ambiente e ficam fora da imagem. Antes de
promover, validar conectividade PostgreSQL, migrações, identidade da integração,
health de ambas e smoke test autenticado do fluxo aprovado. Health isolado não
atesta autorização/consulta de recebíveis. Definir timeout de startup e critérios
de sucesso/falha de modo explícito no mecanismo de deploy escolhido.

Registrar o par de digests anterior e o novo junto com a configuração da versão.
Rollback deve reaplicar o par anterior, esperar health e repetir o smoke test,
com logs/evidência do resultado. Flyway pode ter alterado o banco: rollback de
imagem não reverte migrations. Antes de deploy, verificar compatibilidade das
migrations com a versão anterior e planejar backup/restauração quando necessário.
Ensaiar o procedimento em ambiente de teste antes de qualquer promoção.

Trade-off: adiar o destino evita custo e operação prematuros; publicação por
digest já define a fronteira necessária entre build e deploy.
