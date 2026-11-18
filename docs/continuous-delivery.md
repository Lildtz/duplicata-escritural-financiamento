# Entrega 4.5 — Continuous Delivery

Entrega independente na branch `feat/4.5-cd`, criada da `origin/main` em
`42be1338dce4cf305318779ce7ccf54b5bbd1796`. A 4.4 foi integrada pelo
[PR #3](https://github.com/Lildtz/duplicata-escritural-financiamento/pull/3);
o [CI pós-merge](https://github.com/Lildtz/duplicata-escritural-financiamento/actions/runs/44280078489)
foi aprovado. O baseline informado no encerramento é de 324 testes Gradle e
seis testes PromQL. A **4.5.1 está aprovada tecnicamente em Code Review, sem
achados bloqueantes**, com registro da aprovação e único commit local autorizados.
As duas imagens foram construídas e validadas localmente; permanecem exclusivamente
locais. A 4.5 continua em andamento, não concluída nem integrada à `main`;
4.5.2 e 4.5.3 permanecem planejadas, sem autorização para publicação ou deploy.

## Estratégia e limites

| Incremento | Decisão | Estado |
|---|---|---|
| 4.5.1 — Empacotamento | Uma imagem por aplicação, build multi-stage com Wrapper, JRE 21 e usuário não root | Aprovada tecnicamente em Code Review; commit local autorizado |
| 4.5.2 — Publicação | GitHub Actions → GHCR, após aprovação em `main`/release | Somente planejamento |
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
Dockerfiles. Java permanece na versão principal 21; Spring Boot 4.1.1,
dependency management 1.1.7 e Wrapper Gradle 9.8.0 permanecem inalterados.

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

## 4.5.2 — Publicação planejada

Adicionar futuramente workflow separado de publicação no GHCR para as duas
imagens, sem enfraquecer ou renomear o check obrigatório **Build e testes**.
Publicar somente código aprovado da `main` ou release aprovada e vinculada a um
commit dessa branch; execução de PR deve validar sem publicar. O desenho inicial
é publicação após sucesso do CI do mesmo SHA, nunca baseada só no nome da branch.

Usar o token efêmero do GitHub Actions com `contents: read` e `packages: write`
somente no job de publicação, evitando PAT ou secrets permanentes. Actions devem
ser fixadas por SHA. Tags de release e `sha-<SHA completo>` identificam a versão;
não usar `latest` como referência de implantação ou sobrescrever releases.
Registrar, por aplicação, commit de origem, tag, plataforma e digest retornado
pelo registry no resumo/artefato de publicação. O digest identifica o artefato
efetivo; não confundir o SHA do Git com o digest OCI. Scan de vulnerabilidades
e política de atualização das bases devem preceder promoção a um ambiente.

Trade-off: workflow separado evita acoplar credenciais de escrita ao CI, mas
exige conferir explicitamente que a publicação corresponde ao SHA aprovado.
O build Docker continua sem suíte embutida; a aprovação do CI é o gate.

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
