CREATE TABLE escrituradora.sacador (
    id UUID PRIMARY KEY,
    cnpj VARCHAR(14) NOT NULL UNIQUE CHECK (cnpj ~ '^[A-Z0-9]{12}[0-9]{2}$'),
    nome VARCHAR(150) NOT NULL CHECK (btrim(nome) <> ''),
    domicilio JSONB NOT NULL CHECK (jsonb_typeof(domicilio) = 'object'),
    inicio_operacao DATE NOT NULL,
    habilitado BOOLEAN NOT NULL
);

CREATE TABLE escrituradora.processamento_escrituracao (
    id UUID PRIMARY KEY,
    sacador_id UUID NOT NULL REFERENCES escrituradora.sacador(id),
    operacao VARCHAR(30) NOT NULL CHECK (operacao = 'ESCRITURACAO_MANUAL'),
    chave_idempotencia VARCHAR(128) NOT NULL CHECK (btrim(chave_idempotencia) <> ''),
    dados JSONB NOT NULL CHECK (jsonb_typeof(dados) = 'object'),
    sacador_no_recebimento JSONB NOT NULL CHECK (jsonb_typeof(sacador_no_recebimento) = 'object'),
    recebido_em TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado = 'PENDENTE'),
    CONSTRAINT uk_processamento_idempotencia UNIQUE (sacador_id, operacao, chave_idempotencia)
);
