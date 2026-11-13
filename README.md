# Plataforma de Financiamento de Duplicatas Escriturais

Plataforma experimental para explorar o financiamento de Duplicatas Escriturais no contexto do sistema financeiro brasileiro, sob a perspectiva de um **Agente Financiador**.

O projeto utiliza Java e Spring Boot para explorar sistemas transacionais e integração distribuída. Spring AI e as capacidades de inteligência artificial permanecem planejadas.

## Objetivo

Construir uma plataforma que permita simular operações de financiamento de duplicatas escriturais, desde a consulta de títulos e avaliação de elegibilidade até a seleção de carteiras, simulação financeira e recomendação de financiamento.

Além do domínio financeiro, o projeto busca demonstrar decisões arquiteturais fundamentadas, tratamento de falhas, consistência de dados, resiliência e integração de agentes de IA com serviços transacionais.

## Arquitetura proposta

A plataforma adota uma abordagem híbrida, combinando modularidade interna com componentes independentes quando houver justificativa arquitetural.

| Componente | Responsabilidade |
|---|---|
| `duplicata-escritural-financiamento-api` | Banco DUP: solicitação/consulta de autorização e consulta paginada de Duplicatas por HTTP; crédito, elegibilidade e simulação planejados |
| `duplicata-escritural-signataria-api` | Simulador externo: Escrituração local, autorização e consulta protegida; Registro regulatório planejado |
| `duplicata-escritural-financiamento-mcp` | Planejado: disponibilização de capacidades de negócio via MCP |
| `duplicata-escritural-agente-financiamento` | Planejado: interpretação de solicitações e orquestração de ferramentas por IA |

A arquitetura será implementada incrementalmente, evitando a criação de componentes sem necessidade concreta.

Na Entrega 3.3, o fluxo implementado é:

```text
Banco DUP: ServicoConsultaRecebiveis → GatewayRegistradora
                                      → adapter RestClient → HTTP → Signatária
```

Cada aplicação possui modelos próprios. O Banco DUP não acessa o PostgreSQL da Signatária, não replica autorizações e não executa roteamento ou interoperabilidade. O ADR 0003 mantém esses limites; os ADRs anteriores são preservados como histórico.

## Princípio fundamental

**A inteligência artificial não executa regras financeiras críticas.**

O agente de IA atua na interpretação, no planejamento, na utilização de ferramentas e na elaboração de recomendações.

Elegibilidade, cálculos financeiros, limites, validações, consistência transacional e alterações de estado permanecem sob responsabilidade de código determinístico.

## Tecnologias previstas

- Java e Spring Boot
- Spring AI, MCP e Tool Calling
- PostgreSQL
- Kafka e arquitetura orientada a eventos
- APIs REST
- DDD e arquitetura hexagonal
- Transactional Outbox e idempotência
- Observabilidade e testes automatizados

A presença de uma tecnologia nesta lista não significa que ela já esteja implementada.

## Estado do projeto

**Fase de referência: Entrega 4.1 — OpenAPI e Swagger UI aprovada tecnicamente em 13/11/2026, com Code Review encerrado favoravelmente; versionamento pendente de autorização.** A validação da UI foi HTTP, sem inspeção visual efetiva. A 3.4 foi versionada em `27e950e`; a 3.3 permanece em `04081ca`.

As Entregas 1, 2, 3.1 e 3.2 estão implementadas. A 3.2 recebeu aprovação técnica retrospectiva em 13/11/2026, sem evidência de aceite histórico anterior, conforme o [roadmap](docs/roadmap.md). A 3.4 exercita o fluxo completo do simulador, preservando as fronteiras entre as aplicações.

| Frente | Situação documentada |
|---|---|
| Definição do domínio e objetivos | Concepção inicial estabelecida |
| Desenho arquitetural | Fronteiras documentadas nos [ADRs](docs/adr/README.md) |
| API de financiamento | Integração HTTP implementada; aprovação técnica final da 3.3 em 13/11/2026 |
| Simulador de Signatária | Escrituração e consulta local autorizada implementadas |
| MCP Server e agente de IA | Planejados |
| Integração assíncrona com Kafka | Evolução prevista |

## Execução e testes

Baseline: Java 21, Spring Boot 4.1.x e Gradle Wrapper 9.8. Docker é necessário para os testes PostgreSQL/Testcontainers. Execute na raiz:

```powershell
.\gradlew.bat clean build
git diff --check
git status --short
```

