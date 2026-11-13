# Entrega 2 — processamento local de Escrituração Manual

Implementação sujeita ao Code Review. Contrato de recebimento e provisionamento no
[guia de Escrituração Manual](escrituracao-manual.md). Tudo abaixo é uma
**simplificação do projeto**, sem representar contrato oficial B3. Não há Registro,
Agenda, Opt-in, interoperabilidade, IUD, Kafka, Outbox ou integração real.

## Domínio e persistência

`Duplicata` contém UUID técnico local, identidade e CNPJ do Sacador, os dados
imutáveis da emissão (Sacado, documento, valores e datas) e `escrituradaEm`.
Não existe IUD regulatório. Os componentes comerciais são colunas com CHECK de
consistência com os dados JSONB. Não há modelos duplicados de domínio e persistência.

A V2 cria `escrituradora.duplicata` e evolui `processamento_escrituracao` por
ALTER TABLE, sem recriar tabelas. V1, histórico, banco, variáveis e volumes são preservados.
Pedidos antigos continuam PENDENTE, com zero tentativas e `proxima_execucao_em = recebido_em`.

Constraints comerciais nomeadas protegem:

* `uk_duplicata_sacador_numero`: Sacador + número da duplicata;
* `uk_duplicata_sacador_fatura_parcela`: Sacador + fatura + parcela.

Não existe unicidade isolada por documento fiscal. Sacadores distintos podem emitir
títulos com os mesmos números. Os CHECKs do processamento protegem estado, reserva,
finalização e presença/ausência do resultado. Índices parciais cobrem pendências por
próxima execução e reservas por expiração, excluindo estados terminais.

O CHECK `numero_parcela = 1` acompanha a limitação deliberada de parcela única
do domínio e do contrato HTTP, com valor da Duplicata igual ao da fatura. A chave
comercial inclui a parcela, mas isso não habilita parcelamento nesta versão.

Chave idempotente, dados, fotografia, Sacador e instante do recebimento permanecem
imutáveis no mapeamento e sem setters. O `@Immutable` é removido somente do
processamento. Métodos explícitos protegem as transições; Duplicata é `@Immutable`.
A habilitação cadastral local é reavaliada durante a execução. O período operacional
é conferido contra a fotografia e o dia UTC do recebimento, preservando o pedido.
Não se acrescentam validações regulatórias ou proibição de título vencido.

## Máquina de estados

| Origem | Evento | Destino |
|---|---|---|
| PENDENTE elegível | Reserva confirmada e contador incrementado | EM_PROCESSAMENTO |
| EM_PROCESSAMENTO | Criação local e conclusão no mesmo commit | CONCLUIDO |
| EM_PROCESSAMENTO | Duplicidade ou outra rejeição de negócio | REJEITADO |
| EM_PROCESSAMENTO | Falha transitória/abandono, com orçamento | PENDENTE agendado |
| EM_PROCESSAMENTO | Terceira falha/abandono ou erro permanente | FALHA_TECNICA |
| PENDENTE | Orçamento já esgotado, inclusive por redução da configuração | FALHA_TECNICA |

Estados terminais não voltam à fila. Uma tentativa reservada nunca é decrementada.
`ReservaExecucao(idProcessamento, tentativa)` identifica a execução. Estado, número
e expiração são conferidos sob lock. Chamadas antigas são ignoradas antes de criar
efeitos; o domínio também recusa conclusão sem reserva válida.
Rejeição e falha recebidas de uma execução também exigem reserva vigente, mesmo
que nenhuma tentativa posterior tenha sido reservada. Após a expiração, somente
a recuperação de abandono em T1 pode alterar o estado, com código RESERVA_EXPIRADA
e o mesmo orçamento já consumido. Esse código não pode ser aplicado antes da expiração.

## Transações e SQL especializado

O serviço usa `TransactionTemplate` com REQUIRES_NEW e READ COMMITTED, garantindo
limites efetivos sem depender de self-invocation de `@Transactional`. As três
transações usam o timeout configurado de execução:

