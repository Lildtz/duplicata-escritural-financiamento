# ADR 0003 — Consolidação dos papéis de Escrituração e Registro na Signatária simulada

**Status:** Aceito — implementação pendente

**Data:** 2026-10-29

**Escopo:** Arquitetura da plataforma de Duplicata Escritural

## 1. Contexto

A plataforma possui atualmente duas aplicações Spring Boot:

- `duplicata-escritural-financiamento-api`;
- `duplicata-escritural-escrituradora-api`.

A segunda aplicação foi inicialmente criada para simular uma infraestrutura externa de escrituração e registro de duplicatas. Hoje, ambas são aplicações iniciais, sem capacidades de negócio implementadas.

O modelo de domínio distingue os papéis de Escriturador, Registradora e Signatária.

**Regra oficial:** o Glossário da [Convenção de Duplicatas Escriturais publicada pelo Banco Central][convencao-bcb] define Signatárias como instituições autorizadas ou em processo de autorização para Escrituração, Registro ou Depósito, signatárias ou aderentes à Convenção. O termo é abrangente; o papel exercido depende do contexto e das autorizações aplicáveis. A [documentação da B3 sobre Escrituração][escrituracao-b3] distingue suas atividades de escrituração e registro de Duplicatas Escriturais.

**Simplificação arquitetural do projeto:** adotaremos uma única infraestrutura B3-like simulando capacidades de Escrituração e Registro dentro da mesma aplicação. Essa escolha de organização de software não é uma exigência regulatória nem uma reprodução da topologia física da B3 ou do mercado.

O nome atual `escrituradora-api` não representa integralmente o escopo planejado dessa aplicação.

---

## 2. Problema

Precisamos estabelecer uma identidade técnica que represente a infraestrutura simulada sem:

- confundir Escrituração com Registro;
- multiplicar aplicações desnecessariamente;
- criar dependência direta entre Financiamento e Interoperabilidade;
- compartilhar modelos persistentes entre diferentes contextos;
- descaracterizar a arquitetura híbrida adotada;
- apresentar a simulação como uma implementação oficial da B3.

---

## 3. Alternativas consideradas

### A — Manter `escrituradora-api`

Preserva todos os nomes atuais, mas o nome da aplicação representa apenas parte das responsabilidades planejadas.

### B — Evoluir para `signataria-api`

Renomeia a aplicação existente e organiza as responsabilidades de Escrituração e Registro como capacidades distintas dentro do mesmo processo.

### C — Criar uma nova `signataria-api`

Mantém a aplicação atual e acrescenta outra, introduzindo sobreposição de responsabilidades sem justificativa operacional.

### D — Separar Escrituração e Registro em aplicações

Oferece isolamento operacional, mas introduz complexidade distribuída prematuramente.

---

## 4. Decisão

**Adotar a alternativa B. A decisão está aceita; sua implementação permanece pendente.**

A aplicação:

```text
duplicata-escritural-escrituradora-api
```

será evoluída e renomeada para:

```text
duplicata-escritural-signataria-api
```

Trata-se da evolução da aplicação existente, não da criação de uma nova aplicação.

A Signatária simulada exercerá duas capacidades de negócio distintas:

### Escrituração

Responsável, dentro do escopo simulado, pelo ciclo de vida das duplicatas escriturais mantidas pelo Escriturador, seus eventos e processamentos.

### Registro

Responsável, dentro do escopo simulado, pelas operações de registro, agendas, autorizações de consulta, contratos, atos cambiais e respectivos efeitos, conforme os casos de uso e contratos públicos selecionados para implementação.

As duas capacidades deverão ter responsabilidades claramente delimitadas, mas não exigirão processos executáveis ou bancos independentes.

O termo *Signatária* representa a identidade da infraestrutura simulada, não um novo bounded context que substitui automaticamente os conceitos de Escrituração e Registro.

---

## 5. Fronteiras arquiteturais

As fronteiras a seguir são decisões arquiteturais e simplificações do projeto.

### Agente Financiador

O `duplicata-escritural-financiamento-api` representa o Banco DUP.

