package app.concord.common.exception;

import app.concord.common.dto.ErrorResponse;
import app.concord.common.request.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tradutor único de exceções para o formato de erro da API.
 *
 * <p>Nenhum stack trace, nenhuma mensagem de exceção de infraestrutura e nenhum
 * nome de classe chegam ao cliente. O que o usuário recebe é um código estável e
 * um {@code requestId}; o detalhe fica no log do servidor, correlacionado pelo
 * mesmo id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        if (ex.code().status().is5xxServerError()) {
            log.error("Erro de aplicação: {}", ex.code(), ex);
        } else {
            log.debug("Erro tratado: {} - {}", ex.code(), ex.getMessage());
        }
        return ResponseEntity.status(ex.code().status())
                .body(ErrorResponse.of(ex.code().name(), ex.getMessage(),
                        RequestIdFilter.current(request), ex.fieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(),
                        RequestIdFilter.current(request), fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        ErrorCode code = ErrorCode.MALFORMED_REQUEST;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(),
                        RequestIdFilter.current(request)));
    }

    @ExceptionHandler(CsrfException.class)
    public ResponseEntity<ErrorResponse> handleCsrf(CsrfException ex, HttpServletRequest request) {
        ErrorCode code = ErrorCode.CSRF_TOKEN_INVALID;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(),
                        RequestIdFilter.current(request)));
    }

    /**
     * Acesso negado. Em {@code /admin/**} a resposta é 404: um 403 confirmaria a
     * existência do painel administrativo para quem está sondando.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleDenied(AccessDeniedException ex,
                                                      HttpServletRequest request) {
        boolean admin = request.getServletPath().startsWith("/admin");
        ErrorCode code = admin ? ErrorCode.NOT_FOUND : ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(),
                        RequestIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = RequestIdFilter.current(request);
        log.error("Erro não tratado [requestId={}]", requestId, ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code.name(), code.defaultMessage(), requestId));
    }
}
