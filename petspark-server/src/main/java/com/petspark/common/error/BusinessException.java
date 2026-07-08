package com.petspark.common.error;

import java.util.Collections;
import java.util.List;

/**
 * 业务异常基类。业务模块抛出时携带 {@link ErrorCode}，可选自定义消息与字段明细；
 * {@link GlobalExceptionHandler} 捕获后转为 {@link ErrorResponse}，HTTP 状态取自
 * {@link ErrorCode#httpStatus()}。
 *
 * <p>异常消息不得包含敏感数据或堆栈线索；这类细节只能写入由
 * {@code requestId} 关联的服务端日志。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldViolation> details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.details = Collections.emptyList();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.emptyList();
    }

    public BusinessException(ErrorCode errorCode, String message, List<FieldViolation> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? Collections.emptyList() : details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldViolation> details() {
        return details;
    }
}
