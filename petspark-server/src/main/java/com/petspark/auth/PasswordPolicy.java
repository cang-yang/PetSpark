package com.petspark.auth;

import com.petspark.common.error.BusinessException;
import com.petspark.common.error.ErrorCode;
import com.petspark.common.error.FieldViolation;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public void validate(String password) {
        boolean strong = password != null
                && password.length() >= 8
                && password.chars().anyMatch(Character::isUpperCase)
                && password.chars().anyMatch(Character::isLowerCase)
                && password.chars().anyMatch(Character::isDigit);
        if (!strong) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FIELD_001,
                    ErrorCode.VALIDATION_FIELD_001.defaultMessage(),
                    List.of(new FieldViolation("password", "密码至少 8 位，并包含大小写字母和数字")));
        }
    }
}
