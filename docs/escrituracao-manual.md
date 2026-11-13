# Escrituração Manual — Entregas 1 e 2

## Escopo e limites

O POST confirma o recebimento durável e o GET consulta o estado atual. A Entrega 2
acrescenta processamento assíncrono interno, retentativas limitadas e criação de
Duplicata local simulada. O agendamento exige habilitação explícita e fica desabilitado
por padrão. A implementação permanece sujeita ao Code Review da Entrega 2.
CONCLUIDO significa somente criação local no simulador. Não declara Registro,
apresentação ao Sacado, aceite, comprovação de lastro ou conformidade regulatória integral.
Consulte [execução, estados, configuração e recuperação](processamento-assincrono-escrituracao.md).

Os contratos abaixo são próprios do simulador. A referência documental B3 é o
[Manual de Operações, seções 7.1.1 e 7.1.3](https://www.b3.com.br/data/files/00/B5/6B/D0/8F17F910FC0436F9AC094EA8/Duplicata%20Escritural%20-%20Manual%20Operacional.pdf),
que documenta solicitação manual e consulta de processamento; não reproduzimos
seus endpoints, envelopes, identificadores ou estados. Os ADRs históricos são preservados.

O ADR 0001 atribui a geração do IUD à Escrituração; essa parte está adiada.
O ADR 0002 deixa seu formato em aberto. O ADR 0003 mantém Escrituração e Registro
na mesma Signatária, com responsabilidades distintas. O UUID retornado nesta
entrega identifica somente o processamento; não é um IUD. O `idDuplicata` também
é um UUID técnico interno, distinto de um futuro IUD regulatório, ainda não gerado.

## Contratos REST

POST `/api/v1/escrituracoes/manuais`, com `Authorization: Bearer <token-local>` e
`Idempotency-Key` obrigatório (1 a 128 caracteres ASCII: letras, números, ponto,
dois-pontos, hífen e sublinhado; comparação sensível a maiúsculas).

```json
{
  "sacado": {
    "cnpj": "12.ABC.345/01DE-35",
    "nome": "Sacado de Demonstração",
    "domicilio": {
      "logradouro": "Rua de Demonstração",
      "numero": "10",
      "bairro": "Centro",
      "cep": "01001-000",
      "municipioIbge": "3550308",
      "uf": "SP"
    }
  },
  "tipoDuplicata": "MERCANTIL",
  "numeroDuplicata": "DUP-1",
  "numeroFatura": "FAT-1",
  "numeroParcela": 1,
  "dataFaturamento": "2026-11-01",
  "dataVencimento": "2026-12-01",
  "valorFatura": 100.00,
  "valorDuplicata": 100.00,
  "documentoFiscal": { "tipo": "NFE", "numero": "123", "serie": "1" }
}
```

O Sacador é obtido exclusivamente do token e do cadastro local; `sacadorCnpj`
e demais campos desconhecidos são rejeitados. O exemplo pressupõe início operacional
até a data de faturamento; ajuste as datas à demonstração.

Após o commit, retorna `202 Accepted`, com `Location` apontando para o GET:

```json
{
  "idProcessamento": "<UUID técnico>",
  "status": "PENDENTE",
  "recebidoEm": "2026-11-12T15:00:00Z"
}
```

GET `/api/v1/escrituracoes/processamentos/{idProcessamento}`, com o mesmo esquema
de autenticação, retorna `200` e o estado atual. A consulta usa o ID e o Sacador
autenticado; não expõe o payload ou dados de outros Sacadores.

O POST mantém a representação original de recebimento, inclusive após conclusão
ou falha: `202`, mesmo Location, ID, `recebidoEm` e `status: PENDENTE`. Use o GET
para acompanhar a execução. A comparação de conteúdo idempotente e o 409 são preservados.

O GET preserva os três campos originais e amplia os valores de `status`:

| Status | Campos adicionais |
|---|---|
| PENDENTE, EM_PROCESSAMENTO | Nenhum |
| CONCLUIDO | `idDuplicata`, `escrituradaEm`, `finalizadoEm` |
| REJEITADO | `codigo`, `motivo`, `finalizadoEm` |
| FALHA_TECNICA | `codigo`, `motivo`, `finalizadoEm` seguros |

Campos sem aplicação são omitidos. Clientes que restringiam o enum a PENDENTE
precisam aceitar os novos estados do GET; endpoints, autenticação e POST são preservados.
Os códigos e exemplos terminais estão no [guia de processamento](processamento-assincrono-escrituracao.md).

| Situação | HTTP |
|---|---|
| Token ausente, inválido ou associado a cadastro inexistente | 401, com WWW-Authenticate: Bearer |
| Sacador desabilitado | 403 |
| Processamento inexistente ou pertencente a outro Sacador | 404 |
| JSON, campo desconhecido ou chave inválidos | 400 |
| Violação das validações da solicitação ou UUID de processamento malformado | 422 |
| Mesma chave com conteúdo normalizado diferente | 409 |
| Falha de persistência reconhecidamente transitória ou recuperável | 503 |
| Falha de persistência não transitória ou sem classificação segura | 500 |

As respostas de persistência são genéricas, sem mensagens de exceção, SQL, nomes
de tabelas ou stack traces. No caso de uso, a classificação também considera causas
transitórias/recuperáveis encapsuladas em exceções de transação. Erros de integridade
ou uso incorreto da persistência não se tornam automaticamente indisponibilidade.
Um 503 não aciona retry no servidor nem comprova que nada foi confirmado; eventual
reenvio do cliente deve manter a mesma chave idempotente e o mesmo conteúdo.

## Validação e normalização

As restrições de mercantil, pessoas jurídicas, NF-e, parcela única e ausência de
ajustes são **simplificações do simulador**, não regras universais do domínio.

* CNPJ: formato compacto ou máscara convencional; espaços externos removidos,
  letras ASCII convertidas para maiúsculas, zeros preservados e DVs validados.
  Não se removem caracteres arbitrários. A implementação usa o algoritmo oficial
  [ASCII menos 48 e módulo 11](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf).
  Validade estrutural não comprova existência cadastral.
* Nomes, identificadores e endereço: espaços externos removidos, tamanho limitado,
  sem controles. Números da duplicata/fatura preservam maiúsculas e zeros.
* CEP: oito algarismos canônicos; município: sete algarismos; UF brasileira válida.
  Não há consulta de CEP ou da tabela IBGE.
* Número/série fiscal: somente algarismos, até nove/três posições, normalizados
  sem zeros à esquerda. Não há consulta à Receita ou SEFAZ.
* Valores: positivos, até 999999999999.99, com duas casas canônicas e sem
  arredondamento de frações de centavo. O valor do título deve igualar o da fatura.
* Parcela: somente 1, inteira. Vencimento não pode anteceder o faturamento.
* Faturamento: não anterior à data de início operacional e não futuro na data
  UTC do recebimento. A comparação por dia é uma simplificação temporal local;
  não comprova o início operacional regulado nem a efetiva data de faturamento.

## Persistência e idempotência

A migration `V1__recebimento_escrituracao.sql` acrescentou `sacador` e
`processamento_escrituracao` ao schema existente `escrituradora` e permanece preservada.
A V2 cria `duplicata` e evolui o processamento por ALTER TABLE, preservando pedidos
existentes em PENDENTE, com zero tentativas e elegibilidade inicial em `recebidoEm`.
Não altera banco, histórico Flyway anterior, variáveis ESCRITURADORA_DB_*, Compose,
volumes, configuração JPA ou porta 8081. Não existem dados cadastrais padrão em migrations.

O processamento guarda `FotografiaSacador` e os dados normalizados como valores
imutáveis, separados da entidade cadastral `Sacador`. Alterar o cadastro posteriormente
não reescreve o pedido. Não há operações de atualização ou exclusão pela API.

A constraint única cobre `(sacador_id, operacao, chave_idempotencia)`.
O serviço usa uma inserção SQL especializada seguida de leitura JPA em transação
READ COMMITTED. Uma disputa espera a decisão da transação concorrente;
o conteúdo vencedor é comparado estruturalmente, sem hash ou lock em memória.
O controlador recebe o resultado somente após a confirmação transacional.

* Chave e conteúdo equivalentes: mesmo ID, instante e resposta 202, sem novo registro.
* Chave e conteúdo diferentes: 409, sem modificar o original.
* Mesma chave em Sacadores diferentes: solicitações independentes.
* Rotação do token não modifica o escopo: a identidade é a do Sacador, não a credencial.
* Chaves novas criam pedidos distintos, mesmo com título ou referência fiscal iguais.
  Na execução, as constraints comerciais impedem a criação de um segundo título.
* Não existe constraint única isolada por referência fiscal nem expiração automática
  das chaves. Retenção e descarte exigem decisão explícita futura.

Spring Data JPA é o mecanismo principal: `SacadorRepository` usa `findById` e
`ProcessamentoEscrituracaoRepository` usa consultas derivadas para chave idempotente e
ID/Sacador. Ambas são interfaces `JpaRepository`; não há CRUD manual na aplicação.

A operação JDBC `InsercaoIdempotenteProcessamento.inserirSeAusente`
mantém `INSERT ... ON CONFLICT ON CONSTRAINT uk_processamento_idempotencia DO NOTHING`.
Sua vantagem concreta é arbitrar atomicamente a disputa pela chave sem abortar a
transação por violação de unicidade. Substituí-la por `save` e captura da exceção
não preservaria essa propriedade dentro da mesma transação.
JDBC e JPA usam o DataSource e o gerenciamento transacional Spring existentes.
Não há limpeza manual do contexto JPA. A seleção concorrente de trabalho adiciona
uma consulta nativa para SKIP LOCKED; Duplicata e transições usam JPA.

SQL nos testes serve para fixtures independentes do mapeamento, alterações de
cadastro externas à sessão JPA e verificações do schema/Flyway. Não implementa CRUD de produção.
Na Entrega 1, Spring Security foi a dependência acrescentada à stack inicial.

## Organização e trade-off de persistência

```text
br.com.duplicataescritural.signataria
├── SignatariaApplication
├── seguranca
│   ├── FiltroTokenLocal
│   ├── SacadorAutenticado
│   ├── SegurancaEscrituracao
│   └── TokensLocais
└── escrituracao
    ├── api
    │   ├── EscrituracaoController
    │   ├── ErrosEscrituracao
    │   └── SolicitacaoEscrituracaoRequest
    ├── aplicacao
    │   ├── ServicoEscrituracao
    │   ├── ServicoProcessamentoEscrituracao
    │   ├── ConfiguracaoProcessamento
    │   └── AgendadorEscrituracao
    ├── dominio
    │   ├── ChaveIdempotencia
    │   ├── Cnpj
    │   ├── ConflitoIdempotencia
    │   ├── DadosEscrituracao
    │   ├── Endereco
    │   ├── FotografiaSacador
    │   ├── ProcessamentoEscrituracao
    │   ├── Duplicata
    │   ├── ReservaExecucao
    │   ├── CodigoResultado
    │   ├── RejeicaoEscrituracao
    │   └── Sacador
    └── infraestrutura
        ├── ConfiguracaoEscrituracao
        └── persistencia
            ├── CnpjConverter
            ├── InsercaoIdempotenteProcessamento
            ├── ProcessamentoEscrituracaoRepository
            ├── DuplicataRepository
            └── SacadorRepository
```

`api` contém DTOs e conversão HTTP, sem reproduzir validações financeiras.
O DTO de entrada e seus grupos são convertidos em valores do domínio, onde continuam
todas as normalizações e invariantes. A resposta HTTP permanece no controlador.
`aplicacao` orquestra e delimita transações, sem depender de HTTP ou da identidade
Spring Security. `seguranca` estabelece `SacadorAutenticado`, contendo os dados
imutáveis capturados durante a autenticação; não expõe uma entidade JPA como principal.

`Sacador` e `ProcessamentoEscrituracao` são as próprias entidades JPA do domínio,
sem um segundo modelo persistente. O trade-off é aceitar anotações JPA/Hibernate e
construtores protegidos sem argumentos no domínio, reduzindo isolamento em relação
ao ORM e portabilidade do JSONB. Em troca, evitamos entidades e mapeadores duplicados.
O acesso JPA é por campos, sem setters públicos. O processamento deixa de ter
`@Immutable`, mantendo chave, dados, fotografia, Sacador e recebimento com
`updatable = false`. Somente métodos explícitos mudam o estado da execução.
Duplicata é `@Immutable` e reutiliza os dados imutáveis da emissão. Seus componentes
de identidade comercial também são colunas, com CHECK de consistência com o JSON.
Os novos estados e atributos são introduzidos pela V2, sem alterar V1.
`FotografiaSacador` é um valor histórico, não outra entidade ou cadastro.
Seu JSON preserva exatamente a estrutura anterior, sem migração de dados.

O `CnpjConverter` fica na infraestrutura e converte o Value Object para VARCHAR,
sem alterar seu algoritmo ou formato persistido. Não há dependência do domínio em
DTOs HTTP, controllers ou classes da infraestrutura. Os testes acompanham os packages
de suas responsabilidades. Nenhum módulo Gradle, port ou interface de serviço foi criado.

## Provisionamento exclusivamente local

Sem perfil `local`, as rotas de Escrituração ficam bloqueadas. Actuator health/info
continua público. Com `local`, a aplicação exige uma lista explícita de impressões
SHA-256; configuração ausente, inválida ou ambígua impede a inicialização.
Não há usuário/senha gerados, autenticação Basic, sessão, JWT, OAuth2 ou credencial padrão.

1. Inicie o PostgreSQL e a aplicação sem perfil conforme o [guia local](../infra/local/README.md),
   para aplicar Flyway. Encerre a aplicação antes de reiniciá-la com o perfil local.
2. Cadastre um Sacador de demonstração no PostgreSQL local usando `psql`:

```sql
INSERT INTO escrituradora.sacador
    (id, cnpj, nome, domicilio, inicio_operacao, habilitado)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    '11222333000181',
    'Sacador de Demonstração',
    '{"logradouro":"Rua de Demonstração","numero":"10","bairro":"Centro","cep":"01001000","municipioIbge":"3550308","uf":"SP"}'::jsonb,
    DATE '2026-01-01', true
);
```

O ID e o cadastro acima são fictícios e não são credenciais. Utilize uma ferramenta
SQL ou `docker compose -f infra/local/docker-compose.yml exec postgres-escrituradora psql`,
informando o usuário e o banco que já utiliza no Compose. Não crie cadastro de Sacador externo.

3. Em PowerShell, na raiz do repositório, gere um token e salve **somente seu hash**
   no arquivo ignorado `.env.signataria.local.yml`:

```powershell
$bytesToken = New-Object byte[] 32
$geradorToken = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $geradorToken.GetBytes($bytesToken) } finally { $geradorToken.Dispose() }
$tokenLocal = [Convert]::ToBase64String($bytesToken).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$algoritmoHash = [Security.Cryptography.SHA256]::Create()
try {
    $digestLocal = $algoritmoHash.ComputeHash([Text.Encoding]::ASCII.GetBytes($tokenLocal))
} finally { $algoritmoHash.Dispose() }
$hashLocal = [BitConverter]::ToString($digestLocal).Replace('-', '').ToLowerInvariant()
$configLocal = Join-Path (Get-Location).Path '.env.signataria.local.yml'
$conteudoLocal = @"
signataria:
  autenticacao-local:
    tokens:
      - sha256: '$hashLocal'
        sacador-id: '10000000-0000-0000-0000-000000000001'
"@
[IO.File]::WriteAllText($configLocal, $conteudoLocal, (New-Object Text.UTF8Encoding($false)))
git check-ignore .env.signataria.local.yml
.\gradlew.bat :duplicata-escritural-signataria-api:bootRun "--args=--spring.profiles.active=local --spring.config.additional-location=file:$configLocal"
```

O token permanece na variável `$tokenLocal`; não o imprima, versione ou coloque em
argumentos de linha de comando. Use um cliente HTTP que permita configurar o Bearer
sem colocá-lo em arquivos versionados. Em outra execução, gere uma credencial nova;
remover/substituir a impressão e reiniciar revoga o acesso da credencial anterior.
É possível configurar duas impressões diferentes para o mesmo Sacador durante rotação.

Este mecanismo não é autenticação de produção. Use-o somente em ambiente local
controlado. Ele identifica o Sacador representado, sem individualizar operadores
humanos ou comprovar juridicamente uma ordem de emissão. Não habilite logs de
cabeçalhos HTTP nem logs TRACE de parâmetros JDBC com dados reais.

## Verificação

```powershell
.\gradlew.bat projects
.\gradlew.bat clean build
git diff --check
git status --short
```

Os testes usam tokens aleatórios em memória e PostgreSQL descartável via Testcontainers.
Cobrem autenticação, validação, isolamento, snapshots, commit, idempotência concorrente,
processamento e estados terminais, constraints comerciais, rollback após INSERT,
retentativas, abandono e execução atrasada. A migração incremental aplica V1, insere
um pedido antigo e aplica V2 preservando conteúdo e checksum. O teste JPA continua
lendo JSON no formato anterior. Triggers no PostgreSQL descartável injetam falhas;
um Clock controlável determina backoff e expiração. A coordenação concorrente usa
latches e futures com timeout, sem Thread.sleep.

## OpenAPI 3 e Swagger UI — Entrega 4.1

**Aprovada tecnicamente em 13/11/2026, com Code Review encerrado favoravelmente; versionamento pendente de autorização.** A validação da UI foi HTTP, sem inspeção visual efetiva. A 3.4 está isolada no commit
`27e950e`; esta entrega acrescenta documentação, sem novas capacidades de negócio.
O starter `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0` está somente na
Signatária. A [matriz oficial](https://springdoc.org/faq.html#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot)
indica Spring Boot 4.1.x com springdoc 3.1.x–3.2.x; o projeto mantém Boot 4.1.1.

### Acesso e autenticação

Inicie PostgreSQL e provisione as identidades locais conforme os guias anteriores.
O [guia local](../infra/local/README.md#openapi-e-swagger-ui-entrega-41) contém o
comando de inicialização com `local` e o arquivo ignorado de identidades.

* UI: `http://localhost:8081/swagger-ui.html`, redirecionando para `/swagger-ui/index.html`.
* JSON gerado: `http://localhost:8081/v3/api-docs`.
* Configuração da UI: `/v3/api-docs/swagger-config`.

São as URLs padrão; adapte a origem se a porta for configurada. Somente GET dessas
rotas e `/swagger-ui/**` está liberado sem Bearer no perfil `local`. As propriedades
`springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` são falsas por padrão
e verdadeiras somente em `application-local.yml`. Os filtros de negócio permanecem
ativos; fora de `local` as rotas documentais também são bloqueadas por segurança.
Actuator não integra a especificação de negócio.

Em **Authorize**, informe apenas o token opaco fictício provisionado pelo operador;
a UI acrescenta o prefixo Bearer. Não há token padrão, JWT, OAuth2, OpenID Connect
ou endpoint de emissão. O token identifica o participante e seu papel; o Banco DUP
não define livremente o beneficiário por headers. Aprovação, revogação e consultas
continuam sujeitas às regras reais. `persist-authorization=false` evita persistir
a credencial da UI entre sessões. Não compartilhe capturas ou requisições com tokens.

### Cobertura dos contratos

O JSON OpenAPI 3.0 é gerado dos controllers e DTOs existentes; não há arquivo estático
gerado versionado nem modelos HTTP paralelos. As operações são agrupadas em
Escrituração Manual, Processamentos, Autorizações de consulta e Consulta de Duplicatas.

| Método e caminho | Papel | Sucesso |
|---|---|---|
| POST `/api/v1/escrituracoes/manuais` | Sacador | 202 e Location |
| GET `/api/v1/escrituracoes/processamentos/{idProcessamento}` | Sacador correspondente | 200 |
| POST `/api/v1/autorizacoes-consulta` | Financiador | 201 e Location |
| GET `/api/v1/autorizacoes-consulta/{id}` | Sacador correspondente ou Financiador beneficiário | 200 |
| PUT `/api/v1/autorizacoes-consulta/{id}/decisao` | Sacador correspondente | 200 |
| DELETE `/api/v1/autorizacoes-consulta/{id}` | Sacador correspondente ou Financiador beneficiário | 204, sem corpo |
| GET `/api/v1/autorizacoes-consulta/{id}/duplicatas` | Financiador beneficiário, autorização ATIVA e vigente | 200 |

Os schemas descrevem UUID, CNPJ completo numérico/alfanumérico, valores decimais,
datas, instantes, estados, campos obrigatórios e campos omitidos conforme o estado.
Os dois POSTs exigem Idempotency-Key; suas repetições válidas mantêm respectivamente
202/201, representação original e Location. Paginação usa `pagina=0`, `tamanho=50`,
máximo 100; não aceita sort ou expansão de escopo pelo cliente. Cada página revalida
a autorização, inclusive após revogação e expiração; ATIVA não equivale a vigente.

Os erros permanecem distintos: segurança retorna `status/title`; handlers de
Escrituração retornam Problem Detail; autorizações acrescentam `codigo`. Respostas
403/500/503 documentam as alternativas possíveis entre filtro e handler, sem
uniformizar o JSON. Há 400/401/403/404/409/422/500/503 somente nas operações aplicáveis.
O 404 alheio/inexistente é genérico. Não há contrato de rate limiting/429.

A validação HTTP da 4.1 identificou uma inconsistência no texto anterior: UUID
malformado no GET de processamento retorna 422, pois a causa IllegalArgumentException
é tratada pelo handler existente. O teste compara esse resultado com a documentação;
o handler e a semântica de negócio foram preservados. GET de autorização com UUID
malformado continua documentado com 400 pelo seu handler específico.

### Testes e limites

Com Java 21 e Docker, execute na raiz:

```powershell
.\gradlew.bat :duplicata-escritural-signataria-api:test --tests '*OpenApiHttpTests' --tests '*SignatariaApplicationTests'
.\gradlew.bat :duplicata-escritural-signataria-api:test --tests '*OpenApiHttpTests' --tests '*SignatariaApplicationTests' --tests '*EscrituracaoHttpTests' --tests '*AutorizacaoConsultaHttpTests'
.\gradlew.bat clean build
git diff --check
git status --short
```

Os testes usam HTTP real, portas dinâmicas, PostgreSQL/Testcontainers, Flyway e
filtros ativos. Verificam as sete operações e ausência de Actuator, schemas e
formatos, obrigatoriedade, respostas, Bearer, idempotência, paginação, recursos da UI,
negócio protegido e bloqueio sem perfil local. O primeiro ensaio específico detectou
problemas na documentação/assertions e na fixture; foram corrigidos sem alterar
handlers ou regras de negócio. A execução seguinte com regressões passou em
61 testes, sem falhas, erros ou ignorados. Em 13/11/2026, o build final completo
passou em 2min24s: 270 testes (100 do Financiamento e 170 da Signatária), sem
falhas, erros ou ignorados, incluindo os nove testes verticais. `git diff --check`
passou. Compilação sem avisos novos; o aviso de CDS do OpenJDK nos testes com
instrumentação permanece sem impedir o build.

A Signatária real também foi iniciada pelo JAR no perfil `local`, com porta dinâmica,
PostgreSQL descartável, Flyway e identidades efêmeras, confirmando health 200.
A inspeção visual foi tentada com Computer Use, mas a ferramenta interrompeu a
automação por não conseguir determinar com confiança a URL atual do Chrome no
Windows para aplicar sua política. Não houve inspeção visual efetiva: o carregamento
da UI e de seus recursos está comprovado pelos testes HTTP. O processo e o container
adicionais foram encerrados. O comando interativo `bootRun` com arquivo do operador
não foi executado; as configurações locais foram exercitadas pelos testes e pelo JAR.

**Problema → alternativas → decisão → trade-offs → consequências:** manter contratos
consultáveis e sincronizados → especificação estática ou geração dos controllers →
springdoc com anotações pontuais e uma configuração local → dependência de documentação
e metadados nos DTOs/controladores, sem novos modelos ou geradores → contratos
verificados pelo JSON gerado; erro de anotação pode falhar o teste antes da revisão.
OpenAPI 3.0 foi escolhido explicitamente para manter formatos e restrições numéricas
convencionais. A geração automática de erros globais foi desabilitada para documentar
os handlers aplicáveis sem atribuir respostas de uma capacidade à outra.

Swagger não amplia o domínio: Duplicata local não é Agenda regulatória completa,
valor nominal não é saldo financiável, UUID não é IUD. Registro, interoperabilidade,
crédito e monitoramento centralizado continuam futuros. A instrumentação fundamental
é descrita na [Entrega 4.2](observabilidade.md). O Banco DUP permanece sem
API de entrada desses casos de uso e sem dependência springdoc. AGENTS e ADRs não
foram alterados. A aprovação técnica da 4.1 foi registrada em 13/11/2026, com Code
Review encerrado favoravelmente. Após autorização explícita, a 4.1 foi versionada em `03290c8`.
