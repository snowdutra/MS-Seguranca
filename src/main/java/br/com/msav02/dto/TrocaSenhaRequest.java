package br.com.msav02.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record TrocaSenhaRequest(
        @NotBlank String atual,
        @NotBlank @Size(min = 8, max = 72) String nova) { }
