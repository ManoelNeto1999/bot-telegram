ALTER TABLE usuarios_saldo
ADD credito_atual DECIMAL(18,2) NOT NULL
    CONSTRAINT DF_usuarios_saldo_credito_atual DEFAULT 0;