1. **T1 — reserva curta:** seleciona uma linha por
   `LIMIT 1 FOR UPDATE SKIP LOCKED`, incrementa tentativas e grava estado/expiração.
   JPA não expressa SKIP LOCKED, justificando essa consulta nativa. Um trabalho
   bloqueado é ignorado em favor de outro elegível. Ao encontrar reserva abandonada,
   registra a falha daquela tentativa e agenda a próxima; não reserva outra imediatamente.
2. **T2 — execução:** bloqueia a linha por `PESSIMISTIC_WRITE`, confere a reserva,
   valida regras locais, insere Duplicata com flush e conclui com outro flush.
   Inserção e conclusão pertencem ao mesmo commit. O lock permanece até commit ou
   rollback, impedindo recuperação concorrente durante uma execução ativa.
3. **T3 — falha:** começa após rollback de T2, bloqueia a linha e verifica estado e
   número e vigência da reserva antes de rejeitar, reagendar ou finalizar. Somente as duas constraints
   comerciais conhecidas viram duplicidade de negócio. Nenhuma escrita continua
   dentro de uma transação PostgreSQL abortada.

As demais operações novas usam Spring Data JPA. O INSERT JDBC idempotente da
Entrega 1 permanece inalterado. Não há fila em memória nem lock distribuído adicional.

Se T1 falha, a reserva não é presumida. Se T3 falha ou a aplicação reinicia, a reserva
durável expira e permite recuperação. Um resultado de commit incerto é reavaliado
pelo estado no banco: T3 não sobrescreve CONCLUIDO. Não prometemos exactly-once
execution; constraints e transações garantem ausência de títulos duplicados.

## Retentativas e recuperação

Falhas Spring/JDBC reconhecidamente transitórias/recuperáveis, timeout e SQLStates
de conexão (classe 08), serialização (40001) e deadlock (40P01) recebem retry limitado.
Falhas permanentes ou não classificadas finalizam como ERRO_TECNICO, sem retry cego.
Rejeições de negócio terminam na tentativa atual e não agendam outras tentativas.

O terceiro erro técnico transitório ou abandono encerra o processamento.
Nenhuma transação permanece aberta durante backoff. Na recuperação, a espera conta
da detecção do abandono. O orçamento já reservado não é devolvido nem após reinício.

Prefixo: `signataria.escrituracao.processamento`.

| Propriedade | Padrão | Uso |
|---|---|---|
| `habilitado` | `false` | Habilitação explícita do agendador |
| `polling` | `1s` | Fixed delay entre ciclos |
| `maximo-tentativas` | `3` | Pode reduzir para 1 ou 2; nunca ultrapassa 3 |
| `espera-segunda-tentativa` | `5s` | Após a primeira falha técnica |
| `espera-terceira-tentativa` | `30s` | Após a segunda falha técnica |
| `duracao-reserva` | `60s` | Validade da reserva persistida |
| `timeout-execucao` | `10s` | Timeout transacional, em segundos inteiros |

Intervalos devem ser positivos. A reserva deve durar mais que o timeout.
Configuração inválida impede inicialização. Para habilitar localmente, mescle este
bloco com a chave `signataria` já existente no arquivo local provisionado:

```yaml
signataria:
  escrituracao:
    processamento:
      habilitado: true
```

Também é possível passar `--signataria.escrituracao.processamento.habilitado=true`
nos argumentos do bootRun. Habilitar processa também pedidos PENDENTE anteriores.
Os testes de integração desabilitam explicitamente o agendamento; os testes do
agendador usam um coordenador substituto e sincronização por latch.

## Resultado REST e diagnóstico

O POST preserva a representação original de recebimento com status PENDENTE,
inclusive em repetições após conclusão ou falha. O GET informa o estado atual.
Exemplo de conclusão (UUIDs ilustrativos, não oficiais):

