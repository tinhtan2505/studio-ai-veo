package nqt.base_java_spring_be.veo;

import org.springframework.http.HttpStatus;

public class VeoProviderException extends RuntimeException {
    private final HttpStatus status;

    public VeoProviderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
