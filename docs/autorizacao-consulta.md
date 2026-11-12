# Entrega 3.1 — identidades e autorização local de consulta

Esta capacidade é uma **simplificação do simulador**, não um Opt-in regulatório
completo nem um contrato oficial B3. Não implementa Registro, Agenda, consulta de
Duplicatas pelo Financiador ou integração HTTP do Banco DUP. Não comprova anuência
jurídica de representantes humanos. Os ADRs 0001–0003 permanecem preservados.

O package funcional é `consultarecebiveis`, com `api`, `aplicacao`, `dominio` e
`infraestrutura/persistencia`. Poderá ser integrado ao contexto de Registro quando
ele existir. Não há novo projeto Gradle, dependência, serviço executável ou banco.
Os valores técnicos CNPJ e chave idempotente são reutilizados da Escrituração,
sem mover classes ou duplicar modelos persistentes.

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
Repetições retornam a mesma representação e Location, inclusive após aprovação,
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

A implementação permanece sujeita ao Code Review da Entrega 3.1. A próxima etapa
não está habilitada: nenhum endpoint consulta Duplicatas como Financiador.
