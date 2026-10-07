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

# 4. Arquitetura atual

O projeto utiliza um monorepo Gradle multi-project.

Estrutura inicial:

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

Atualmente existem duas aplicações Spring Boot principais.

## duplicata-escritural-financiamento-api

Representa o domínio transacional controlado pelo Agente Financiador.

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

## duplicata-escritural-escrituradora-api

Representa uma simulação de uma escrituradora/registradora externa.

Para o financiamento, essa aplicação deve ser tratada como um sistema externo que ele não controla.

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

A escrituradora é um mock/simulador criado exclusivamente para este projeto.

Nunca assuma acesso real aos ambientes de B3, Núclea, Banco Central ou outras instituições.

---

# 5. Stack atual

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

# 6. Filosofia arquitetural

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

# 7. Microsserviços

Não transforme módulos em microsserviços apenas para demonstrar arquitetura distribuída.

Um novo serviço deve possuir justificativa concreta como:

* deploy independente;
* escala independente;
* isolamento operacional;
* responsabilidade claramente independente;
* ownership diferente;
* ciclo de vida independente;
* fronteira externa real.

Exemplo de justificativa válida:

A escrituradora é independente porque representa um sistema externo ao Agente Financiador.

Exemplo de justificativa inválida:

`MotorElegibilidade` virou microsserviço porque queremos demonstrar microsserviços.

Se elegibilidade compartilhar invariantes e transação com financiamento, mantenha-a como módulo interno.

---

# 8. Modularidade

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

# 9. Arquitetura hexagonal

Arquitetura hexagonal é um instrumento, não um objetivo.

Utilize ports/adapters quando existir uma fronteira real.

Exemplo válido:

```text
GatewayEscrituradora
```

porque a escrituradora representa uma integração externa.

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

# 10. DDD

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

# 11. Nomenclatura

Nomes relacionados ao domínio e às capacidades de negócio devem preferencialmente estar em português.

Exemplos:

```text
ServicoFinanciamento
MotorElegibilidade
MotorSelecaoCarteira
PoliticaFinanciamento
ServicoSimulacao
GatewayEscrituradora
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

Exemplos:

```text
duplicata-escritural-financiamento-api
duplicata-escritural-escrituradora-api
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

# 12. Código Java

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

# 13. Dependências

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

# 14. Banco de dados

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

# 15. Kafka

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

# 16. Transactional Outbox

Quando uma mesma operação precisar:

1. persistir mudança de estado no banco;
2. publicar evento para Kafka;

não implemente dual write ingênuo.

Avalie Transactional Outbox.

O Outbox só deve surgir quando esse problema existir concretamente.

---

# 17. Idempotência

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

# 18. Concorrência

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

# 19. Resiliência

Quando a integração entre financiamento e escrituradora existir, trate-a como integração distribuída real.

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

# 20. Observabilidade

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

# 21. APIs

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

# 22. Spring AI, MCP e Agents

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

# 23. Documentação regulatória e integrações externas

Nunca invente:

* APIs;
* endpoints;
* campos;
* regras;
* contratos;
* comportamento;

de B3, Núclea, Banco Central ou outras instituições.

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

---

# 24. Processo obrigatório antes de codificar

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

# 25. Decisões arquiteturais

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

---

# 26. Escopo das mudanças

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

# 27. Testes

Toda alteração de comportamento deve considerar testes compatíveis com seu risco.

Priorize:

* testes unitários para regras determinísticas;
* testes de integração quando infraestrutura fizer parte do comportamento;
* Testcontainers quando banco ou infraestrutura real forem relevantes.

Não crie mocks apenas para aumentar cobertura.

Teste comportamento significativo.

Não teste detalhes internos desnecessariamente.

---

# 28. Critério de conclusão

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

# 29. Git

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

# 30. Secrets

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

# 31. Arquivos gerados

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

# 32. Skills do projeto

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

# 33. Ponytail

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

Se `GatewayEscrituradora` representa uma fronteira externa real, não remova essa abstração simplesmente porque uma chamada HTTP direta possui menos linhas.

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

# 34. Autonomia do agente

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

# 35. Comportamento esperado do Codex

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

# 36. Regra final

O código deste projeto deve contar uma história arquitetural defensável.

Cada tecnologia, abstração, serviço e padrão relevante deve responder à pergunta:

> Qual problema real isso resolve?

Se não houver uma boa resposta, provavelmente não deve existir ainda.
