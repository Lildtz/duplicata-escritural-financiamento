# AGENTS.md

# Duplicata Escritural — Instruções para Agentes de Engenharia

## 1. Objetivo deste arquivo

Este arquivo define as regras permanentes para qualquer agente de IA que trabalhe neste repositório.

Leia este arquivo antes de propor, criar, alterar ou remover código.

Estas instruções têm precedência sobre preferências genéricas de implementação do agente.

Não altere decisões arquiteturais documentadas apenas porque existe uma solução tecnicamente possível ou mais moderna.

O objetivo deste projeto não é demonstrar o maior número possível de tecnologias.

O objetivo é demonstrar boas decisões de engenharia aplicadas a um domínio financeiro real.

---

# 2. Contexto do projeto

Este repositório contém uma plataforma experimental baseada no domínio de Duplicata Escritural, principalmente pela perspectiva de um Agente Financiador.

O projeto é um portfólio técnico público voltado a demonstrar competências relevantes para posições como:

* Senior Software Engineer;
* Tech Lead;
* Staff Engineer;
* posições de arquitetura hands-on.

O projeto deve demonstrar engenharia real, especialmente:

* Java;
* Spring Boot;
* sistemas transacionais;
* APIs REST;
* integração entre sistemas;
* PostgreSQL;
* Kafka;
* arquitetura orientada a eventos;
* DDD;
* arquitetura hexagonal;
* idempotência;
* concorrência;
* Transactional Outbox;
* retries;
* circuit breaker;
* observabilidade;
* testes automatizados;
* Spring AI;
* AI Agents;
* MCP Client;
* MCP Server;
* Tool Calling;
* RAG.

Essas tecnologias NÃO precisam existir todas desde o início.

Cada tecnologia deve entrar apenas quando resolver um problema concreto.

---

# 3. Princípio central do projeto

A IA não controla regras financeiras críticas.

Agents e LLMs podem ser responsáveis principalmente por:

* interpretar solicitações;
* identificar intenção;
* planejar ações;
* escolher ferramentas;
* investigar informações;
* correlacionar dados;
* explicar resultados;
* produzir recomendações.

Código Java determinístico deve ser responsável por:

* elegibilidade;
* limites;
* cálculos;
* pricing;
* concentração;
* risco calculável;
* validações;
* transições de estado;
* consistência;
* integridade;
* concorrência;
* idempotência;
* persistência;
* regras financeiras críticas.

Exemplo:

Um Agent poderá recomendar:

`APROVAR COM RESSALVAS`

Mas:

* taxa;
* limite;
* elegibilidade;
* valor financiável;
* concentração;
* critérios objetivos;

devem ser calculados por código determinístico.

Nunca mova uma regra financeira determinística para um prompt simplesmente porque um LLM consegue executá-la.

---

# 4. Estado atual e arquitetura planejada

O projeto utiliza um monorepo Gradle multi-project.

O nome lógico do projeto raiz no Gradle é `duplicata-escritural-plataforma`. A pasta do repositório permanece `duplicata-escritural-financiamento`; esse caminho não define o nome lógico nem exige renomeação.

Após a alteração do nome lógico, reimporte o projeto Gradle no IntelliJ para atualizar referências locais ao nome raiz; não edite nem versione os metadados gerados em `.idea/`.

## Componentes implementados

Existem apenas dois subprojetos, ambos aplicações Spring Boot iniciais, sem capacidades de negócio ou integrações entre aplicações implementadas:

* `duplicata-escritural-financiamento-api`: bootstrap da aplicação e configuração do Actuator;
* `duplicata-escritural-escrituradora-api`: bootstrap, Actuator e infraestrutura PostgreSQL/JPA/Flyway configurada, com teste de integração via Testcontainers; ainda sem entidades ou migrations de negócio.

Estrutura existente (pasta do repositório preservada):

```text
duplicata-escritural-financiamento/
├── duplicata-escritural-financiamento-api/
├── duplicata-escritural-escrituradora-api/
├── docs/
├── infra/
├── .agents/
├── .codex/
├── AGENTS.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```

## Componentes e capacidades planejados

Interoperabilidade/Base de Controle, MCP, Agent, Spring AI e as capacidades de negócio descritas abaixo são planejados. O nome `duplicata-escritural-interoperabilidade-api` é uma referência de planejamento; não é um subprojeto existente.

O subprojeto existente `duplicata-escritural-escrituradora-api` mantém seu nome. A eventual evolução, renomeação ou reorganização dessa aplicação para representar os papéis da Signatária B3-like depende do ADR 0003. `duplicata-escritural-signataria-api` é uma proposta de nomenclatura para essa possível evolução, não um novo executável aprovado nem uma terceira aplicação que duplique as responsabilidades da Escrituradora.

Fluxo conceitual proposto para a primeira versão, sujeito ao ADR 0003 (as portas e integrações ainda não estão implementadas):

