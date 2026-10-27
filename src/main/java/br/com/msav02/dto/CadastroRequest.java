package br.com.msav02.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CadastroRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 72) String senha) { }
