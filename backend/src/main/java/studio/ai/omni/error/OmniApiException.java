package studio.ai.omni.error;

import org.springframework.http.HttpStatus;

public class OmniApiException extends RuntimeException {
    private final HttpStatus httpStatus;
    private final String code;
    private final String providerStatus;
    private final boolean retryable;

    public OmniApiException(HttpStatus httpStatus, String code, String message) {
        this(httpStatus, code, message, null, false);
    }

    public OmniApiException(HttpStatus httpStatus, String code, String message,
                            String providerStatus, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
        this.providerStatus = providerStatus;
        this.retryable = retryable;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public String getProviderStatus() { return providerStatus; }
    public boolean isRetryable() { return retryable; }
}
