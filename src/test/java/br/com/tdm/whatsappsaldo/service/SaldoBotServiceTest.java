package br.com.tdm.whatsappsaldo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import br.com.tdm.whatsappsaldo.entity.MovimentacaoSaldo;
import br.com.tdm.whatsappsaldo.entity.UsuarioSaldo;
import br.com.tdm.whatsappsaldo.enums.TipoMovimentacao;
import br.com.tdm.whatsappsaldo.repository.MovimentacaoSaldoRepository;
import br.com.tdm.whatsappsaldo.repository.UsuarioSaldoRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SaldoBotServiceTest {

    @Mock
    private UsuarioSaldoRepository usuarioSaldoRepository;

    @Mock
    private MovimentacaoSaldoRepository movimentacaoSaldoRepository;

    private SaldoBotService saldoBotService;

    @BeforeEach
    void setUp() {
        saldoBotService = new SaldoBotService(
                new ComandoSaldoParserService(new ValorMonetarioParserService()),
                usuarioSaldoRepository,
                movimentacaoSaldoRepository
        );
    }

    @Test
    void naoDeveSalvarQuandoValorForInvalido() {
        MensagemSimuladaRequest request = new MensagemSimuladaRequest("123", "paguei 303anshsjskansj20");

        saldoBotService.processarMensagem(request);

        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void gastoNoCreditoNaoDeveAlterarSaldoNormal() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("500.00"))
                .creditoAtual(new BigDecimal("1000.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.existsByTelefoneAndTipoIn(eq("123"), anyCollection())).thenReturn(true);

        saldoBotService.processarMensagem(new MensagemSimuladaRequest("123", "paguei credito 50 lanche"));

        assertEquals(0, new BigDecimal("500.00").compareTo(usuario.getSaldoAtual()));
        assertEquals(0, new BigDecimal("950.00").compareTo(usuario.getCreditoAtual()));
    }

    @Test
    void deveBloquearGastoQuandoUltrapassarCreditoDisponivel() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("500.00"))
                .creditoAtual(new BigDecimal("20.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));

        var resposta = saldoBotService.processarMensagem(new MensagemSimuladaRequest("123", "usei credito 50 mercado"));

        assertTrue(resposta.resposta().contains("ultrapassa"));
        assertEquals(0, new BigDecimal("20.00").compareTo(usuario.getCreditoAtual()));
        verify(usuarioSaldoRepository, never()).save(any(UsuarioSaldo.class));
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void deveRetornarResumoGeralSemAlterarDados() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("850.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(movimentacaoSaldoRepository.somarValorPorTelefoneETipo("123", TipoMovimentacao.GASTO))
                .thenReturn(new BigDecimal("150.00"));
        when(movimentacaoSaldoRepository.countByTelefoneAndTipo("123", TipoMovimentacao.GASTO)).thenReturn(1L);
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoAposMarco(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class)
        )).thenReturn(1L);
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(
                eq("123"),
                anyCollection()
        )).thenReturn(List.of(MovimentacaoSaldo.builder().criadoEm(LocalDateTime.now().minusMinutes(1)).build()));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resumo")
        );

        assertTrue(resposta.resposta().contains("Resumo financeiro"));
        assertTrue(resposta.resposta().contains("Saldo atual: R$ 850,00"));
        assertTrue(resposta.resposta().contains("Total gasto no saldo: R$ 150,00"));
        assertTrue(resposta.resposta().contains("Limite: R$ 1.000,00"));
        assertTrue(resposta.resposta().contains("Usado: R$ 120,00"));
        assertTrue(resposta.resposta().contains("Dispon\u00EDvel: R$ 880,00"));
        assertTrue(resposta.resposta().contains("Gastos no cr\u00E9dito: 1"));
        verify(usuarioSaldoRepository, never()).save(any(UsuarioSaldo.class));
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void deveRetornarResumoZeradoParaUsuarioSemMovimentacoes() {
        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.empty());
        when(movimentacaoSaldoRepository.somarValorPorTelefoneETipo("123", TipoMovimentacao.GASTO))
                .thenReturn(BigDecimal.ZERO);
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(
                eq("123"),
                anyCollection()
        )).thenReturn(List.of());

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "financeiro")
        );

        assertTrue(resposta.resposta().contains("Saldo atual: R$ 0,00"));
        assertTrue(resposta.resposta().contains("Nenhum limite configurado ainda."));
        assertTrue(resposta.resposta().contains("Comandos"));
        verify(usuarioSaldoRepository, never()).save(any(UsuarioSaldo.class));
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void resumoComCreditoResetadoNaoDeveMostrarGastosAntigosComoAtivos() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("850.00"))
                .creditoAtual(BigDecimal.ZERO)
                .creditoLimite(BigDecimal.ZERO)
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(movimentacaoSaldoRepository.somarValorPorTelefoneETipo("123", TipoMovimentacao.GASTO))
                .thenReturn(BigDecimal.ZERO);
        when(movimentacaoSaldoRepository.countByTelefoneAndTipo("123", TipoMovimentacao.GASTO)).thenReturn(0L);
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(
                eq("123"),
                anyCollection()
        )).thenReturn(List.of(MovimentacaoSaldo.builder().criadoEm(LocalDateTime.now().minusMinutes(1)).build()));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resumo")
        );

        assertTrue(resposta.resposta().contains("Nenhum limite configurado ainda."));
        assertTrue(!resposta.resposta().contains("Gastos no cr\u00E9dito: 1"));
        verify(movimentacaoSaldoRepository, never()).contarGastosPorTelefoneETipoAposMarco(
                any(),
                any(),
                any()
        );
    }

    @Test
    void resetarSaldoNaoDeveAlterarCredito() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("470.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.save(any(MovimentacaoSaldo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resetar saldo")
        );

        assertTrue(resposta.resposta().contains("Saldo resetado com sucesso"));
        assertEquals(0, BigDecimal.ZERO.compareTo(usuario.getSaldoAtual()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(usuario.getCreditoLimite()));
        assertEquals(0, new BigDecimal("880.00").compareTo(usuario.getCreditoAtual()));

        ArgumentCaptor<MovimentacaoSaldo> captor = ArgumentCaptor.forClass(MovimentacaoSaldo.class);
        verify(movimentacaoSaldoRepository).save(captor.capture());
        assertEquals(TipoMovimentacao.RESET, captor.getValue().getTipo());
        verify(movimentacaoSaldoRepository, never()).deleteByTelefoneAndTipoIn(any(), anyCollection());
    }

    @Test
    void resetarCreditoNaoDeveAlterarSaldo() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("500.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.existsByTelefoneAndTipoIn(eq("123"), anyCollection())).thenReturn(true);
        when(movimentacaoSaldoRepository.save(any(MovimentacaoSaldo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resetar credito")
        );

        assertTrue(resposta.resposta().contains("Cr\u00E9dito resetado com sucesso"));
        assertEquals(0, new BigDecimal("500.00").compareTo(usuario.getSaldoAtual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(usuario.getCreditoLimite()));
        assertEquals(0, BigDecimal.ZERO.compareTo(usuario.getCreditoAtual()));

        ArgumentCaptor<MovimentacaoSaldo> captor = ArgumentCaptor.forClass(MovimentacaoSaldo.class);
        verify(movimentacaoSaldoRepository).save(captor.capture());
        assertEquals(TipoMovimentacao.RESET_CREDITO, captor.getValue().getTipo());
        verify(movimentacaoSaldoRepository, never()).deleteByTelefoneAndTipoIn(any(), anyCollection());
    }

    @Test
    void resetarIndefinidoNaoDeveApagarNada() {
        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resetar")
        );

        assertTrue(resposta.resposta().contains("resetar saldo ou resetar credito"));
        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void ajudaDeveMostrarComandoResetarSaldo() {
        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "ajuda")
        );

        assertTrue(resposta.resposta().contains("resetar saldo"));
        assertTrue(resposta.resposta().contains("resetar credito"));
        assertTrue(resposta.resposta().contains("resetar saldo total"));
        assertTrue(resposta.resposta().contains("resetar credito total"));
        assertTrue(resposta.resposta().contains("desfazer"));
        assertTrue(resposta.resposta().contains("desfazer saldo"));
        assertTrue(resposta.resposta().contains("desfazer credito"));
        assertTrue(resposta.resposta().contains("resumo hoje"));
        assertTrue(resposta.resposta().contains("resumo semana"));
        assertTrue(resposta.resposta().contains("resumo mes"));
    }

    @Test
    void resumoAposResetarSaldoDevePreservarCredito() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("500.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.save(any(MovimentacaoSaldo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.somarValorPorTelefoneETipo("123", TipoMovimentacao.GASTO))
                .thenReturn(BigDecimal.ZERO);
        when(movimentacaoSaldoRepository.countByTelefoneAndTipo("123", TipoMovimentacao.GASTO)).thenReturn(0L);
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(
                eq("123"),
                anyCollection()
        )).thenReturn(List.of(MovimentacaoSaldo.builder().criadoEm(LocalDateTime.now().minusMinutes(1)).build()));
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoAposMarco(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class)
        )).thenReturn(1L);

        saldoBotService.processarMensagem(new MensagemSimuladaRequest("123", "resetar saldo"));
        MensagemSimuladaResponse resumo = saldoBotService.processarMensagem(new MensagemSimuladaRequest("123", "resumo"));

        assertTrue(resumo.resposta().contains("Saldo atual: R$ 0,00"));
        assertTrue(resumo.resposta().contains("Limite: R$ 1.000,00"));
        assertTrue(resumo.resposta().contains("Usado: R$ 120,00"));
        assertTrue(resumo.resposta().contains("Dispon\u00EDvel: R$ 880,00"));
        assertFalse(resumo.resposta().contains("Nenhum limite configurado ainda."));
        verify(movimentacaoSaldoRepository, times(1)).save(any(MovimentacaoSaldo.class));
    }

    @Test
    void resumoHojeComGastosDeveSepararSaldoECredito() {
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("30.00"));
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("120.00"));
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1L);
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1L);

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resumo hoje")
        );

        assertTrue(resposta.resposta().contains("Resumo de hoje"));
        assertTrue(resposta.resposta().contains("Gasto no saldo hoje: R$ 30,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no saldo: 1"));
        assertTrue(resposta.resposta().contains("Gasto no cr\u00E9dito hoje: R$ 120,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no cr\u00E9dito: 1"));
        assertTrue(resposta.resposta().contains("Total gasto no per\u00EDodo: R$ 150,00"));

        verify(movimentacaoSaldoRepository, never()).somarGastosPorTelefoneETipoEPeriodo(
                any(),
                eq(TipoMovimentacao.DEFINICAO_SALDO),
                any(),
                any()
        );
        verify(movimentacaoSaldoRepository, never()).somarGastosPorTelefoneETipoEPeriodo(
                any(),
                eq(TipoMovimentacao.DEFINICAO_CREDITO),
                any(),
                any()
        );
        verify(movimentacaoSaldoRepository, never()).somarGastosPorTelefoneETipoEPeriodo(
                any(),
                eq(TipoMovimentacao.RESET),
                any(),
                any()
        );
        verify(movimentacaoSaldoRepository, never()).somarGastosPorTelefoneETipoEPeriodo(
                any(),
                eq(TipoMovimentacao.RESET_CREDITO),
                any(),
                any()
        );
        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void resumoHojeSemGastosDeveMostrarZero() {
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                any(),
                any(),
                any(),
                any()
        )).thenReturn(BigDecimal.ZERO);

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resumo do dia")
        );

        assertTrue(resposta.resposta().contains("Resumo de hoje"));
        assertTrue(resposta.resposta().contains("Gasto no saldo hoje: R$ 0,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no saldo: 0"));
        assertTrue(resposta.resposta().contains("Gasto no cr\u00E9dito hoje: R$ 0,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no cr\u00E9dito: 0"));
        assertTrue(resposta.resposta().contains("Total gasto no per\u00EDodo: R$ 0,00"));
    }

    @Test
    void resumoSemanaComGastosDeveCalcularTotal() {
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("80.00"));
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("220.00"));
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(3L);
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(2L);

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resumo semanal")
        );

        assertTrue(resposta.resposta().contains("Resumo da semana"));
        assertTrue(resposta.resposta().contains("Gasto no saldo na semana: R$ 80,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no saldo: 3"));
        assertTrue(resposta.resposta().contains("Gasto no cr\u00E9dito na semana: R$ 220,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no cr\u00E9dito: 2"));
        assertTrue(resposta.resposta().contains("Total gasto no per\u00EDodo: R$ 300,00"));
    }

    @Test
    void resumoMesComGastosDeveCalcularTotal() {
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("338.29"));
        when(movimentacaoSaldoRepository.somarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(new BigDecimal("120.00"));
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(14L);
        when(movimentacaoSaldoRepository.contarGastosPorTelefoneETipoEPeriodo(
                eq("123"),
                eq(TipoMovimentacao.GASTO_CREDITO),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(1L);

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resumo mês")
        );

        assertTrue(resposta.resposta().contains("Resumo do m\u00EAs"));
        assertTrue(resposta.resposta().contains("Gasto no saldo no m\u00EAs: R$ 338,29"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no saldo: 14"));
        assertTrue(resposta.resposta().contains("Gasto no cr\u00E9dito no m\u00EAs: R$ 120,00"));
        assertTrue(resposta.resposta().contains("Quantidade de gastos no cr\u00E9dito: 1"));
        assertTrue(resposta.resposta().contains("Total gasto no per\u00EDodo: R$ 458,29"));
    }

    @Test
    void desfazerSaldoDeveRemoverUltimoGastoSaldo() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("470.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();
        MovimentacaoSaldo gastoSaldo = MovimentacaoSaldo.builder()
                .id(10L)
                .telefone("123")
                .tipo(TipoMovimentacao.GASTO)
                .valor(new BigDecimal("30.00"))
                .descricao("mercado")
                .criadoEm(LocalDateTime.now().minusMinutes(2))
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(eq("123"), anyCollection()))
                .thenReturn(List.of());
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.of(gastoSaldo));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "desfazer saldo")
        );

        assertTrue(resposta.resposta().contains("Gasto de saldo desfeito"));
        assertTrue(resposta.resposta().contains("Valor: R$ 30,00"));
        assertTrue(resposta.resposta().contains("Descri\u00E7\u00E3o: mercado"));
        assertTrue(resposta.resposta().contains("Saldo atual: R$ 500,00"));
        assertEquals(0, new BigDecimal("500.00").compareTo(usuario.getSaldoAtual()));
        assertEquals(0, new BigDecimal("880.00").compareTo(usuario.getCreditoAtual()));
        verify(movimentacaoSaldoRepository).delete(gastoSaldo);
    }

    @Test
    void desfazerCreditoDeveRemoverUltimoGastoCredito() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("500.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();
        LocalDateTime marcoCredito = LocalDateTime.now().minusHours(2);
        MovimentacaoSaldo definicaoCredito = MovimentacaoSaldo.builder()
                .id(1L)
                .telefone("123")
                .tipo(TipoMovimentacao.DEFINICAO_CREDITO)
                .valor(new BigDecimal("1000.00"))
                .criadoEm(marcoCredito)
                .build();
        MovimentacaoSaldo gastoCredito = MovimentacaoSaldo.builder()
                .id(2L)
                .telefone("123")
                .tipo(TipoMovimentacao.GASTO_CREDITO)
                .valor(new BigDecimal("120.00"))
                .descricao("farmacia")
                .criadoEm(LocalDateTime.now().minusMinutes(3))
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(eq("123"), anyCollection()))
                .thenReturn(List.of(definicaoCredito));
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanAndCriadoEmAfterOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO_CREDITO,
                BigDecimal.ZERO.setScale(2),
                marcoCredito
        )).thenReturn(Optional.of(gastoCredito));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "desfazer credito")
        );

        assertTrue(resposta.resposta().contains("Gasto de cr\u00E9dito desfeito"));
        assertTrue(resposta.resposta().contains("Valor: R$ 120,00"));
        assertTrue(resposta.resposta().contains("Descri\u00E7\u00E3o: farmacia"));
        assertTrue(resposta.resposta().contains("Limite: R$ 1.000,00"));
        assertTrue(resposta.resposta().contains("Usado: R$ 0,00"));
        assertTrue(resposta.resposta().contains("Dispon\u00EDvel: R$ 1.000,00"));
        assertEquals(0, new BigDecimal("500.00").compareTo(usuario.getSaldoAtual()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(usuario.getCreditoAtual()));
        verify(movimentacaoSaldoRepository).delete(gastoCredito);
    }

    @Test
    void desfazerGeralDeveRemoverGastoMaisRecenteEntreSaldoECredito() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("470.00"))
                .creditoAtual(new BigDecimal("880.00"))
                .creditoLimite(new BigDecimal("1000.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();
        MovimentacaoSaldo gastoSaldo = MovimentacaoSaldo.builder()
                .id(10L)
                .telefone("123")
                .tipo(TipoMovimentacao.GASTO)
                .valor(new BigDecimal("30.00"))
                .descricao("mercado")
                .criadoEm(LocalDateTime.now().minusMinutes(10))
                .build();
        MovimentacaoSaldo gastoCredito = MovimentacaoSaldo.builder()
                .id(11L)
                .telefone("123")
                .tipo(TipoMovimentacao.GASTO_CREDITO)
                .valor(new BigDecimal("120.00"))
                .descricao("farmacia")
                .criadoEm(LocalDateTime.now().minusMinutes(2))
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(eq("123"), anyCollection()))
                .thenReturn(List.of(), List.of());
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.of(gastoSaldo));
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO_CREDITO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.of(gastoCredito));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "desfazer")
        );

        assertTrue(resposta.resposta().contains("Gasto de cr\u00E9dito desfeito"));
        assertEquals(0, new BigDecimal("470.00").compareTo(usuario.getSaldoAtual()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(usuario.getCreditoAtual()));
        verify(movimentacaoSaldoRepository).delete(gastoCredito);
        verify(movimentacaoSaldoRepository, never()).delete(gastoSaldo);
    }

    @Test
    void desfazerSaldoSemGastoDeveRetornarMensagemAmigavel() {
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(eq("123"), anyCollection()))
                .thenReturn(List.of());
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.empty());

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "desfazer saldo")
        );

        assertTrue(resposta.resposta().contains("N\u00E3o encontrei nenhum gasto de saldo para desfazer."));
        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).delete(any());
    }

    @Test
    void desfazerCreditoSemGastoDeveRetornarMensagemAmigavel() {
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(eq("123"), anyCollection()))
                .thenReturn(List.of());
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO_CREDITO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.empty());

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "desfazer credito")
        );

        assertTrue(resposta.resposta().contains("N\u00E3o encontrei nenhum gasto de cr\u00E9dito para desfazer."));
        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).delete(any());
    }

    @Test
    void desfazerSemGastoDeveRetornarMensagemAmigavel() {
        when(movimentacaoSaldoRepository.findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(eq("123"), anyCollection()))
                .thenReturn(List.of(), List.of());
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.empty());
        when(movimentacaoSaldoRepository.findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
                "123",
                TipoMovimentacao.GASTO_CREDITO,
                BigDecimal.ZERO.setScale(2)
        )).thenReturn(Optional.empty());

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "desfazer")
        );

        assertTrue(resposta.resposta().contains("N\u00E3o encontrei nenhum gasto para desfazer."));
        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).delete(any());
    }

    @Test
    void resetarSaldoTotalDeveApagarHistoricoDeSaldoEPreservarCredito() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("376.28"))
                .creditoAtual(new BigDecimal("610.91"))
                .creditoLimite(new BigDecimal("2500.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resetar saldo total")
        );

        assertTrue(resposta.resposta().contains("Reset total do saldo realizado com sucesso."));
        assertTrue(resposta.resposta().contains("Saldo atual: R$ 0,00"));
        assertTrue(resposta.resposta().contains("Hist\u00F3rico de saldo apagado."));
        assertEquals(0, BigDecimal.ZERO.compareTo(usuario.getSaldoAtual()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(usuario.getCreditoLimite()));
        assertEquals(0, new BigDecimal("610.91").compareTo(usuario.getCreditoAtual()));

        ArgumentCaptor<Collection<TipoMovimentacao>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(movimentacaoSaldoRepository).deleteByTelefoneAndTipoIn(eq("123"), captor.capture());
        Collection<TipoMovimentacao> tiposDeletados = captor.getValue();
        assertTrue(tiposDeletados.contains(TipoMovimentacao.DEFINICAO_SALDO));
        assertTrue(tiposDeletados.contains(TipoMovimentacao.GASTO));
        assertTrue(tiposDeletados.contains(TipoMovimentacao.RESET));
        assertFalse(tiposDeletados.contains(TipoMovimentacao.DEFINICAO_CREDITO));
        assertFalse(tiposDeletados.contains(TipoMovimentacao.GASTO_CREDITO));
        assertFalse(tiposDeletados.contains(TipoMovimentacao.RESET_CREDITO));
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void resetarCreditoTotalDeveApagarHistoricoDeCreditoEPreservarSaldo() {
        UsuarioSaldo usuario = UsuarioSaldo.builder()
                .telefone("123")
                .saldoAtual(new BigDecimal("500.00"))
                .creditoAtual(new BigDecimal("610.91"))
                .creditoLimite(new BigDecimal("2500.00"))
                .criadoEm(LocalDateTime.now())
                .atualizadoEm(LocalDateTime.now())
                .build();

        when(usuarioSaldoRepository.findByTelefone("123")).thenReturn(Optional.of(usuario));
        when(usuarioSaldoRepository.save(any(UsuarioSaldo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resetar credito total")
        );

        assertTrue(resposta.resposta().contains("Reset total do cr\u00E9dito realizado com sucesso."));
        assertTrue(resposta.resposta().contains("Limite: R$ 0,00"));
        assertTrue(resposta.resposta().contains("Hist\u00F3rico de cr\u00E9dito apagado."));
        assertEquals(0, new BigDecimal("500.00").compareTo(usuario.getSaldoAtual()));
        assertEquals(0, BigDecimal.ZERO.compareTo(usuario.getCreditoLimite()));
        assertEquals(0, BigDecimal.ZERO.compareTo(usuario.getCreditoAtual()));

        ArgumentCaptor<Collection<TipoMovimentacao>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(movimentacaoSaldoRepository).deleteByTelefoneAndTipoIn(eq("123"), captor.capture());
        Collection<TipoMovimentacao> tiposDeletados = captor.getValue();
        assertTrue(tiposDeletados.contains(TipoMovimentacao.DEFINICAO_CREDITO));
        assertTrue(tiposDeletados.contains(TipoMovimentacao.GASTO_CREDITO));
        assertTrue(tiposDeletados.contains(TipoMovimentacao.RESET_CREDITO));
        assertFalse(tiposDeletados.contains(TipoMovimentacao.DEFINICAO_SALDO));
        assertFalse(tiposDeletados.contains(TipoMovimentacao.GASTO));
        assertFalse(tiposDeletados.contains(TipoMovimentacao.RESET));
        verify(movimentacaoSaldoRepository, never()).save(any());
    }

    @Test
    void resetarTotalIndefinidoNaoDeveApagarHistorico() {
        MensagemSimuladaResponse resposta = saldoBotService.processarMensagem(
                new MensagemSimuladaRequest("123", "resetar total")
        );

        assertTrue(resposta.resposta().contains("resetar saldo total ou resetar credito total"));
        verify(usuarioSaldoRepository, never()).save(any());
        verify(movimentacaoSaldoRepository, never()).deleteByTelefoneAndTipoIn(any(), anyCollection());
        verify(movimentacaoSaldoRepository, never()).save(any());
    }
}