```text
duplicata-escritural-financiamento-api
        |
        | GatewayRegistradora
        v
duplicata-escritural-escrituradora-api (evolução sujeita ao ADR 0003)
        |
        | GatewayInteroperabilidade, quando necessário
        v
duplicata-escritural-interoperabilidade-api
```

O Financiamento não deve implementar lógica de roteamento ou interoperabilidade. A organização dos executáveis e as responsabilidades das portas nesse fluxo permanecem propostas sujeitas ao ADR 0003.

## duplicata-escritural-financiamento-api — responsabilidades planejadas

Representa o Banco DUP no papel de Agente Financiador.

Deverá ser a fonte de verdade para:

* clientes do Banco DUP;
* propostas e operações de crédito;
* política de crédito;
* elegibilidade;
* limites;
* pricing;
* concentração;
* exposição;
* decisão de financiar;
* relação entre cliente e Banco DUP.

Futuramente poderá conter capacidades como:

* elegibilidade;
* simulação;
* seleção de carteira;
* exposição;
* pricing;
* contratos;
* contratação de financiamento.

Essas capacidades não devem automaticamente virar microsserviços.

Preferencialmente permanecem como módulos internos enquanto compartilharem:

* invariantes;
* consistência transacional;
* ciclo de vida;
* necessidade operacional.

Como simplificação proposta para a primeira versão do projeto, sujeita ao ADR 0003, o financiamento integrará uma única Registradora contratada, simulada pela infraestrutura B3-like, por uma porta conceitual como `GatewayRegistradora`. Essa escolha não é uma regra universal do domínio nem define um novo executável.

Poderá solicitar capacidades de negócio como:

* solicitar Opt-in;
* consultar Agenda;
* consultar Duplicata;
* registrar Contrato;
* informar Ato Cambial.

Nesse cenário simplificado, o financiamento não precisará conhecer outros participantes nem a topologia do Ambiente de Interoperabilidade ou da Base de Controle.

O financiamento não descobre nem escolhe onde um Sacador, uma Duplicata ou uma Agenda está mantido.

## Signatária B3-like — papéis propostos

A infraestrutura B3-like simulará os papéis de mercado descritos abaixo. A proposta é evoluir a aplicação existente `duplicata-escritural-escrituradora-api`, conforme decisão futura no ADR 0003, sem criar uma aplicação adicional para duplicar essas responsabilidades.

Essa Signatária é B3-like porque os contratos públicos da B3 são a referência documental adotada. Isso não transforma o projeto em uma implementação oficial da B3 nem autoriza inferir comportamentos não documentados.

A proposta para o mock reúne funções distintas, cuja organização na aplicação depende do ADR 0003:

```text
Signatária B3-like
├── função de Escrituração
│   ├── Sacador local
│   ├── Escrituração
│   ├── ProcessamentoEscrituracao
│   └── Duplicata
├── função de Registro
│   ├── Agenda
│   ├── Opt-in
│   ├── Contratos
│   ├── Atos Cambiais
│   └── demais capacidades da Registradora
└── integração
    └── GatewayInteroperabilidade
```

Essas funções devem permanecer explicitamente separadas por capacidade dentro do domínio, mesmo quando executadas pela mesma aplicação.

Como Escriturador, a Signatária deverá ser fonte de verdade somente para os Sacadores que contrataram seus serviços e para as Duplicatas cuja escrituração mantiver.

Como Registradora e infraestrutura do Agente Financiador, deverá receber solicitações de Agenda, Opt-in, Contratos, Atos Cambiais e capacidades correspondentes. Deverá decidir se cada operação pode ser atendida localmente ou exige interoperabilidade.

Quando precisar do Ambiente de Interoperabilidade, a Signatária deve acessá-lo por uma fronteira conceitual como `GatewayInteroperabilidade`.

Para o financiamento, a infraestrutura de mercado é um sistema externo que ele não controla.

Isso permite modelar problemas reais como:

* timeout;
* indisponibilidade;
* latência;
* retry;
* circuit breaker;
* rate limit;
* idempotência;
* versionamento de contratos;
* observabilidade;
* correlation-id.

## duplicata-escritural-interoperabilidade-api — planejada

Representará uma simplificação do Ambiente de Interoperabilidade e da Base de Controle. Ainda não existe como subprojeto.

Será responsável por capacidades como:

* vínculo entre Sacador e Escriturador responsável;
* localização e roteamento entre Signatárias;
* troca simulada de informações entre infraestruturas;
* suporte a procedimentos interoperáveis;
* respostas simuladas de Signatárias externas, quando necessárias ao cenário.

Participantes externos devem permanecer genéricos, por exemplo `SIGNATARIA_EXTERNA_001`. Eles não constituirão aplicações ou Signatárias adicionais implementadas no projeto; quando necessários, deverão existir apenas como fixtures ou respostas simuladas para exercitar a interoperabilidade.

