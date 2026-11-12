CREATE TABLE escrituradora.duplicata (
    id UUID PRIMARY KEY,
    sacador_id UUID NOT NULL REFERENCES escrituradora.sacador(id),
    sacador_cnpj VARCHAR(14) NOT NULL CHECK (sacador_cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    numero_duplicata VARCHAR(60) NOT NULL CHECK (btrim(numero_duplicata) <> ''),
    numero_fatura VARCHAR(60) NOT NULL CHECK (btrim(numero_fatura) <> ''),
    numero_parcela INTEGER NOT NULL CHECK (numero_parcela = 1),
    dados JSONB NOT NULL CHECK (jsonb_typeof(dados) = 'object'),
    escriturada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_duplicata_sacador_numero UNIQUE (sacador_id, numero_duplicata),
    CONSTRAINT uk_duplicata_sacador_fatura_parcela UNIQUE (sacador_id, numero_fatura, numero_parcela),
    CONSTRAINT ck_duplicata_identidade CHECK (
        dados ->> 'numeroDuplicata' IS NOT NULL AND dados ->> 'numeroDuplicata' = numero_duplicata
        AND dados ->> 'numeroFatura' IS NOT NULL AND dados ->> 'numeroFatura' = numero_fatura
        AND dados ->> 'numeroParcela' IS NOT NULL AND (dados ->> 'numeroParcela')::integer = numero_parcela)
);

ALTER TABLE escrituradora.processamento_escrituracao
    DROP CONSTRAINT processamento_escrituracao_estado_check,
    ADD COLUMN tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas BETWEEN 0 AND 3),
    ADD COLUMN proxima_execucao_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN reserva_expira_em TIMESTAMPTZ,
    ADD COLUMN id_duplicata UUID REFERENCES escrituradora.duplicata(id),
    ADD COLUMN escriturada_em TIMESTAMPTZ,
    ADD COLUMN finalizado_em TIMESTAMPTZ,
    ADD COLUMN codigo_resultado VARCHAR(40),
    ADD CONSTRAINT ck_processamento_estado CHECK (
        estado IN ('PENDENTE', 'EM_PROCESSAMENTO', 'CONCLUIDO', 'REJEITADO', 'FALHA_TECNICA')),
    ADD CONSTRAINT ck_processamento_reserva CHECK (
        (estado = 'EM_PROCESSAMENTO' AND reserva_expira_em IS NOT NULL AND tentativas > 0)
        OR (estado <> 'EM_PROCESSAMENTO' AND reserva_expira_em IS NULL)),
    ADD CONSTRAINT ck_processamento_finalizacao CHECK (
        (estado IN ('CONCLUIDO', 'REJEITADO', 'FALHA_TECNICA') AND finalizado_em IS NOT NULL)
        OR (estado IN ('PENDENTE', 'EM_PROCESSAMENTO') AND finalizado_em IS NULL)),
    ADD CONSTRAINT ck_processamento_duplicata CHECK (
        (estado = 'CONCLUIDO' AND id_duplicata IS NOT NULL AND escriturada_em IS NOT NULL AND codigo_resultado IS NULL)
        OR (estado <> 'CONCLUIDO' AND id_duplicata IS NULL AND escriturada_em IS NULL)),
    ADD CONSTRAINT ck_processamento_diagnostico CHECK (
        estado NOT IN ('REJEITADO', 'FALHA_TECNICA') OR codigo_resultado IS NOT NULL);

UPDATE escrituradora.processamento_escrituracao SET proxima_execucao_em = recebido_em;

-- Polling de pendências e recuperação de reservas; índices parciais excluem resultados terminais.
CREATE INDEX ix_processamento_pendente ON escrituradora.processamento_escrituracao (proxima_execucao_em, recebido_em, id)
    WHERE estado = 'PENDENTE';
CREATE INDEX ix_processamento_reserva ON escrituradora.processamento_escrituracao (reserva_expira_em, recebido_em, id)
    WHERE estado = 'EM_PROCESSAMENTO';
