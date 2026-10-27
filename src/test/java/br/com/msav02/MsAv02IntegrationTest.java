package br.com.msav02;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:msav02test;DB_CLOSE_DELAY=-1",
        "app.bootstrap.admin-password=AdminSegura#2026",
        "app.bootstrap.maintenance-password=Manutencao#2026"
})
class MsAv02IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private String corpo(Object objeto) throws Exception {
        return json.writeValueAsString(objeto);
    }

    private RequestPostProcessor manutencao() {
        return httpBasic("manutencao", "Manutencao#2026");
    }

    private RequestPostProcessor admin() {
        return httpBasic("admin", "AdminSegura#2026");
    }

    @BeforeEach
    void limparUsuariosDeTeste() {
        jdbc.update("DELETE FROM authorities WHERE username NOT IN ('admin', 'manutencao')");
        jdbc.update("DELETE FROM login_attempts WHERE username NOT IN ('admin', 'manutencao')");
        jdbc.update("DELETE FROM usuarios WHERE username NOT IN ('admin', 'manutencao')");
        jdbc.update("UPDATE login_attempts SET failed_attempts = 0, locked = FALSE, locked_at = NULL");
    }

    private void cadastrar(String username) throws Exception {
        mvc.perform(post("/").with(manutencao()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", username, "senha", "SenhaTeste#2026"))))
                .andExpect(status().isCreated());
    }

    @Test
    void loginValidoRetorna200() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "admin", "senha", "AdminSegura#2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void loginInvalidoRetorna401() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "inexistente", "senha", "senhaErrada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bloqueiaNaSegundaFalhaEDesbloqueiaApenasComAdmin() throws Exception {
        cadastrar("teste_bloqueio");
        var incorreto = corpo(Map.of("username", "teste_bloqueio", "senha", "erro"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(incorreto))
                    .andExpect(status().isUnauthorized());
        }
        Integer falhas = jdbc.queryForObject(
                "SELECT failed_attempts FROM login_attempts WHERE username = 'teste_bloqueio'", Integer.class);
        Boolean bloqueado = jdbc.queryForObject(
                "SELECT locked FROM login_attempts WHERE username = 'teste_bloqueio'", Boolean.class);
        assertThat(falhas).isEqualTo(2);
        assertThat(bloqueado).isTrue();

        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "teste_bloqueio", "senha", "SenhaTeste#2026"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/unlock/teste_bloqueio").with(manutencao()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/unlock/teste_bloqueio").with(admin()))
                .andExpect(status().isOk());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "teste_bloqueio", "senha", "SenhaTeste#2026"))))
                .andExpect(status().isOk());
    }

    @Test
    void acessoBasicoDeBloqueadoTambemEhRejeitado() throws Exception {
        // Bloqueio do proprio administrador deve desabilitar Basic Auth no banco.
        jdbc.update("UPDATE login_attempts SET locked = TRUE WHERE username = 'admin'");
        mvc.perform(put("/unlock/admin").with(admin())).andExpect(status().isUnauthorized());
    }

    @Test
    void usuariosNaoAutenticadosNaoPodemListarOuCadastrar() throws Exception {
        mvc.perform(get("/")).andExpect(status().isUnauthorized());
        mvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "x", "senha", "SenhaTeste#2026"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioAdminNaoPodeListarMasManutencaoPode() throws Exception {
        mvc.perform(get("/").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/").with(manutencao())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").exists());
    }

    @Test
    void usuarioCriadoTemRoleUsuarioENaoExpoeSenha() throws Exception {
        cadastrar("teste_novo");
        mvc.perform(get("/").with(manutencao())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SenhaTeste#2026"))));
        String hash = jdbc.queryForObject("SELECT senha FROM usuarios WHERE username = 'teste_novo'", String.class);
        assertThat(hash).startsWith("$2").isNotEqualTo("SenhaTeste#2026");
        String role = jdbc.queryForObject("SELECT authority FROM authorities WHERE username = 'teste_novo'",
                String.class);
        assertThat(role).isEqualTo("ROLE_USUARIO");
    }

    @Test
    void cadastroDuplicadoRetorna409() throws Exception {
        cadastrar("teste_duplicado");
        mvc.perform(post("/").with(manutencao()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "teste_duplicado", "senha", "SenhaTeste#2026"))))
                .andExpect(status().isConflict());
    }

    @Test
    void trocaSenhaPublicaValidaSenhaAtual() throws Exception {
        cadastrar("teste_senha");
        mvc.perform(put("/trocarsenha/teste_senha").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("atual", "senhaErrada", "nova", "SenhaNova#2026"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/trocarsenha/teste_senha").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("atual", "SenhaTeste#2026", "nova", "SenhaNova#2026"))))
                .andExpect(status().isOk());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "teste_senha", "senha", "SenhaNova#2026"))))
                .andExpect(status().isOk());
    }

    @Test
    void sucessoZeraFalhasAnteriores() throws Exception {
        cadastrar("teste_reset");
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "teste_reset", "senha", "erro"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "teste_reset", "senha", "SenhaTeste#2026"))))
                .andExpect(status().isOk());
        Integer falhas = jdbc.queryForObject(
                "SELECT failed_attempts FROM login_attempts WHERE username = 'teste_reset'", Integer.class);
        assertThat(falhas).isZero();
    }

    @Test
    void dadosInvalidosRetornam400EUsuarioInexistente404() throws Exception {
        mvc.perform(post("/").with(manutencao()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo(Map.of("username", "", "senha", "curta"))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/unlock/inexistente").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerHealthEPrometheusPublicos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/login'].post").exists());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("app_users_blocked")));
    }
}
