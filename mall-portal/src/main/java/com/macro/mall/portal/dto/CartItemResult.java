package com.macro.mall.portal.dto;

import com.macro.mall.model.OmsCartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 购物车操作结果封装，包含购物车行信息和库存提示
 */
@Getter
@Setter
public class CartItemResult {
    @Schema(title = "购物车商品")
    private OmsCartItem cartItem;

    @Schema(title = "库存提示")
    private String stockHint;

    @Schema(title = "当前可用库存")
    private Integer availableStock;

    public CartItemResult(OmsCartItem cartItem, Integer availableStock) {
        this.cartItem = cartItem;
        this.availableStock = availableStock;
        if (availableStock != null && availableStock > 10) {
            this.stockHint = "库存充足";
        } else if (availableStock != null) {
            this.stockHint = "仅剩" + availableStock + "件，请尽快购买";
        } else {
            this.stockHint = "库存信息未知";
        }
    }
}
