# Architecture Decision Records

- ADR 0001 — Base de Controle e escrituração assíncrona
- ADR 0002 — Intermediação e fronteiras de interoperabilidade
- [ADR 0003 — Consolidação dos papéis de Escrituração e Registro na Signatária simulada](0003-consolidacao-papeis-signataria.md) — Aceito; renomeação e recebimento/consulta de Escrituração implementados, demais capacidades e integrações pendentes

Os ADRs registram decisões no contexto em que foram tomadas; seus nomes e diagramas históricos foram preservados. Eles não são um inventário de funcionalidades implementadas. O estado atual e a arquitetura planejada estão descritos no [AGENTS.md](../../AGENTS.md#4-estado-atual-e-arquitetura-planejada).

O ADR 0003 aceita a alternativa B: evoluir e renomear `duplicata-escritural-escrituradora-api` para `duplicata-escritural-signataria-api`, preservando uma única aplicação executável com capacidades distintas de Escrituração e Registro e as fronteiras do ADR 0002. A renomeação foi implementada, preservando os identificadores de persistência e infraestrutura. Escrituração possui recebimento durável e consulta de solicitações, sempre `PENDENTE`; execução, Duplicata, IUD e Registro permanecem planejados. Interoperabilidade é uma aplicação futura. A integração com uma única infraestrutura contratada é uma simplificação da primeira versão. Os nomes definitivos e os contratos das portas serão refinados nos primeiros casos de uso, sem substituição indiscriminada de `GatewayEscrituradora`. O conteúdo dos ADRs 0001, 0002 e 0003 permanece inalterado como registro histórico.
