package br.com.tdm.whatsappsaldo.service;

import br.com.tdm.whatsappsaldo.dto.ExtratoTelefoneResponse;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import br.com.tdm.whatsappsaldo.dto.MovimentacaoExtratoResponse;
import br.com.tdm.whatsappsaldo.dto.SaldoAtualResponse;
import br.com.tdm.whatsappsaldo.entity.MovimentacaoSaldo;
import br.com.tdm.whatsappsaldo.entity.UsuarioSaldo;
import br.com.tdm.whatsappsaldo.enums.TipoPeriodoResumo;
import br.com.tdm.whatsappsaldo.enums.TipoMovimentacao;
import br.com.tdm.whatsappsaldo.repository.MovimentacaoSaldoRepository;
import br.com.tdm.whatsappsaldo.repository.UsuarioSaldoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SaldoBotService {

    private static final Locale LOCALE_BR = new Locale("pt", "BR");
    private static final String QUEBRA_LINHA = System.lineSeparator();
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String MENSAGEM_SEM_SALDO =
            "Voc\u00EA ainda n\u00E3o tem saldo cadastrado. Envie, por exemplo: saldo 500";
    private static final String MENSAGEM_SEM_CREDITO =
            "Antes de usar o cr\u00E9dito, defina seu limite. Exemplo: credito 1000";
    private static final String MENSAGEM_VALOR_INVALIDO =
            "N\u00E3o consegui identificar um valor v\u00E1lido. Use assim: paguei 30 ou paguei 30,50.";
    private static final String MENSAGEM_GASTO_ZERO =
            "O valor do gasto precisa ser maior que zero.";
    private static final String MENSAGEM_GASTO_CREDITO_EXCEDEU =
            "\u26A0\uFE0F Esse gasto ultrapassa seu cr\u00E9dito dispon\u00EDvel.";
    private static final String MENSAGEM_SEM_GASTO_PARA_DESFAZER =
            "N\u00E3o encontrei nenhum gasto para desfazer.";
    private static final String MENSAGEM_SEM_GASTO_SALDO_PARA_DESFAZER =
            "N\u00E3o encontrei nenhum gasto de saldo para desfazer.";
    private static final String MENSAGEM_SEM_GASTO_CREDITO_PARA_DESFAZER =
            "N\u00E3o encontrei nenhum gasto de cr\u00E9dito para desfazer.";
    private static final String MENSAGEM_RESETE_INDEFINIDO =
            "Voc\u00EA quer resetar o saldo ou o cr\u00E9dito? Use: resetar saldo ou resetar credito.";
    private static final String MENSAGEM_RESETE_TOTAL_INDEFINIDO =
            "Voc\u00EA quer resetar saldo total ou cr\u00E9dito total? Use: resetar saldo total ou resetar credito total.";
    private static final Set<TipoMovimentacao> TIPOS_MARCO_SALDO = Set.of(
            TipoMovimentacao.DEFINICAO_SALDO,
            TipoMovimentacao.RESET
    );
    private static final Set<TipoMovimentacao> TIPOS_SALDO = Set.of(
            TipoMovimentacao.DEFINICAO_SALDO,
            TipoMovimentacao.GASTO,
            TipoMovimentacao.RESET
    );
    private static final Set<TipoMovimentacao> TIPOS_CREDITO = Set.of(
            TipoMovimentacao.DEFINICAO_CREDITO,
            TipoMovimentacao.GASTO_CREDITO,
            TipoMovimentacao.RESET_CREDITO
    );
    private static final Set<TipoMovimentacao> TIPOS_MARCO_CREDITO = Set.of(
            TipoMovimentacao.DEFINICAO_CREDITO,
            TipoMovimentacao.RESET_CREDITO
    );

    private final ComandoSaldoParserService comandoSaldoParserService;
    private final UsuarioSaldoRepository usuarioSaldoRepository;
    private final MovimentacaoSaldoRepository movimentacaoSaldoRepository;

    @Transactional
    public MensagemSimuladaResponse processarMensagem(MensagemSimuladaRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisicao invalida.");
        }

        String telefoneNormalizado = normalizarTelefone(request.telefone());
        String mensagemOriginal = request.mensagem() == null ? "" : request.mensagem().trim();
        if (mensagemOriginal.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensagem e obrigatoria.");
        }

        ResultadoParseComando resultado = comandoSaldoParserService.parsear(mensagemOriginal);

        return switch (resultado.tipo()) {
            case DEFINIR_SALDO -> definirSaldo(telefoneNormalizado, mensagemOriginal, resultado.valor());
            case REGISTRAR_GASTO -> registrarGasto(
                    telefoneNormalizado,
                    mensagemOriginal,
                    resultado.valor(),
                    resultado.descricao()
            );
            case CONSULTAR_SALDO -> consultarSaldo(telefoneNormalizado, mensagemOriginal);
            case DEFINIR_CREDITO -> definirCredito(telefoneNormalizado, mensagemOriginal, resultado.valor());
            case REGISTRAR_GASTO_CREDITO -> registrarGastoCredito(
                    telefoneNormalizado,
                    mensagemOriginal,
                    resultado.valor(),
                    resultado.descricao()
            );
            case CONSULTAR_CREDITO -> consultarCredito(telefoneNormalizado, mensagemOriginal);
            case CONSULTAR_EXTRATO -> consultarExtrato(telefoneNormalizado, mensagemOriginal);
            case CONSULTAR_EXTRATO_CREDITO -> consultarExtratoCredito(telefoneNormalizado, mensagemOriginal);
            case RESUMO_GERAL -> consultarResumoGeral(telefoneNormalizado, mensagemOriginal);
            case RESUMO_HOJE -> consultarResumoPorPeriodo(
                    telefoneNormalizado,
                    mensagemOriginal,
                    TipoPeriodoResumo.HOJE
            );
            case RESUMO_SEMANA -> consultarResumoPorPeriodo(
                    telefoneNormalizado,
                    mensagemOriginal,
                    TipoPeriodoResumo.SEMANA
            );
            case RESUMO_MES -> consultarResumoPorPeriodo(
                    telefoneNormalizado,
                    mensagemOriginal,
                    TipoPeriodoResumo.MES
            );
            case DESFAZER_ULTIMO -> desfazerUltimoGasto(telefoneNormalizado, mensagemOriginal);
            case DESFAZER_SALDO -> desfazerUltimoGastoSaldo(telefoneNormalizado, mensagemOriginal);
            case DESFAZER_CREDITO -> desfazerUltimoGastoCredito(telefoneNormalizado, mensagemOriginal);
            case RESETAR_SALDO_TOTAL -> resetarSaldoTotal(telefoneNormalizado, mensagemOriginal);
            case RESETAR_CREDITO_TOTAL -> resetarCreditoTotal(telefoneNormalizado, mensagemOriginal);
            case RESETAR_TOTAL_INDEFINIDO -> montarResposta(
                    telefoneNormalizado,
                    mensagemOriginal,
                    MENSAGEM_RESETE_TOTAL_INDEFINIDO,
                    saldoAtualOuNulo(telefoneNormalizado)
            );
            case RESETAR_SALDO -> resetarSaldo(telefoneNormalizado, mensagemOriginal);
            case RESETAR_CREDITO -> resetarCredito(telefoneNormalizado, mensagemOriginal);
            case RESETAR_INDEFINIDO -> montarResposta(
                    telefoneNormalizado,
                    mensagemOriginal,
                    MENSAGEM_RESETE_INDEFINIDO,
                    saldoAtualOuNulo(telefoneNormalizado)
            );
            case AJUDA -> montarResposta(
                    telefoneNormalizado,
                    mensagemOriginal,
                    montarRespostaAjuda(),
                    saldoAtualOuNulo(telefoneNormalizado)
            );
            case VALOR_INVALIDO -> montarResposta(
                    telefoneNormalizado,
                    mensagemOriginal,
                    MENSAGEM_VALOR_INVALIDO,
                    saldoAtualOuNulo(telefoneNormalizado)
            );
            case VALOR_GASTO_ZERO -> montarResposta(
                    telefoneNormalizado,
                    mensagemOriginal,
                    MENSAGEM_GASTO_ZERO,
                    saldoAtualOuNulo(telefoneNormalizado)
            );
            case DESCONHECIDO -> montarResposta(
                    telefoneNormalizado,
                    mensagemOriginal,
                    montarRespostaComandoDesconhecido(),
                    saldoAtualOuNulo(telefoneNormalizado)
            );
        };
    }

    @Transactional(readOnly = true)
    public SaldoAtualResponse buscarSaldoAtual(String telefone) {
        String telefoneNormalizado = normalizarTelefone(telefone);
        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefoneNormalizado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Telefone sem saldo cadastrado."));
        return new SaldoAtualResponse(usuario.getTelefone(), saldoAtual(usuario));
    }

    @Transactional(readOnly = true)
    public ExtratoTelefoneResponse buscarExtrato(String telefone) {
        String telefoneNormalizado = normalizarTelefone(telefone);
        List<MovimentacaoExtratoResponse> movimentacoes = movimentacaoSaldoRepository
                .findTop10ByTelefoneOrderByCriadoEmDesc(telefoneNormalizado)
                .stream()
                .map(movimentacao -> new MovimentacaoExtratoResponse(
                        movimentacao.getTipo(),
                        escalaMonetaria(movimentacao.getValor()),
                        escalaMonetaria(movimentacao.getSaldoAntes()),
                        escalaMonetaria(movimentacao.getSaldoDepois()),
                        movimentacao.getMensagemOriginal(),
                        movimentacao.getCriadoEm()))
                .toList();

        return new ExtratoTelefoneResponse(telefoneNormalizado, movimentacoes);
    }

    private MensagemSimuladaResponse definirSaldo(String telefone, String mensagemOriginal, BigDecimal valor) {
        if (!valorMaiorQueZero(valor)) {
            return respostaValorInvalido(telefone, mensagemOriginal);
        }

        BigDecimal valorDefinido = escalaMonetaria(valor);
        UsuarioSaldo usuario = obterOuCriarUsuario(telefone);
        BigDecimal saldoAntes = saldoAtual(usuario);

        usuario.setSaldoAtual(valorDefinido);
        usuario.setAtualizadoEm(LocalDateTime.now());
        UsuarioSaldo usuarioSalvo = usuarioSaldoRepository.save(usuario);

        registrarMovimentacao(
                telefone,
                TipoMovimentacao.DEFINICAO_SALDO,
                valorDefinido,
                saldoAntes,
                valorDefinido,
                mensagemOriginal,
                null
        );

        String resposta = "Saldo definido com sucesso: " + formatarMoeda(valorDefinido) + ".";
        return montarResposta(usuarioSalvo.getTelefone(), mensagemOriginal, resposta, usuarioSalvo.getSaldoAtual());
    }

    private MensagemSimuladaResponse registrarGasto(
            String telefone,
            String mensagemOriginal,
            BigDecimal valor,
            String descricao
    ) {
        if (valor == null) {
            return respostaValorInvalido(telefone, mensagemOriginal);
        }
        if (!valorMaiorQueZero(valor)) {
            return montarResposta(telefone, mensagemOriginal, MENSAGEM_GASTO_ZERO, saldoAtualOuNulo(telefone));
        }

        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElse(null);
        if (usuario == null || !temSaldoCadastrado(telefone)) {
            return montarResposta(telefone, mensagemOriginal, MENSAGEM_SEM_SALDO, null);
        }

        BigDecimal valorGasto = escalaMonetaria(valor);
        BigDecimal saldoAntes = saldoAtual(usuario);
        BigDecimal saldoDepois = escalaMonetaria(saldoAntes.subtract(valorGasto));

        usuario.setSaldoAtual(saldoDepois);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        registrarMovimentacao(
                telefone,
                TipoMovimentacao.GASTO,
                valorGasto,
                saldoAntes,
                saldoDepois,
                mensagemOriginal,
                descricao
        );

        String resposta;
        if (saldoDepois.signum() < 0) {
            resposta = "Gasto registrado: " + formatarMoeda(valorGasto)
                    + ". Seu saldo ficou negativo: " + formatarMoeda(saldoDepois) + ".";
        } else {
            resposta = "Gasto registrado: " + formatarMoeda(valorGasto)
                    + ". Saldo restante: " + formatarMoeda(saldoDepois) + ".";
        }

        return montarResposta(telefone, mensagemOriginal, resposta, saldoDepois);
    }

    private MensagemSimuladaResponse consultarSaldo(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElse(null);
        if (usuario == null || !temSaldoCadastrado(telefone)) {
            return montarResposta(telefone, mensagemOriginal, MENSAGEM_SEM_SALDO, null);
        }

        BigDecimal saldoAtual = saldoAtual(usuario);
        String resposta = "Seu saldo atual \u00E9: " + formatarMoeda(saldoAtual) + ".";
        return montarResposta(telefone, mensagemOriginal, resposta, saldoAtual);
    }

    private MensagemSimuladaResponse definirCredito(String telefone, String mensagemOriginal, BigDecimal valor) {
        if (!valorMaiorQueZero(valor)) {
            return respostaValorInvalido(telefone, mensagemOriginal);
        }

        BigDecimal limiteDefinido = escalaMonetaria(valor);
        UsuarioSaldo usuario = obterOuCriarUsuario(telefone);
        BigDecimal creditoDisponivelAntes = creditoDisponivel(usuario);

        usuario.setCreditoLimite(limiteDefinido);
        usuario.setCreditoAtual(limiteDefinido);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        registrarMovimentacao(
                telefone,
                TipoMovimentacao.DEFINICAO_CREDITO,
                limiteDefinido,
                creditoDisponivelAntes,
                limiteDefinido,
                mensagemOriginal,
                null
        );

        String resposta = "\uD83D\uDCB3 Limite de cr\u00E9dito definido: " + formatarMoeda(limiteDefinido);
        return montarResposta(telefone, mensagemOriginal, resposta, saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse registrarGastoCredito(
            String telefone,
            String mensagemOriginal,
            BigDecimal valor,
            String descricao
    ) {
        if (valor == null) {
            return respostaValorInvalido(telefone, mensagemOriginal);
        }
        if (!valorMaiorQueZero(valor)) {
            return montarResposta(telefone, mensagemOriginal, MENSAGEM_GASTO_ZERO, saldoAtualOuNulo(telefone));
        }

        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElse(null);
        if (usuario == null || !temLimiteCreditoDefinido(usuario)) {
            return montarResposta(telefone, mensagemOriginal, MENSAGEM_SEM_CREDITO, saldoAtualOuNulo(telefone));
        }

        BigDecimal valorGasto = escalaMonetaria(valor);
        BigDecimal limite = creditoLimite(usuario);
        BigDecimal creditoDisponivelAntes = creditoDisponivel(usuario);

        if (valorGasto.compareTo(creditoDisponivelAntes) > 0) {
            return montarResposta(
                    telefone,
                    mensagemOriginal,
                    MENSAGEM_GASTO_CREDITO_EXCEDEU,
                    saldoAtualOuNulo(telefone)
            );
        }

        BigDecimal creditoDisponivelDepois = escalaMonetaria(creditoDisponivelAntes.subtract(valorGasto));
        usuario.setCreditoAtual(creditoDisponivelDepois);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        registrarMovimentacao(
                telefone,
                TipoMovimentacao.GASTO_CREDITO,
                valorGasto,
                creditoDisponivelAntes,
                creditoDisponivelDepois,
                mensagemOriginal,
                descricao
        );

        BigDecimal usado = escalaMonetaria(limite.subtract(creditoDisponivelDepois));
        StringBuilder resposta = new StringBuilder();
        resposta.append("\uD83D\uDCB3 Gasto no cr\u00E9dito registrado").append(QUEBRA_LINHA)
                .append("Valor: ").append(formatarMoeda(valorGasto)).append(QUEBRA_LINHA);
        if (descricao != null && !descricao.isBlank()) {
            resposta.append("Descri\u00E7\u00E3o: ").append(descricao).append(QUEBRA_LINHA);
        }
        resposta.append("Limite: ").append(formatarMoeda(limite)).append(QUEBRA_LINHA)
                .append("Usado: ").append(formatarMoeda(usado)).append(QUEBRA_LINHA)
                .append("Dispon\u00EDvel: ").append(formatarMoeda(creditoDisponivelDepois));

        return montarResposta(telefone, mensagemOriginal, resposta.toString(), saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse consultarCredito(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElse(null);
        if (usuario == null || !temLimiteCreditoDefinido(usuario)) {
            return montarResposta(telefone, mensagemOriginal, MENSAGEM_SEM_CREDITO, saldoAtualOuNulo(telefone));
        }

        BigDecimal limite = creditoLimite(usuario);
        BigDecimal disponivel = creditoDisponivel(usuario);
        BigDecimal usado = escalaMonetaria(limite.subtract(disponivel));

        String resposta = "\uD83D\uDCB3 Seu cr\u00E9dito" + QUEBRA_LINHA
                + "Limite: " + formatarMoeda(limite) + QUEBRA_LINHA
                + "Usado: " + formatarMoeda(usado) + QUEBRA_LINHA
                + "Dispon\u00EDvel: " + formatarMoeda(disponivel);
        return montarResposta(telefone, mensagemOriginal, resposta, saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse consultarExtrato(String telefone, String mensagemOriginal) {
        List<MovimentacaoSaldo> movimentacoes =
                movimentacaoSaldoRepository.findTop10ByTelefoneOrderByCriadoEmDesc(telefone);
        if (movimentacoes.isEmpty()) {
            return montarResposta(
                    telefone,
                    mensagemOriginal,
                    "Voc\u00EA ainda n\u00E3o tem movimenta\u00E7\u00F5es registradas.",
                    saldoAtualOuNulo(telefone)
            );
        }

        String extratoFormatado = montarRespostaExtrato(movimentacoes);
        return montarResposta(telefone, mensagemOriginal, extratoFormatado, saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse consultarExtratoCredito(String telefone, String mensagemOriginal) {
        List<MovimentacaoSaldo> movimentacoesCredito = movimentacaoSaldoRepository
                .findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(telefone, TIPOS_CREDITO);
        if (movimentacoesCredito.isEmpty()) {
            return montarResposta(
                    telefone,
                    mensagemOriginal,
                    "\uD83D\uDCC4 Voc\u00EA ainda n\u00E3o tem movimenta\u00E7\u00F5es de cr\u00E9dito.",
                    saldoAtualOuNulo(telefone)
            );
        }

        String extratoCredito = montarRespostaExtratoCredito(movimentacoesCredito);
        return montarResposta(telefone, mensagemOriginal, extratoCredito, saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse consultarResumoGeral(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElse(null);
        LocalDateTime marcoCreditoAtivo = obterMarcoCreditoAtivo(telefone);

        BigDecimal saldoAtual = usuario == null ? ZERO : saldoAtual(usuario);
        BigDecimal limiteCredito = usuario == null ? ZERO : creditoLimite(usuario);
        BigDecimal creditoDisponivel = usuario == null ? ZERO : creditoDisponivel(usuario);
        BigDecimal totalGastoSaldo = somarValorMovimentacoes(telefone, TipoMovimentacao.GASTO);
        BigDecimal totalUsadoCredito = calcularCreditoUsado(limiteCredito, creditoDisponivel);
        long quantidadeGastosSaldo = movimentacaoSaldoRepository.countByTelefoneAndTipo(telefone, TipoMovimentacao.GASTO);
        long quantidadeGastosCredito = contarGastosCreditoAtivos(telefone, marcoCreditoAtivo, totalUsadoCredito);

        String resposta = montarRespostaResumoGeral(
                saldoAtual,
                totalGastoSaldo,
                quantidadeGastosSaldo,
                limiteCredito,
                totalUsadoCredito,
                creditoDisponivel,
                quantidadeGastosCredito
        );

        return montarResposta(telefone, mensagemOriginal, resposta, usuario == null ? null : saldoAtual);
    }

    private MensagemSimuladaResponse desfazerUltimoGasto(String telefone, String mensagemOriginal) {
        MovimentacaoSaldo ultimoGastoSaldo = buscarUltimoGastoSaldoAtivo(telefone).orElse(null);
        MovimentacaoSaldo ultimoGastoCredito = buscarUltimoGastoCreditoAtivo(telefone).orElse(null);
        MovimentacaoSaldo gastoMaisRecente = selecionarGastoMaisRecente(ultimoGastoSaldo, ultimoGastoCredito);

        if (gastoMaisRecente == null) {
            return montarResposta(
                    telefone,
                    mensagemOriginal,
                    MENSAGEM_SEM_GASTO_PARA_DESFAZER,
                    saldoAtualOuNulo(telefone)
            );
        }

        if (gastoMaisRecente.getTipo() == TipoMovimentacao.GASTO_CREDITO) {
            return desfazerGastoCredito(telefone, mensagemOriginal, gastoMaisRecente);
        }
        return desfazerGastoSaldo(telefone, mensagemOriginal, gastoMaisRecente);
    }

    private MensagemSimuladaResponse desfazerUltimoGastoSaldo(String telefone, String mensagemOriginal) {
        MovimentacaoSaldo ultimoGastoSaldo = buscarUltimoGastoSaldoAtivo(telefone).orElse(null);
        if (ultimoGastoSaldo == null) {
            return montarResposta(
                    telefone,
                    mensagemOriginal,
                    MENSAGEM_SEM_GASTO_SALDO_PARA_DESFAZER,
                    saldoAtualOuNulo(telefone)
            );
        }
        return desfazerGastoSaldo(telefone, mensagemOriginal, ultimoGastoSaldo);
    }

    private MensagemSimuladaResponse desfazerUltimoGastoCredito(String telefone, String mensagemOriginal) {
        MovimentacaoSaldo ultimoGastoCredito = buscarUltimoGastoCreditoAtivo(telefone).orElse(null);
        if (ultimoGastoCredito == null) {
            return montarResposta(
                    telefone,
                    mensagemOriginal,
                    MENSAGEM_SEM_GASTO_CREDITO_PARA_DESFAZER,
                    saldoAtualOuNulo(telefone)
            );
        }
        return desfazerGastoCredito(telefone, mensagemOriginal, ultimoGastoCredito);
    }

    private MensagemSimuladaResponse desfazerGastoSaldo(
            String telefone,
            String mensagemOriginal,
            MovimentacaoSaldo gastoSaldo
    ) {
        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElseGet(() -> obterOuCriarUsuario(telefone));
        BigDecimal valor = escalaMonetaria(gastoSaldo.getValor());
        BigDecimal saldoAtual = saldoAtual(usuario);
        BigDecimal novoSaldo = escalaMonetaria(saldoAtual.add(valor));

        usuario.setSaldoAtual(novoSaldo);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);
        movimentacaoSaldoRepository.delete(gastoSaldo);

        StringBuilder resposta = new StringBuilder("\u21A9\uFE0F Gasto de saldo desfeito");
        resposta.append(QUEBRA_LINHA).append(QUEBRA_LINHA)
                .append("Valor: ").append(formatarMoeda(valor)).append(QUEBRA_LINHA);
        if (gastoSaldo.getDescricao() != null && !gastoSaldo.getDescricao().isBlank()) {
            resposta.append("Descri\u00E7\u00E3o: ").append(gastoSaldo.getDescricao()).append(QUEBRA_LINHA);
        }
        resposta.append("Saldo atual: ").append(formatarMoeda(novoSaldo));

        return montarResposta(telefone, mensagemOriginal, resposta.toString(), novoSaldo);
    }

    private MensagemSimuladaResponse desfazerGastoCredito(
            String telefone,
            String mensagemOriginal,
            MovimentacaoSaldo gastoCredito
    ) {
        UsuarioSaldo usuario = usuarioSaldoRepository.findByTelefone(telefone).orElseGet(() -> obterOuCriarUsuario(telefone));
        BigDecimal valor = escalaMonetaria(gastoCredito.getValor());
        BigDecimal limite = creditoLimite(usuario);
        BigDecimal disponivelAtual = creditoDisponivel(usuario);
        BigDecimal novoDisponivel = escalaMonetaria(disponivelAtual.add(valor));

        if (limite.signum() > 0 && novoDisponivel.compareTo(limite) > 0) {
            novoDisponivel = limite;
        }
        if (novoDisponivel.signum() < 0) {
            novoDisponivel = ZERO;
        }

        usuario.setCreditoAtual(novoDisponivel);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);
        movimentacaoSaldoRepository.delete(gastoCredito);

        BigDecimal usado = calcularCreditoUsado(limite, novoDisponivel);
        StringBuilder resposta = new StringBuilder("\u21A9\uFE0F Gasto de cr\u00E9dito desfeito");
        resposta.append(QUEBRA_LINHA).append(QUEBRA_LINHA)
                .append("Valor: ").append(formatarMoeda(valor)).append(QUEBRA_LINHA);
        if (gastoCredito.getDescricao() != null && !gastoCredito.getDescricao().isBlank()) {
            resposta.append("Descri\u00E7\u00E3o: ").append(gastoCredito.getDescricao()).append(QUEBRA_LINHA);
        }
        resposta.append("Limite: ").append(formatarMoeda(limite)).append(QUEBRA_LINHA)
                .append("Usado: ").append(formatarMoeda(usado)).append(QUEBRA_LINHA)
                .append("Dispon\u00EDvel: ").append(formatarMoeda(novoDisponivel));

        return montarResposta(telefone, mensagemOriginal, resposta.toString(), saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse consultarResumoPorPeriodo(
            String telefone,
            String mensagemOriginal,
            TipoPeriodoResumo periodo
    ) {
        LocalDateTime inicio = inicioPeriodo(periodo);
        LocalDateTime fim = fimPeriodo(periodo, inicio);

        BigDecimal gastoSaldo = somarGastosPorPeriodo(telefone, TipoMovimentacao.GASTO, inicio, fim);
        long quantidadeGastosSaldo = contarGastosPorPeriodo(telefone, TipoMovimentacao.GASTO, inicio, fim);
        BigDecimal gastoCredito = somarGastosPorPeriodo(telefone, TipoMovimentacao.GASTO_CREDITO, inicio, fim);
        long quantidadeGastosCredito = contarGastosPorPeriodo(telefone, TipoMovimentacao.GASTO_CREDITO, inicio, fim);
        BigDecimal totalPeriodo = escalaMonetaria(gastoSaldo.add(gastoCredito));

        String resposta = montarRespostaResumoPeriodo(
                periodo,
                gastoSaldo,
                quantidadeGastosSaldo,
                gastoCredito,
                quantidadeGastosCredito,
                totalPeriodo
        );

        return montarResposta(telefone, mensagemOriginal, resposta, saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse resetarSaldo(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = obterOuCriarUsuario(telefone);
        BigDecimal saldoAntes = saldoAtual(usuario);

        usuario.setSaldoAtual(ZERO);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        registrarMovimentacao(
                telefone,
                TipoMovimentacao.RESET,
                ZERO,
                saldoAntes,
                ZERO,
                mensagemOriginal,
                null
        );

        String resposta = "Saldo resetado com sucesso. Saldo atual: " + formatarMoeda(ZERO) + ".";
        return montarResposta(telefone, mensagemOriginal, resposta, ZERO);
    }

    private MensagemSimuladaResponse resetarCredito(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = obterOuCriarUsuario(telefone);
        BigDecimal creditoDisponivelAntes = creditoDisponivel(usuario);

        usuario.setCreditoAtual(ZERO);
        usuario.setCreditoLimite(ZERO);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        registrarMovimentacao(
                telefone,
                TipoMovimentacao.RESET_CREDITO,
                ZERO,
                creditoDisponivelAntes,
                ZERO,
                mensagemOriginal,
                null
        );

        String resposta = "\uD83D\uDCB3 Cr\u00E9dito resetado com sucesso.";
        return montarResposta(telefone, mensagemOriginal, resposta, saldoAtualOuNulo(telefone));
    }

    private MensagemSimuladaResponse resetarSaldoTotal(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = obterOuCriarUsuario(telefone);

        movimentacaoSaldoRepository.deleteByTelefoneAndTipoIn(telefone, TIPOS_SALDO);
        usuario.setSaldoAtual(ZERO);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        String resposta = "\uD83E\uDDF9 Reset total do saldo realizado com sucesso." + QUEBRA_LINHA
                + "Saldo atual: " + formatarMoeda(ZERO) + QUEBRA_LINHA
                + "Hist\u00F3rico de saldo apagado.";
        return montarResposta(telefone, mensagemOriginal, resposta, ZERO);
    }

    private MensagemSimuladaResponse resetarCreditoTotal(String telefone, String mensagemOriginal) {
        UsuarioSaldo usuario = obterOuCriarUsuario(telefone);

        movimentacaoSaldoRepository.deleteByTelefoneAndTipoIn(telefone, TIPOS_CREDITO);
        usuario.setCreditoLimite(ZERO);
        usuario.setCreditoAtual(ZERO);
        usuario.setAtualizadoEm(LocalDateTime.now());
        usuarioSaldoRepository.save(usuario);

        String resposta = "\uD83E\uDDF9 Reset total do cr\u00E9dito realizado com sucesso." + QUEBRA_LINHA
                + "Limite: " + formatarMoeda(ZERO) + QUEBRA_LINHA
                + "Hist\u00F3rico de cr\u00E9dito apagado.";
        return montarResposta(telefone, mensagemOriginal, resposta, saldoAtualOuNulo(telefone));
    }

    private UsuarioSaldo obterOuCriarUsuario(String telefone) {
        LocalDateTime agora = LocalDateTime.now();
        return usuarioSaldoRepository.findByTelefone(telefone)
                .orElseGet(() -> UsuarioSaldo.builder()
                        .telefone(telefone)
                        .saldoAtual(ZERO)
                        .creditoAtual(ZERO)
                        .creditoLimite(ZERO)
                        .criadoEm(agora)
                        .atualizadoEm(agora)
                        .build());
    }

    private void registrarMovimentacao(
            String telefone,
            TipoMovimentacao tipo,
            BigDecimal valor,
            BigDecimal saldoAntes,
            BigDecimal saldoDepois,
            String mensagemOriginal,
            String descricao
    ) {
        MovimentacaoSaldo movimentacao = MovimentacaoSaldo.builder()
                .telefone(telefone)
                .tipo(tipo)
                .valor(escalaMonetaria(valor))
                .saldoAntes(escalaMonetaria(saldoAntes))
                .saldoDepois(escalaMonetaria(saldoDepois))
                .mensagemOriginal(mensagemOriginal)
                .descricao(descricao)
                .criadoEm(LocalDateTime.now())
                .build();
        movimentacaoSaldoRepository.save(movimentacao);
    }

    private MensagemSimuladaResponse respostaValorInvalido(String telefone, String mensagemRecebida) {
        return montarResposta(
                telefone,
                mensagemRecebida,
                MENSAGEM_VALOR_INVALIDO,
                saldoAtualOuNulo(telefone)
        );
    }

    private MensagemSimuladaResponse montarResposta(
            String telefone,
            String mensagemRecebida,
            String resposta,
            BigDecimal saldoAtual
    ) {
        return new MensagemSimuladaResponse(
                telefone,
                mensagemRecebida,
                resposta,
                saldoAtual == null ? null : escalaMonetaria(saldoAtual)
        );
    }

    private BigDecimal saldoAtualOuNulo(String telefone) {
        if (!temSaldoCadastrado(telefone)) {
            return null;
        }
        return usuarioSaldoRepository.findByTelefone(telefone)
                .map(this::saldoAtual)
                .orElse(null);
    }

    private boolean temSaldoCadastrado(String telefone) {
        return movimentacaoSaldoRepository.existsByTelefoneAndTipoIn(telefone, TIPOS_SALDO);
    }

    private boolean temLimiteCreditoDefinido(UsuarioSaldo usuario) {
        return usuario != null && creditoLimite(usuario).signum() > 0;
    }

    private BigDecimal somarValorMovimentacoes(String telefone, TipoMovimentacao tipoMovimentacao) {
        BigDecimal soma = movimentacaoSaldoRepository.somarValorPorTelefoneETipo(telefone, tipoMovimentacao);
        return escalaMonetaria(soma == null ? ZERO : soma);
    }

    private BigDecimal somarGastosPorPeriodo(
            String telefone,
            TipoMovimentacao tipoMovimentacao,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        BigDecimal soma = movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                telefone,
                tipoMovimentacao,
                inicio,
                fim
        );
        return escalaMonetaria(soma == null ? ZERO : soma);
    }

    private long contarGastosPorPeriodo(
            String telefone,
            TipoMovimentacao tipoMovimentacao,
            LocalDateTime inicio,
            LocalDateTime fim
    ) {
        return movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                telefone,
                tipoMovimentacao,
                inicio,
                fim
        );
    }

    private BigDecimal calcularCreditoUsado(BigDecimal limiteCredito, BigDecimal creditoDisponivel) {
        BigDecimal usado = escalaMonetaria(limiteCredito.subtract(creditoDisponivel));
        return usado.signum() < 0 ? ZERO : usado;
    }

    private long contarGastosCreditoAtivos(
            String telefone,
            LocalDateTime marcoCreditoAtivo,
            BigDecimal totalUsadoCredito
    ) {
        if (totalUsadoCredito.signum() <= 0) {
            return 0L;
        }
        return movimentacaoSaldoRepository.contarGastosPorTelefoneETipoAposMarco(
                telefone,
                TipoMovimentacao.GASTO_CREDITO,
                marcoCreditoAtivo
        );
    }

    private LocalDateTime obterMarcoCreditoAtivo(String telefone) {
        return obterMarcoAtivo(telefone, TIPOS_MARCO_CREDITO);
    }

    private LocalDateTime obterMarcoSaldoAtivo(String telefone) {
        return obterMarcoAtivo(telefone, TIPOS_MARCO_SALDO);
    }

    private LocalDateTime obterMarcoAtivo(String telefone, Set<TipoMovimentacao> tiposMarco) {
        List<MovimentacaoSaldo> marcosCredito = movimentacaoSaldoRepository
                .findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(telefone, tiposMarco);
        if (marcosCredito == null || marcosCredito.isEmpty()) {
            return null;
        }
        return marcosCredito.get(0).getCriadoEm();
    }

    private java.util.Optional<MovimentacaoSaldo> buscarUltimoGastoSaldoAtivo(String telefone) {
        return buscarUltimoGastoPorTipoAposMarco(
                telefone,
                TipoMovimentacao.GASTO,
                obterMarcoSaldoAtivo(telefone)
        );
    }

    private java.util.Optional<MovimentacaoSaldo> buscarUltimoGastoCreditoAtivo(String telefone) {
        return buscarUltimoGastoPorTipoAposMarco(
                telefone,
                TipoMovimentacao.GASTO_CREDITO,
                obterMarcoCreditoAtivo(telefone)
        );
    }

    private java.util.Optional<MovimentacaoSaldo> buscarUltimoGastoPorTipoAposMarco(
            String telefone,
            TipoMovimentacao tipoMovimentacao,
            LocalDateTime marco
    ) {
        if (marco == null) {
            return movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                    telefone,
                    tipoMovimentacao,
                    ZERO
            );
        }
        return movimentacaoSaldoRepository
                .findTopByTelefoneAndTipoAndValorGreaterThanAndCriadoEmAfterOrderByCriadoEmDescIdDesc(
                        telefone,
                        tipoMovimentacao,
                        ZERO,
                        marco
                );
    }

    private MovimentacaoSaldo selecionarGastoMaisRecente(
            MovimentacaoSaldo gastoSaldo,
            MovimentacaoSaldo gastoCredito
    ) {
        if (gastoSaldo == null) {
            return gastoCredito;
        }
        if (gastoCredito == null) {
            return gastoSaldo;
        }

        int comparacaoData = gastoSaldo.getCriadoEm().compareTo(gastoCredito.getCriadoEm());
        if (comparacaoData > 0) {
            return gastoSaldo;
        }
        if (comparacaoData < 0) {
            return gastoCredito;
        }

        long idSaldo = gastoSaldo.getId() == null ? Long.MIN_VALUE : gastoSaldo.getId();
        long idCredito = gastoCredito.getId() == null ? Long.MIN_VALUE : gastoCredito.getId();
        return idSaldo >= idCredito ? gastoSaldo : gastoCredito;
    }

    private BigDecimal saldoAtual(UsuarioSaldo usuario) {
        return escalaMonetaria(usuario.getSaldoAtual() == null ? ZERO : usuario.getSaldoAtual());
    }

    private BigDecimal creditoDisponivel(UsuarioSaldo usuario) {
        return escalaMonetaria(usuario.getCreditoAtual() == null ? ZERO : usuario.getCreditoAtual());
    }

    private BigDecimal creditoLimite(UsuarioSaldo usuario) {
        return escalaMonetaria(usuario.getCreditoLimite() == null ? ZERO : usuario.getCreditoLimite());
    }

    private boolean valorMaiorQueZero(BigDecimal valor) {
        return valor != null && valor.signum() > 0;
    }

    private BigDecimal escalaMonetaria(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizarTelefone(String telefoneOriginal) {
        if (telefoneOriginal == null || telefoneOriginal.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone e obrigatorio.");
        }

        String telefoneNormalizado = telefoneOriginal.replaceAll("[^0-9]", "");
        if (telefoneNormalizado.isBlank() || telefoneNormalizado.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone invalido.");
        }
        return telefoneNormalizado;
    }

    private String formatarMoeda(BigDecimal valor) {
        NumberFormat formatador = NumberFormat.getCurrencyInstance(LOCALE_BR);
        return formatador.format(valor).replace('\u00A0', ' ');
    }

    private String montarRespostaAjuda() {
        return "\uD83D\uDCB0 Comandos de saldo:" + QUEBRA_LINHA
                + "saldo 500" + QUEBRA_LINHA
                + "gastei 30 mercado" + QUEBRA_LINHA
                + "gastei 30" + QUEBRA_LINHA
                + "saldo" + QUEBRA_LINHA
                + "extrato" + QUEBRA_LINHA
                + "resetar saldo" + QUEBRA_LINHA
                + "resetar saldo total" + QUEBRA_LINHA
                + "desfazer saldo" + QUEBRA_LINHA + QUEBRA_LINHA
                + "\uD83D\uDCB3 Comandos de cr\u00E9dito:" + QUEBRA_LINHA
                + "limite 1000" + QUEBRA_LINHA
                + "usei credito 120 mercado" + QUEBRA_LINHA
                + "usei credito 120" + QUEBRA_LINHA
                + "meu credito" + QUEBRA_LINHA
                + "extrato credito" + QUEBRA_LINHA
                + "resetar credito" + QUEBRA_LINHA
                + "resetar credito total" + QUEBRA_LINHA
                + "desfazer credito" + QUEBRA_LINHA + QUEBRA_LINHA
                + "\u21A9\uFE0F Desfazer \u00FAltimo gasto:" + QUEBRA_LINHA
                + "desfazer" + QUEBRA_LINHA + QUEBRA_LINHA
                + "\uD83D\uDCCA Resumo:" + QUEBRA_LINHA
                + "resumo" + QUEBRA_LINHA
                + "resumo hoje" + QUEBRA_LINHA
                + "resumo semana" + QUEBRA_LINHA
                + "resumo mes";
    }

    private String montarRespostaComandoDesconhecido() {
        return "N\u00E3o entendi sua mensagem." + QUEBRA_LINHA + QUEBRA_LINHA
                + "Envie 'ajuda' para ver os comandos dispon\u00EDveis.";
    }

    private String montarRespostaExtrato(List<MovimentacaoSaldo> movimentacoes) {
        StringBuilder builder = new StringBuilder("\u00DAltimas movimenta\u00E7\u00F5es:");

        int ordem = 1;
        for (MovimentacaoSaldo movimentacao : movimentacoes) {
            builder.append(QUEBRA_LINHA).append(QUEBRA_LINHA)
                    .append(ordem++)
                    .append(". ")
                    .append(formatarTipoMovimentacao(movimentacao.getTipo()))
                    .append(": ")
                    .append(formatarMoeda(escalaMonetaria(movimentacao.getValor())));
            if (movimentacao.getDescricao() != null && !movimentacao.getDescricao().isBlank()) {
                builder.append(" ").append(movimentacao.getDescricao());
            }
            builder.append(QUEBRA_LINHA)
                    .append(formatarRotuloSaldoDepois(movimentacao.getTipo()))
                    .append(": ")
                    .append(formatarMoeda(escalaMonetaria(movimentacao.getSaldoDepois())));
        }

        return builder.toString();
    }

    private String montarRespostaExtratoCredito(List<MovimentacaoSaldo> movimentacoesCredito) {
        StringBuilder builder = new StringBuilder("\uD83D\uDCC4 Extrato do cr\u00E9dito");

        int ordem = 1;
        for (MovimentacaoSaldo movimentacao : movimentacoesCredito) {
            builder.append(QUEBRA_LINHA)
                    .append(ordem++)
                    .append(". ");
            if (movimentacao.getTipo() == TipoMovimentacao.GASTO_CREDITO) {
                builder.append("-").append(formatarMoeda(escalaMonetaria(movimentacao.getValor())));
                if (movimentacao.getDescricao() != null && !movimentacao.getDescricao().isBlank()) {
                    builder.append(" ").append(movimentacao.getDescricao());
                }
            } else if (movimentacao.getTipo() == TipoMovimentacao.DEFINICAO_CREDITO) {
                builder.append("Limite definido: ").append(formatarMoeda(escalaMonetaria(movimentacao.getValor())));
            } else {
                builder.append("Cr\u00E9dito resetado");
            }
        }

        return builder.toString();
    }

    private String montarRespostaResumoGeral(
            BigDecimal saldoAtual,
            BigDecimal totalGastoSaldo,
            long quantidadeGastosSaldo,
            BigDecimal limiteCredito,
            BigDecimal totalUsadoCredito,
            BigDecimal creditoDisponivel,
            long quantidadeGastosCredito
    ) {
        StringBuilder builder = new StringBuilder("\uD83D\uDCCA Resumo financeiro");
        builder.append(QUEBRA_LINHA).append(QUEBRA_LINHA)
                .append("\uD83D\uDCB0 Saldo").append(QUEBRA_LINHA)
                .append("Saldo atual: ").append(formatarMoeda(saldoAtual)).append(QUEBRA_LINHA)
                .append("Total gasto no saldo: ").append(formatarMoeda(totalGastoSaldo)).append(QUEBRA_LINHA)
                .append("Gastos no saldo: ").append(quantidadeGastosSaldo).append(QUEBRA_LINHA)
                .append(QUEBRA_LINHA)
                .append("\uD83D\uDCB3 Cr\u00E9dito").append(QUEBRA_LINHA);

        boolean semLimiteConfigurado = limiteCredito.signum() == 0 && totalUsadoCredito.signum() == 0;
        if (semLimiteConfigurado) {
            builder.append("Nenhum limite configurado ainda.");
        } else {
            builder.append("Limite: ").append(formatarMoeda(limiteCredito)).append(QUEBRA_LINHA)
                    .append("Usado: ").append(formatarMoeda(totalUsadoCredito)).append(QUEBRA_LINHA)
                    .append("Dispon\u00EDvel: ").append(formatarMoeda(creditoDisponivel)).append(QUEBRA_LINHA)
                    .append("Gastos no cr\u00E9dito: ").append(quantidadeGastosCredito);
        }

        if (semLimiteConfigurado) {
            builder.append(QUEBRA_LINHA).append(QUEBRA_LINHA)
                    .append("Para come\u00E7ar a usar cr\u00E9dito:").append(QUEBRA_LINHA)
                    .append("limite 1000");
        }

        if (saldoAtual.signum() == 0
                && totalGastoSaldo.signum() == 0
                && limiteCredito.signum() == 0
                && totalUsadoCredito.signum() == 0
                && creditoDisponivel.signum() == 0) {
            builder.append(QUEBRA_LINHA).append(QUEBRA_LINHA)
                    .append("Comandos \u00FAteis:").append(QUEBRA_LINHA)
                    .append("saldo 500").append(QUEBRA_LINHA)
                    .append("limite 1000").append(QUEBRA_LINHA)
                    .append("gastei 30 mercado").append(QUEBRA_LINHA)
                    .append("usei credito 120 mercado");
        }

        return builder.toString();
    }

    private String montarRespostaResumoPeriodo(
            TipoPeriodoResumo periodo,
            BigDecimal gastoSaldo,
            long quantidadeGastosSaldo,
            BigDecimal gastoCredito,
            long quantidadeGastosCredito,
            BigDecimal totalPeriodo
    ) {
        String titulo = switch (periodo) {
            case HOJE -> "\uD83D\uDCCA Resumo de hoje";
            case SEMANA -> "\uD83D\uDCCA Resumo da semana";
            case MES -> "\uD83D\uDCCA Resumo do m\u00EAs";
        };

        String sufixoSaldo = switch (periodo) {
            case HOJE -> "hoje";
            case SEMANA -> "na semana";
            case MES -> "no m\u00EAs";
        };

        String sufixoCredito = switch (periodo) {
            case HOJE -> "hoje";
            case SEMANA -> "na semana";
            case MES -> "no m\u00EAs";
        };

        return titulo + QUEBRA_LINHA + QUEBRA_LINHA
                + "\uD83D\uDCB0 Saldo" + QUEBRA_LINHA
                + "Gasto no saldo " + sufixoSaldo + ": " + formatarMoeda(gastoSaldo) + QUEBRA_LINHA
                + "Quantidade de gastos no saldo: " + quantidadeGastosSaldo + QUEBRA_LINHA + QUEBRA_LINHA
                + "\uD83D\uDCB3 Cr\u00E9dito" + QUEBRA_LINHA
                + "Gasto no cr\u00E9dito " + sufixoCredito + ": " + formatarMoeda(gastoCredito) + QUEBRA_LINHA
                + "Quantidade de gastos no cr\u00E9dito: " + quantidadeGastosCredito + QUEBRA_LINHA + QUEBRA_LINHA
                + "Total gasto no per\u00EDodo: " + formatarMoeda(totalPeriodo);
    }

    private LocalDateTime inicioPeriodo(TipoPeriodoResumo periodo) {
        LocalDate hoje = LocalDate.now();
        return switch (periodo) {
            case HOJE -> hoje.atStartOfDay();
            case SEMANA -> hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case MES -> hoje.withDayOfMonth(1).atStartOfDay();
        };
    }

    private LocalDateTime fimPeriodo(TipoPeriodoResumo periodo, LocalDateTime inicio) {
        return switch (periodo) {
            case HOJE -> inicio.plusDays(1);
            case SEMANA -> inicio.plusWeeks(1);
            case MES -> inicio.plusMonths(1);
        };
    }

    private String formatarTipoMovimentacao(TipoMovimentacao tipoMovimentacao) {
        return switch (tipoMovimentacao) {
            case GASTO -> "Gasto";
            case DEFINICAO_SALDO -> "Saldo definido";
            case RESET -> "Saldo resetado";
            case DEFINICAO_CREDITO -> "Limite de cr\u00E9dito definido";
            case GASTO_CREDITO -> "Gasto no cr\u00E9dito";
            case RESET_CREDITO -> "Cr\u00E9dito resetado";
        };
    }

    private String formatarRotuloSaldoDepois(TipoMovimentacao tipoMovimentacao) {
        return switch (tipoMovimentacao) {
            case DEFINICAO_CREDITO, GASTO_CREDITO, RESET_CREDITO -> "Cr\u00E9dito ficou";
            case DEFINICAO_SALDO, GASTO, RESET -> "Saldo ficou";
        };
    }
}
