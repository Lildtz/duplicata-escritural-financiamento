# Architecture Decision Records

- ADR 0001 — Base de Controle e escrituração assíncrona
- ADR 0002 — Intermediação e fronteiras de interoperabilidade
- [ADR 0003 — Consolidação dos papéis de Escrituração e Registro na Signatária simulada](0003-consolidacao-papeis-signataria.md) — Aceito; implementação pendente

Os ADRs registram decisões no contexto em que foram tomadas; seus nomes e diagramas históricos foram preservados. Eles não são um inventário de funcionalidades implementadas. O estado atual e a arquitetura planejada estão descritos no [AGENTS.md](../../AGENTS.md#4-estado-atual-e-arquitetura-planejada).

O ADR 0003 aceita a alternativa B: evoluir e renomear `duplicata-escritural-escrituradora-api` para `duplicata-escritural-signataria-api`, preservando uma única aplicação executável com capacidades distintas de Escrituração e Registro e as fronteiras do ADR 0002. A integração com uma única infraestrutura contratada é uma simplificação da primeira versão. Os nomes definitivos e os contratos das portas serão refinados nos primeiros casos de uso, sem substituição indiscriminada de `GatewayEscrituradora`. A decisão foi registrada; a migração e as capacidades de negócio ainda não foram implementadas.