A execução e o provisionamento da Signatária estão no [guia local](infra/local/README.md) e no [guia de autorização](docs/autorizacao-consulta.md#identidades-e-provisionamento-local). Ela utiliza a porta 8081; o Banco DUP utiliza 8080.

A integração exige o perfil `local` em ambas as aplicações e um Bearer cujo hash esteja provisionado na Signatária com o `financiador-id` do Banco DUP. O token permanece somente no ambiente do Financiamento; não existe credencial padrão ou identidade por CNPJ em header.

O provisionamento define a identidade utilizada: um token válido de outro Financiador fará a aplicação atuar como aquele participante. Antes de consultar títulos, confira a associação do hash e o `financiadorId` retornado pela solicitação/consulta de autorização, conforme o [guia de provisionamento](docs/autorizacao-consulta.md#conexão-e-identidade-local). Essa conferência não substitui a autorização remota em cada página.

| Variável do Financiamento | Padrão / requisito |
|---|---|
| `SIGNATARIA_URL_BASE` | `http://localhost:8081`; HTTPS ou HTTP em loopback, sem caminho/query/credenciais |
| `SIGNATARIA_TOKEN` | Obrigatória no perfil `local`; token do Financiador beneficiário |
| `SIGNATARIA_TIMEOUT_CONEXAO` | `2s` |
| `SIGNATARIA_TIMEOUT_LEITURA` | `5s` |

Com o token provisionado de forma privada na variável de ambiente `SIGNATARIA_TOKEN`:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:bootRun "--args=--spring.profiles.active=local"
```

Sem perfil `local`, o Banco DUP mantém apenas o bootstrap/Actuator, sem ativar a integração. Configuração local ausente ou inválida impede a inicialização. Health checks: `http://localhost:8080/actuator/health` e `http://localhost:8081/actuator/health`.

Os casos de uso são beans de aplicação, sem novos endpoints no Banco DUP nesta entrega. O adapter consome POST `/api/v1/autorizacoes-consulta`, GET `/api/v1/autorizacoes-consulta/{id}` e GET `/api/v1/autorizacoes-consulta/{id}/duplicatas?pagina=0&tamanho=50`. Aprovação continua sendo responsabilidade do Sacador na Signatária.

Consulte [contratos, falhas e limites da integração 3.3](docs/autorizacao-consulta.md#entrega-33--integração-http-do-banco-dup). A paginação é repassada por chamada, sem cache ou retry da aplicação. A autorização é revalidada remotamente em cada página. `valorNominal` não é saldo financiável; `consultadaEm` é uma referência local, sem snapshot entre páginas. O escopo é `LOCAL_SIMULADO`, sem equivalência com Agenda regulatória, Registro, IUD ou disponibilidade financeira.

Os testes da 3.3 exercitam o adapter com respostas simuladas e HTTP descartável do JDK, incluindo configuração, Bearer, falhas e timeouts. A 3.4 acrescenta a Signatária real em outro processo e PostgreSQL descartável, com migrations, segurança e agendador ativos. O Banco DUP usa seu contexto Spring no perfil `local`, sem servidor de entrada, e chama o `RestClient` real.

Para executar somente o fluxo vertical, com Java 21 e Docker disponíveis:

```powershell
.\gradlew.bat :duplicata-escritural-financiamento-api:test --tests '*FluxoConsultaAutorizadaVerticalTests'
```

O Wrapper gera o JAR da Signatária; o teste provisiona participantes, tokens aleatórios e portas dinâmicas, recebe Escriturações Manuais e aguarda seu processamento antes de solicitar/aprovar autorizações e consultar páginas. Não requer Compose, configuração manual de credenciais ou dados preexistentes. A suíte também integra o `clean build`. Veja [sequência, cenários e limites da validação vertical](docs/autorizacao-consulta.md#entrega-34--validação-vertical).

## OpenAPI e Swagger UI locais

Na Signatária com perfil `local` e identidades provisionadas conforme o [guia local](infra/local/README.md), acesse `http://localhost:8081/swagger-ui.html` ou `http://localhost:8081/v3/api-docs`. A documentação é pública somente nesse perfil; os endpoints de negócio continuam exigindo Bearer opaco e os papéis corretos. Em **Authorize**, informe apenas um token fictício provisionado pelo operador, sem o prefixo `Bearer`.

O JSON é gerado dos controllers/DTOs existentes, sem especificação estática versionada. O Banco DUP não recebe springdoc nem endpoints novos. Consulte [contratos, erros, segurança e testes da documentação](docs/escrituracao-manual.md#openapi-3-e-swagger-ui--entrega-41).

## Evolução do projeto
O desenvolvimento segue entregas incrementais, priorizando funcionalidades verificáveis, testes automatizados e decisões arquiteturais justificadas.

Cada entrega relevante deve manter atualizados o estado dos componentes, as instruções de execução e a documentação técnica.

Consulte o [Roadmap do Projeto](docs/roadmap.md) para acompanhar
as fases planejadas, os entregáveis e o progresso da implementação.

## Referências e limitações

O domínio será fundamentado, sempre que possível, em documentação pública e oficial, incluindo legislação brasileira e materiais do Banco Central, da B3 e da Núclea.

**Este é um projeto independente, educacional e experimental.** Não possui integração real ou vínculo institucional com essas entidades. Interfaces externas serão representadas por mocks e simuladores desenvolvidos com base em informações públicas.
