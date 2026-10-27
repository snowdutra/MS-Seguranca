package br.com.msav02.dto;
import java.util.List;
public record UsuarioResponse(String username, List<String> roles, boolean bloqueado) { }
