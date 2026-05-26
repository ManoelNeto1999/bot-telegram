package br.com.tdm.whatsappsaldo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemSimuladaRequest(
        @NotBlank(message = "Telefone é obrigatório")
        @Size(max = 50, message = "Telefone inválido")
        String telefone,

        @NotBlank(message = "Mensagem é obrigatória")
        @Size(max = 500, message = "Mensagem deve ter no máximo 500 caracteres")
        String mensagem
) {
}
