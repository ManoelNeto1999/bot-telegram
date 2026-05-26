package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.enums.TipoComando;
import java.math.BigDecimal;

public record ResultadoParseComando(
        TipoComando tipo,
        BigDecimal valor,
        String descricao
) {
}