Não é responsável por:

* crédito;
* clientes do Banco DUP;
* pricing;
* limites;
* escrituração local de uma Signatária;
* ser fonte de verdade de todas as Duplicatas;
* ser fonte de verdade de todas as Agendas.

O Ambiente de Interoperabilidade não é acessado diretamente pelo Financiamento.

Quando utilizar fixtures ou respostas remotas simuladas, documente que isso existe apenas para testar a interoperabilidade sem implementar uma segunda Signatária.

Os componentes planejados de Signatária e Interoperabilidade serão mocks/simuladores criados exclusivamente para este projeto.

Nunca assuma acesso real aos ambientes da B3 ou de qualquer participante do mercado.

Não assuma que a topologia física do projeto representa a topologia real das infraestruturas brasileiras.

---

# 5. Fronteiras arquiteturais do ecossistema

Esta seção preserva a separação entre o Agente Financiador e a infraestrutura de mercado: o Financiamento não implementa roteamento nem interoperabilidade. A integração com uma única Registradora e a organização dos papéis e portas descritas a seguir são propostas para a primeira versão, sujeitas ao ADR 0003, ainda sem integrações implementadas.

Escriturador identifica o papel de escrituração; Registradora, o papel de registro. Na [Convenção de Duplicatas Escriturais, Glossário](https://www.bcb.gov.br/content/estabilidadefinanceira/spb_docs/convencoes/Conven%C3%A7%C3%A3o%20entre%20Entidades%20Registradoras,%20Deposit%C3%A1rios%20Centrais%20e%20Escrituradores%20-%20Duplicatas%20Escriturais.pdf), Signatária é um termo abrangente para a entidade que atua como Escriturador, Registradora ou Depositário Central, conforme o contexto. O Ambiente de Interoperabilidade compreende a Base de Controle e os mecanismos de troca de informações entre Signatárias. Esses conceitos não implicam um serviço executável para cada papel.

## Fronteira do Financiamento

Na simplificação proposta para a primeira versão, `duplicata-escritural-financiamento-api` se integrará com uma Registradora contratada por uma porta futura, conceitualmente chamada `GatewayRegistradora`. A definição final dessa porta depende do ADR 0003.

O financiamento NÃO deve:

* consultar diretamente a Base de Controle;
* acessar diretamente o Ambiente de Interoperabilidade;
* implementar roteamento entre Signatárias;
* implementar protocolos de interoperabilidade;
* decidir onde uma Duplicata ou Agenda está mantida;
* possuir clients ou adapters para executar roteamento ou protocolos de interoperabilidade.

Exemplos de atalhos proibidos para executar roteamento ou interoperabilidade:

```text
financiamento-api -> interoperabilidade-api
financiamento-api -> BaseControleClient
financiamento-api -> SignatariaExternaClient
financiamento-api -> RegistradoraExternaClient
```

A interação proposta para a primeira versão é:

```text
financiamento-api -> GatewayRegistradora (conceitual) -> infraestrutura contratada
```

A infraestrutura contratada abstrai do Financiamento a localização, o roteamento e a interoperabilidade. As responsabilidades de Registradora e Escriturador permanecem distintas por capacidade; sua organização na aplicação B3-like será definida no ADR 0003.

## Sacador local e Sacador externo

`Sacador` dentro da Signatária representa exclusivamente uma pessoa que contratou aquela Signatária, no papel de Escriturador, para realizar a escrituração de suas Duplicatas.

Para um Sacador local, a Signatária pode manter:

* cadastro do Sacador;
* Duplicatas;
* Processamentos de Escrituração;
* Agenda derivada dos ativos locais;
* eventos e ciclo de vida sob sua responsabilidade.

Se o Sacador pertence a uma Signatária externa:

* não existe cadastro de `Sacador` local;
* suas Duplicatas não pertencem ao banco local;
* sua Agenda não pertence ao banco local;
* a Signatária contratada pelo Banco DUP opera sobre essas informações somente pela interoperabilidade.

A Signatária B3-like pode manter apenas referências e estado técnico transitório necessários a uma operação interoperável, como CNPJ, identificador genérico da Signatária responsável, status, chave de consulta e vigência.

Esse estado técnico não transforma o Sacador externo em Sacador local, não autoriza persistir suas Duplicatas ou Agenda como dados locais e não torna a Signatária B3-like fonte de verdade dos dados externos.

## Regra de roteamento

O Agente Financiador não pergunta:

> Em qual Escriturador está este Sacador?

Ele solicita à sua Registradora contratada:

> Consulte a Agenda deste Sacador.

A Signatária B3-like, no papel de Registradora, determina se o atendimento é local ou interoperável.

Quando a operação for interoperável, cabe à Signatária, por meio de `GatewayInteroperabilidade`, e ao Ambiente de Interoperabilidade localizar o Escriturador responsável e executar o roteamento.

## Intermediador

Agente Financiador e Intermediador são papéis distintos.

O Banco DUP pode exercer ambos quando possuir autorização apropriada do Sacador.

Quando atuar como Intermediador:

* o solicitante técnico pode ser o Banco DUP;
* o Sacador continua sendo a empresa representada;
* a autorização de intermediação deve ser validada;
* a intermediação não altera qual Escriturador é responsável pelo Sacador.

Nunca assuma:

```text
AgenteFinanciador == Intermediador
```

Não inclua o Intermediador como atributo permanente de `Sacador`.

Quando o caso de uso existir, represente autorização, escopo, vigência e revogação em conceito próprio.

## Ownership e integração

Cada bounded context mantém apenas aquilo sobre o qual é fonte de verdade.

Não compartilhe entre Financiamento, Signatária e Interoperabilidade:

* entidades JPA;
* bancos de dados;
* tabelas;
* modelos persistentes;
* foreign keys entre bancos.

A correlação entre contextos deve ocorrer por identificadores de negócio e contratos de integração.

Dentro da Signatária, separe as funções de Escrituração e Registro por capacidades de negócio, sem criar processos executáveis separados antes de existir necessidade concreta.

---

# 6. Stack atual

Utilize como baseline do projeto:

* Java 21;
* Spring Boot 4.1.x;
* Gradle 9.8;
* Gradle Kotlin DSL;
* JUnit;
* Git;
* GitHub.

Utilize sempre o Gradle Wrapper versionado no repositório.

No Windows:

```powershell
.\gradlew.bat
```

Não dependa do Gradle instalado globalmente para executar o projeto.

Para build completo:

```powershell
.\gradlew.bat clean build
```

Não altere versões de Java, Spring Boot, Gradle ou dependências estruturais incidentalmente.

Mudanças de versão devem ser deliberadas.

---

# 7. Filosofia arquitetural

Sempre procure a solução mais simples que preserve corretamente os requisitos.

Evite overengineering.

Antes de introduzir qualquer nova estrutura, pergunte:

1. Qual problema concreto estamos resolvendo?
2. O problema existe agora?
3. Já existe uma solução simples no projeto?
4. Precisamos realmente de uma nova abstração?
5. Precisamos realmente de uma nova dependência?
6. Precisamos realmente de um novo processo executável?
7. Essa decisão pode ser defendida em uma entrevista técnica?

Não crie arquitetura baseada apenas em desejo de demonstrar tecnologia.

Nunca siga:

```text
tecnologia
→ criar componente
→ procurar um problema para justificar
```

Siga:

```text
problema
→ responsabilidade
→ fronteira
→ solução
→ tecnologia
```

---

# 8. Microsserviços

Não transforme módulos em microsserviços apenas para demonstrar arquitetura distribuída.

Um novo serviço deve possuir justificativa concreta como:

* deploy independente;
* escala independente;
* isolamento operacional;
* responsabilidade claramente independente;
* ownership diferente;
* ciclo de vida independente;
* fronteira externa real.

Exemplos de justificativa válida:

A aplicação existente `duplicata-escritural-escrituradora-api` representa uma infraestrutura externa ao Agente Financiador. A proposta de evolução dos seus papéis depende do ADR 0003.

O Ambiente de Interoperabilidade é independente porque representa uma infraestrutura externa à Signatária e possui responsabilidade própria de roteamento.

Exemplo de justificativa inválida:

`MotorElegibilidade` virou microsserviço porque queremos demonstrar microsserviços.

Se elegibilidade compartilhar invariantes e transação com financiamento, mantenha-a como módulo interno.

---

# 9. Modularidade

Dentro das aplicações, priorize organização por capacidade de negócio.

Prefira:

```text
financiamento/
├── simulacao/
├── elegibilidade/
├── carteira/
├── contrato/
└── exposicao/
```

em vez de estruturas globais puramente técnicas como:

```text
controller/
service/
repository/
entity/
dto/
```

Isso não significa que `controller`, `repository`, adapters ou DTOs não possam existir.

Eles devem ficar associados à capacidade que atendem quando isso melhorar a coesão.

Não crie todos os módulos antecipadamente.

Crie uma capacidade quando existir um caso de uso real que a necessite.

---

# 10. Arquitetura hexagonal

Arquitetura hexagonal é um instrumento, não um objetivo.

Utilize ports/adapters quando existir uma fronteira real.

Exemplos válidos:

```text
GatewayRegistradora
GatewayInteroperabilidade
```

`GatewayRegistradora` é o nome conceitual proposto para uma porta futura entre o Financiamento e a Registradora contratada no cenário simplificado da primeira versão.

`GatewayInteroperabilidade` é o nome conceitual proposto para uma porta futura entre a infraestrutura de mercado simulada e o Ambiente de Interoperabilidade.

Nenhuma dessas portas é uma interface implementada. A definição final das responsabilidades de `GatewayRegistradora`, `GatewayInteroperabilidade` e `GatewayEscrituradora` depende do ADR 0003. Preserve `GatewayEscrituradora` nos exemplos anteriores; não o substitua automaticamente por `GatewayRegistradora`.

As portas do Financiamento não devem lhe atribuir lógica de roteamento ou interoperabilidade. A proposta de integrar uma única Registradora na primeira versão não estabelece uma proibição universal de outras integrações de negócio.

Não crie interfaces simplesmente porque:

> arquitetura hexagonal usa interfaces.

Uma interface deve existir quando representar:

* uma fronteira;
* um contrato;
* uma variação real;
* desacoplamento significativo;
* necessidade de substituição;
* necessidade de teste relevante.

Evite abstrações especulativas.

---

# 11. DDD

Utilize conceitos de DDD quando ajudarem a modelar o domínio.

Não utilize padrões de DDD apenas para demonstrá-los.

Utilize linguagem consistente com o domínio.

Prefira conceitos como:

* Duplicata;
* Sacador;
* Sacado;
* Agenda;
* Financiamento;
* Contrato;
* Elegibilidade;
* Exposição;
* Carteira.

Evite modelos anêmicos ou excessivamente genéricos quando comportamento de domínio justificar um modelo mais rico.

Ao mesmo tempo, não transforme toda entidade em Aggregate Root sem necessidade.

---

# 12. Nomenclatura

Nomes relacionados ao domínio e às capacidades de negócio devem preferencialmente estar em português.

Exemplos:

```text
ServicoFinanciamento
MotorElegibilidade
MotorSelecaoCarteira
PoliticaFinanciamento
ServicoSimulacao
GatewayRegistradora
GatewayInteroperabilidade
```

Termos técnicos consolidados podem permanecer em inglês quando apropriado:

* API;
* REST;
* Gateway;
* Adapter;
* Consumer;
* Producer;
* Worker;
* Retry;
* Circuit Breaker;
* Outbox;
* Kafka;
* MCP;
* SDK.

Para projetos e aplicações, utilizar:

```text
duplicata-escritural-{capacidade}
```

ou quando o tipo arquitetural for relevante:

```text
duplicata-escritural-{capacidade}-{tipo}
```

Aplicações existentes (nomes preservados):

```text
duplicata-escritural-financiamento-api
duplicata-escritural-escrituradora-api
```

Proposta de nomenclatura para a possível evolução da aplicação existente, dependente do ADR 0003: `duplicata-escritural-signataria-api`.

Referências de nomenclatura para outros componentes planejados, sem criação automática:

```text
duplicata-escritural-interoperabilidade-api
duplicata-escritural-financiamento-mcp
duplicata-escritural-agente-financiamento
duplicata-escritural-regulacao-mcp
```

Não coloque tecnologia na identidade principal do componente.

Evite:

```text
spring-financing-service
kafka-service
postgres-api
```

---

# 13. Código Java

Priorize:

* clareza;
* coesão;
* baixo acoplamento;
* nomes explícitos;
* pequenas unidades de responsabilidade;
* composição;
* imutabilidade quando apropriada;
* APIs simples.

Evite:

* abstrações sem uso concreto;
* herança artificial;
* classes utilitárias gigantes;
* métodos excessivamente longos;
* parâmetros booleanos pouco claros;
* comentários explicando código óbvio;
* design patterns adicionados sem necessidade.

Não otimize para quantidade mínima de linhas.

Otimize para legibilidade e entendimento.

Código explícito é preferível a código inteligente demais.

---

# 14. Dependências

Não adicione uma biblioteca sem necessidade concreta.

Antes de adicionar uma dependência:

1. identifique o problema;
2. verifique se Java ou Spring já fornecem a capacidade;
3. explique por que a dependência é necessária;
4. verifique compatibilidade com a stack atual;
5. considere custo de manutenção.

Não introduza frameworks apenas para reduzir poucas linhas de código.

Dependências estruturais devem ser justificáveis.

---

# 15. Banco de dados

O banco principal planejado é PostgreSQL.

Migrations devem ser versionadas.

Quando Flyway for introduzido, mudanças de schema devem ocorrer por migration.

Não utilize atualização automática de schema em ambientes relevantes como estratégia de evolução do banco.

Modelagem deve considerar:

* integridade;
* constraints;
* unique constraints;
* índices;
* concorrência;
* idempotência;
* invariantes do domínio.

Não adicione índices sem justificar o padrão de acesso que eles atendem.

---

# 16. Kafka

Kafka ainda não deve ser introduzido apenas porque faz parte do roadmap.

Introduza Kafka somente quando existir necessidade concreta como:

* processamento assíncrono;
* integração;
* desacoplamento;
* múltiplos consumidores;
* eventos relevantes do domínio.

Possíveis eventos futuros:

```text
DuplicataCriada
DuplicataRegistrada
DuplicataNegociada
DuplicataLiquidada
ContratoRegistrado
FinanciamentoContratado
```

Quando Kafka for introduzido, considerar explicitamente:

* delivery semantics;
* at-least-once;
* consumer idempotente;
* ordering;
* retry;
* DLQ;
* consumer groups;
* schema evolution;
* observabilidade.

Não assuma exactly-once end-to-end apenas porque determinada tecnologia possui recursos relacionados.

---

# 17. Transactional Outbox

Quando uma mesma operação precisar:

1. persistir mudança de estado no banco;
2. publicar evento para Kafka;

não implemente dual write ingênuo.

Avalie Transactional Outbox.

O Outbox só deve surgir quando esse problema existir concretamente.

---

# 18. Idempotência

Idempotência é um requisito importante para operações financeiras.

Sempre avalie:

* chamadas repetidas;
* retries;
* timeout após processamento;
* processamento duplicado;
* concorrência;
* redelivery de eventos.

Não confunda idempotência HTTP com idempotência de processamento de negócio.

Quando um caso de uso exigir idempotência, documente:

* qual é a chave;
* quem a gera;
* escopo da chave;
* tempo de retenção;
* comportamento para repetição;
* tratamento concorrente.

---

# 19. Concorrência

Considere explicitamente cenários como:

> duas operações tentando utilizar a mesma duplicata simultaneamente.

Não resolva concorrência apenas em memória quando existirem múltiplas instâncias da aplicação.

Quando necessário, avalie:

* constraints de banco;
* optimistic locking;
* pessimistic locking;
* operações atômicas;
* isolamento transacional.

Escolha a estratégia de acordo com o problema.

---

# 20. Resiliência

Quando existirem as integrações entre Financiamento e sua Registradora contratada ou entre Signatária e Ambiente de Interoperabilidade, trate cada fronteira como integração distribuída real.

Uma falha na interoperabilidade deve ser tratada pela Signatária B3-like. O Financiamento não deve contornar indisponibilidade acessando diretamente a Base de Controle, o Ambiente de Interoperabilidade ou qualquer participante externo.

Considere:

* connect timeout;
* read timeout;
* retry;
* backoff;
* circuit breaker;
* indisponibilidade;
* rate limit;
* correlation-id;
* observabilidade.

Retry não deve ser aplicado indiscriminadamente.

Nunca faça retry automático de uma operação não idempotente sem analisar consequências.

---

# 21. Observabilidade

Quando a aplicação evoluir, observabilidade deve incluir progressivamente:

* logs estruturados;
* métricas;
* tracing;
* correlation-id;
* health checks;
* indicadores operacionais.

Não registre informações sensíveis ou financeiras desnecessárias nos logs.

Evite logs sem contexto como:

```text
erro ao processar
```

Prefira informações que permitam diagnóstico sem expor dados sensíveis.

---

# 22. APIs

APIs devem utilizar contratos claros.

Considere:

* códigos HTTP adequados;
* validação;
* erros previsíveis;
* idempotência;
* versionamento quando necessário;
* compatibilidade;
* observabilidade.

Controllers não devem concentrar regra de negócio.

DTOs de transporte não devem automaticamente se tornar entidades de domínio.

Evite expor detalhes internos de persistência diretamente na API.

---

# 23. Spring AI, MCP e Agents

Spring AI e MCP entrarão em etapas posteriores.

Não adicione Spring AI ao domínio transacional apenas para demonstrar IA.

O MCP Server deve disponibilizar capacidades úteis para Agents.

Não deve simplesmente espelhar endpoints REST.

Exemplos futuros de Tools:

```text
consultarOptIn
consultarAgenda
consultarDuplicata
consultarEventos
consultarExposicaoPorSacado
verificarElegibilidade
simularFinanciamento
registrarIntencaoFinanciamento
consultarProcessamento
```

Uma Tool deve representar uma capacidade significativa para o Agent.

---

# 24. Documentação regulatória e integrações externas

Nunca invente:

* APIs;
* endpoints;
* campos;
* regras;
* contratos;
* comportamento;

da B3 ou de qualquer instituição ou infraestrutura de mercado.

Utilize documentação pública e oficial quando necessário.

Sempre diferencie claramente:

```text
REGRA OFICIAL
```

de:

```text
SIMPLIFICAÇÃO DO PROJETO
```

e:

```text
HIPÓTESE ARQUITETURAL
```

Nunca apresente comportamento criado para o mock como se fosse contrato oficial de uma instituição.

Os contratos públicos da B3 são a referência documental do projeto. A Signatária planejada será uma simulação B3-like, não uma implementação oficial, e a topologia do repositório não representa a topologia física real da B3 ou do mercado brasileiro.

Não modele instituições reais adicionais como participantes implementados. Quando um cenário exigir outra ponta para testar interoperabilidade, utilize um identificador genérico, como `SIGNATARIA_EXTERNA_001`, e documente que a resposta é simulada.

No cenário simplificado proposto para a primeira versão, sujeito ao ADR 0003, o `duplicata-escritural-financiamento-api` integra a infraestrutura contratada e não acessa outros participantes para executar roteamento ou interoperabilidade.

Exemplos de acessos de roteamento ou interoperabilidade que não pertencem ao Financiamento:

```text
InteroperabilidadeClient
BaseControleClient
SignatariaExternaClient
RegistradoraExternaClient
```

Detalhes de localização, roteamento e interoperabilidade pertencem à infraestrutura de mercado. `GatewayRegistradora` é um nome conceitual proposto; as responsabilidades finais das portas serão definidas no ADR 0003.

---

# 25. Processo obrigatório antes de codificar

Para mudanças não triviais:

1. leia este `AGENTS.md`;
2. inspecione o código existente;
3. leia arquivos diretamente relacionados;
4. entenda o comportamento atual;
5. identifique o problema;
6. identifique restrições;
7. avalie alternativas;
8. escolha a solução mais simples adequada;
9. implemente somente o necessário;
10. teste;
11. revise o diff.

Não altere código antes de entender o contexto relevante.

Não reescreva partes não relacionadas apenas porque encontrou uma abordagem que prefere.

---

# 26. Decisões arquiteturais

Para decisões arquiteturais importantes, utilize mentalmente ou apresente quando solicitado:

```text
Problema
→ alternativas
→ decisão
→ trade-offs
→ consequências
```

Não apresente apenas a solução escolhida.

Uma boa decisão arquitetural deve conseguir explicar por que alternativas foram descartadas.

Preserve o histórico dos ADRs: não reescreva decisões anteriores para refletir decisões posteriores. O ADR 0003 permanece pendente para definir a evolução da aplicação existente, a organização dos papéis de Escrituração, Registro e Signatária e as responsabilidades das portas em relação ao ADR 0002, que ainda agrupa essas capacidades sob `escrituradora-api`. Esta revisão documental não implementa o ADR 0003 nem renomeia a aplicação existente.

Toda revisão arquitetural deve responder:

1. Esta mudança faz o Agente Financiador assumir roteamento ou interoperabilidade que pertencem à infraestrutura de mercado?
2. Estamos persistindo localmente um Sacador, uma Duplicata ou uma Agenda cuja fonte de verdade pertence a outra Signatária?
3. Esta mudança cria um atalho do Financiamento para executar roteamento ou interoperabilidade diretamente na Base de Controle, no Ambiente de Interoperabilidade ou em outro participante?
4. As funções de Escrituração e Registro estão explícitas dentro da Signatária sem gerar serviços prematuros?

Se qualquer uma das três primeiras respostas for sim, pare e revise a fronteira antes de implementar.

---

# 27. Escopo das mudanças

Faça o menor conjunto coerente de mudanças necessário para atender à tarefa.

Não faça drive-by refactoring.

Exemplo:

Se a tarefa é criar persistência de Duplicata, não aproveite para:

* renomear módulos não relacionados;
* atualizar todas as dependências;
* reorganizar todo o projeto;
* trocar framework de testes;
* mudar estilo de código globalmente.

Refatorações maiores devem ser tratadas separadamente.

---

# 28. Testes

Toda alteração de comportamento deve considerar testes compatíveis com seu risco.

Priorize:

* testes unitários para regras determinísticas;
* testes de integração quando infraestrutura fizer parte do comportamento;
* Testcontainers quando banco ou infraestrutura real forem relevantes.

Não crie mocks apenas para aumentar cobertura.

Teste comportamento significativo.

Não teste detalhes internos desnecessariamente.

---

# 29. Critério de conclusão

Uma tarefa de implementação não está concluída apenas porque o código foi escrito.

Antes de declarar conclusão:

1. compile o módulo alterado;
2. execute testes relevantes;
3. execute build mais abrangente quando apropriado;
4. examine warnings relevantes;
5. revise o diff;
6. verifique arquivos não rastreados;
7. confirme que nenhum secret foi adicionado;
8. confirme que nenhum arquivo gerado foi versionado.

Comando padrão:

```powershell
.\gradlew.bat clean build
```

Nunca diga que uma alteração está funcionando se os testes ou build relevantes não foram executados, a menos que explique explicitamente por que não foi possível executá-los.

---

# 30. Git

O agente pode executar livremente comandos Git somente de leitura, como:

```text
git status
git diff
git log
git show
git branch
```

Não execute sem autorização explícita:

```text
git commit
git push
git pull
git merge
git rebase
git reset
git checkout destrutivo
git clean
git force push
```

Não altere histórico Git sem autorização explícita.

Antes de sugerir um commit:

1. examine `git status`;
2. examine o diff;
3. resuma as alterações;
4. sugira uma mensagem.

Utilize Conventional Commits.

Exemplos:

```text
feat: adiciona consulta de duplicatas
fix: corrige processamento duplicado
test: adiciona testes de elegibilidade
refactor: simplifica motor de simulacao
docs: documenta decisao de arquitetura
chore: configura infraestrutura local
```

---

# 31. Secrets

Nunca adicione:

* tokens;
* passwords;
* API keys;
* secrets;
* credenciais;
* certificados privados;

ao repositório.

Utilize variáveis de ambiente.

Arquivos `.env` devem permanecer fora do Git.

Quando necessário, utilize:

```text
.env.example
```

sem credenciais reais.

---

# 32. Arquivos gerados

Não versione arquivos gerados localmente como:

```text
.gradle/
build/
out/
.idea/
*.class
```

O Gradle Wrapper deve permanecer versionado:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

---

# 33. Skills do projeto

As Skills ficam em:

```text
.agents/skills/
```

Skills previstas inicialmente:

```text
implementation-strategy
architecture-review
code-change-verification
```

## implementation-strategy

Utilize antes de implementar uma mudança relevante.

Objetivo:

* entender o problema;
* localizar pontos de alteração;
* identificar riscos;
* propor estratégia mínima de implementação.

## architecture-review

Utilize quando houver:

* nova integração;
* nova dependência;
* novo serviço;
* nova tecnologia;
* nova fronteira;
* mudança relevante de arquitetura.

Objetivo:

avaliar se a decisão é necessária e defensável.

## code-change-verification

Utilize depois de implementar mudanças relevantes.

Objetivo:

* executar testes;
* executar build;
* revisar diff;
* identificar regressões;
* verificar arquivos indevidos;
* confirmar aderência ao `AGENTS.md`.

---

# 34. Ponytail

Se o plugin Ponytail estiver instalado, utilize-o como mecanismo de revisão de complexidade.

Ponytail deve ajudar a identificar:

* abstrações desnecessárias;
* dependências desnecessárias;
* duplicações artificiais;
* patterns sem necessidade;
* excesso de classes;
* código mais complexo do que o problema exige.

Ponytail NÃO possui autoridade para remover uma decisão arquitetural documentada apenas para reduzir código.

Exemplo:

Se `GatewayRegistradora` ou `GatewayInteroperabilidade` representa uma fronteira externa real, não remova essa abstração simplesmente porque uma chamada HTTP direta possui menos linhas.

Ponytail também não autoriza fundir Financiamento, Signatária e Interoperabilidade, compartilhar seus modelos persistentes ou pular uma fronteira documentada para reduzir código.

A prioridade é:

```text
simplicidade
+
clareza arquitetural
+
correção
```

e não apenas:

```text
menos código
```

Utilize Ponytail preferencialmente depois que a implementação estiver funcional e os testes estiverem passando.

---

# 35. Autonomia do agente

O agente possui autonomia para:

* ler arquivos;
* pesquisar no repositório;
* criar código relacionado à tarefa;
* modificar código relacionado à tarefa;
* executar build;
* executar testes;
* executar ferramentas de análise;
* executar comandos Git de leitura;
* corrigir problemas diretamente relacionados à implementação.

O agente não possui autonomia para:

* redefinir arquitetura principal;
* adicionar serviços sem justificativa;
* adicionar grandes frameworks incidentalmente;
* remover módulos arquiteturais documentados;
* alterar versões estruturais sem necessidade;
* apagar arquivos relevantes;
* alterar histórico Git;
* fazer commit;
* fazer push;
* publicar releases;
* adicionar secrets.

Quando encontrar uma decisão arquitetural significativa não coberta por estas instruções, apresente:

```text
problema
alternativas
recomendação
trade-offs
```

antes de alterar a arquitetura.

---

# 36. Comportamento esperado do Codex

Não aja como simples gerador de código.

Aja como engenheiro responsável pelo projeto.

Antes de implementar:

* compreenda;
* questione complexidade;
* respeite contexto;
* procure a menor solução correta.

Durante a implementação:

* mantenha escopo;
* preserve convenções;
* escreva código legível;
* evite abstrações especulativas.

Depois da implementação:

* teste;
* revise;
* simplifique quando apropriado;
* reporte claramente o que mudou.

Quando houver dúvida entre:

```text
uma solução simples suficiente
```

e:

```text
uma solução sofisticada para possíveis necessidades futuras
```

prefira a solução simples suficiente.

YAGNI deve ser aplicado, exceto quando um requisito real, risco concreto ou decisão arquitetural documentada justificar complexidade adicional.

---

# 37. Regra final

O código deste projeto deve contar uma história arquitetural defensável.

Cada tecnologia, abstração, serviço e padrão relevante deve responder à pergunta:

> Qual problema real isso resolve?

Se não houver uma boa resposta, provavelmente não deve existir ainda.
