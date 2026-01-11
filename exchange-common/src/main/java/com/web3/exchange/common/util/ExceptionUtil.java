package com.web3.exchange.common.util;

import com.web3.exchange.common.exception.*;
import com.web3.exchange.common.exception.web3.TransactionException;
import com.web3.exchange.common.exception.web3.WalletException;
import com.web3.exchange.common.model.Result;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 异常处理工具类
 */
@Component
@Slf4j
public class ExceptionUtil {
    /**
     * 将异常转换为Result
     */
    public static <T> Result<T> toResult(Exception e) {
        if ( e instanceof BaseException){
            return Result.error((BaseException) e);
        } else if( e instanceof ValidationException){
            ValidationException ve = (ValidationException) e;
            Result<Map<String, Object>> result = Result.error(422, "参数验证失败");
            result.setData(Map.of("errors", ve.getErrors()));
            return (Result<T>) result;
        } else {
            log.error("系统异常", e);
            return Result.error(500, "系统内部错误");
        }
    }

    /**
     * 包装Feign异常
     */
    public static Result<?> wrapFeignException(FeignException e){
        int status = e.status();
        String message = "服务调用失败";

        switch (status) {
            case 400: message = "请求参数错误"; break;
            case 401: message = "认证失败"; break;
            case 403: message = "权限不足"; break;
            case 404: message = "资源未找到"; break;
            case 429: message = "请求过于频繁"; break;
            case 500:
            case 502:
            case 503:
            case 504: message = "服务暂时不可用"; break;
        }
        return Result.error(status, message);
    }

    /**
     * 获取异常根原因
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable rootCause = throwable;

        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        return rootCause;
    }

    /**
     * 判断是否为业务异常
     */
    public static boolean isBusinessException(Exception e) {
        return e instanceof BusinessException ||
                e instanceof AuthException ||
                e instanceof PermissionException ||
                e instanceof NotFoundException ||
                e instanceof ValidationException ||
                e instanceof WalletException ||
                e instanceof TransactionException;
    }
}
