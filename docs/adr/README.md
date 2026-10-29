# Architecture Decision Records

- ADR 0001 — Base de Controle e escrituração assíncrona
- ADR 0002 — Intermediação e fronteiras de interoperabilidade
- ADR 0003 — Pendente: evolução da aplicação existente, papéis de mercado e responsabilidades das portas

Os ADRs registram decisões no contexto em que foram tomadas; seus nomes e diagramas históricos foram preservados. Eles não são um inventário de funcionalidades implementadas. O estado atual e a arquitetura planejada estão descritos no [AGENTS.md](../../AGENTS.md#4-estado-atual-e-arquitetura-planejada).

O ADR 0003 deverá definir a eventual evolução, renomeação ou reorganização de `duplicata-escritural-escrituradora-api`, a distinção dos papéis de Escriturador, Registradora e Signatária e as responsabilidades de `GatewayEscrituradora`, `GatewayRegistradora` e `GatewayInteroperabilidade`, preservando as fronteiras do ADR 0002. A integração com uma única Registradora é uma simplificação proposta para a primeira versão. `duplicata-escritural-signataria-api` permanece uma proposta de nomenclatura para a possível evolução da aplicação existente, sem aprovação de um novo executável que duplique suas responsabilidades. O ADR 0003 não é implementado nesta revisão documental.
