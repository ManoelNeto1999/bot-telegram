package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.enums.TipoComando;
import br.com.tdm.whatsappsaldo.service.ValorMonetarioParserService.ParseValorResultado;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ComandoSaldoParserService {

    private static final Pattern DEFINIR_SALDO_DIRETO_PATTERN = Pattern.compile("^saldo\\s+(.+)$");
    private static final Pattern DEFINIR_SALDO_EXPLICITO_PATTERN = Pattern.compile("^definir\\s+saldo\\s+(.+)$");
    private static final Pattern DEFINIR_SALDO_TENHO_PATTERN = Pattern.compile("^tenho\\s+(.+)$");
    private static final Pattern DEFINIR_CREDITO_DIRETO_PATTERN = Pattern.compile("^credito\\s+(.+)$");
    private static final Pattern DEFINIR_CREDITO_EXPLICITO_PATTERN = Pattern.compile("^definir\\s+credito\\s+(.+)$");
    private static final Pattern DEFINIR_CREDITO_LIMITE_PATTERN = Pattern.compile("^limite\\s+(.+)$");
    private static final Pattern REGISTRAR_GASTO_CREDITO_PATTERN =
            Pattern.compile("^(usei|gastei|gasto|paguei|comprei)\\s+credito\\s+(.+)$");
    private static final Pattern REGISTRAR_GASTO_PATTERN =
            Pattern.compile("^(gastei|gasto|paguei|comprei)\\s+(.+)$");

    private final ValorMonetarioParserService valorMonetarioParserService;

    public ResultadoParseComando parsear(String mensagemOriginal) {
        if (mensagemOriginal == null || mensagemOriginal.isBlank()) {
            return resultado(TipoComando.DESCONHECIDO, null, null);
        }

        String mensagemNormalizada = normalizarTexto(mensagemOriginal);
        String comandoNormalizado = normalizarComandoTelegram(mensagemNormalizada);

        if (ehAjuda(comandoNormalizado)) {
            return resultado(TipoComando.AJUDA, null, null);
        }
        if (ehDesfazerCredito(comandoNormalizado)) {
            return resultado(TipoComando.DESFAZER_CREDITO, null, null);
        }
        if (ehDesfazerSaldo(comandoNormalizado)) {
            return resultado(TipoComando.DESFAZER_SALDO, null, null);
        }
        if (ehDesfazerUltimo(comandoNormalizado)) {
            return resultado(TipoComando.DESFAZER_ULTIMO, null, null);
        }
        if (ehResumoHoje(comandoNormalizado)) {
            return resultado(TipoComando.RESUMO_HOJE, null, null);
        }
        if (ehResumoSemana(comandoNormalizado)) {
            return resultado(TipoComando.RESUMO_SEMANA, null, null);
        }
        if (ehResumoMes(comandoNormalizado)) {
            return resultado(TipoComando.RESUMO_MES, null, null);
        }
        if (ehResumoGeral(comandoNormalizado)) {
            return resultado(TipoComando.RESUMO_GERAL, null, null);
        }
        if (ehResetarCreditoTotal(comandoNormalizado)) {
            return resultado(TipoComando.RESETAR_CREDITO_TOTAL, null, null);
        }
        if (ehResetarSaldoTotal(comandoNormalizado)) {
            return resultado(TipoComando.RESETAR_SALDO_TOTAL, null, null);
        }
        if (ehResetarTotalIndefinido(comandoNormalizado)) {
            return resultado(TipoComando.RESETAR_TOTAL_INDEFINIDO, null, null);
        }
        if (ehResetarCredito(comandoNormalizado)) {
            return resultado(TipoComando.RESETAR_CREDITO, null, null);
        }
        if (ehResetarSaldo(comandoNormalizado)) {
            return resultado(TipoComando.RESETAR_SALDO, null, null);
        }
        if (ehResetarIndefinido(comandoNormalizado)) {
            return resultado(TipoComando.RESETAR_INDEFINIDO, null, null);
        }
        if (ehConsultarExtratoCredito(comandoNormalizado)) {
            return resultado(TipoComando.CONSULTAR_EXTRATO_CREDITO, null, null);
        }
        if (ehConsultarExtrato(comandoNormalizado)) {
            return resultado(TipoComando.CONSULTAR_EXTRATO, null, null);
        }
        if (ehConsultarCredito(comandoNormalizado)) {
            return resultado(TipoComando.CONSULTAR_CREDITO, null, null);
        }
        if (ehConsultarSaldo(comandoNormalizado)) {
            return resultado(TipoComando.CONSULTAR_SALDO, null, null);
        }

        ResultadoParseComando comando = parsearGastoCreditoExplicito(comandoNormalizado);
        if (comando != null) {
            return comando;
        }

        comando = parsearDefinicaoCreditoExplicita(comandoNormalizado);
        if (comando != null) {
            return comando;
        }

        comando = parsearComandoCreditoDireto(comandoNormalizado);
        if (comando != null) {
            return comando;
        }

        comando = parsearDefinicaoSaldo(comandoNormalizado);
        if (comando != null) {
            return comando;
        }

        comando = parsearGastoSaldo(comandoNormalizado);
        if (comando != null) {
            return comando;
        }

        return resultado(TipoComando.DESCONHECIDO, null, null);
    }

    private ResultadoParseComando parsearGastoCreditoExplicito(String comandoNormalizado) {
        Matcher matcher = REGISTRAR_GASTO_CREDITO_PATTERN.matcher(comandoNormalizado);
        if (!matcher.matches()) {
            return null;
        }

        ParseValorResultado parseValor = valorMonetarioParserService.parseValorMonetarioComDescricao(matcher.group(2));
        if (parseValor == null) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }
        if (!valorMonetarioParserService.validarValorMaiorQueZero(parseValor.valor())) {
            return resultado(TipoComando.VALOR_GASTO_ZERO, null, null);
        }

        return resultado(TipoComando.REGISTRAR_GASTO_CREDITO, parseValor.valor(), parseValor.descricao());
    }

    private ResultadoParseComando parsearDefinicaoCreditoExplicita(String comandoNormalizado) {
        Matcher definirCredito = DEFINIR_CREDITO_EXPLICITO_PATTERN.matcher(comandoNormalizado);
        if (definirCredito.matches()) {
            return parsearDefinicaoCredito(definirCredito.group(1));
        }

        Matcher definirLimite = DEFINIR_CREDITO_LIMITE_PATTERN.matcher(comandoNormalizado);
        if (definirLimite.matches()) {
            return parsearDefinicaoCredito(definirLimite.group(1));
        }

        return null;
    }

    private ResultadoParseComando parsearComandoCreditoDireto(String comandoNormalizado) {
        Matcher creditoDireto = DEFINIR_CREDITO_DIRETO_PATTERN.matcher(comandoNormalizado);
        if (!creditoDireto.matches()) {
            return null;
        }

        ParseValorResultado parseValor = valorMonetarioParserService.parseValorMonetarioComDescricao(creditoDireto.group(1));
        if (parseValor == null) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }

        if (parseValor.descricao() == null) {
            if (!valorMonetarioParserService.validarValorMaiorQueZero(parseValor.valor())) {
                return resultado(TipoComando.VALOR_INVALIDO, null, null);
            }
            return resultado(TipoComando.DEFINIR_CREDITO, parseValor.valor(), null);
        }

        if (!valorMonetarioParserService.validarValorMaiorQueZero(parseValor.valor())) {
            return resultado(TipoComando.VALOR_GASTO_ZERO, null, null);
        }
        return resultado(TipoComando.REGISTRAR_GASTO_CREDITO, parseValor.valor(), parseValor.descricao());
    }

    private ResultadoParseComando parsearDefinicaoSaldo(String comandoNormalizado) {
        Matcher saldoDireto = DEFINIR_SALDO_DIRETO_PATTERN.matcher(comandoNormalizado);
        if (saldoDireto.matches()) {
            return parsearDefinicaoSaldoPorValor(saldoDireto.group(1));
        }

        Matcher saldoExplicito = DEFINIR_SALDO_EXPLICITO_PATTERN.matcher(comandoNormalizado);
        if (saldoExplicito.matches()) {
            return parsearDefinicaoSaldoPorValor(saldoExplicito.group(1));
        }

        Matcher saldoTenho = DEFINIR_SALDO_TENHO_PATTERN.matcher(comandoNormalizado);
        if (saldoTenho.matches()) {
            return parsearDefinicaoSaldoPorValor(saldoTenho.group(1));
        }

        return null;
    }

    private ResultadoParseComando parsearDefinicaoSaldoPorValor(String textoValor) {
        ParseValorResultado parseValor = valorMonetarioParserService.parseValorMonetarioComDescricao(textoValor);
        if (parseValor == null || parseValor.descricao() != null) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }
        if (!valorMonetarioParserService.validarValorMaiorQueZero(parseValor.valor())) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }

        return resultado(TipoComando.DEFINIR_SALDO, parseValor.valor(), null);
    }

    private ResultadoParseComando parsearDefinicaoCredito(String textoValor) {
        ParseValorResultado parseValor = valorMonetarioParserService.parseValorMonetarioComDescricao(textoValor);
        if (parseValor == null || parseValor.descricao() != null) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }
        if (!valorMonetarioParserService.validarValorMaiorQueZero(parseValor.valor())) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }

        return resultado(TipoComando.DEFINIR_CREDITO, parseValor.valor(), null);
    }

    private ResultadoParseComando parsearGastoSaldo(String comandoNormalizado) {
        Matcher matcher = REGISTRAR_GASTO_PATTERN.matcher(comandoNormalizado);
        if (!matcher.matches()) {
            return null;
        }

        String trechoValorDescricao = matcher.group(2);
        if (trechoValorDescricao.startsWith("credito ")) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }

        ParseValorResultado parseValor = valorMonetarioParserService.parseValorMonetarioComDescricao(trechoValorDescricao);
        if (parseValor == null) {
            return resultado(TipoComando.VALOR_INVALIDO, null, null);
        }
        if (!valorMonetarioParserService.validarValorMaiorQueZero(parseValor.valor())) {
            return resultado(TipoComando.VALOR_GASTO_ZERO, null, null);
        }

        return resultado(TipoComando.REGISTRAR_GASTO, parseValor.valor(), parseValor.descricao());
    }

    private boolean ehAjuda(String mensagemNormalizada) {
        return "ajuda".equals(mensagemNormalizada)
                || "comandos".equals(mensagemNormalizada)
                || "help".equals(mensagemNormalizada)
                || "start".equals(mensagemNormalizada);
    }

    private boolean ehResumoGeral(String mensagemNormalizada) {
        return "resumo".equals(mensagemNormalizada)
                || "meu resumo".equals(mensagemNormalizada)
                || "resumo geral".equals(mensagemNormalizada)
                || "financeiro".equals(mensagemNormalizada);
    }

    private boolean ehResumoHoje(String mensagemNormalizada) {
        return "resumo hoje".equals(mensagemNormalizada)
                || "resumo do dia".equals(mensagemNormalizada)
                || "resumo diario".equals(mensagemNormalizada);
    }

    private boolean ehResumoSemana(String mensagemNormalizada) {
        return "resumo semana".equals(mensagemNormalizada)
                || "resumo da semana".equals(mensagemNormalizada)
                || "resumo semanal".equals(mensagemNormalizada);
    }

    private boolean ehResumoMes(String mensagemNormalizada) {
        return "resumo mes".equals(mensagemNormalizada)
                || "resumo mensal".equals(mensagemNormalizada);
    }

    private boolean ehDesfazerUltimo(String mensagemNormalizada) {
        return "desfazer".equals(mensagemNormalizada)
                || "apagar ultimo".equals(mensagemNormalizada)
                || "remover ultimo".equals(mensagemNormalizada)
                || "cancelar ultimo".equals(mensagemNormalizada);
    }

    private boolean ehDesfazerSaldo(String mensagemNormalizada) {
        return "desfazer saldo".equals(mensagemNormalizada)
                || "apagar ultimo saldo".equals(mensagemNormalizada)
                || "remover ultimo saldo".equals(mensagemNormalizada)
                || "cancelar ultimo saldo".equals(mensagemNormalizada);
    }

    private boolean ehDesfazerCredito(String mensagemNormalizada) {
        return "desfazer credito".equals(mensagemNormalizada)
                || "apagar ultimo credito".equals(mensagemNormalizada)
                || "remover ultimo credito".equals(mensagemNormalizada)
                || "cancelar ultimo credito".equals(mensagemNormalizada);
    }

    private boolean ehResetarSaldoTotal(String mensagemNormalizada) {
        return "resetar saldo total".equals(mensagemNormalizada)
                || "zerar saldo total".equals(mensagemNormalizada)
                || "limpar saldo total".equals(mensagemNormalizada);
    }

    private boolean ehResetarCreditoTotal(String mensagemNormalizada) {
        return "resetar credito total".equals(mensagemNormalizada)
                || "zerar credito total".equals(mensagemNormalizada)
                || "limpar credito total".equals(mensagemNormalizada);
    }

    private boolean ehResetarTotalIndefinido(String mensagemNormalizada) {
        return "resetar total".equals(mensagemNormalizada)
                || "zerar total".equals(mensagemNormalizada)
                || "limpar total".equals(mensagemNormalizada);
    }

    private boolean ehResetarSaldo(String mensagemNormalizada) {
        return "resetar saldo".equals(mensagemNormalizada)
                || "zerar saldo".equals(mensagemNormalizada)
                || "limpar saldo".equals(mensagemNormalizada);
    }

    private boolean ehResetarCredito(String mensagemNormalizada) {
        return "resetar credito".equals(mensagemNormalizada)
                || "zerar credito".equals(mensagemNormalizada);
    }

    private boolean ehResetarIndefinido(String mensagemNormalizada) {
        return "resetar".equals(mensagemNormalizada)
                || "zerar".equals(mensagemNormalizada);
    }

    private boolean ehConsultarExtrato(String mensagemNormalizada) {
        return "extrato".equals(mensagemNormalizada) || "historico".equals(mensagemNormalizada);
    }

    private boolean ehConsultarExtratoCredito(String mensagemNormalizada) {
        return "extrato credito".equals(mensagemNormalizada);
    }

    private boolean ehConsultarSaldo(String mensagemNormalizada) {
        return "saldo".equals(mensagemNormalizada)
                || "quanto tenho".equals(mensagemNormalizada)
                || "quanto resta".equals(mensagemNormalizada);
    }

    private boolean ehConsultarCredito(String mensagemNormalizada) {
        return "credito".equals(mensagemNormalizada)
                || "limite".equals(mensagemNormalizada)
                || "meu credito".equals(mensagemNormalizada);
    }

    private String normalizarTexto(String texto) {
        String textoSemAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return textoSemAcento
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizarComandoTelegram(String mensagemNormalizada) {
        if (!mensagemNormalizada.startsWith("/")) {
            return mensagemNormalizada;
        }

        String semBarra = mensagemNormalizada.substring(1).trim();
        if (semBarra.isBlank()) {
            return mensagemNormalizada;
        }

        int indiceEspaco = semBarra.indexOf(' ');
        String primeiroToken = indiceEspaco >= 0 ? semBarra.substring(0, indiceEspaco) : semBarra;
        String restante = indiceEspaco >= 0 ? semBarra.substring(indiceEspaco + 1).trim() : "";

        int indiceArroba = primeiroToken.indexOf('@');
        if (indiceArroba >= 0) {
            primeiroToken = primeiroToken.substring(0, indiceArroba);
        }

        if (primeiroToken.isBlank()) {
            return mensagemNormalizada;
        }

        return restante.isBlank() ? primeiroToken : primeiroToken + " " + restante;
    }

    private ResultadoParseComando resultado(TipoComando tipo, java.math.BigDecimal valor, String descricao) {
        return new ResultadoParseComando(tipo, valor, descricao);
    }
}
