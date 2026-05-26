package br.com.tdm.whatsappsaldo.controller;

import br.com.tdm.whatsappsaldo.dto.ExtratoTelefoneResponse;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaRequest;
import br.com.tdm.whatsappsaldo.dto.MensagemSimuladaResponse;
import br.com.tdm.whatsappsaldo.dto.SaldoAtualResponse;
import br.com.tdm.whatsappsaldo.service.SaldoBotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class SaldoBotController {

    private final SaldoBotService saldoBotService;

    @PostMapping("/simular")
    public MensagemSimuladaResponse simularMensagem(@Valid @RequestBody MensagemSimuladaRequest request) {
        return saldoBotService.processarMensagem(request);
    }

    @GetMapping("/saldo/{telefone}")
    public SaldoAtualResponse buscarSaldo(@PathVariable String telefone) {
        return saldoBotService.buscarSaldoAtual(telefone);
    }

    @GetMapping("/extrato/{telefone}")
    public ExtratoTelefoneResponse buscarExtrato(@PathVariable String telefone) {
        return saldoBotService.buscarExtrato(telefone);
    }
}
