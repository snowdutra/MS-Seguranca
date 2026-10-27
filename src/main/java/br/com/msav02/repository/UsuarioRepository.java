package br.com.msav02.repository;

import br.com.msav02.dto.UsuarioResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepository {
    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> senhaCriptografada(String username) {
        return jdbc.query("SELECT senha FROM usuarios WHERE username = ?",
                rs -> rs.next() ? Optional.of(rs.getString("senha")) : Optional.empty(), username);
    }

    public boolean usuarioExiste(String username) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username = ?",
                Integer.class, username);
        return count != null && count > 0;
    }

    public boolean bloqueado(String username) {
        Boolean valor = jdbc.query("SELECT locked FROM login_attempts WHERE username = ?",
                rs -> rs.next() && rs.getBoolean("locked"), username);
        return Boolean.TRUE.equals(valor);
    }

    public void inicializarTentativas(String username) {
        // Chamado uma vez para cada usuario criado com JdbcUserDetailsManager.
        jdbc.update("INSERT INTO login_attempts(username) VALUES (?)", username);
    }

    public void registrarFalha(String username) {
        // A 2a falha consecutiva bloqueia a conta: compara o valor ANTES do incremento.
        jdbc.update("""
            UPDATE login_attempts SET
                locked_at = CASE WHEN failed_attempts >= 1 AND locked_at IS NULL
                                 THEN CURRENT_TIMESTAMP ELSE locked_at END,
                locked = CASE WHEN failed_attempts >= 1 THEN TRUE ELSE locked END,
                failed_attempts = failed_attempts + 1,
                last_failed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE username = ?
            """, username);
    }

    public void limparFalhas(String username) {
        jdbc.update("""
            UPDATE login_attempts SET failed_attempts = 0, last_failed_at = NULL,
                updated_at = CURRENT_TIMESTAMP WHERE username = ?
            """, username);
    }

    public void desbloquear(String username) {
        jdbc.update("""
            UPDATE login_attempts SET failed_attempts = 0, locked = FALSE,
                locked_at = NULL, last_failed_at = NULL,
                updated_at = CURRENT_TIMESTAMP WHERE username = ?
            """, username);
    }

    public long contarBloqueados() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM login_attempts WHERE locked = TRUE", Long.class);
        return total == null ? 0L : total;
    }

    public List<UsuarioResponse> listar() {
        return jdbc.query("""
            SELECT u.username, a.authority, COALESCE(l.locked, FALSE) AS bloqueado
            FROM usuarios u
            LEFT JOIN authorities a ON a.username = u.username
            LEFT JOIN login_attempts l ON l.username = u.username
            ORDER BY u.username, a.authority
            """, rs -> {
                java.util.Map<String, java.util.List<String>> roles = new java.util.LinkedHashMap<>();
                java.util.Map<String, Boolean> bloqueados = new java.util.HashMap<>();
                while (rs.next()) {
                    String username = rs.getString("username");
                    roles.computeIfAbsent(username, k -> new java.util.ArrayList<>());
                    String role = rs.getString("authority");
                    if (role != null) {
                        roles.get(username).add(role);
                    }
                    bloqueados.put(username, rs.getBoolean("bloqueado"));
                }
                return roles.entrySet().stream()
                        .map(e -> new UsuarioResponse(e.getKey(), List.copyOf(e.getValue()),
                                bloqueados.getOrDefault(e.getKey(), false)))
                        .toList();
            });
    }
}
