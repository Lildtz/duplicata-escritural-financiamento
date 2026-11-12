# Escrituração Manual — Entrega 1

## Escopo e limites

Esta entrega recebe uma solicitação por POST, confirma sua persistência e permite
consultá-la por GET. **Todo processamento permanece PENDENTE indefinidamente.**
Não há execução assíncrona, agendamento, retentativas ou criação de Duplicata.
O recebimento não declara emissão de título, Registro, apresentação ao Sacado,
aceite, comprovação de lastro ou conformidade regulatória integral.

Os contratos abaixo são próprios do simulador. A referência documental B3 é o
[Manual de Operações, seções 7.1.1 e 7.1.3](https://www.b3.com.br/data/files/00/B5/6B/D0/8F17F910FC0436F9AC094EA8/Duplicata%20Escritural%20-%20Manual%20Operacional.pdf),
que documenta solicitação manual e consulta de processamento; não reproduzimos
seus endpoints, envelopes, identificadores ou estados. Os ADRs históricos são preservados.

O ADR 0001 atribui a geração do IUD à Escrituração; essa parte está adiada.
O ADR 0002 deixa seu formato em aberto. O ADR 0003 mantém Escrituração e Registro
na mesma Signatária, com responsabilidades distintas. O UUID retornado nesta
entrega identifica somente o processamento; não é um IUD.

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
de autenticação, retorna `200` e a mesma representação. A consulta usa o ID e o
Sacador autenticado; não expõe o payload ou dados de outros Sacadores.

| Situação | HTTP |
|---|---|
| Token ausente, inválido ou associado a cadastro inexistente | 401, com WWW-Authenticate: Bearer |
| Sacador desabilitado | 403 |
| Processamento inexistente ou pertencente a outro Sacador | 404 |
| JSON, campo desconhecido, UUID ou chave inválidos | 400 |
| Violação das validações da solicitação | 422 |
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

A migration `V1__recebimento_escrituracao.sql` acrescenta somente `sacador` e
`processamento_escrituracao` ao schema existente `escrituradora`.
Não altera banco, histórico Flyway anterior, variáveis ESCRITURADORA_DB_*, Compose,
volumes, JPA ou porta 8081. Não existem dados cadastrais padrão em migrations.

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
  Deduplicação de títulos pertence à entrega de criação da Duplicata, ainda ausente.
* Não existe constraint única isolada por referência fiscal nem expiração automática
  das chaves. Retenção e descarte exigem decisão explícita futura.

Spring Data JPA é o mecanismo principal: `Sacadores` usa `findById` e
`ProcessamentosEscrituracao` usa consultas derivadas para chave idempotente e
ID/Sacador. Ambas são interfaces `JpaRepository`; não há CRUD manual na aplicação.

A única operação JDBC em produção é `InsercaoIdempotenteProcessamento.inserirSeAusente`,
que mantém `INSERT ... ON CONFLICT ON CONSTRAINT uk_processamento_idempotencia DO NOTHING`.
Sua vantagem concreta é arbitrar atomicamente a disputa pela chave sem abortar a
transação por violação de unicidade. Substituí-la por `save` e captura da exceção
não preservaria essa propriedade dentro da mesma transação.
JDBC e JPA usam o DataSource e o gerenciamento transacional Spring existentes.
Não há limpeza manual do contexto JPA ou alteração dos limites transacionais.

SQL nos testes serve para fixtures independentes do mapeamento, alterações de
cadastro externas à sessão JPA e verificações do schema/Flyway. Não implementa CRUD de produção.
Spring Security continua sendo a única dependência adicionada à stack inicial.

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
    │   └── ServicoEscrituracao
    ├── dominio
    │   ├── ChaveIdempotencia
    │   ├── Cnpj
    │   ├── ConflitoIdempotencia
    │   ├── DadosEscrituracao
    │   ├── Endereco
    │   ├── FotografiaSacador
    │   ├── ProcessamentoEscrituracao
    │   └── Sacador
    └── infraestrutura
        └── persistencia
            ├── CnpjConverter
            ├── InsercaoIdempotenteProcessamento
            ├── ProcessamentosEscrituracao
            └── Sacadores
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
O acesso JPA é por campos, sem setters públicos. O processamento é marcado
`@Immutable` nesta entrega; os dados e a fotografia continuam records imutáveis.
O `@Immutable` na entidade inteira e o CHECK `estado = 'PENDENTE'` da migration V1
são restrições temporárias da Entrega 1, que somente recebe e consulta solicitações.
Antes de implementar transições em uma entrega futura, será necessário rever esse
mapeamento e evoluir o CHECK por uma nova migration, preservando o histórico V1.
Isso não autoriza alterar a fotografia ou os dados originais da solicitação.
Nenhum novo estado ou mudança de schema faz parte desta revisão.
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
Cobrem autenticação, validação, isolamento, snapshots, commit, idempotência concorrente
e reaplicação de Flyway sem alteração do histórico. A coordenação concorrente usa
latches e futures com timeout, sem Thread.sleep.
