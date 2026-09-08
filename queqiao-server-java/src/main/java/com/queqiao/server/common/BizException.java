package com.queqiao.server.common;

/** 业务异常：由 GlobalExceptionHandler 统一转成 { error: msg } + 状态码 */
public class BizException extends RuntimeException {

    private final int status;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BizException notFound(String what) {
        return new BizException(404, what + "不存在");
    }

    /** 401：未认证 / 身份无效（前端应跳登录） */
    public static BizException unauthorized(String msg) {
        return new BizException(401, msg);
    }

    /** 403：已认证但权限不足 / 越权访问（前端仅提示，不应登出） */
    public static BizException forbidden(String msg) {
        return new BizException(403, msg);
    }
}
