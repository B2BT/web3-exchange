package com.web3.exchange.common.exception;

import lombok.Data;

import java.util.Map;

/**
 * 参数验证异常
 */
@Data
public class ValidationException extends BaseException {
    private Map<String, String> errors;

    public ValidationException(String message) {
        super(422, message);
    }

    public ValidationException(Map<String, String> errors) {
        super(422, "参数验证失败");
        this.errors = errors;
    }

    public ValidationException(String field, String error) {
        super(422, "参数验证失败");
        this.errors = Map.of(field, error);
    }
}