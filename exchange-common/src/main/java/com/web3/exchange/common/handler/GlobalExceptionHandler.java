package com.web3.exchange.common.handler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.web3.exchange.common.exception.*;
import com.web3.exchange.common.exception.web3.*;
import com.web3.exchange.common.model.Result;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.View;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * // @RestControllerAdvice：它结合了 @ControllerAdvice 和 @ResponseBody 的功能：全局异常处理（最常用）
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;
    private final View error;

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage(), e);
        return buildResponse(e);
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Result<?>> handleAuthException(AuthException e) {
        log.warn("认证异常: {}", e.getMessage());
        return buildResponse(e);
    }

    /**
     * 处理权限异常
     */
    @ExceptionHandler(PermissionException.class)
    public ResponseEntity<Result<?>> handlePermissionException(PermissionException e) {
        log.warn("权限异常: {}", e.getMessage());
        return buildResponse(e);
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<?>> handleValidationException(ValidationException e) {
        log.warn("参数验证异常: {}", e.getMessage());

        HashMap<String, Object> errorData = new HashMap<>();
        errorData.put("message", e.getMessage());
        if (e.getErrors() != null) {
            errorData.put("errors", e.getErrors());
        }

        Result<Map<String, Object>> result = Result.error(422, "参数验证失败");
        result.setData(errorData);

        return ResponseEntity.status(422).body(result);
    }
    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {

        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("参数绑定异常: {}", errors);

        Map<String, Object> errorData = new HashMap<>();
        errorData.put("message", "参数验证失败");
        errorData.put("errors", errors);

        Result<Map<String, Object>> result = Result.error(422, "参数验证失败");
        result.setData(errorData);

        return ResponseEntity.status(422).body(result);
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolationException(
            ConstraintViolationException e) {

        Map<String, String> errors = new HashMap<>();
        e.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            errors.put(field, violation.getMessage());
        });

        log.warn("约束违反异常: {}", errors);

        Map<String, Object> errorData = new HashMap<>();
        errorData.put("message", "参数验证失败");
        errorData.put("errors", errors);

        Result<Map<String, Object>> result = Result.error(422, "参数验证失败");
        result.setData(errorData);

        return ResponseEntity.status(422).body(result);
    }

    /**
     * 处理HTTP消息不可读异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        log.warn("HTTP消息不可读: {}", e.getMessage());

        String message = "请求体格式错误";
        if (e.getCause() instanceof JsonParseException) {
            message = "JSON解析错误";
        } else if (e.getCause() instanceof InvalidFormatException) {
            message = "数据格式错误";
        } else if (e.getCause() instanceof MismatchedInputException) {
            message = "数据类型不匹配";
        }

        return ResponseEntity.status(400)
                .body(Result.error(400, message));
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {

        String message = String.format("参数 '%s' 类型错误，期望类型: %s",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");

        log.warn("参数类型不匹配: {}", message);

        return ResponseEntity.status(400)
                .body(Result.error(400, message));
    }

    /**
     * 处理缺失ServletRequest参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {

        String message = String.format("缺少必要参数: %s", e.getParameterName());
        log.warn(message);

        return ResponseEntity.status(400)
                .body(Result.error(400, message));
    }

    /**
     * 处理不支持的方法异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {

        String message = String.format("不支持的HTTP方法: %s，支持的方法: %s",
                e.getMethod(), Arrays.toString(e.getSupportedMethods()));

        log.warn(message);

        return ResponseEntity.status(405)
                .body(Result.error(405, message));
    }

    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<?>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {

        String message = String.format("不支持的媒体类型: %s，支持的媒体类型: %s",
                e.getContentType(), e.getSupportedMediaTypes());

        log.warn(message);

        return ResponseEntity.status(415)
                .body(Result.error(415, message));
    }

    /**
     * 处理Feign客户端异常
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Result<?>> handleFeignException(FeignException e) {
        log.error("Feign调用异常: {}", e.getMessage(), e);

        int status = e.status();
        String message = "服务调用失败";

        if (status == 400) {
            message = "请求参数错误";
        } else if (status == 401) {
            message = "认证失败";
        } else if (status == 403) {
            message = "权限不足";
        } else if (status == 404) {
            message = "服务未找到";
        } else if (status == 429) {
            message = "请求过于频繁";
        } else if (status >= 500) {
            message = "服务暂时不可用";
        }

        return ResponseEntity.status(status)
                .body(Result.error(status, message));
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<?>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);

        // 记录异常详情
        recordException(e, "RuntimeException");

        return ResponseEntity.status(500)
                .body(Result.error(500, "系统内部错误"));
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);

        // 记录异常详情
        recordException(e, "Exception");

        return ResponseEntity.status(500)
                .body(Result.error(500, "系统内部错误"));
    }



    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Result<?>> handleNotFoundException(NotFoundException e) {
        log.warn("资源未找到: {}", e.getMessage());
        return buildResponse(e);
    }
    /**
     * 处理Web3相关异常
     */
    @ExceptionHandler({
            WalletException.class,
            TransactionException.class,
            ContractException.class,
            SignatureException.class,
            GasException.class,
            TokenException.class
    })
    public ResponseEntity<Result<?>> handleWeb3Exception(BaseException e) {
        log.error("Web3异常: {}", e.getMessage(), e);
        return buildResponse(e);
    }


    /**
     * 构建响应
     *
     * @param e
     * @return
     */
    private ResponseEntity<Result<?>> buildResponse(BaseException e) {
        Result<?> result = Result.error(e);

        // 设置请求ID
        String requestId = request.getHeader("X-Requested-Id");
        if (requestId != null) {
            result.setRequestId(requestId);
        }

        // 根据异常状态码设置HTTP状态码
        int status = e.getCode() >= 400 && e.getCode() < 600 ? e.getCode() : 500;
        if (status < 500) {
            status = 400;
        } else {
            status = 500;
        }
        return ResponseEntity.status(status).body(result);
    }

    /**
     * 记录异常详情
     */
    private void recordException(Exception e, String type) {
        try{
            HashMap<Object, Object> errorInfo = new HashMap<>();
            errorInfo.put("timestamp", System.currentTimeMillis());
            errorInfo.put("type", type);
            errorInfo.put("message", e.getMessage());
            errorInfo.put("exception", e.getClass().getName());
            errorInfo.put("path", request.getRequestURI());
            errorInfo.put("method", request.getMethod());
            errorInfo.put("ip", getClientIp(request));
            errorInfo.put("userAgent", request.getHeader("User-Agent"));
            errorInfo.put("requestId", request.getHeader("X-Request-Id"));

            // 获取堆栈跟踪
            if(log.isDebugEnabled()){
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter);
                e.printStackTrace(printWriter);
                errorInfo.put("stackTrace", stringWriter.toString());
            }
            // 记录到日志
            log.error("异常详情: {}", objectMapper.writeValueAsString(errorInfo));
        } catch (Exception ex) {
            log.error("记录异常信息失败", ex);
        }
    }
    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
