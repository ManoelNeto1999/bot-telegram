CREATE TABLE usuarios_saldo (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    telefone VARCHAR(30) NOT NULL UNIQUE,
    saldo_atual DECIMAL(18,2) NOT NULL,
    criado_em DATETIME2 NOT NULL,
    atualizado_em DATETIME2 NOT NULL
);

CREATE TABLE movimentacoes_saldo (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    telefone VARCHAR(30) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    valor DECIMAL(18,2) NOT NULL,
    saldo_antes DECIMAL(18,2) NOT NULL,
    saldo_depois DECIMAL(18,2) NOT NULL,
    mensagem_original VARCHAR(500) NULL,
    criado_em DATETIME2 NOT NULL
);

CREATE INDEX idx_movimentacoes_saldo_telefone_criado_em
    ON movimentacoes_saldo (telefone, criado_em DESC);
