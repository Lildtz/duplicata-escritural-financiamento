# Entrega 3 — autorização, consulta local, integração HTTP e validação vertical

Esta capacidade é uma **simplificação do simulador**, não um Opt-in regulatório
completo nem um contrato oficial B3. A Entrega 3.2 permite consulta autorizada de
Duplicatas locais pelo Financiador. Não implementa Registro, Agenda regulatória
completa. Os contratos da Signatária das Entregas 3.1/3.2 são consumidos pelo Banco DUP
na Entrega 3.3, com validação vertical na 3.4, descritas neste guia. Não comprova anuência
jurídica de representantes humanos. Os ADRs 0001–0003 permanecem preservados.

O package funcional é `consultarecebiveis`, com `api`, `aplicacao`, `dominio` e
`infraestrutura/persistencia`. Poderá ser integrado ao contexto de Registro quando
ele existir. Não há novo projeto Gradle, dependência, serviço executável ou banco.
Os valores técnicos CNPJ e chave idempotente são reutilizados da Escrituração,
sem mover classes ou duplicar modelos persistentes.

A documentação OpenAPI/Swagger da Signatária está disponível somente em `local`;
veja [acesso, autenticação e testes](escrituracao-manual.md#openapi-3-e-swagger-ui--entrega-41).
Ela não concede acesso às operações: papéis, beneficiário e vigência continuam validados a cada chamada.

## Identidades e provisionamento local

O mecanismo Bearer continua exclusivo do perfil `local`. Sem esse perfil, todas as
rotas de negócio ficam bloqueadas; health/info permanecem públicos. Configuração
ausente, hash inválido, hash duplicado ou associação ambígua impedem inicialização.

Cada impressão SHA-256 deve identificar **exatamente um** destes papéis:

* `sacador-id`: UUID do Sacador cadastrado e habilitado, mantendo a configuração
  das Entregas 1 e 2;
* `financiador-id`: UUID estável do Financiador local provisionado, independente
  de qualquer cadastro de Sacador. Não é criada entidade/tabela Financiador.

Exemplo estrutural para o arquivo ignorado `.env.signataria.local.yml`:

```yaml
signataria:
  autenticacao-local:
    tokens:
      - sha256: '<impressao SHA-256 do token do Sacador>'
        sacador-id: '10000000-0000-0000-0000-000000000001'
      - sha256: '<impressao SHA-256 do token do Financiador>'
        financiador-id: '20000000-0000-0000-0000-000000000001'
```

Os placeholders devem ser substituídos por impressões válidas. Gere cada token
aleatório e seu hash pelo procedimento do [guia de Escrituração](escrituracao-manual.md#provisionamento-exclusivamente-local).
Preserve as entradas existentes ao acrescentar o Financiador. Nunca versione ou
registre os tokens, nem os passe em argumentos da aplicação. Não há credencial
padrão, OAuth, mTLS ou servidor de identidade nesta entrega.

Rotação pode manter duas impressões diferentes para a mesma identidade. Remover
as impressões e reiniciar revoga as credenciais, sem apagar autorizações. Mesmo
UUID em papéis distintos não permite trocar de papel. A configuração é o cadastro
local dos Financiadores; não há consulta ao banco de Sacadores para autenticá-los.

## Contrato REST próprio do simulador

Todas as chamadas exigem `Authorization: Bearer <token-local>`.

| Operação | Papel | Retorno |
|---|---|---|
| POST `/api/v1/autorizacoes-consulta` | Financiador | 201 e Location |
| GET `/api/v1/autorizacoes-consulta/{id}` | Sacador correspondente ou Financiador beneficiário | 200 |
| GET `/api/v1/autorizacoes-consulta/{id}/duplicatas` | Financiador beneficiário com autorização ATIVA e vigente | 200 |
| PUT `/api/v1/autorizacoes-consulta/{id}/decisao` | Sacador correspondente | 200 |
| DELETE `/api/v1/autorizacoes-consulta/{id}` | Sacador correspondente ou Financiador beneficiário | 204 |

O POST exige `Idempotency-Key` no formato já utilizado na Escrituração:
1–128 caracteres ASCII, letras, números, ponto, dois-pontos, hífen ou sublinhado,
com comparação sensível a maiúsculas. Corpo:

```json
{
  "cnpjSacador": "11222333000181",
  "fimVigencia": "2030-12-31T23:59:59Z"
}
```

Ajuste o fim para um instante futuro. CNPJ segue a normalização e validação já
existentes, inclusive alfanumérico. Instantes são normalizados à precisão de
microssegundos do PostgreSQL; offsets equivalentes e frações submicrossegundo
resultam no mesmo conteúdo. O Sacador precisa existir localmente e estar habilitado
para nova solicitação. CNPJ desconhecido/desabilitado retorna 404 genérico; não
declara inexistência no mercado nem vínculo externo.

O Financiador vem exclusivamente do token. O Sacador é resolvido pelo CNPJ local.
Campos como `financiadorId`, `sacadorId`, estado, escopo ou identidade de quem
aprova são rejeitados no corpo. O escopo fixo é consulta dos recebíveis locais
do CNPJ completo, sem abrangência automática de matriz/filiais.

A resposta do POST contém `idAutorizacao`, `financiadorId`, `cnpjSacador`,
`status: PENDENTE`, `vigente: false`, `solicitadaEm` e `fimVigencia`.
Repetições válidas também retornam HTTP 201, com a mesma representação e Location, inclusive após aprovação,
revogação ou expiração. GET retorna o estado atual e, quando aplicáveis,
`inicioVigencia`, `decididaEm`, `revogadaEm` e `revogadoPor`.

Corpo do PUT: `{"decisao":"APROVAR"}` ou `{"decisao":"RECUSAR"}`.
O DELETE registra revogação; não remove a linha e não aceita identidade pelo corpo.

## Transições, vigência e concorrência

| Estado | Operação | Resultado |
|---|---|---|
| PENDENTE antes do fim | APROVAR | ATIVA, início da vigência igual à decisão |
| PENDENTE antes do fim | RECUSAR | RECUSADA |
| ATIVA | APROVAR novamente | Mesmo estado e datas, inclusive após expiração |
| RECUSADA | RECUSAR novamente | Mesmo estado e datas |
| PENDENTE ou ATIVA, inclusive expirada | DELETE | REVOGADA |
| REVOGADA | DELETE novamente por participante autorizado | 204, preservando primeiro autor e instante |
| PENDENTE expirada | Nova decisão | 409 AUTORIZACAO_EXPIRADA |
| ATIVA + RECUSAR, RECUSADA + APROVAR/DELETE, REVOGADA + qualquer decisão | Operação incompatível | 409 TRANSICAO_INVALIDA |

`vigente` é verdadeiro somente para ATIVA no intervalo
`inicioVigencia <= agora < fimVigencia`. Expiração não altera o estado persistido,
não cria estado EXPIRADA e não exige agendamento. Repetir aprovação não renova
vigência. Nova autorização exige outro POST com nova chave.

Decisão e revogação fazem leitura JPA com `PESSIMISTIC_WRITE`, restrita ao
participante autenticado, dentro da mesma transação. Se aprovação vencer o lock,
a revogação subsequente encontra ATIVA e revoga. Se revogação vencer, a aprovação
recebe 409. Em ambos os casos, ambas confirmadas as operações possíveis, o estado
final é REVOGADA. Relógios das instâncias precisam estar sincronizados.

## Persistência e idempotência

A V3 cria somente `escrituradora.autorizacao_consulta`, com UUID próprio,
beneficiário, referência ao Sacador local, fotografia do CNPJ, pedido, vigência,
estado e datas/autor da revogação. Identidade, conteúdo solicitado e fim da
vigência são imutáveis pelo modelo JPA e serviço. Aprovação revalida cadastro
habilitado e CNPJ do Sacador. Não há vínculo com outro banco nem modelos JPA
duplicados. V1, V2 e dados existentes são preservados.

O POST tem escopo `(financiador_id, operacao, chave_idempotencia)`, protegido por
unique constraint. Conteúdo comparado: CNPJ canônico e fim da vigência normalizado.
Uma consulta nativa `INSERT ... ON CONFLICT DO NOTHING` no repositório Spring Data
evita abortar a transação na disputa. A leitura subsequente em READ COMMITTED
compara o vencedor. As demais operações usam JPA. Não há lock em memória ou retry
automático. Transações possuem timeout de 10 segundos.

Rotação de credencial preserva o escopo. Chaves novas são solicitações independentes;
podem existir autorizações distintas para o mesmo par Sacador/Financiador.
Revogar um ID revoga somente aquela autorização. Não há retenção ou limpeza
automática; chaves e histórico duram enquanto os registros forem mantidos.

## Erros e verificação

| Situação | HTTP |
|---|---|
| Credencial ausente/inválida | 401 com WWW-Authenticate: Bearer |
| Papel incorreto ou Sacador autenticado desabilitado | 403 |
| Recurso inexistente ou de outro participante | 404 genérico |
| JSON/campo/UUID/enum/chave inválidos | 400 |
| Campo obrigatório, CNPJ ou vigência inválidos | 422 |
| Conteúdo diferente na mesma chave ou transição incompatível | 409 |
| Falha reconhecidamente transitória/recuperável | 503 |
| Outra falha de persistência | 500 |

Os erros da capacidade usam `application/problem+json` e `codigo` estável,
sem SQL, tabelas, payload ou stack trace. Falhas de autenticação preservam as
respostas de segurança existentes. 503 ou timeout não comprovam ausência de commit;
reenvie o POST com a mesma chave e conteúdo.

Testes cobrem HTTP real, papéis, isolamento, estados, relógio controlável,
normalização, disputas de POST e aprovação/revogação, constraints, migração V2→V3
com checksums e dados preservados e encerramento/reabertura do contexto Spring.
PostgreSQL é descartável via Testcontainers; não se usa o banco persistente local.

```powershell
.\gradlew.bat clean build
git diff --check
git status --short
```

## Entrega 3.2 — consulta autorizada de Duplicatas locais

```http
GET /api/v1/autorizacoes-consulta/{id}/duplicatas?pagina=0&tamanho=50
Authorization: Bearer <token-local-do-financiador>
```

Exemplo de resposta 200 (UUID técnico ilustrativo):

```json
{
  "cnpjSacador": "11222333000181",
  "escopo": "LOCAL_SIMULADO",
  "itens": [
    {
      "idDuplicata": "1b7d4cef-171e-4a91-b52e-eeb8ced7b2f9",
      "numeroDuplicata": "DUP-1",
      "cnpjSacado": "12ABC34501DE35",
      "dataVencimento": "2026-12-01",
      "valorNominal": 100.00,
      "escrituradaEm": "2026-11-12T15:00:01Z"
    }
  ],
  "pagina": 0,
  "tamanho": 50,
  "temProximaPagina": false
}
```

`valorNominal` corresponde a `DadosEscrituracao.valorDuplicata` (`BigDecimal`),
preservando a precisão decimal e os dados imutáveis da emissão. Não representa
saldo disponível, antecipável ou livre de ônus. `idDuplicata` é UUID técnico,
sem equivalência com IUD. Não são expostos JSONB, documento fiscal, endereço,
dados de processamento ou informações cadastrais adicionais.

O serviço de aplicação valida a identidade tipada de Financiador local, busca
a autorização por ID **e beneficiário** e reavalia `vigente` com o Clock injetado.
Sacador não pode assumir o papel de Financiador, mesmo tendo o mesmo UUID.
O intervalo é `inicioVigencia <= agora < fimVigencia`, somente no estado ATIVA.

| Situação específica da consulta | HTTP |
|---|---|
| Autorização própria ATIVA e vigente, inclusive sem títulos | 200 |
| Autorização própria PENDENTE, RECUSADA, REVOGADA ou fora da vigência | 403 |
| Autorização inexistente ou de outro Financiador | 404 genérico |
| Token de Sacador | 403 |
| Parâmetros inválidos, repetidos, vazios ou desconhecidos | 400 |

Não existe parâmetro para escolher Sacador, CNPJ, beneficiário ou escopo.
`pagina` e `tamanho` são os únicos parâmetros aceitos. Valores omitidos usam 0 e 50;
`pagina` deve ser inteiro não negativo, `tamanho` deve estar entre 1 e 100.
Offsets superiores a `Integer.MAX_VALUE`, limite da paginação JPA, retornam 400.
Ordenação fixa: `escrituradaEm ASC, id ASC`, sem `sort` fornecido pelo cliente.
A consulta usa paginação no banco com `Slice`, lendo no máximo `tamanho + 1`
Duplicatas para detectar próxima página; não calcula contagem total.
Uma página sem títulos retorna `itens: []` e `temProximaPagina: false`.

`consultarecebiveis` chama `escrituracao.aplicacao.ServicoConsultaDuplicatas`,
recebendo somente a projeção `DuplicataConsultada`. A entidade `Duplicata` e seu
repository permanecem na Escrituração. A busca JPA exige simultaneamente UUID
do Sacador e CNPJ completo capturados na autorização; não amplia para matriz,
filiais ou dados de outros Sacadores. Alterações cadastrais posteriores não
reescrevem a fotografia autorizada nem as emissões históricas. Pedidos ainda
PENDENTE não são Duplicatas e não aparecem na consulta.

A operação usa transação somente de leitura em READ COMMITTED, timeout de 10s,
sem cache de autorização. Cada requisição relê a autorização no banco; consultas
iniciadas após o commit da revogação são bloqueadas. Uma consulta que já tenha
validado a autorização antes desse commit pode terminar. Não há promessa de
snapshot consistente entre páginas: inserções concorrentes podem deslocar offsets.
O JPA carrega os dados imutáveis das entidades da página e a aplicação os projeta;
não há extração SQL específica de campos JSONB. O volume lido é limitado pela página.
V1–V3 permanecem intactas; não há V4 ou novo índice. O índice existente iniciado
por `sacador_id` atende ao filtro; custo de ordenação e offsets altos deve ser
medido antes de propor índices adicionais ou paginação por cursor.

Os testes HTTP com PostgreSQL/Testcontainers cobrem papéis, beneficiário,
todos os estados, início/fim exatos de vigência, consultas após commit da revogação,
lista vazia, isolamento por UUID e CNPJ completo (incluindo filial), fotografia,
pedidos PENDENTE, precisão decimal, paginação e desempate por ID. Também exercitam
o serviço diretamente, sem depender das proteções do controller/Spring Security.
O build completo executa as regressões das Entregas 1, 2 e 3.1.

Esta é somente uma **consulta local simulada**, sem equivalência com uma Agenda
regulatória completa. Não inclui Registro, IUD, interoperabilidade, crédito,
MCP, Agents, Spring AI ou Kafka. Não foi encontrada evidência inequívoca de aprovação
histórica do Code Review da Entrega 3.2. Uma revisão retrospectiva foi realizada em
13/11/2026, sem defeitos funcionais bloqueantes e com parecer técnico favorável.
O aceite técnico retrospectivo foi autorizado e registrado em 13/11/2026 no roadmap,
sem presumir aprovação anterior nem alterar `eba5d38`. Build e testes da 3.2 foram
novamente aprovados na preparação da 3.3.

## Entrega 3.3 — integração HTTP do Banco DUP

**Implementada e validada; aprovação técnica final registrada em 13/11/2026, após Code Review e revisão corretiva favoráveis.** O fluxo vertical
entre as duas aplicações não foi executado na 3.3; sua validação está na seção da 3.4.

### Fronteira e casos de uso

O package `br.com.duplicataescritural.financiamento.consultarecebiveis` possui:

```text
dominio/
  GatewayRegistradora, SolicitacaoAutorizacao, AutorizacaoConsulta
  DuplicataConsultada, PaginaDuplicatas, PaginacaoConsulta, FalhaRegistradora
aplicacao/
  ServicoConsultaRecebiveis
infraestrutura/integracao/signataria/
  GatewayRegistradoraHttp, ConexaoSignataria, ConfiguracaoSignataria
```

O `ServicoConsultaRecebiveis` disponibiliza:

* `solicitarAutorizacao(cnpjSacador, fimVigencia, chaveIdempotencia)`;
* `consultarAutorizacao(idAutorizacao)`;
* `consultarDuplicatas(idAutorizacao)` com página 0 e tamanho 50;
* `consultarDuplicatas(idAutorizacao, pagina, tamanho)`.

A porta `GatewayRegistradora` recebe/devolve modelos do Financiamento, sem classes
da Signatária, HTTP ou JPA. O adapter `RestClient` mantém DTOs de transporte privados
e consome somente os endpoints publicados acima: POST de autorização e os dois GETs.
Não existe novo endpoint no Banco DUP, persistência de autorização ou catálogo de títulos.
Aprovação/recusa e revogação continuam na Signatária; o Banco DUP não reproduz seu motor.

Validação local limita entradas: UUID obrigatório, CNPJ completo compacto (14 posições,
normalização de espaços externos e letras), chave no formato existente e paginação
com os mesmos limites da 3.2. Dígitos verificadores, cadastro e regras de vigência
continuam na Signatária. O fim da vigência não é rejeitado localmente por estar no
passado: isso preserva repetições idempotentes de um POST antigo. A chave é fornecida
pelo chamador, enviada somente em `Idempotency-Key` e não é trocada após falha.

Cada página faz uma nova chamada remota; consultar previamente a autorização não
habilita consultas futuras. Não há cache, pré-consulta automática, busca de todas as
páginas, cálculo financeiro ou reordenação dos itens. Campos decimais são `BigDecimal`.
O resultado da aplicação é `ConsultaDuplicatas(consultadaEm, resultado)`, onde
`resultado` preserva o envelope da 3.2. `consultadaEm` é o início da chamada no Clock
UTC do Banco DUP, **não** um timestamp publicado pela Signatária nem um snapshot.

### Conexão e identidade local

O perfil `local` ativa a configuração e os beans de integração. Sem esse perfil,
o bootstrap/Actuator existente continua disponível, sem criar esses beans.
O arquivo versionado `application-local.yml` contém somente referências ao ambiente:

| Propriedade `financiamento.signataria` | Variável | Padrão |
|---|---|---|
| `url-base` | `SIGNATARIA_URL_BASE` | `http://localhost:8081` |
| `token` | `SIGNATARIA_TOKEN` | Sem credencial padrão; obrigatório |
| `timeout-conexao` | `SIGNATARIA_TIMEOUT_CONEXAO` | `2s` |
| `timeout-leitura` | `SIGNATARIA_TIMEOUT_LEITURA` | `5s` |

Timeouts aceitam Duration do Spring, de 1ms até 2147483647ms. URL deve ser uma
origem HTTPS ou HTTP em loopback (`localhost`, `127.0.0.1`, `[::1]`), sem path além
de `/`, query, fragmento ou credenciais. Redirecionamentos HTTP são desabilitados
para evitar envio da credencial a outro destino. Erros de configuração impedem
startup; a representação textual da configuração omite a credencial.

Provisione na Signatária, conforme a seção de identidades deste guia, o hash do
token associado ao `financiador-id` do Banco DUP. Configure o **token original**
somente no ambiente privado do processo de Financiamento. Hash e token não são
intercambiáveis. Não utilize token de Sacador ou credencial de outro Financiador.
O header de saída é `Authorization: Bearer <token-do-financiador>`; nenhum CNPJ
ou UUID fornecido pelo chamador substitui essa identidade. A Signatária determina
o beneficiário e revalida a autorização em cada chamada. 401/403/404 bloqueiam
o resultado; não existe fallback para dados anteriores ou vazios.

O provisionamento é a fronteira de confiança deste simulador local: a validação do
Financiamento verifica formato/presença do token, mas não possui um UUID independente
que identifique o Banco DUP. É possível configurar um token válido de outro Financiador;
nesse caso, a Signatária autentica aquele participante, associa a ele novos pedidos e
continua negando acesso às autorizações de terceiros. Isso não é detectado como erro
de configuração pelo Banco DUP e não deve ser confundido com bypass de autorização.
No provisionamento e antes de consultar títulos, o operador deve conferir a associação hash/`financiador-id`
na configuração privada da Signatária e comparar o `financiadorId` devolvido pela
solicitação ou consulta de autorização com a identidade pretendida para o Banco DUP.
Essa conferência operacional não habilita páginas futuras nem substitui sua validação
remota. Não registre o token para realizar a conferência.

Foi preservado o mecanismo local existente, sem adicionar um segundo UUID configurável.
Essa segunda configuração poderia detectar uma troca isolada de token, mas não seria
prova de identidade e continuaria dependente do mesmo provisionamento confiável.
Não foi criado um vínculo independente de produção nem replicado o motor de autorização.

Com `SIGNATARIA_TOKEN` provisionada no ambiente, execute na raiz:

```powershell
$env:SIGNATARIA_URL_BASE = 'http://localhost:8081'
$env:SIGNATARIA_TIMEOUT_CONEXAO = '2s'
$env:SIGNATARIA_TIMEOUT_LEITURA = '5s'
.\gradlew.bat :duplicata-escritural-financiamento-api:bootRun "--args=--spring.profiles.active=local"
```

Use a [execução local da Signatária](../infra/local/README.md) e preserve seu
provisionamento existente ao acrescentar o Financiador. Não coloque o token em
argumentos do bootRun, comandos literais versionados ou logs. O mecanismo continua
exclusivamente local, sem autenticação de produção ou comprovação jurídica.
Os casos de uso são beans Spring para consumidores da aplicação; a entrega não
publica uma API de entrada que permita a qualquer chamador utilizar essa credencial.

### Erros da aplicação do Banco DUP

`FalhaRegistradora.motivo()` diferencia as condições abaixo. O serviço propaga esse
erro tipado; não há mapeamento para uma nova API HTTP de entrada nesta entrega.

| Condição remota | Motivo no Financiamento |
|---|---|
| HTTP 401 | `AUTENTICACAO_REJEITADA` |
| HTTP 403, inclusive autorização revogada/expirada | `ACESSO_NEGADO` |
| HTTP 404, autorização alheia ou inexistente | `RECURSO_NAO_ENCONTRADO` |
| HTTP 400 ou 422 | `PARAMETROS_INVALIDOS` |
| HTTP 409 | `CONFLITO` |
| HTTP 429, não previsto no contrato atual | `RESPOSTA_INCOMPATIVEL` |
| Timeout de conexão ou leitura | `TIMEOUT` |
| Conexão recusada, DNS/TLS ou outra falha de transporte | `INDISPONIVEL` |
| HTTP 5xx, inclusive 503 | `ERRO_REMOTO` |
| Status inesperado, corpo ausente/inválido ou contrato incompatível | `RESPOSTA_INCOMPATIVEL` |

O adapter espera 201 no POST e 200 nos GETs. Não preserva mensagens, corpo remoto,
causa HTTP, URL ou cabeçalhos nas exceções devolvidas à aplicação. O 404 permanece
genérico; não permite distinguir autorização alheia de inexistente. Enum desconhecido,
campos obrigatórios ausentes, página incompatível com a solicitada e escopo diferente
de `LOCAL_SIMULADO` são rejeitados. Campos adicionais são ignorados para evolução
aditiva do contrato, sem disponibilizá-los nos modelos do Banco DUP.

A criação e a repetição idempotente válida do POST retornam 201. O recebimento
original permanece PENDENTE/`vigente: false`, inclusive após aprovação ou revogação;
o GET informa o estado atual. Mesma chave com conteúdo diferente retorna 409.
Assim, não há outro status de sucesso de POST a acrescentar ao adapter.
Não existe rate limiting implementado ou resposta 429 publicada pela Signatária:
um 429 inesperado é rejeitado como incompatibilidade, sem retry nem interpretação
de `Retry-After`. Se o contrato remoto passar a prever limitação de requisições,
essa classificação deverá ser revista junto com o contrato e seus testes.

Não existe retry da aplicação, circuit breaker, cache ou lista vazia por indisponibilidade.
Um POST com timeout pode ter sido confirmado remotamente; repetição explícita deve
usar a mesma chave e conteúdo. Não há correlation-id implementado nas aplicações
atuais; esta entrega não inventa um protocolo ou header de correlação.

### Verificação e decisões

Os testes do Financiamento usam recursos já instalados: Mockito para orquestração,
MockRestServiceServer para contratos/mapeamentos e servidor HTTP descartável do JDK
para Bearer, bootstrap local, timeout de leitura real, conexão recusada e recusa
de redirecionamento. Não utilizam PostgreSQL da Signatária. Negação por revogação,
expiração e outro beneficiário é simulada no adapter; a regra real continua coberta
pelos testes PostgreSQL/Testcontainers da Signatária, executados no build completo.

O timeout de conexão é configurado no HttpClient do JDK e sua classificação é
testada com falha injetada; não se tenta provocar timeout real de conexão em rede
externa. O teste de bootstrap verifica os nomes das variáveis de ambiente e a
criação dos beans sem iniciar ambas as aplicações. A validação vertical com dados
reais do simulador e provisionamento conjunto permanece na Entrega 3.4.

Validação anterior à revisão corretiva, em 13/11/2026: `.\gradlew.bat clean build` aprovado, com 89 testes do
Financiamento e 163 da Signatária (252 no total, sem falhas, erros ou ignorados).
`git diff --check` aprovado. Os comandos de teste foram executados via Wrapper;
o bootstrap local e as variáveis foram exercitados pelos testes Spring/HTTP.
O comando interativo `bootRun` contra a Signatária não foi executado, pois a
execução conjunta está reservada para a 3.4. Naquela validação, não foram criados commit ou push.

Na revisão corretiva pós-Code Review, foram reforçados `GatewayRegistradoraHttpTests`
(criação e repetição 201, POST com timeout seguido de reenvio explícito, conflito 409
sem retry) e `AutorizacaoConsultaHttpTests` (201, corpo e Location preservados nas
repetições após aprovação/revogação). O caso 429 já existente foi preservado.
`FiltroTokenLocal`, `TokensLocais`, `ServicoAutorizacaoConsulta` e os testes de papéis,
isolamento e escopo por Financiador fundamentam a manutenção da autenticação local.
Nenhum código funcional, contrato, dependência ou migration foi alterado nesta revisão.
Validação da revisão corretiva em 13/11/2026: as duas classes modificadas passaram
nos testes específicos (40 do adapter e 35 HTTP da Signatária). O novo
`.\gradlew.bat clean build` aprovou 254 testes: 91 do Financiamento e 163 da
Signatária, sem falhas, erros ou ignorados. `git diff --check` aprovado.
Posteriormente, foram autorizados e registrados em 13/11/2026 o aceite técnico
retrospectivo da 3.2 e a aprovação técnica final da 3.3, após as revisões favoráveis.
O commit único de encerramento da 3.3 foi autorizado separadamente; seu registro
é verificável no histórico Git e não equivale a uma aprovação histórica da 3.2.
A Entrega 3.4 permanece planejada e não foi iniciada; não houve validação E2E.

**Problema → alternativas → decisão → trade-offs → consequências:**

* Consumir infraestrutura externa → chamada direta no serviço ou porta com adapter
  → `GatewayRegistradora` e `RestClient` → pequeno custo de DTOs/mapeamento próprios
  → domínio do Banco DUP independente de HTTP e dos modelos da Signatária.
* Identidade entre aplicações → novo provedor ou Bearer local existente → reutilizar
  credencial técnica do Financiador → uso exclusivamente local e rotação por configuração
  → autorização e beneficiário continuam controlados pela Signatária.
* Referência temporal ausente na 3.2 → alterar o contrato remoto ou registrar instante
  local → `consultadaEm` no caso de uso → não é snapshot nem timestamp remoto
  → contrato anterior preservado.
* Consumidor de entrada ainda não definido → duplicar endpoints ou expor casos de uso
  como beans → beans sem nova API → execução externa do fluxo fica para a 3.4
  → não se expõe a credencial técnica por um proxy HTTP sem autenticação própria.

O README e este guia são as fontes de execução/contrato; o [roadmap](roadmap.md)
mantém os estados. Os ADRs históricos foram preservados. Após o aceite da proposta
pontual, `AGENTS.md` foi atualizado na preparação do encerramento para descrever
a integração existente, a porta implementada e as capacidades que continuam futuras,
sem alterar as fronteiras arquiteturais ou presumir aprovação formal das entregas.

## Entrega 3.4 — validação vertical

**Implementada e validada; aprovação técnica final em 13/11/2026 e versionamento autorizado em `27e950e`.**
A 3.3 foi encerrada e versionada em `04081ca`. Esta validação não acrescenta
capacidades de negócio, endpoints ou mudanças de segurança às aplicações.

### Ambiente e reprodução

Requisitos: Java 21, Docker disponível e Gradle Wrapper do repositório. Na raiz:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:test --tests '*FluxoConsultaAutorizadaVerticalTests'
.\gradlew.bat clean build
git diff --check
```

O teste está em `financiamento.consultarecebiveis.integracao`, no source set de
testes do Banco DUP. A tarefa `test` gera o `bootJar` da Signatária e fornece seu
caminho à fixture `SignatariaReal`; não inclui classes da Signatária no classpath
do Banco DUP. O artefato é registrado como input `signatariaBootJar` da tarefa,
com `PathSensitivity.NONE`, para rastrear alterações de conteúdo independentemente
de seu caminho. Foram reutilizados os módulos Testcontainers JUnit/PostgreSQL já
adotados na Signatária, agora também como dependências de teste do Financiamento.
Awaitility vem do starter de testes existente.

A fixture inicia PostgreSQL `postgres:18-alpine` descartável e o JAR da Signatária
em JVM separada, com `server.port=0`, loopback e perfil `local`. Descobre a porta
no log de bootstrap e confirma `/actuator/health`. A Signatária aplica Flyway
V1–V3 e valida o schema JPA. A fixture confere as três migrations bem-sucedidas.
Não é necessário iniciar Compose nem ocupar previamente as portas 8080/8081.

São provisionados três Sacadores habilitados, dois Financiadores e Bearers
aleatórios por execução. Os CNPJs fictícios são `12ABC34501DE35` (principal,
alfanumérico), `11222333000181` (outro Sacador) e `11444777000161` (sem títulos).
O UUID do Financiador principal coincide intencionalmente com o do Sacador
principal para exercitar a separação de papéis. Somente hashes dos Bearers são
provisionados na Signatária, por configuração efêmera no ambiente do processo.
Não são gravados tokens ou arquivos de credenciais. O teste verifica ausência
dos Bearers no log da Signatária.

O único acesso SQL da fixture provisiona o cadastro dos Sacadores, que não tem
endpoint, e confere Flyway. Não insere nem altera autorizações, processamentos ou
duplicatas. As operações do Banco DUP continuam exclusivamente por HTTP.
Os contextos do Banco DUP usam `FinanciamentoApplication`, perfil `local` e
`WebApplicationType.NONE`, sem controller ou servidor público de teste. A
configuração existente recebe URL dinâmica, Bearer e timeouts de 2s/5s. O teste
obtém o bean `ServicoConsultaRecebiveis` e verifica o adapter real.

### Sequência comprovada

1. O Sacador envia POST `/api/v1/escrituracoes/manuais` com chave idempotente;
   recebe 202, estado PENDENTE e Location do processamento.
2. O agendador real, habilitado com polling de 100ms, processa as solicitações.
   O teste consulta GET `/api/v1/escrituracoes/processamentos/{id}` até CONCLUIDO,
   com prazo de 20s por emissão, sem chamada direta ao worker ou repository.
3. São escriturados três títulos principais (100.01, 999999999999.99 e 0.01) e
   um título de outro Sacador. IDs e instantes de Escrituração são recuperados
   do processamento persistido; os demais dados esperados vêm da emissão.
4. O Banco DUP solicita autorização pelo serviço, porta e `RestClient` reais.
   O POST `/api/v1/autorizacoes-consulta` produz PENDENTE e o Financiador correto.
   Consultar Duplicatas nesse estado lança `FalhaRegistradora(ACESSO_NEGADO)`.
5. O Sacador correspondente envia PUT `/api/v1/autorizacoes-consulta/{id}/decisao`
   com APROVAR. O Banco DUP confirma ATIVA, vigente e beneficiário correto via GET.
6. O Banco DUP consulta GET `/api/v1/autorizacoes-consulta/{id}/duplicatas` nas
   páginas 0/1 com tamanho 2, uma página além do fim e com os padrões 0/50.
   Compara os seis campos de cada título com os dados escriturados, usando
   `BigDecimal`, e confere CNPJ completo, LOCAL_SIMULADO e metadados de paginação.
   Confere ordenação por `escrituradaEm, id` e repetição determinística da página.
   Nenhum título de outro Sacador aparece.
7. Após nova página 0, o Sacador envia DELETE da autorização e recebe 204 após
   commit. O Banco DUP confirma REVOGADA e a página 1 é negada com ACESSO_NEGADO.

### Cenários negativos e idempotência

| Cenário | Evidência observada |
|---|---|
| PENDENTE e RECUSADA | ACESSO_NEGADO no Banco DUP, sem fallback vazio |
| REVOGADA entre páginas | Próxima página negada após DELETE confirmado |
| ATIVA expirada | Continua ATIVA, `vigente=false`; nova consulta negada |
| Outro Financiador | RECURSO_NAO_ENCONTRADO para autorização alheia |
| ID inexistente | Mesmo 404, código, título e mensagem de recurso alheio |
| Bearer inválido | AUTENTICACAO_REJEITADA pelo adapter real |
| Outro Sacador tenta aprovar | HTTP 404 e autorização permanece PENDENTE |
| Sacador usa endpoint de duplicatas | HTTP 403, mesmo com UUID igual ao Financiador |
| Sacador sem títulos | Página vazia 0/50, sem próxima página, somente após aprovação |
| Página negativa, tamanho 0 ou 101 | Validação local e HTTP 400 na Signatária |
| Repetição idempotente válida | Mesmo ID/representação original e Location, HTTP 201, inclusive após aprovação |
| Mesma chave/conteúdo diferente | CONFLITO no Banco DUP e HTTP 409 remoto |

`instance` nos Problems 404 ecoa o caminho público solicitado e naturalmente
difere entre IDs. A comparação verifica a mesma semântica de erro, sem exigir
igualdade de caminhos diferentes. O primeiro ensaio teve 8/9 casos aprovados;
a asserção foi corrigida, sem modificar produção, e a execução específica
seguinte aprovou 9/9 casos.

Validação inicial em 13/11/2026: o comando específico passou em 39s. O build completo
`.\gradlew.bat clean build` passou em 1min43s com 263 testes: 100 do Financiamento
(nove verticais) e 163 da Signatária, sem falhas, erros ou ignorados. As regressões
anteriores foram preservadas. `git diff --check` passou; não houve commit ou push
nesta entrega. O aviso de CDS do OpenJDK nos testes com instrumentação não impediu
o build.

Na expiração utiliza-se o relógio real: o fim é solicitado 30s à frente,
cobrindo quatro chamadas iniciais de até 5s cada, mais 10s de margem. A consulta
é permitida antes e o teste aguarda `vigente=false` via HTTP por até 45s,
com 15s adicionais de tolerância. A inicialização tem prazo de 60s;
cada chamada técnica tem timeout de 5s.
As esperas verificam condições com Awaitility, sem `Thread.sleep` arbitrário.

Na revisão final do Code Review em 13/11/2026, os nove testes verticais passaram
com a margem de 30s/45s. Sem alterações, a tarefa ficou `UP-TO-DATE`; ao adicionar
somente um recurso inerte ao JAR gerado, o Gradle identificou mudança no input
`signatariaBootJar` e reexecutou os nove casos com sucesso. O artefato original
foi restaurado e conferido por SHA-256. O novo `.\gradlew.bat clean build`, sem
exclusões de tarefas, passou em 2min38s: 263 testes (100 do Financiamento e 163 da
Signatária), sem falhas, erros ou ignorados. `git diff --check` passou.

Os processos e contextos são encerrados ao final e Testcontainers remove o banco.
Logs temporários são removidos em sucesso; em falha são preservados no diretório
temporário do JUnit para diagnóstico. Não há limpeza ou escrita no Compose local.

### Limites e decisões

**Problema → alternativas → decisão → trade-offs → consequências:** executar as
duas aplicações reais sem compartilhar classes → dependência entre projetos de
produção, execução manual ou JAR em processo separado → JAR real e contexto Spring
do Banco DUP no teste → maior duração e dependência de Docker/build do artefato,
com descoberta da porta pelo log de bootstrap → fronteiras preservadas, sem nova
API, aplicação executável do projeto ou dependência de produção.

O teste comprova o fluxo vertical do simulador. O Banco DUP não inicia servidor
HTTP, pois não possui endpoints desses casos de uso. Não há integração regulatória
real, Agenda completa, IUD, interoperabilidade, análise de crédito ou cálculo de
disponibilidade. `valorNominal` continua sendo valor de emissão, e `consultadaEm`
continua uma referência local do Banco DUP, sem snapshot entre páginas. Não há
retry automático, cache ou circuit breaker. Os testes de transporte da 3.3 permanecem.
Nenhum defeito funcional das entregas anteriores foi identificado nesta validação.
AGENTS e ADRs continuam coerentes e não foram alterados. A aprovação técnica final
da 3.4 foi registrada em 13/11/2026; após autorização explícita, os sete arquivos
foram versionados em `27e950e`, sem mudanças da 4.1 nesse commit e sem push.
