package br.com.msav02.config;

import br.com.msav02.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import java.util.List;

@Configuration
public class BootstrapConfig {
    @Bean
    CommandLineRunner bootstrapUsers(JdbcUserDetailsManager manager,
                                     PasswordEncoder encoder,
                                     UsuarioRepository repository,
                                     @Value("${app.bootstrap.admin-password:}") String adminPassword,
                                     @Value("${app.bootstrap.maintenance-password:}") String manutencaoPassword) {
        return args -> {
            if (!manager.userExists("admin")) {
                validarSenhaInicial(adminPassword, "APP_ADMIN_PASSWORD");
                manager.createUser(new User("admin", encoder.encode(adminPassword),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                repository.inicializarTentativas("admin");
            }
            if (!manager.userExists("manutencao")) {
                validarSenhaInicial(manutencaoPassword, "APP_MANUTENCAO_PASSWORD");
                manager.createUser(new User("manutencao", encoder.encode(manutencaoPassword),
                        List.of(new SimpleGrantedAuthority("ROLE_MANUTENCAO"))));
                repository.inicializarTentativas("manutencao");
            }
        };
    }
    private void validarSenhaInicial(String senha, String variavel) {
        if (senha == null || senha.length() < 8 || senha.length() > 72) {
            throw new IllegalStateException("Defina " + variavel
                    + " (de 8 a 72 caracteres) antes da primeira execucao.");
        }
    }
}
