package com.macro.mall.portal.exception;

/**
 * 购物车校验异常
 * 用于购物车添加、修改数量、修改规格时的业务校验失败场景
 */
public class CartValidationException extends RuntimeException {
    public CartValidationException(String message) {
        super(message);
    }
}
