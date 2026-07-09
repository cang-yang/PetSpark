package com.petspark.adoption;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;

/**
 * 交接失败业务异常。与普通 {@link BusinessException} 区分，用于
 * {@link AdoptionService#handover} 的 {@code @Transactional(noRollbackFor = ...)}
 * 精准排除回滚：失败路径在状态、pet 回滚、通知、审计一致落库后再抛此异常，
 * 事务正常提交，对外映射为 422 {@link ErrorCode#ADOPTION_HANDOVER_001}。
 *
 * <p>不携带额外字段：错误码与消息全部取自 {@link ErrorCode#ADOPTION_HANDOVER_001}，
 * 与全局异常处理器的 {@code BusinessException} 处理路径完全一致。
 */
public class HandoverFailedException extends BusinessException {

    public HandoverFailedException() {
        super(ErrorCode.ADOPTION_HANDOVER_001);
    }
}
