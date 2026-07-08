package com.petspark.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(min = 1, max = 64, message = "昵称长度必须在 1 到 64 个字符之间")
        String nickname,

        @Pattern(regexp = "^$|^\\+?[0-9]{7,15}$", message = "手机号格式不合法")
        String phone,

        @Size(max = 64, message = "头像文件标识过长")
        String avatarFileId,

        @Size(max = 255, message = "个人简介不能超过 255 个字符")
        String bio,

        @NotNull(message = "版本号不能为空")
        Integer version
) {}
