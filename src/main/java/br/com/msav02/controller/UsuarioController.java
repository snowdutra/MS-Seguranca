package br.com.msav02.controller;

import br.com.msav02.dto.*;
import br.com.msav02.service.LoginService;
import br.com.msav02.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class UsuarioController {
    private final LoginService loginService;
    private final UsuarioService usuarioService;

    public UsuarioController(LoginService loginService, UsuarioService usuarioService) {
        this.loginService = loginService;
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Efetuar login", description = "Publico. Bloqueia apos duas falhas consecutivas.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Credenciais validas"),
                   @ApiResponse(responseCode = "401", description = "Credenciais invalidas ou conta bloqueada")})
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @Operation(summary = "Trocar senha", description = "Publico, exige a senha atual correta.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Senha alterada"),
                   @ApiResponse(responseCode = "401", description = "Senha atual incorreta"),
                   @ApiResponse(responseCode = "404", description = "Usuario inexistente")})
    @PutMapping("/trocarsenha/{userid}")
    public MensagemResponse trocarSenha(@PathVariable("userid") String username,
                                        @Valid @RequestBody TrocaSenhaRequest request) {
        return usuarioService.trocarSenha(username, request);
    }

    @Operation(summary = "Cadastrar usuario", description = "Exige ROLE_MANUTENCAO; novo usuario recebe ROLE_USUARIO.")
    @SecurityRequirement(name = "basicAuth")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Usuario criado"),
                   @ApiResponse(responseCode = "403", description = "Sem ROLE_MANUTENCAO"),
                   @ApiResponse(responseCode = "409", description = "Username ja utilizado")})
    @PreAuthorize("hasAuthority('ROLE_MANUTENCAO')")
    @PostMapping("/")
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.cadastrar(request));
    }

    @Operation(summary = "Desbloquear usuario", description = "Exige ROLE_ADMIN.")
    @SecurityRequirement(name = "basicAuth")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Usuario desbloqueado"),
                   @ApiResponse(responseCode = "403", description = "Sem ROLE_ADMIN"),
                   @ApiResponse(responseCode = "404", description = "Usuario inexistente")})
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/unlock/{userid}")
    public MensagemResponse desbloquear(@PathVariable("userid") String username) {
        return usuarioService.desbloquear(username);
    }

    @Operation(summary = "Listar usuarios", description = "Exige ROLE_MANUTENCAO. Nao retorna senhas.")
    @SecurityRequirement(name = "basicAuth")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Lista de usuarios"),
                   @ApiResponse(responseCode = "403", description = "Sem ROLE_MANUTENCAO")})
    @PreAuthorize("hasAuthority('ROLE_MANUTENCAO')")
    @GetMapping("/")
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }
}
