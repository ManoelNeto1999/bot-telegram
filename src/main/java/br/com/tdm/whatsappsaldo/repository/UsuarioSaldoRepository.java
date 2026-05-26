package br.com.tdm.whatsappsaldo.repository;

import br.com.tdm.whatsappsaldo.entity.UsuarioSaldo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioSaldoRepository extends JpaRepository<UsuarioSaldo, Long> {

    Optional<UsuarioSaldo> findByTelefone(String telefone);
}
