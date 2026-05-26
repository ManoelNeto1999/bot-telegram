package br.com.tdm.whatsappsaldo.repository;

import br.com.tdm.whatsappsaldo.entity.MovimentacaoSaldo;
import br.com.tdm.whatsappsaldo.enums.TipoMovimentacao;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovimentacaoSaldoRepository extends JpaRepository<MovimentacaoSaldo, Long> {

    List<MovimentacaoSaldo> findTop10ByTelefoneOrderByCriadoEmDesc(String telefone);
    List<MovimentacaoSaldo> findTop10ByTelefoneAndTipoInOrderByCriadoEmDesc(
            String telefone,
            Collection<TipoMovimentacao> tipos
    );

    boolean existsByTelefoneAndTipoIn(String telefone, Collection<TipoMovimentacao> tipos);

    long deleteByTelefoneAndTipoIn(String telefone, Collection<TipoMovimentacao> tipos);

    @Query("select coalesce(sum(m.valor), 0) from MovimentacaoSaldo m where m.telefone = :telefone and m.tipo = :tipo")
    BigDecimal somarValorPorTelefoneETipo(
            @Param("telefone") String telefone,
            @Param("tipo") TipoMovimentacao tipo
    );

    long countByTelefoneAndTipo(String telefone, TipoMovimentacao tipo);

    @Query("""
            select count(m)
            from MovimentacaoSaldo m
            where m.telefone = :telefone
              and m.tipo = :tipo
              and m.valor > 0
              and (:marco is null or m.criadoEm > :marco)
            """)
    long contarGastosPorTelefoneETipoAposMarco(
            @Param("telefone") String telefone,
            @Param("tipo") TipoMovimentacao tipo,
            @Param("marco") LocalDateTime marco
    );

    @Query("""
            select coalesce(sum(m.valor), 0)
            from MovimentacaoSaldo m
            where m.telefone = :telefone
              and m.tipo = :tipo
              and m.valor > 0
              and m.criadoEm >= :inicio
              and m.criadoEm < :fim
            """)
    BigDecimal somarGastosPorTelefoneETipoEPeriodo(
            @Param("telefone") String telefone,
            @Param("tipo") TipoMovimentacao tipo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    @Query("""
            select count(m)
            from MovimentacaoSaldo m
            where m.telefone = :telefone
              and m.tipo = :tipo
              and m.valor > 0
              and m.criadoEm >= :inicio
              and m.criadoEm < :fim
            """)
    long contarGastosPorTelefoneETipoEPeriodo(
            @Param("telefone") String telefone,
            @Param("tipo") TipoMovimentacao tipo,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim
    );

    Optional<MovimentacaoSaldo> findTopByTelefoneAndTipoAndValorGreaterThanOrderByCriadoEmDescIdDesc(
            String telefone,
            TipoMovimentacao tipo,
            BigDecimal valor
    );

    Optional<MovimentacaoSaldo> findTopByTelefoneAndTipoAndValorGreaterThanAndCriadoEmAfterOrderByCriadoEmDescIdDesc(
            String telefone,
            TipoMovimentacao tipo,
            BigDecimal valor,
            LocalDateTime criadoEm
    );
}
