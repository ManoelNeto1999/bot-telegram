package br.com.tdm.whatsappsaldo.dto;

import java.util.List;

public record ExtratoTelefoneResponse(
        String telefone,
        List<MovimentacaoExtratoResponse> movimentacoes
) {
}
