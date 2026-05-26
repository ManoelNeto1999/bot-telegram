package br.com.tdm.whatsappsaldo.dto;

import br.com.tdm.whatsappsaldo.enums.TipoMovimentacao;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimentacaoExtratoResponse(
        TipoMovimentacao tipo,
        BigDecimal valor,
        BigDecimal saldoAntes,
        BigDecimal saldoDepois,
        String mensagemOriginal,
        LocalDateTime criadoEm
) {
}
