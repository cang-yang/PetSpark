package com.petspark.common.error;

import com.petspark.common.web.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理。将异常统一映射为 {@link ErrorResponse}，HTTP 状态由
 * {@link ErrorCode#httpStatus()} 决定（架构 §7、接口设计 §2）。
 *
 * <p>关键安全约束：
 * <ul>
 *   <li>异常响应不输出堆栈、SQL、内部路径或供应商密钥；</li>
 *   <li>未识别异常一律 500 {@code INTERNAL_ERROR_001}，仅记录服务端日志；</li>
 *   <li>401/403 也走此处理器，保证前端能拿到统一信封（由 SecurityConfig 把
 *       {@link AuthenticationException}/{@link AccessDeniedException} 转交这里）；</li>
 *   <li>若请求尚无 requestId，此处补一个并写入 MDC，保证日志与响应可关联。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ex.errorCode();
        if (code.httpStatus() >= 500) {
            log.error("business error [{}] requestId={}", code.code(), RequestIdContext.current(), ex);
        } else {
            log.warn("business error [{}] requestId={}: {}", code.code(), RequestIdContext.current(), ex.getMessage());
        }
        ErrorResponse body = new ErrorResponse(code.code(), ex.getMessage(), ex.details());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        List<FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        ErrorCode code = ErrorCode.VALIDATION_FIELD_001;
        log.warn("validation error [{}] requestId={}: {} field(s)", code.code(), RequestIdContext.current(), details.size());
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage(), details);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        List<FieldViolation> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(parameterName(result), error.getDefaultMessage())))
                .toList();
        ErrorCode code = ErrorCode.VALIDATION_FIELD_001;
        log.warn("method validation error [{}] requestId={}: {} field(s)",
                code.code(), RequestIdContext.current(), details.size());
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage(), details);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.VALIDATION_FIELD_001;
        log.warn("type mismatch [{}] requestId={}: param={}", code.code(), RequestIdContext.current(), ex.getName());
        ErrorResponse body = new ErrorResponse(code.code(), "参数类型不匹配",
                List.of(new FieldViolation(ex.getName(), "类型不匹配")));
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.VALIDATION_FIELD_001;
        log.warn("missing request parameter [{}] requestId={}: param={}",
                code.code(), RequestIdContext.current(), ex.getParameterName());
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage(),
                List.of(new FieldViolation(ex.getParameterName(), "缺少必填参数")));
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.AUTH_TOKEN_001;
        log.warn("authentication failed [{}] requestId={}", code.code(), RequestIdContext.current());
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.ACCESS_DENIED_001;
        log.warn("access denied [{}] requestId={} path={}", code.code(), RequestIdContext.current(), request.getRequestURI());
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND_001;
        log.warn("not found [{}] requestId={} path={}", code.code(), RequestIdContext.current(), request.getRequestURI());
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.VALIDATION_FIELD_001;
        log.warn("illegal argument [{}] requestId={}: {}", code.code(), RequestIdContext.current(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(code.code(), ex.getMessage());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        ensureRequestId(ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR_001;
        // 仅服务端记录完整堆栈；响应只给通用消息。
        log.error("unexpected error [{}] requestId={} path={}", code.code(), RequestIdContext.current(), request.getRequestURI(), ex);
        ErrorResponse body = new ErrorResponse(code.code(), code.defaultMessage());
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    private FieldViolation toViolation(FieldError fe) {
        return new FieldViolation(fe.getField(), fe.getDefaultMessage());
    }

    private String parameterName(org.springframework.validation.method.ParameterValidationResult result) {
        String name = result.getMethodParameter().getParameterName();
        if (name == null || name.isBlank()) {
            return "arg" + result.getMethodParameter().getParameterIndex();
        }
        return name;
    }

    /**
     * 若过滤器未生成 requestId（例如静态资源/异常早于过滤器），此处补一个
     * 并写入 MDC，保证响应与日志可关联。正常路径下过滤器已设好，这里是幂等兜底。
     */
    private void ensureRequestId(Throwable ex) {
        String existing = RequestIdContext.current();
        if (existing == null || existing.isBlank()) {
            String generated = generateRequestId();
            RequestIdContext.set(generated);
        }
    }

    static String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}
