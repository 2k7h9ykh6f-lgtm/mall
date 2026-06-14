package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 计算确认单金额明细请求参数
 */
@Data
public class CalcConfirmOrderAmountParam {
    @Schema(title = "被选中的购物车商品ID", required = true)
    private List<Long> cartIds;
    @Schema(title = "优惠券ID（可选，不传则不使用优惠券）")
    private Long couponId;
    @Schema(title = "使用的积分数（可选，不传或0则不使用积分）")
    private Integer useIntegration;
}
