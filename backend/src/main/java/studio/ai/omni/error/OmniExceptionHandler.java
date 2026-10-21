package studio.ai.omni.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class OmniExceptionHandler {
    @ExceptionHandler(OmniApiException.class)
    ResponseEntity<ProblemDetail> omni(OmniApiException exception) {
        ProblemDetail detail = problem(
                exception.getHttpStatus(), exception.getCode(), exception.getMessage(), exception.isRetryable()
        );
        detail.setProperty("providerStatus", exception.getProviderStatus());
        return ResponseEntity.status(exception.getHttpStatus()).body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, false
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), false
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> uploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "MEDIA_TOO_LARGE",
                "Tệp tải lên vượt quá giới hạn cấu hình của backend.",
                false
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Backend gặp lỗi không mong đợi khi xử lý yêu cầu Omni.",
                true
        ));
    }

    private ProblemDetail problem(HttpStatus status, String code, String message, boolean retryable) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(code);
        detail.setType(URI.create("urn:studio-ai:omni:" + code.toLowerCase().replace('_', '-')));
        detail.setProperty("code", code);
        detail.setProperty("message", message);
        detail.setProperty("providerStatus", null);
        detail.setProperty("retryable", retryable);
        return detail;
    }
}
