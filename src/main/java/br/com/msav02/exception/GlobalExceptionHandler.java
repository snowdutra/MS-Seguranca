package br.com.msav02.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidLoginException.class)
    ResponseEntity<Map<String, Object>> loginInvalido(InvalidLoginException ex) {
        return resposta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> erroDeNegocio(ApiException ex) {
        return resposta(ex.getStatus(), ex.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> requisicaoInvalida(MethodArgumentNotValidException ex) {
        return resposta(HttpStatus.BAD_REQUEST, "Campos obrigatorios ou tamanhos invalidos.");
    }
    private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "mensagem", mensagem,
                "timestamp", Instant.now().toString()));
    }
}
