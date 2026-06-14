package com.macro.mall.portal.dao;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.PromotionProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台购物车商品管理自定义Dao
 * Created by macro on 2018/8/2.
 */
public interface PortalProductDao {
    /**
     * 获取购物车商品信息
     */
    CartProduct getCartProduct(@Param("id") Long id);

    /**
     * 获取购物车商品的发布状态信息（仅包含上下架/删除状态，用于加购及改数量时校验商品是否可售）
     */
    PmsProduct getCartProductInfo(@Param("id") Long id);

    /**
     * 获取促销商品信息列表
     */
    List<PromotionProduct> getPromotionProductList(@Param("ids") List<Long> ids);

    /**
     * 获取可用优惠券列表
     */
    List<SmsCoupon> getAvailableCouponList(@Param("productId") Long productId, @Param("productCategoryId") Long productCategoryId);
}