Ele mantém as decisões de crédito, elegibilidade, limites, precificação, concentração, carteira, propostas e operações de financiamento.

Na primeira versão, integrará uma única infraestrutura contratada, representada pela Signatária simulada. Essa é uma simplificação do projeto, não uma regra universal: a definição de Participante no Glossário da [Convenção][convencao-bcb] admite Agentes Financiadores com conexão operacional ativa com pelo menos uma Signatária.

O Financiamento não implementará lógica de roteamento ou interoperabilidade nem acessará diretamente a Base de Controle ou o Ambiente de Interoperabilidade.

A porta candidata para a integração contratada é `GatewayRegistradora`, pois expressa a capacidade externa utilizada pelo Financiador.

O nome definitivo e os métodos dessa porta serão refinados quando seu primeiro caso de uso for implementado. `GatewayEscrituradora`, presente em exemplos anteriores, não deve ser substituído indiscriminadamente; sua responsabilidade deverá ser avaliada conforme o caso de uso e as fronteiras deste ADR.

### Signatária

O `duplicata-escritural-signataria-api` simulará a infraestrutura contratada.

Deverá:

- distinguir Escrituração e Registro;
- responder às operações suportadas pelos contratos simulados;
- preservar a responsabilidade sobre informações locais;
- encapsular a complexidade da interoperabilidade;
- tratar adequadamente operações que dependam de dados mantidos por outros participantes.

Não deverá implementar políticas de concessão de crédito do Banco DUP.

### Ambiente de Interoperabilidade

O futuro `duplicata-escritural-interoperabilidade-api` representará uma simulação simplificada da Base de Controle e dos mecanismos necessários à interoperabilidade.

Será uma aplicação independente apenas quando houver um caso de uso que justifique sua criação.

A integração entre Signatária e esse ambiente poderá utilizar uma porta como `GatewayInteroperabilidade`. Essa porta é conceitual e ainda não está implementada.

Participantes externos poderão ser representados por respostas simuladas e identificadores genéricos, como `SIGNATARIA_EXTERNA_001`, sem criar novas Signatárias executáveis.

---

## 6. Propriedade dos dados

Cada contexto deve preservar suas fontes de verdade dentro do escopo simulado:

- Financiamento controla suas informações de clientes, crédito e operações financeiras;
- Escrituração controla as duplicatas e os respectivos estados sob sua responsabilidade;
- Registro controla os registros e efeitos de negociação sob sua responsabilidade, conforme o escopo simulado;
- Interoperabilidade mantém informações de localização, direcionamento e controle necessárias à troca entre participantes.

Informações de ativos externos podem ser referenciadas ou consultadas por contratos de integração, mas não devem ser assumidas como dados locais de autoridade.

Não haverá compartilhamento direto de entidades JPA, tabelas ou chaves estrangeiras entre as aplicações.

---

## 7. Consequências técnicas e impactos de migração

A implementação desta decisão deverá considerar:

1. Renomeação controlada do subprojeto Gradle.
2. Atualização dos packages Java e classes de inicialização afetadas.
3. Atualização de `spring.application.name`.
4. Atualização das referências documentais e de configuração.
5. Preservação ou migração explícita dos identificadores de PostgreSQL, Flyway, Docker e volumes, sem provocar perda de dados.
6. Adequação dos testes existentes à nova identidade.
7. Preservação da separação lógica entre Escrituração e Registro.
8. Nenhuma criação automática de módulos ou classes sem caso de uso concreto.

O nome lógico do monorepo permanece `duplicata-escritural-plataforma`.

A renomeação não implica alterar imediatamente o diretório raiz do repositório GitHub.

O registro deste ADR não executa a migração: Java, Gradle, configurações Spring, Docker, banco e testes permanecem inalterados nesta etapa.

---

## 8. Trade-offs

### Benefícios

- Nome mais representativo da infraestrutura simulada.
- Responsabilidades de mercado explicitamente diferenciadas.
- Menor quantidade de aplicações executáveis em comparação com as alternativas C e D.
- Evolução incremental preservada.
- Melhor justificativa arquitetural para apresentação em portfólio técnico.

