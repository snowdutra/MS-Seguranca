package br.com.msav02.service;

import br.com.msav02.dto.CadastroRequest;
import br.com.msav02.dto.MensagemResponse;
import br.com.msav02.dto.TrocaSenhaRequest;
import br.com.msav02.dto.UsuarioResponse;
import br.com.msav02.exception.ApiException;
import br.com.msav02.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {
    private final JdbcUserDetailsManager manager;
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;

    public UsuarioService(JdbcUserDetailsManager manager, UsuarioRepository repository,
                          PasswordEncoder encoder) {
        this.manager = manager;
        this.repository = repository;
        this.encoder = encoder;
    }

    @Transactional
    public UsuarioResponse cadastrar(CadastroRequest request) {
        if (repository.usuarioExiste(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Usuario ja cadastrado.");
        }
        manager.createUser(new User(request.username(), encoder.encode(request.senha()),
                List.of(new SimpleGrantedAuthority("ROLE_USUARIO"))));
        repository.inicializarTentativas(request.username());
        return new UsuarioResponse(request.username(), List.of("ROLE_USUARIO"), false);
    }

    @Transactional
    public MensagemResponse trocarSenha(String username, TrocaSenhaRequest request) {
        String hashAtual = repository.senhaCriptografada(username).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Usuario nao encontrado."));
        if (!encoder.matches(request.atual(), hashAtual)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Senha atual incorreta.");
        }
        if (encoder.matches(request.nova(), hashAtual)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A nova senha deve ser diferente da atual.");
        }
        UserDetails usuarioAtual = manager.loadUserByUsername(username);
        UserDetails usuarioAtualizado = User.withUserDetails(usuarioAtual)
                .password(encoder.encode(request.nova()))
                .build();
        manager.updateUser(usuarioAtualizado);
        return new MensagemResponse("Senha alterada com sucesso.");
    }

    @Transactional
    public MensagemResponse desbloquear(String username) {
        if (!repository.usuarioExiste(username)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Usuario nao encontrado.");
        }
        repository.desbloquear(username);
        return new MensagemResponse("Usuario desbloqueado com sucesso.");
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return repository.listar();
    }
}
