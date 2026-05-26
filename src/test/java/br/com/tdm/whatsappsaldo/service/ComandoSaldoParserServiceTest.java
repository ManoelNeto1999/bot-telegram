package br.com.tdm.whatsappsaldo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.tdm.whatsappsaldo.enums.TipoComando;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ComandoSaldoParserServiceTest {

    private final ComandoSaldoParserService parser =
            new ComandoSaldoParserService(new ValorMonetarioParserService());

    @ParameterizedTest
    @MethodSource("comandosValidosComValor")
    void deveParsearComandosValidosComValor(
            String mensagem,
            TipoComando tipoEsperado,
            String valorEsperado,
            String descricaoEsperada
    ) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(tipoEsperado, resultado.tipo());
        assertNotNull(resultado.valor());
        assertEquals(0, new BigDecimal(valorEsperado).compareTo(resultado.valor()));
        assertEquals(descricaoEsperada, resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosInvalidosComValor")
    void deveRejeitarComandosComValorInvalido(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.VALOR_INVALIDO, resultado.tipo());
        assertNull(resultado.valor());
    }

    @Test
    void deveRejeitarGastoZeroComMensagemEspecifica() {
        ResultadoParseComando resultado = parser.parsear("paguei 0");

        assertEquals(TipoComando.VALOR_GASTO_ZERO, resultado.tipo());
        assertNull(resultado.valor());
    }

    @ParameterizedTest
    @MethodSource("comandosAjuda")
    void deveReconhecerComandosDeAjuda(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.AJUDA, resultado.tipo());
        assertNull(resultado.valor());
    }

    @ParameterizedTest
    @MethodSource("comandosConsultaCredito")
    void deveReconhecerComandosConsultaCredito(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.CONSULTAR_CREDITO, resultado.tipo());
    }

    @ParameterizedTest
    @MethodSource("comandosResumoGeral")
    void deveReconhecerComandosResumoGeral(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESUMO_GERAL, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResumoHoje")
    void deveReconhecerComandosResumoHoje(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESUMO_HOJE, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResumoSemana")
    void deveReconhecerComandosResumoSemana(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESUMO_SEMANA, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResumoMes")
    void deveReconhecerComandosResumoMes(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESUMO_MES, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosExtratoCredito")
    void deveReconhecerComandosExtratoCredito(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.CONSULTAR_EXTRATO_CREDITO, resultado.tipo());
    }

    @ParameterizedTest
    @MethodSource("comandosResetarSaldo")
    void deveReconhecerComandosResetarSaldo(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESETAR_SALDO, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResetarSaldoTotal")
    void deveReconhecerComandosResetarSaldoTotal(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESETAR_SALDO_TOTAL, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResetarCredito")
    void deveReconhecerComandosResetarCredito(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESETAR_CREDITO, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResetarCreditoTotal")
    void deveReconhecerComandosResetarCreditoTotal(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESETAR_CREDITO_TOTAL, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosResetarIndefinido")
    void deveReconhecerComandosResetarIndefinido(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.RESETAR_INDEFINIDO, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosDesfazerUltimo")
    void deveReconhecerComandosDesfazerUltimo(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.DESFAZER_ULTIMO, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosDesfazerSaldo")
    void deveReconhecerComandosDesfazerSaldo(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.DESFAZER_SALDO, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    @ParameterizedTest
    @MethodSource("comandosDesfazerCredito")
    void deveReconhecerComandosDesfazerCredito(String mensagem) {
        ResultadoParseComando resultado = parser.parsear(mensagem);

        assertEquals(TipoComando.DESFAZER_CREDITO, resultado.tipo());
        assertNull(resultado.valor());
        assertNull(resultado.descricao());
    }

    static Stream<Arguments> comandosValidosComValor() {
        return Stream.of(
                Arguments.of("paguei 10", TipoComando.REGISTRAR_GASTO, "10", null),
                Arguments.of("paguei 10,50", TipoComando.REGISTRAR_GASTO, "10.50", null),
                Arguments.of("paguei 10.50", TipoComando.REGISTRAR_GASTO, "10.50", null),
                Arguments.of("paguei 30320", TipoComando.REGISTRAR_GASTO, "30320", null),
                Arguments.of("paguei 30320,99", TipoComando.REGISTRAR_GASTO, "30320.99", null),
                Arguments.of("paguei 1.000,50", TipoComando.REGISTRAR_GASTO, "1000.50", null),
                Arguments.of("saldo 500", TipoComando.DEFINIR_SALDO, "500", null),
                Arguments.of("saldo 999999999", TipoComando.DEFINIR_SALDO, "999999999", null),
                Arguments.of("credito 1000", TipoComando.DEFINIR_CREDITO, "1000", null),
                Arguments.of("cr\u00E9dito 1000", TipoComando.DEFINIR_CREDITO, "1000", null),
                Arguments.of("limite 1000", TipoComando.DEFINIR_CREDITO, "1000", null),
                Arguments.of("definir credito 1000", TipoComando.DEFINIR_CREDITO, "1000", null),
                Arguments.of("usei credito 120 mercado", TipoComando.REGISTRAR_GASTO_CREDITO, "120", "mercado"),
                Arguments.of("usei cr\u00E9dito 120 mercado", TipoComando.REGISTRAR_GASTO_CREDITO, "120", "mercado"),
                Arguments.of("gastei credito 50 lanche", TipoComando.REGISTRAR_GASTO_CREDITO, "50", "lanche"),
                Arguments.of("gastei cr\u00E9dito 50 lanche", TipoComando.REGISTRAR_GASTO_CREDITO, "50", "lanche"),
                Arguments.of("paguei credito 30 teste", TipoComando.REGISTRAR_GASTO_CREDITO, "30", "teste"),
                Arguments.of("credito 30 uber", TipoComando.REGISTRAR_GASTO_CREDITO, "30", "uber"),
                Arguments.of("gastei 30 mercado", TipoComando.REGISTRAR_GASTO, "30", "mercado")
        );
    }

    static Stream<Arguments> comandosInvalidosComValor() {
        return Stream.of(
                Arguments.of("paguei 303anshsjskansj20"),
                Arguments.of("paguei abc20"),
                Arguments.of("paguei 20abc"),
                Arguments.of("paguei R$ 10"),
                Arguments.of("saldo abc"),
                Arguments.of("saldo 10 reais"),
                Arguments.of("credito abc"),
                Arguments.of("paguei credito abc"),
                Arguments.of("paguei credito 20abc"),
                Arguments.of("paguei credito 30teste"),
                Arguments.of("credito 99999999999999999"),
                Arguments.of("credito -10")
        );
    }

    static Stream<Arguments> comandosAjuda() {
        return Stream.of(
                Arguments.of("/start"),
                Arguments.of("/START"),
                Arguments.of("Start"),
                Arguments.of("ajuda"),
                Arguments.of("Ajuda"),
                Arguments.of("/help"),
                Arguments.of("comandos"),
                Arguments.of("/comandos")
        );
    }

    static Stream<Arguments> comandosConsultaCredito() {
        return Stream.of(
                Arguments.of("credito"),
                Arguments.of("cr\u00E9dito"),
                Arguments.of("meu credito"),
                Arguments.of("meu cr\u00E9dito"),
                Arguments.of("limite")
        );
    }

    static Stream<Arguments> comandosResumoGeral() {
        return Stream.of(
                Arguments.of("resumo"),
                Arguments.of("meu resumo"),
                Arguments.of("resumo geral"),
                Arguments.of("financeiro")
        );
    }

    static Stream<Arguments> comandosResumoHoje() {
        return Stream.of(
                Arguments.of("resumo hoje"),
                Arguments.of("resumo do dia"),
                Arguments.of("resumo diario"),
                Arguments.of("resumo diário")
        );
    }

    static Stream<Arguments> comandosResumoSemana() {
        return Stream.of(
                Arguments.of("resumo semana"),
                Arguments.of("resumo da semana"),
                Arguments.of("resumo semanal")
        );
    }

    static Stream<Arguments> comandosResumoMes() {
        return Stream.of(
                Arguments.of("resumo mes"),
                Arguments.of("resumo mês"),
                Arguments.of("resumo mensal")
        );
    }

    static Stream<Arguments> comandosExtratoCredito() {
        return Stream.of(
                Arguments.of("extrato credito"),
                Arguments.of("extrato cr\u00E9dito")
        );
    }

    static Stream<Arguments> comandosResetarSaldo() {
        return Stream.of(
                Arguments.of("resetar saldo"),
                Arguments.of("zerar saldo"),
                Arguments.of("limpar saldo"),
                Arguments.of("/resetar saldo")
        );
    }

    static Stream<Arguments> comandosResetarSaldoTotal() {
        return Stream.of(
                Arguments.of("resetar saldo total"),
                Arguments.of("zerar saldo total"),
                Arguments.of("limpar saldo total")
        );
    }

    static Stream<Arguments> comandosResetarCredito() {
        return Stream.of(
                Arguments.of("resetar credito"),
                Arguments.of("zerar credito"),
                Arguments.of("/resetar credito")
        );
    }

    static Stream<Arguments> comandosResetarCreditoTotal() {
        return Stream.of(
                Arguments.of("resetar credito total"),
                Arguments.of("resetar cr\u00E9dito total"),
                Arguments.of("zerar credito total"),
                Arguments.of("zerar cr\u00E9dito total"),
                Arguments.of("limpar credito total"),
                Arguments.of("limpar cr\u00E9dito total")
        );
    }

    static Stream<Arguments> comandosResetarIndefinido() {
        return Stream.of(
                Arguments.of("resetar"),
                Arguments.of("zerar"),
                Arguments.of("/resetar")
        );
    }

    static Stream<Arguments> comandosDesfazerUltimo() {
        return Stream.of(
                Arguments.of("desfazer"),
                Arguments.of("apagar ultimo"),
                Arguments.of("apagar último"),
                Arguments.of("remover ultimo"),
                Arguments.of("remover último"),
                Arguments.of("cancelar ultimo"),
                Arguments.of("cancelar último")
        );
    }

    static Stream<Arguments> comandosDesfazerSaldo() {
        return Stream.of(
                Arguments.of("desfazer saldo"),
                Arguments.of("apagar ultimo saldo"),
                Arguments.of("apagar último saldo"),
                Arguments.of("remover ultimo saldo"),
                Arguments.of("remover último saldo"),
                Arguments.of("cancelar ultimo saldo"),
                Arguments.of("cancelar último saldo")
        );
    }

    static Stream<Arguments> comandosDesfazerCredito() {
        return Stream.of(
                Arguments.of("desfazer credito"),
                Arguments.of("desfazer crédito"),
                Arguments.of("apagar ultimo credito"),
                Arguments.of("apagar último crédito"),
                Arguments.of("remover ultimo credito"),
                Arguments.of("remover último crédito"),
                Arguments.of("cancelar ultimo credito"),
                Arguments.of("cancelar último crédito")
        );
    }
}
