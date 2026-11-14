package com.yupi.springbootinit.exception;

import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器
 *
 * <p>分三类：
 * <ol>
 *   <li>业务异常：沿用自带的 code / message；</li>
 *   <li>参数类异常（缺参、类型不匹配、JSON 解析失败、校验失败、违反数据库约束）：
 *       统一返回 40000，避免前端只看到「系统错误」；</li>
 *   <li>其余运行时异常：50000，堆栈进日志。</li>
 * </ol>
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数类异常：缺参、类型不匹配、请求体无法解析、Bean 校验失败。
     * 这些是调用方的问题，不该回 50000「系统错误」。
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            BindException.class,
            MethodArgumentNotValidException.class
    })
    public BaseResponse<?> paramExceptionHandler(Exception e) {
        log.warn("ParamException: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数错误");
    }

    /**
     * 数据库约束：非空字段没给、唯一键冲突、字段超长等。
     * 本质仍是入参不合法，按参数错误回。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> dataIntegrityHandler(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolation: {}", e.getMostSpecificCause() == null
                ? e.getMessage() : e.getMostSpecificCause().getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数不完整或违反数据约束");
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
