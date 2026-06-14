package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车数量更新校验逻辑的Service层单元测试（使用Mockito，无需数据库）。
 * 覆盖add/updateQuantity/updateAttr在商品下架、SKU不存在、库存不足等场景下的失败处理，
 * 以及成功时写入库存提示字段的行为。
 */
@ExtendWith(MockitoExtension.class)
class OmsCartItemServiceImplTest {

    @Mock
    private OmsCartItemMapper cartItemMapper;
    @Mock
    private PortalProductDao productDao;
    @Mock
    private PmsSkuStockMapper skuStockMapper;
    @Mock
    private OmsPromotionService promotionService;
    @Mock
    private UmsMemberService memberService;

    @InjectMocks
    private OmsCartItemServiceImpl cartItemService;

    private static final Long MEMBER_ID = 100L;
    private static final Long PRODUCT_ID = 1L;
    private static final Long SKU_ID = 11L;
    private static final Long CART_ID = 5L;

    // ---------------------------------------------------------------------
    // 测试数据构造辅助方法
    // ---------------------------------------------------------------------
    private UmsMember member() {
        UmsMember member = new UmsMember();
        member.setId(MEMBER_ID);
        member.setNickname("tester");
        return member;
    }

    private PmsProduct publishedProduct() {
        PmsProduct product = new PmsProduct();
        product.setId(PRODUCT_ID);
        product.setName("测试商品");
        product.setDeleteStatus(0);
        product.setPublishStatus(1);
        return product;
    }

    private PmsProduct offShelfProduct() {
        PmsProduct product = publishedProduct();
        product.setPublishStatus(0);
        return product;
    }

    private PmsSkuStock sku(int stock, int lockStock, Integer lowStock) {
        PmsSkuStock skuStock = new PmsSkuStock();
        skuStock.setId(SKU_ID);
        skuStock.setProductId(PRODUCT_ID);
        skuStock.setStock(stock);
        skuStock.setLockStock(lockStock);
        skuStock.setLowStock(lowStock);
        return skuStock;
    }

    private OmsCartItem incoming(int quantity) {
        OmsCartItem cartItem = new OmsCartItem();
        cartItem.setProductId(PRODUCT_ID);
        cartItem.setProductSkuId(SKU_ID);
        cartItem.setQuantity(quantity);
        return cartItem;
    }

    private OmsCartItem existingLine(int quantity) {
        OmsCartItem cartItem = incoming(quantity);
        cartItem.setId(CART_ID);
        cartItem.setMemberId(MEMBER_ID);
        cartItem.setDeleteStatus(0);
        return cartItem;
    }

    // ---------------------------------------------------------------------
    // add
    // ---------------------------------------------------------------------
    @Test
    void add_success_insertsNewLineAndSetsStockTip() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10, 2, 0));

        OmsCartItem result = cartItemService.add(incoming(3));

        assertNotNull(result);
        assertEquals(MEMBER_ID, result.getMemberId());
        assertEquals(0, result.getDeleteStatus());
        assertEquals("库存充足，当前剩余 8 件", result.getStockTip());
        verify(cartItemMapper).insert(any(OmsCartItem.class));
        verify(cartItemMapper, never()).updateByPrimaryKey(any(OmsCartItem.class));
    }

    @Test
    void add_success_setsLowStockTipWhenBelowThreshold() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        // 可用库存 = 4 - 1 = 3，预警库存 5，应提示库存紧张
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(4, 1, 5));

        OmsCartItem result = cartItemService.add(incoming(2));

        assertEquals("库存紧张，仅剩 3 件", result.getStockTip());
        verify(cartItemMapper).insert(any(OmsCartItem.class));
    }

    @Test
    void add_success_mergesWithExistingLineAndValidatesTotal() {
        OmsCartItem existing = existingLine(2);
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existing));
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10, 2, 0));

        OmsCartItem result = cartItemService.add(incoming(3));

        // 合并后数量 2 + 3 = 5
        assertEquals(5, result.getQuantity());
        assertEquals("库存充足，当前剩余 8 件", result.getStockTip());
        verify(cartItemMapper).updateByPrimaryKey(any(OmsCartItem.class));
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    void add_fails_whenProductOffShelf() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(offShelfProduct());

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(incoming(1)));
        assertEquals("商品已下架，无法添加到购物车", ex.getMessage());
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    void add_fails_whenProductNotFound() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(incoming(1)));
        assertEquals("商品不存在或已删除", ex.getMessage());
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    void add_fails_whenSkuNotExist() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(incoming(1)));
        assertEquals("商品规格不存在", ex.getMessage());
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    void add_fails_whenStockInsufficient() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(2, 0, 0));

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(incoming(5)));
        assertEquals("商品库存不足，当前剩余 2 件", ex.getMessage());
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    @Test
    void add_fails_whenQuantityNotPositive() {
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.add(incoming(0)));
        assertEquals("购买数量必须大于0", ex.getMessage());
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }

    // ---------------------------------------------------------------------
    // updateQuantity
    // ---------------------------------------------------------------------
    @Test
    void updateQuantity_success_updatesAndSetsStockTip() {
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existingLine(2)));
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10, 2, 0));

        OmsCartItem result = cartItemService.updateQuantity(CART_ID, MEMBER_ID, 4);

        assertEquals(4, result.getQuantity());
        assertEquals("库存充足，当前剩余 8 件", result.getStockTip());
        verify(cartItemMapper).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
    }

    @Test
    void updateQuantity_fails_whenItemNotInCart() {
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.updateQuantity(CART_ID, MEMBER_ID, 4));
        assertEquals("购物车中不存在该商品", ex.getMessage());
        verify(cartItemMapper, never()).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
    }

    @Test
    void updateQuantity_fails_whenStockInsufficient() {
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(List.of(existingLine(2)));
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(3, 0, 0));

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.updateQuantity(CART_ID, MEMBER_ID, 10));
        assertEquals("商品库存不足，当前剩余 3 件", ex.getMessage());
        verify(cartItemMapper, never()).updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class));
    }

    // ---------------------------------------------------------------------
    // updateAttr
    // ---------------------------------------------------------------------
    @Test
    void updateAttr_success_softDeletesOriginalAndReAddsWithStockTip() {
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(publishedProduct());
        when(skuStockMapper.selectByPrimaryKey(SKU_ID)).thenReturn(sku(10, 2, 0));
        when(memberService.getCurrentMember()).thenReturn(member());
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        OmsCartItem result = cartItemService.updateAttr(existingLine(2));

        assertEquals("库存充足，当前剩余 8 件", result.getStockTip());
        // 原购物车行被软删除
        verify(cartItemMapper).updateByPrimaryKeySelective(any(OmsCartItem.class));
        // 新规格被重新加入
        verify(cartItemMapper).insert(any(OmsCartItem.class));
    }

    @Test
    void updateAttr_fails_whenNewSkuOffShelfAndKeepsOriginalLine() {
        when(productDao.getCartProductInfo(PRODUCT_ID)).thenReturn(offShelfProduct());

        ApiException ex = assertThrows(ApiException.class, () -> cartItemService.updateAttr(existingLine(2)));
        assertEquals("商品已下架，无法添加到购物车", ex.getMessage());
        // 校验失败发生在删除之前，原购物车行不应被删除，也不应新增
        verify(cartItemMapper, never()).updateByPrimaryKeySelective(any(OmsCartItem.class));
        verify(cartItemMapper, never()).insert(any(OmsCartItem.class));
    }
}