### Custos

- Refatoração dos identificadores existentes.
- Necessidade de disciplinar as fronteiras internas.
- Necessidade de explicar que uma aplicação simula múltiplos papéis institucionais.

---

## 9. Relação com ADRs anteriores

Os ADRs [0001](0001-base-controle-e-escrituracao-assincrona.md) e [0002](0002-intermediacao-e-fronteiras-de-interoperabilidade.md) permanecem preservados como registros históricos.

Este ADR consolida a evolução da identidade da aplicação existente e a separação entre os papéis de Escrituração, Registro e Signatária. O nome `escrituradora-api` nos ADRs anteriores permanece como referência histórica à aplicação que será evoluída; as capacidades antes agrupadas sob essa identidade passam a ser distinguidas conforme esta decisão.

Não substitui automaticamente outras decisões de negócio ou procedimentos de interoperabilidade anteriormente documentados. Eventuais conflitos devem ser explicitados e conciliados antes da implementação.

---

## 10. Critérios de validação

A implementação estará concluída quando:

- o Gradle reconhecer a aplicação com o novo nome;
- não existir uma aplicação `escrituradora-api` duplicada;
- o build e os testes existentes passarem;
- o contexto Spring iniciar corretamente;
- as configurações de persistência e Flyway permanecerem funcionais;
- a documentação distinguir a infraestrutura simulada de suas capacidades internas;
- nenhuma nova funcionalidade ou regra de negócio tiver sido introduzida inadvertidamente pela renomeação.

Esses critérios validam a implementação futura; não são satisfeitos apenas pela criação deste documento.

---

## 11. Limitações

Esta decisão não autoriza acesso a ambientes reais da B3, de outras Signatárias ou do Banco Central.

A implementação será um simulador educacional baseado em documentação pública, com simplificações e hipóteses explicitamente identificadas.

Os contratos implementados deverão ser selecionados e verificados nos documentos oficiais. O [Regulamento][regulamento-b3], o [Manual de Normas][manual-b3] e o [Catálogo de APIs de Duplicata Escritural][apis-b3] são referências documentais, não autorização de acesso nem evidência de funcionalidades existentes no simulador.

---

## Referências oficiais

- [Convenção de Duplicatas Escriturais — Banco Central, 29/11/2024, Glossário: Participante e Signatárias][convencao-bcb].
- [Infraestruturas do Mercado Financeiro — Banco Central](https://www.bcb.gov.br/estabilidadefinanceira/infraestruturamercado).
- [Escrituração — B3: Regulamento e Manual de Normas][escrituracao-b3].
- [Regulamento de Duplicatas Escriturais — B3, 30/06/2026][regulamento-b3].
- [Manual de Normas de Duplicatas Escriturais — B3, 30/06/2026][manual-b3].
- [Catálogo de APIs de Duplicata Escritural — B3][apis-b3].

Referências consultadas em 2026-10-29.

[convencao-bcb]: https://www.bcb.gov.br/content/estabilidadefinanceira/spb_docs/convencoes/Conven%C3%A7%C3%A3o%20entre%20Entidades%20Registradoras,%20Deposit%C3%A1rios%20Centrais%20e%20Escrituradores%20-%20Duplicatas%20Escriturais.pdf
[escrituracao-b3]: https://www.b3.com.br/pt_br/regulacao/estrutura-normativa/regulamentos-e-manuais/escrituracao.htm
[regulamento-b3]: https://www.b3.com.br/data/files/80/A3/4D/70/05510A1094DCD8F9AC094EA8/Regulamento%20de%20Duplicata%20Escritural%20-%2030062026%201.pdf
[manual-b3]: https://www.b3.com.br/data/files/A4/93/09/70/05510A1094DCD8F9AC094EA8/Manual%20de%20Normas%20de%20Duplicata%20Escritural%20-%2030062026%201.pdf
[apis-b3]: https://developers.b3.com.br/apis?tag=DUPLICATA+ESCRITURAL
