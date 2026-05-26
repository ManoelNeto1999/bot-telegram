package br.com.tdm.whatsappsaldo.dto;

import java.math.BigDecimal;

public record SaldoAtualResponse(
        String telefone,
        BigDecimal saldoAtual
) {
}
