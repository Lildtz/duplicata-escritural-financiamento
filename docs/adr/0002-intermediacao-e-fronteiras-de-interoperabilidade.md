# ADR 0002 — Intermediação e fronteiras de interoperabilidade

**Status:** Aceito

## Contexto

A plataforma simula um ecossistema de Duplicatas Escriturais composto inicialmente por:

- um Agente Financiador fictício, denominado Banco DUP;
- uma infraestrutura de mercado/escrituradora simulada, inicialmente B3-like;
- uma infraestrutura simulada responsável pelos aspectos de Base de Controle e interoperabilidade.

É necessário definir claramente quais componentes o Agente Financiador conhece, quem é responsável pela interoperabilidade e como funciona a atuação do Agente Financiador quando também exerce o papel de Intermediador.

O objetivo é preservar uma arquitetura próxima ao comportamento real do ecossistema sem reproduzir desnecessariamente toda a topologia das infraestruturas existentes.

---

## Papéis

### Sacador

O Sacador é a empresa responsável pela emissão das Duplicatas Escriturais.

O Sacador possui relacionamento com uma única Escrituradora responsável pela escrituração de suas duplicatas, observadas as regras aplicáveis ao CNPJ Raiz.

Exemplo:

```text
Empresa XPTO
    │
    │ contratação da escrituração
    ▼
B3-like
```

Todas as duplicatas da Empresa XPTO devem ser escrituradas através da Escrituradora responsável pelo seu vínculo.

---

### Escriturador

O Escriturador é responsável pelo processo de escrituração e gestão da Duplicata Escritural.

No projeto, a primeira implementação desse papel será:

```text
duplicata-escritural-escrituradora-api
```

Essa aplicação será B3-like.

Ela será responsável por capacidades como:

- cadastro/relacionamento do Sacador;
- escrituração;
- processamento de escrituração;
- geração e manutenção do IUD;
- consulta de Duplicatas;
- Agenda;
- Opt-in;
- contratos e atos relacionados ao seu domínio;
- comunicação com a infraestrutura de interoperabilidade quando necessário.

---

### Agente Financiador

O Banco DUP representa o Agente Financiador deste projeto.

Sua aplicação principal será:

```text
duplicata-escritural-financiamento-api
```

O Agente Financiador não precisa conhecer quais outras Escrituradoras existem no mercado.

Também não deve conhecer:

- regras internas de roteamento entre Escrituradoras;
- Base de Controle diretamente;
- topologia de interoperabilidade;
- endpoints de outras Escrituradoras.

O Agente Financiador conhece apenas a infraestrutura de mercado com a qual possui relacionamento e integração.

No cenário inicial:

```text
Banco DUP
    │
    ▼
B3-like
```

Portanto, a integração do financiamento deve ser com a Escrituradora/infraestrutura contratada e não diretamente com componentes de interoperabilidade.

---

### Intermediador

Intermediador é um papel diferente de Agente Financiador.

Entretanto, uma mesma instituição pode, no contexto deste projeto, possuir ambos os papéis quando estiver devidamente habilitada e autorizada.

O Banco DUP poderá atuar como Intermediador de um Sacador mediante uma autorização contratual de intermediação.

Exemplo:

```text
Empresa XPTO
     │
     │ autorização de intermediação
     ▼
Banco DUP
     │
     │ atua em nome da XPTO
     ▼
B3-like
```

Nesse cenário, o Banco DUP pode comandar operações autorizadas em nome da Empresa XPTO.

A autorização de intermediação não transforma o Banco DUP no Sacador.

A Duplicata continua pertencendo ao contexto do Sacador XPTO.

Também não altera qual é o Escriturador responsável pela XPTO.

---

## Escrituração por Intermediador

A Empresa XPTO pode autorizar o Banco DUP a atuar como seu Intermediador.

O fluxo conceitual será:

```text
Empresa XPTO
     │
     │ Termo/Autorização de Intermediação
     ▼
Banco DUP
     │
     │ solicitação de escrituração
     ▼
B3-like
     │
     ▼
Duplicata da Empresa XPTO
```

O Banco DUP está executando a operação em nome do Sacador.

Portanto:

```text
solicitante técnico = Banco DUP / Intermediador

Sacador da Duplicata = Empresa XPTO
```

Esses conceitos não devem ser confundidos no modelo.

A autorização deverá possuir escopo e situação explícitos.

