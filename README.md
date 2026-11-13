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

**Fase de referência: Entrega 3.3 implementada e validada, com aprovação técnica final em 13/11/2026 após Code Review e revisão corretiva favoráveis.** O commit de encerramento foi autorizado separadamente e é verificável no histórico Git.

As Entregas 1, 2, 3.1 e 3.2 estão implementadas. A 3.2 recebeu aprovação técnica retrospectiva em 13/11/2026, sem evidência de aceite histórico anterior, conforme o [roadmap](docs/roadmap.md). A Entrega 3.4 permanece planejada e validará o fluxo completo entre as duas aplicações.

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

Os testes da 3.3 exercitam o adapter com respostas simuladas e HTTP descartável do JDK, incluindo configuração, Bearer, falhas e timeouts. A execução conjunta contra a Signatária real do projeto e o fluxo vertical completo ficam para a Entrega 3.4.

## Evolução do projeto
O desenvolvimento segue entregas incrementais, priorizando funcionalidades verificáveis, testes automatizados e decisões arquiteturais justificadas.

Cada entrega relevante deve manter atualizados o estado dos componentes, as instruções de execução e a documentação técnica.

Consulte o [Roadmap do Projeto](docs/roadmap.md) para acompanhar
as fases planejadas, os entregáveis e o progresso da implementação.

## Referências e limitações

O domínio será fundamentado, sempre que possível, em documentação pública e oficial, incluindo legislação brasileira e materiais do Banco Central, da B3 e da Núclea.

**Este é um projeto independente, educacional e experimental.** Não possui integração real ou vínculo institucional com essas entidades. Interfaces externas serão representadas por mocks e simuladores desenvolvidos com base em informações públicas.
