package br.com.tdm.whatsappsaldo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios_saldo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioSaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telefone", nullable = false, unique = true, length = 30)
    private String telefone;

    @Column(name = "saldo_atual", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoAtual;

    @Column(name = "credito_atual", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditoAtual;

    @Column(name = "credito_limite", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditoLimite;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
