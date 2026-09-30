package com.example.activity.service;

import com.example.activity.entity.Registration;

/**
 * 报名处理结果：created 区分首次成功（201）与重放（200）。
 */
public record RegistrationResult(Registration registration, boolean created) {

    public static RegistrationResult created(Registration registration) {
        return new RegistrationResult(registration, true);
    }

    public static RegistrationResult replay(Registration registration) {
        return new RegistrationResult(registration, false);
    }
}
