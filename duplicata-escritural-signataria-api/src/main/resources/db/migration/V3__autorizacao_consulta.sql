CREATE TABLE escrituradora.autorizacao_consulta (
    id UUID PRIMARY KEY,
    financiador_id UUID NOT NULL,
    sacador_id UUID NOT NULL REFERENCES escrituradora.sacador(id),
    cnpj_sacador VARCHAR(14) NOT NULL CHECK (cnpj_sacador ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    operacao VARCHAR(40) NOT NULL CHECK (operacao = 'SOLICITAR_AUTORIZACAO_CONSULTA'),
    chave_idempotencia VARCHAR(128) COLLATE "C" NOT NULL CHECK (chave_idempotencia ~ '^[A-Za-z0-9._:-]{1,128}$'),
    solicitada_em TIMESTAMPTZ NOT NULL,
    fim_vigencia TIMESTAMPTZ NOT NULL,
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('PENDENTE', 'ATIVA', 'RECUSADA', 'REVOGADA')),
    decidida_em TIMESTAMPTZ,
    inicio_vigencia TIMESTAMPTZ,
    revogada_em TIMESTAMPTZ,
    revogado_por VARCHAR(12) CHECK (revogado_por IN ('SACADOR', 'FINANCIADOR')),
    CONSTRAINT uk_autorizacao_consulta_idempotencia UNIQUE (financiador_id, operacao, chave_idempotencia),
    CONSTRAINT ck_autorizacao_consulta_vigencia CHECK (fim_vigencia > solicitada_em),
    CONSTRAINT ck_autorizacao_consulta_decisao CHECK (
        (decidida_em IS NULL AND inicio_vigencia IS NULL)
        OR (decidida_em IS NOT NULL AND decidida_em >= solicitada_em AND decidida_em < fim_vigencia
            AND (inicio_vigencia IS NULL OR inicio_vigencia = decidida_em))),
    CONSTRAINT ck_autorizacao_consulta_estado CHECK (
        (estado = 'PENDENTE' AND decidida_em IS NULL AND inicio_vigencia IS NULL
            AND revogada_em IS NULL AND revogado_por IS NULL)
        OR (estado = 'ATIVA' AND decidida_em IS NOT NULL AND inicio_vigencia IS NOT NULL
            AND revogada_em IS NULL AND revogado_por IS NULL)
        OR (estado = 'RECUSADA' AND decidida_em IS NOT NULL AND inicio_vigencia IS NULL
            AND revogada_em IS NULL AND revogado_por IS NULL)
        OR (estado = 'REVOGADA' AND revogada_em IS NOT NULL AND revogado_por IS NOT NULL
            AND revogada_em >= solicitada_em
            AND (decidida_em IS NULL OR (inicio_vigencia IS NOT NULL AND revogada_em >= decidida_em))))
);
