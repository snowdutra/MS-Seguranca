package br.com.msav02.service;

import br.com.msav02.dto.LoginRequest;
import br.com.msav02.dto.LoginResponse;
import br.com.msav02.exception.InvalidLoginException;
import br.com.msav02.repository.UsuarioRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LoginService {
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final Counter validos;
    private final Counter invalidos;

    public LoginService(UsuarioRepository repository, PasswordEncoder encoder, MeterRegistry metrics) {
        this.repository = repository;
        this.encoder = encoder;
        this.validos = Counter.builder("app.logins.valid")
                .description("Quantidade de logins validos desde o inicio do processo")
                .register(metrics);
        this.invalidos = Counter.builder("app.logins.invalid")
                .description("Quantidade de logins invalidos desde o inicio do processo")
                .register(metrics);
        Gauge.builder("app.users.blocked", repository, r -> r.contarBloqueados())
                .description("Total atual de usuarios bloqueados no banco")
                .register(metrics);
    }

    // As tentativas invalidas precisam ser gravadas MESMO quando retornamos HTTP 401.
    @Transactional(noRollbackFor = InvalidLoginException.class)
    public LoginResponse login(LoginRequest req) {
        Optional<String> senhaNoBanco = repository.senhaCriptografada(req.username());
        boolean bloqueado = senhaNoBanco.isPresent() && repository.bloqueado(req.username());
        boolean confere = senhaNoBanco.isPresent() && encoder.matches(req.senha(), senhaNoBanco.get());

        if (!confere || bloqueado) {
            invalidos.increment();
            if (senhaNoBanco.isPresent() && !bloqueado) {
                repository.registrarFalha(req.username());
            }
            throw new InvalidLoginException();
        }

        repository.limparFalhas(req.username());
        validos.increment();
        return new LoginResponse("Login realizado com sucesso.", req.username());
    }
}
