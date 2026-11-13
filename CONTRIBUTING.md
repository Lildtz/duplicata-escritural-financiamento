# Contribuindo — GitHub Flow

`main` é a referência estável. Cada entrega parte dela em uma feature branch curta,
por exemplo `feat/4.3-github-actions` ou `feat/4.4-monitoramento`. Não utilizamos
branches permanentes `develop`, `release` ou `hotfix`.

## Fluxo por entrega

1. Confira a árvore limpa e a base local/remota antes de criar a branch. Resolva
   divergências de histórico explicitamente; não sincronize ou reescreva histórico
   automaticamente.
2. Implemente somente a entrega, respeitando `AGENTS.md`, ADRs e os contratos.
   Use commits pequenos e coerentes, com Conventional Commits (`feat:`, `fix:`,
   `test:`, `docs:`, `ci:`).
3. Execute a validação local com Java 21 e Docker disponíveis:

   ```powershell
   .\gradlew.bat clean build --console=plain
   git diff --check
   git status --short
   ```

   Em Linux, utilize `./gradlew clean build --no-daemon --console=plain`.
4. Publique a feature branch e abra um Pull Request direcionado à `main`, quando
   autorizado. Descreva problema, alteração, validações e limitações reais.
5. Aguarde o CI, o Code Review e o aceite técnico. Corrija as falhas e mantenha o
   PR atualizado. Antes de integrar, o CI deve passar para a revisão atual.
6. Após aprovação, realize o merge e exclua a feature branch. Atualize sua base
   local antes de iniciar a próxima entrega.

Nunca versione credenciais, PATs, Bearers, arquivos `.env`, dumps ou relatórios gerados.

## CI e relatórios

O workflow [CI](.github/workflows/ci.yml) atende PRs para `main`, pushes em `main`
e execução manual. O job estável chama-se **Build e testes** (`build-test`).
Ele usa Ubuntu 24.04, Temurin 21 e o Wrapper versionado, com validação do JAR e
cache básico pelo `setup-gradle`; `setup-java` não configura outro cache Gradle.
As actions são fixadas por SHA, com a versão correspondente em comentário.
Atualizações devem conferir as releases oficiais e seus commits.

Docker é verificado antes do build. Testcontainers baixa `postgres:18-alpine` e
provisiona bancos descartáveis. A tarefa do Banco DUP gera o JAR da Signatária;
os testes verticais iniciam sua JVM e o receptor OTLP em portas dinâmicas de
loopback, sem PostgreSQL manual, coletor externo ou exclusão de testes.

O build executa `./gradlew clean build --no-daemon --console=plain`, com limite
de 20 minutos para o job. Suítes, testes HTTP, persistência, segurança, OpenAPI,
worker e observabilidade participam do build completo. A quantidade de testes
pode evoluir: não é uma condição fixa de aprovação.

Ao final, mesmo após falha, o workflow tenta gerar um resumo numérico dos XMLs
JUnit e publicar o artefato **relatorios-testes**, retido por sete dias:

- `**/build/test-results/test/**/*.xml`
- `**/build/reports/tests/test/**`

Os caminhos cobrem os dois subprojetos. Se a falha ocorrer antes dos testes,
o resumo informa a ausência de XMLs e o upload avisa sobre arquivos ausentes.
O resumo não altera o resultado do build; não há `continue-on-error`. Uma etapa
obrigatória que falha mantém o job com falha. Cancelamento/timeout ou indisponibilidade
do serviço de artefatos podem impedir a preservação dos relatórios.

As permissões do token são apenas `contents: read`. O checkout não persiste
credenciais; não usamos `pull_request_target`, segredos de produção, deploy ou
publicação de releases. Execuções obsoletas da mesma referência/PR são canceladas.

## Primeira validação no GitHub

A implementação local não comprova execução em Linux ou no GitHub. Após publicar
a branch e abrir o primeiro PR com autorização, confira o runner, validação do
Wrapper, Docker/download da imagem, as duas JVMs, OTLP, encerramento dos recursos,
resumo JUnit e artefatos em sucesso/falha. Um build Windows aprovado não comprova
esses comportamentos no runner Linux.

Após uma execução real validada, configure nas regras de `main` a exigência de
PR, revisão e do status check **Build e testes**. Esse check será obrigatório
quando a regra estiver configurada; o YAML, isoladamente, não bloqueia merges.
Regras de proteção e permissões do repositório são configuração separada.
O badge só deve ser acrescentado após publicação e comprovação do workflow.