Não deve ser representada apenas por uma flag como:

```text
sacador.intermediado = true
```

O relacionamento deve ser modelado quando essa capacidade for implementada.

---

## Escrituração e interoperabilidade

A interoperabilidade não significa que qualquer Escrituradora possa assumir livremente a escrituração de um Sacador vinculado a outra Escrituradora.

O Sacador continua possuindo um Escriturador responsável.

Um Intermediador autorizado deve encaminhar a escrituração através da infraestrutura responsável pelo Sacador.

No cenário:

```text
XPTO → B3-like
```

o Banco DUP, atuando como Intermediador, utiliza a B3-like para escriturar em nome da XPTO.

---

## Relação do Agente Financiador com sua infraestrutura

O Banco DUP conhece somente a infraestrutura de mercado com a qual trabalha.

No cenário inicial:

```text
duplicata-escritural-financiamento-api
                  │
                  │ HTTP
                  ▼
duplicata-escritural-escrituradora-api
                  │
                  │ interoperabilidade
                  ▼
infraestrutura de interoperabilidade
```

O `financiamento-api` não deve possuir lógica para descobrir em qual Escrituradora determinada Duplicata está mantida.

Também não deve implementar roteamento entre Escrituradoras.

Essa responsabilidade pertence às infraestruturas de mercado.

---

## Consulta de Agenda

Considere que a Empresa XPTO esteja vinculada a uma infraestrutura diferente daquela utilizada pelo Banco DUP.

Conceitualmente:

```text
Banco DUP
    │
    │ consulta Agenda XPTO
    ▼
Escrituradora/Registradora contratada pelo Banco DUP
    │
    │ interoperabilidade
    ▼
Infraestrutura responsável pela XPTO
    │
    ▼
Agenda XPTO
```

Para o Banco DUP, porém, esse fluxo permanece:

```text
Banco DUP
    │
    ▼
sua infraestrutura contratada
```

A existência das demais infraestruturas é transparente para o Agente Financiador.

O financiamento solicita uma capacidade de negócio, por exemplo:

```text
consultarAgenda(cnpjSacador)
```

A infraestrutura contratada é responsável por resolver o atendimento localmente ou através de interoperabilidade.

---

## Registro de operações de crédito

A mesma regra de abstração deve valer para operações de financiamento.

O Banco DUP informa contratos, atos cambiais ou efeitos de negociação através da infraestrutura com a qual está integrado.

Quando a operação envolver Duplicatas mantidas em outra infraestrutura, o encaminhamento através de interoperabilidade é responsabilidade das infraestruturas envolvidas.

O `financiamento-api` não deve implementar essa coordenação.

---

## Fronteira arquitetural

A arquitetura conceitual será:

```text
┌─────────────────────────────────────┐
│ duplicata-escritural-financiamento  │
│              -api                   │
│                                     │
│ Banco DUP                           │
│ Agente Financiador                  │
│ eventualmente Intermediador         │
└──────────────────┬──────────────────┘
                   │
                   │ HTTP
                   ▼
┌─────────────────────────────────────┐
│ duplicata-escritural-escrituradora │
│              -api                   │
│                                     │
│ B3-like                             │
│ Escriturador / infraestrutura       │
│ contratada                          │
└──────────────────┬──────────────────┘
                   │
                   │ APIs de
                   │ interoperabilidade
                   ▼
┌─────────────────────────────────────┐
│ infraestrutura centralizadora /     │
│ interoperabilidade mock             │
│                                     │
│ Base de Controle                    │
│ roteamento                          │
│ troca padronizada de informações    │
└──────────────────┬──────────────────┘
                   │
                   │ conceitualmente
                   ▼
          outras infraestruturas
```

Outras Escrituradoras podem existir conceitualmente no ecossistema.

Elas não serão implementadas neste momento.

---

## Centralizadora / Interoperabilidade

O projeto poderá possuir um componente separado representando as responsabilidades necessárias para simular Base de Controle e interoperabilidade.

Nome candidato:

```text
duplicata-escritural-interoperabilidade-api
```

Esse componente será uma simplificação arquitetural para fins do projeto.

Ele não deve ser documentado como reprodução da topologia física real da infraestrutura brasileira.

Seu objetivo é permitir simular comportamentos como:

- consulta de vínculo de Sacador;
- roteamento;
- troca de informações entre infraestruturas;
- controles necessários à interoperabilidade;
- cenários distribuídos relevantes.

Ele não será acessado diretamente pelo `financiamento-api`.

---

## Dependências permitidas

A direção de dependência esperada será:

```text
financiamento-api
        │
        ▼
escrituradora-api
        │
        ▼
interoperabilidade-api
```

Não será permitido:

```text
financiamento-api
        │
        ▼
interoperabilidade-api
```

nem:

```text
financiamento-api
        │
        ▼
outra-escrituradora-api
```

O Agente Financiador não deve conhecer essas fronteiras.

---

## Distinção entre papéis

A mesma pessoa jurídica poderá exercer mais de um papel.

Exemplo:

```text
Banco DUP

Agente Financiador   ✅
Intermediador        ✅ quando autorizado
Sacador              não necessariamente
Escriturador         ❌
```

O sistema deve modelar capacidades de acordo com o papel utilizado na operação.

Não assumir:

```text
Agente Financiador == Intermediador
```

A atuação como Intermediador depende de autorização/habilitação apropriada.

---

## Autorização de intermediação

Quando implementada, a autorização de intermediação deverá ser um relacionamento próprio.

Conceitualmente:

```text
AutorizacaoIntermediacao
│
├── Sacador
├── Intermediador
├── escopo
├── situacao
├── inicioVigencia
└── fimVigencia
```

O modelo definitivo será definido com base nos contratos públicos disponíveis no momento da implementação.

Não inventar campos regulatórios que não possuam respaldo documental.

---

## Consequências positivas

Essa decisão permite que o `financiamento-api` permaneça simples e desacoplado da topologia do ecossistema.

O Agente Financiador passa a programar contra uma única infraestrutura conhecida.

Também permite simular:

- Sacadores mantidos em diferentes Escrituradoras;
- Agenda interoperável;
- contratos e atos sobre ativos externos;
- atuação do Banco DUP como Intermediador;
- indisponibilidade de interoperabilidade;
- latência distribuída;
- roteamento;
- idempotência;
- retries;
- consistência entre infraestruturas.

Tudo isso sem colocar conhecimento de interoperabilidade dentro do domínio de financiamento.

---

## Trade-offs

A arquitetura introduz uma fronteira adicional entre Escrituradora e infraestrutura de interoperabilidade.

Isso aumenta:

- quantidade de integrações;
- tratamento de falhas;
- necessidade de observabilidade;
- cenários de consistência distribuída.

Esse custo é aceito porque representa uma responsabilidade externa real do domínio e evita acoplamento incorreto do Agente Financiador à topologia das Escrituradoras.

---

## Fora de escopo deste ADR

Este ADR não define:

- contratos HTTP definitivos;
- estrutura completa da Base de Controle;
- protocolo real entre Signatárias;
- segunda Escrituradora mock;
- Kafka;
- autenticação/mTLS;
- formato definitivo do IUD;
- modelo de Agenda;
- modelo de Contrato;
- modelo de Ato Cambial;
- modelo definitivo da Autorização de Intermediação.

Essas decisões serão tratadas incrementalmente.

---

## Decisão

A plataforma adotará as seguintes regras:

1. O `financiamento-api` conhece apenas a infraestrutura de mercado contratada pelo Banco DUP.

2. O `financiamento-api` não conhece nem implementa interoperabilidade entre Escrituradoras.

3. A `escrituradora-api` é responsável por utilizar a infraestrutura de interoperabilidade quando a operação exigir comunicação com outra infraestrutura.

4. O Sacador mantém seu vínculo com a Escrituradora responsável independentemente de quem tecnicamente envia uma solicitação em seu nome.

5. O Banco DUP poderá acumular os papéis de Agente Financiador e Intermediador.

6. A atuação como Intermediador exige autorização do Sacador e não é consequência automática do papel de Agente Financiador.

7. Quando o Banco DUP atuar como Intermediador, poderá comandar operações autorizadas em nome do Sacador através da Escrituradora competente.

8. O Sacador permanece sendo o titular do contexto de emissão da Duplicata mesmo quando a operação é comandada por um Intermediador.

9. A interoperabilidade será abstraída atrás da `escrituradora-api` e permanecerá invisível para o `financiamento-api`.

10. Outras Escrituradoras não serão implementadas enquanto não houver necessidade concreta para um caso de teste ou demonstração.