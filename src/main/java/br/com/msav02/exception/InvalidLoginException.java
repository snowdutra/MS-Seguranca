package br.com.msav02.exception;
public class InvalidLoginException extends RuntimeException {
    public InvalidLoginException() {
        super("Credenciais invalidas ou usuario bloqueado.");
    }
}
