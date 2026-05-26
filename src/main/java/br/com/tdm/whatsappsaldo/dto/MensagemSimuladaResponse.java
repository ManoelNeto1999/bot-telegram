package br.com.tdm.whatsappsaldo.dto;

import java.math.BigDecimal;

public record MensagemSimuladaResponse(
        String telefone,
        String mensagemRecebida,
        String resposta,
        BigDecimal saldoAtual
) {
}
