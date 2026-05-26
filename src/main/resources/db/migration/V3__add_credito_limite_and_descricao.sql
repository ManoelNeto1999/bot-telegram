ALTER TABLE usuarios_saldo
ADD credito_limite DECIMAL(18,2) NOT NULL
    CONSTRAINT DF_usuarios_saldo_credito_limite DEFAULT 0;

ALTER TABLE movimentacoes_saldo
ADD descricao VARCHAR(255) NULL;