```json
{
  "idProcessamento": "e191f444-70ca-42f7-9b07-7c4dba59e8f6",
  "status": "CONCLUIDO",
  "recebidoEm": "2026-11-12T15:00:00Z",
  "idDuplicata": "1b7d4cef-171e-4a91-b52e-eeb8ced7b2f9",
  "escrituradaEm": "2026-11-12T15:00:01Z",
  "finalizadoEm": "2026-11-12T15:00:01Z"
}
```

Exemplo de rejeição:

```json
{
  "idProcessamento": "12fa4baf-179d-450b-882f-d9f551af8e91",
  "status": "REJEITADO",
  "recebidoEm": "2026-11-12T15:01:00Z",
  "finalizadoEm": "2026-11-12T15:01:01Z",
  "codigo": "DUPLICATA_JA_ESCRITURADA",
  "motivo": "Título já escriturado neste simulador"
}
```

Códigos de negócio: `DUPLICATA_JA_ESCRITURADA`, `SACADOR_NAO_HABILITADO`,
`SOLICITACAO_INVALIDA`. Códigos técnicos: `FALHA_TRANSITORIA`, `RESERVA_EXPIRADA`,
`TENTATIVAS_ESGOTADAS`, `ERRO_TECNICO`. O código técnico terminal preserva a categoria
da última falha; `tentativas` no banco registra o orçamento consumido. A API não
expõe esse contador nem diagnósticos intermediários.

Somente códigos e mensagens previamente definidos são persistidos/retornados.
Os logs próprios contêm ID técnico, tentativa e código, sem payload ou exceção.
O logger de erros JDBC do Hibernate fica habilitado em WARN, com saída exclusiva
configurada no Logback. Preserva o resumo numérico de ErrorCode/SQLState do Hibernate
e substitui demais mensagens por texto fixo, sem stack traces nem propagação para
o appender geral. Assim, erros de persistência mantêm diagnóstico técnico sem expor
valores de chaves comerciais. A lista permitida depende do formato HHH000247 da
versão atual e é coberta por testes com falhas reais do PostgreSQL; uma mudança
de formato omite os detalhes por padrão. Falhas de coordenação recebem mensagem genérica e segura.
Não há mudança de autenticação, de isolamento por Sacador ou dos códigos HTTP existentes.

## Verificação e limitações

Testes PostgreSQL/Testcontainers exercitam unicidades, disputa de consumidores,
SKIP LOCKED, rollback após INSERT real, backoff, esgotamento, reservas abandonadas,
execuções antigas, idempotência e consulta autorizada dos estados terminais.
Triggers no banco descartável injetam falhas determinísticas e um Clock controlável
governa agendamento e expiração, sem Thread.sleep.

O teste incremental aplica V1, insere um pedido e aplica V2 preservando conteúdo e
checksum. O teste de persistência JPA também lê o formato JSON anterior.

Limitações deliberadas:

* Cada instância trata no máximo um trabalho por ciclo; execução e recuperação
  acrescentam tempo ao fixed delay. Não existe meta de vazão nem drenagem em lote.
* Relógios das instâncias precisam estar sincronizados. O Clock UTC é injetável.
* O timeout transacional limita operações de banco; não é interrupção forçada de
  código Java arbitrário. O caminho atual executa apenas validações locais e persistência.
* Não há reprocessamento administrativo, retenção ou remoção automática. Falhas
  permanentes exigem investigação e decisão explícita posterior.
* A V2 altera a constraint de estado. A aplicação da Entrega 1 deve ser encerrada
  antes de habilitar processadores da Entrega 2, pois seu enum conhece só PENDENTE.

A Entrega 4.2 acrescenta uma Observation por execução reservada e logs/contadores
de transições somente após commit. O ID técnico do processamento correlaciona
recebimento e worker; o trace HTTP não é persistido nem continuado pelo agendador.
Reservas, concorrência, backoff e transações T1/T2/T3 são preservados.
Consulte [sinais e testes de rollback](observabilidade.md#processamento-assíncrono).

Comandos de validação na raiz:

```powershell
.\gradlew.bat projects
.\gradlew.bat clean build
git diff --check
git status --short
```
