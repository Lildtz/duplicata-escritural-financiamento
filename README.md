# Plataforma de Financiamento de Duplicatas Escriturais

Plataforma experimental para explorar o financiamento de Duplicatas Escriturais no contexto do sistema financeiro brasileiro, sob a perspectiva de um **Agente Financiador**.

O projeto combina engenharia de software, sistemas transacionais, arquitetura distribuída e inteligência artificial, utilizando principalmente Java, Spring Boot e Spring AI.

## Objetivo

Construir uma plataforma que permita simular operações de financiamento de duplicatas escriturais, desde a consulta de títulos e avaliação de elegibilidade até a seleção de carteiras, simulação financeira e recomendação de financiamento.

Além do domínio financeiro, o projeto busca demonstrar decisões arquiteturais fundamentadas, tratamento de falhas, consistência de dados, resiliência e integração de agentes de IA com serviços transacionais.

## Arquitetura proposta

A plataforma adota uma abordagem híbrida, combinando modularidade interna com componentes independentes quando houver justificativa arquitetural.

| Componente | Responsabilidade |
|---|---|
| `duplicata-escritural-financiamento-api` | Regras transacionais de financiamento, elegibilidade, simulação e carteira |
| `duplicata-escritural-escrituradora-api` | Simulação de uma integração externa com escrituradora |
| `duplicata-escritural-financiamento-mcp` | Disponibilização de capacidades de negócio via MCP |
| `duplicata-escritural-agente-financiamento` | Interpretação de solicitações e orquestração de ferramentas por IA |

A arquitetura será implementada incrementalmente, evitando a criação de componentes sem necessidade concreta.

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

**Fase de referência: concepção e definição arquitetural.**

O desenho inicial contempla os componentes principais, suas responsabilidades e os princípios de engenharia que orientarão a implementação.

O progresso de cada componente deverá ser atualizado conforme entregas verificadas no repositório.

| Frente | Situação documentada |
|---|---|
| Definição do domínio e objetivos | Concepção inicial estabelecida |
| Desenho arquitetural | Arquitetura proposta |
| API de financiamento | Implementação a verificar |
| Simulador de escrituradora | Implementação a verificar |
| MCP Server e agente de IA | Implementação a verificar |
| Integração assíncrona com Kafka | Evolução prevista |

## Evolução do projeto

O desenvolvimento seguirá entregas incrementais, priorizando funcionalidades verificáveis e decisões arquiteturais justificadas.

Cada evolução relevante deverá atualizar o estado dos componentes, as instruções de execução e a documentação das decisões técnicas.

## Referências e limitações

O domínio será fundamentado, sempre que possível, em documentação pública e oficial, incluindo legislação brasileira e materiais do Banco Central, da B3 e da Núclea.

**Este é um projeto independente, educacional e experimental.** Não possui integração real ou vínculo institucional com essas entidades. Interfaces externas serão representadas por mocks e simuladores desenvolvidos com base em informações públicas.