package br.com.tdm.whatsappsaldo.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ValorMonetarioParserService {

    private static final Pattern BR_MONETARIO_PATTERN =
            Pattern.compile("^(?:\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,\\d{1,2})?$");
    private static final Pattern SIMPLE_DOT_MONETARIO_PATTERN =
            Pattern.compile("^\\d+(?:\\.\\d{1,2})?$");
    private static final BigDecimal MAX_VALOR_DECIMAL_18_2 = new BigDecimal("9999999999999999.99");

    public ParseValorResultado parseValorMonetarioComDescricao(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String textoLimpo = texto.trim();
        int indiceEspaco = textoLimpo.indexOf(' ');
        String tokenValor = indiceEspaco < 0 ? textoLimpo : textoLimpo.substring(0, indiceEspaco);
        String descricao = indiceEspaco < 0 ? null : textoLimpo.substring(indiceEspaco + 1).trim();
        if (descricao != null && descricao.isBlank()) {
            descricao = null;
        }

        BigDecimal valor = parseValorMonetario(tokenValor);
        if (valor == null) {
            return null;
        }

        return new ParseValorResultado(valor, descricao);
    }

    public BigDecimal parseValorMonetario(String textoValor) {
        if (textoValor == null || textoValor.isBlank()) {
            return null;
        }

        String valorNormalizado = textoValor.trim();
        String valorConvertido;

        if (BR_MONETARIO_PATTERN.matcher(valorNormalizado).matches()) {
            valorConvertido = valorNormalizado.replace(".", "").replace(',', '.');
        } else if (SIMPLE_DOT_MONETARIO_PATTERN.matcher(valorNormalizado).matches()) {
            valorConvertido = valorNormalizado;
        } else {
            return null;
        }

        BigDecimal valor;
        try {
            valor = new BigDecimal(valorConvertido);
        } catch (NumberFormatException ex) {
            return null;
        }

        if (valor.compareTo(MAX_VALOR_DECIMAL_18_2) > 0) {
            return null;
        }

        return valor;
    }

    public boolean validarValorMaiorQueZero(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }

    public record ParseValorResultado(
            BigDecimal valor,
            String descricao
    ) {
    }
}
