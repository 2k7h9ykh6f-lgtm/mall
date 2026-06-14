package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.UmsIntegrationConsumeSettingMapper;
import com.macro.mall.model.*;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OmsPortalOrderServiceImpl 金额计算单元测试
 */
@ExtendWith(MockitoExtension.class)
public class OmsPortalOrderServiceImplTest {

    @InjectMocks
    private OmsPortalOrderServiceImpl orderService;

    @Mock
    private UmsMemberService memberService;
    @Mock
    private OmsCartItemService cartItemService;
    @Mock
    private UmsMemberReceiveAddressService memberReceiveAddressService;
    @Mock
    private UmsMemberCouponService memberCouponService;
    @Mock
    private UmsIntegrationConsumeSettingMapper integrationConsumeSettingMapper;

    private UmsMember currentMember;
    private UmsIntegrationConsumeSetting integrationSetting;
    private List<Long> cartIds;

    @BeforeEach
    void setUp() {
        // 构造当前会员（持有5000积分）
        currentMember = new UmsMember();
        currentMember.setId(1L);
        currentMember.setUsername("testUser");
        currentMember.setIntegration(5000);

        // 构造积分使用规则：100积分=1元，最低100积分，最高可抵50%，可与优惠券共用
        integrationSetting = new UmsIntegrationConsumeSetting();
        integrationSetting.setId(1L);
        integrationSetting.setDeductionPerAmount(100);
        integrationSetting.setUseUnit(100);
        integrationSetting.setMaxPercentPerOrder(50);
        integrationSetting.setCouponStatus(1); // 1=可与优惠券共用

        cartIds = Arrays.asList(1L, 2L);
    }

    // ======================== 辅助方法 ========================

    /**
     * 创建购物车促销商品
     */
    private CartPromotionItem buildCartItem(Long id, Long productId, Long categoryId,
                                            BigDecimal price, Integer quantity,
                                            BigDecimal reduceAmount) {
        CartPromotionItem item = new CartPromotionItem();
        item.setId(id);
        item.setProductId(productId);
        item.setProductCategoryId(categoryId);
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setReduceAmount(reduceAmount);
        item.setRealStock(100);
        item.setIntegration(10);
        item.setGrowth(10);
        item.setProductName("商品" + productId);
        item.setProductPic("http://example.com/pic.jpg");
        item.setProductSkuId(id * 10);
        item.setProductSkuCode("SKU" + id);
        item.setProductBrand("品牌A");
        item.setProductSn("SN" + productId);
        item.setProductAttr("红色");
        item.setPromotionMessage("促销活动");
        return item;
    }

    /**
     * 创建优惠券详情
     */
    private SmsCouponHistoryDetail buildCouponDetail(Long couponId, BigDecimal amount,
                                                     Integer useType) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(couponId);
        coupon.setAmount(amount);
        coupon.setUseType(useType);
        coupon.setMinPoint(new BigDecimal(0));

        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCouponId(couponId);
        detail.setCoupon(coupon);
        detail.setUseStatus(0);
        return detail;
    }

    /**
     * 创建标准2商品购物车：
     * 商品1: price=100, qty=2, reduceAmount=10 → 小计200，优惠20
     * 商品2: price=50,  qty=1, reduceAmount=5  → 小计50，优惠5
     * totalAmount=250, promotionAmount=25, payAmount=225
     */
    private List<CartPromotionItem> buildStandardCart() {
        List<CartPromotionItem> cart = new ArrayList<>();
        cart.add(buildCartItem(1L, 101L, 10L, new BigDecimal("100"), 2, new BigDecimal("10")));
        cart.add(buildCartItem(2L, 102L, 20L, new BigDecimal("50"), 1, new BigDecimal("5")));
        return cart;
    }

    /**
     * 配置基础mock行为
     */
    private void setupBasicMocks(List<CartPromotionItem> cart) {
        lenient().when(memberService.getCurrentMember()).thenReturn(currentMember);
        lenient().when(cartItemService.listPromotion(eq(1L), anyList())).thenReturn(cart);
        lenient().when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(integrationSetting);
    }

    // ======================== 测试用例 ========================

    /**
     * 测试1：不使用优惠券和积分
     * totalAmount=250, promotionAmount=25, couponAmount=0, integrationAmount=0, payAmount=225
     */
    @Test
    void testCalcAmount_noCouponNoIntegration() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, null, null);

        assertEquals(new BigDecimal("250"), result.getTotalAmount());
        assertEquals(new BigDecimal("25"), result.getPromotionAmount());
        assertEquals(new BigDecimal("0"), result.getCouponAmount());
        assertEquals(new BigDecimal("0"), result.getIntegrationAmount());
        assertEquals(new BigDecimal("0"), result.getFreightAmount());
        assertEquals(new BigDecimal("225"), result.getPayAmount());
        assertEquals("当前订单免运费", result.getFreightDescription());
        assertNotNull(result.getPayAmountBreakdown());
        assertNull(result.getPayAmountBreakdown().getCouponId());
        assertNull(result.getPayAmountBreakdown().getUseIntegration());
    }

    /**
     * 测试2：全场通用优惠券(useType=0)
     * couponId=1, couponAmount=30, 按价格比例分摊
     * 商品1: (200/250)*30=24, 商品2: (50/250)*30=6
     */
    @Test
    void testCalcAmount_universalCoupon() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);

        Long couponId = 1L;
        SmsCouponHistoryDetail couponDetail = buildCouponDetail(couponId, new BigDecimal("30"), 0);
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(couponDetail));

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, null);

        assertEquals(new BigDecimal("250"), result.getTotalAmount());
        assertEquals(new BigDecimal("25"), result.getPromotionAmount());
        assertEquals(new BigDecimal("30"), result.getCouponAmount().setScale(0, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("0"), result.getIntegrationAmount());
        // payAmount = 250 - 25 - 30 = 195
        assertEquals(new BigDecimal("195"), result.getPayAmount().setScale(0, RoundingMode.HALF_EVEN));
        assertEquals(couponId, result.getPayAmountBreakdown().getCouponId());
    }

    /**
     * 测试3：指定分类优惠券(useType=1)
     * 只有categoryId=10的商品享受优惠
     */
    @Test
    void testCalcAmount_categoryCoupon() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);

        Long couponId = 2L;
        SmsCouponHistoryDetail couponDetail = buildCouponDetail(couponId, new BigDecimal("20"), 1);
        // 设置分类关联：只对categoryId=10的商品有效
        SmsCouponProductCategoryRelation catRelation = new SmsCouponProductCategoryRelation();
        catRelation.setProductCategoryId(10L);
        couponDetail.setCategoryRelationList(Collections.singletonList(catRelation));
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(couponDetail));

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, null);

        // 只有商品1(categoryId=10, price=100*2=200)享受优惠，couponAmount=20
        assertEquals(new BigDecimal("20"), result.getCouponAmount().setScale(0, RoundingMode.HALF_EVEN));
        // payAmount = 250 - 25 - 20 = 205
        assertEquals(new BigDecimal("205"), result.getPayAmount().setScale(0, RoundingMode.HALF_EVEN));
    }

    /**
     * 测试4：指定商品优惠券(useType=2)
     * 只有productId=101的商品享受优惠
     */
    @Test
    void testCalcAmount_productCoupon() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);

        Long couponId = 3L;
        SmsCouponHistoryDetail couponDetail = buildCouponDetail(couponId, new BigDecimal("15"), 2);
        // 设置商品关联：只对productId=101有效
        SmsCouponProductRelation prodRelation = new SmsCouponProductRelation();
        prodRelation.setProductId(101L);
        couponDetail.setProductRelationList(Collections.singletonList(prodRelation));
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(couponDetail));

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, null);

        // 只有商品1(productId=101)享受优惠，couponAmount=15
        assertEquals(new BigDecimal("15"), result.getCouponAmount().setScale(0, RoundingMode.HALF_EVEN));
        // payAmount = 250 - 25 - 15 = 210
        assertEquals(new BigDecimal("210"), result.getPayAmount().setScale(0, RoundingMode.HALF_EVEN));
    }

    /**
     * 测试5：积分抵扣在限额内
     * useIntegration=1000, useUnit=100 → integrationAmount=10.00
     * maxPercent=50%, totalAmount=250 → max=125, 10<125 → 可用
     */
    @Test
    void testCalcAmount_integrationWithinLimits() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);

        Integer useIntegration = 1000;

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, null, useIntegration);

        assertEquals(new BigDecimal("250"), result.getTotalAmount());
        assertEquals(new BigDecimal("25"), result.getPromotionAmount());
        assertEquals(new BigDecimal("0"), result.getCouponAmount());
        // 1000/100 = 10.00
        assertEquals(new BigDecimal("10.00"), result.getIntegrationAmount());
        // payAmount = 250 - 25 - 10 = 215
        assertEquals(new BigDecimal("215.00"), result.getPayAmount());
        assertEquals(useIntegration, result.getPayAmountBreakdown().getUseIntegration());
    }

    /**
     * 测试6：积分超过最高百分比限制
     * useIntegration=20000, 20000/100=200, max=250*0.5=125, 200>125 → 不可用 → integrationAmount=0
     */
    @Test
    void testCalcAmount_integrationExceedsMaxPercent() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);
        // 给会员足够的积分
        currentMember.setIntegration(20000);

        Integer useIntegration = 20000;

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, null, useIntegration);

        // 积分超限，预览模式下不抛异常，返回0
        assertEquals(new BigDecimal("0"), result.getIntegrationAmount());
        // payAmount = 250 - 25 - 0 = 225
        assertEquals(new BigDecimal("225"), result.getPayAmount());
    }

    /**
     * 测试7：优惠券+积分组合使用(couponStatus=1允许共用)
     */
    @Test
    void testCalcAmount_couponAndIntegrationCombined() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);
        integrationSetting.setCouponStatus(1); // 允许共用

        Long couponId = 1L;
        SmsCouponHistoryDetail couponDetail = buildCouponDetail(couponId, new BigDecimal("30"), 0);
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(couponDetail));

        Integer useIntegration = 1000; // 1000/100=10元

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, useIntegration);

        assertEquals(new BigDecimal("30"), result.getCouponAmount().setScale(0, RoundingMode.HALF_EVEN));
        assertEquals(new BigDecimal("10.00"), result.getIntegrationAmount());
        // payAmount = 250 - 25 - 30 - 10 = 185
        assertEquals(new BigDecimal("185.00"), result.getPayAmount().setScale(2, RoundingMode.HALF_EVEN));
    }

    /**
     * 测试8：优惠券+积分冲突(couponStatus=0禁止共用)
     * 使用优惠券时积分不可用
     */
    @Test
    void testCalcAmount_couponIntegrationConflict() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);
        integrationSetting.setCouponStatus(0); // 禁止共用

        Long couponId = 1L;
        SmsCouponHistoryDetail couponDetail = buildCouponDetail(couponId, new BigDecimal("30"), 0);
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(couponDetail));

        Integer useIntegration = 1000;

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, useIntegration);

        // 优惠券正常生效
        assertEquals(new BigDecimal("30"), result.getCouponAmount().setScale(0, RoundingMode.HALF_EVEN));
        // 积分因冲突不可用，返回0
        assertEquals(new BigDecimal("0"), result.getIntegrationAmount());
        // payAmount = 250 - 25 - 30 - 0 = 195
        assertEquals(new BigDecimal("195"), result.getPayAmount().setScale(0, RoundingMode.HALF_EVEN));
    }

    /**
     * 测试9：无效优惠券 → 优雅降级为0
     */
    @Test
    void testCalcAmount_invalidCouponFallback() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);

        Long couponId = 999L;
        // 返回空列表，getUseCoupon将返回null → 抛出异常 → 预览模式降级为0
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.emptyList());

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, null);

        assertEquals(new BigDecimal("0"), result.getCouponAmount());
        // payAmount = 250 - 25 - 0 = 225
        assertEquals(new BigDecimal("225"), result.getPayAmount());
    }

    /**
     * 测试10：积分不足 → 优雅降级为0
     */
    @Test
    void testCalcAmount_insufficientIntegrationFallback() {
        List<CartPromotionItem> cart = buildStandardCart();
        setupBasicMocks(cart);
        // 会员只有50积分
        currentMember.setIntegration(50);

        // 尝试使用1000积分（超出持有量）
        Integer useIntegration = 1000;

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, null, useIntegration);

        // 积分不足，预览模式降级为0
        assertEquals(new BigDecimal("0"), result.getIntegrationAmount());
        // payAmount = 250 - 25 - 0 = 225
        assertEquals(new BigDecimal("225"), result.getPayAmount());
    }

    /**
     * 测试11：小数精度验证
     * 商品1: price=99.99, qty=3 → 小计=299.97
     * 商品2: price=33.33, qty=2 → 小计=66.66
     * totalAmount=366.63, promotionAmount=0, payAmount=366.63
     */
    @Test
    void testCalcAmount_decimalPrecision() {
        List<CartPromotionItem> cart = new ArrayList<>();
        cart.add(buildCartItem(1L, 101L, 10L, new BigDecimal("99.99"), 3, BigDecimal.ZERO));
        cart.add(buildCartItem(2L, 102L, 20L, new BigDecimal("33.33"), 2, BigDecimal.ZERO));
        setupBasicMocks(cart);

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, null, null);

        assertEquals(new BigDecimal("366.63"), result.getTotalAmount());
        assertEquals(new BigDecimal("0"), result.getPromotionAmount());
        assertEquals(new BigDecimal("366.63"), result.getPayAmount());
    }

    /**
     * 测试12：3商品优惠券分摊的舍入精度
     * 3件商品价格分别为33.33、33.33、33.34（totalAmount=100.00）
     * 使用30元全场优惠券
     * 分摊: (33.33/100)*30=9.999, (33.33/100)*30=9.999, (33.34/100)*30=10.002
     * calcCouponAmount汇总时应接近30.000
     */
    @Test
    void testCalcAmount_roundingApportionment() {
        List<CartPromotionItem> cart = new ArrayList<>();
        cart.add(buildCartItem(1L, 101L, 10L, new BigDecimal("33.33"), 1, BigDecimal.ZERO));
        cart.add(buildCartItem(2L, 102L, 20L, new BigDecimal("33.33"), 1, BigDecimal.ZERO));
        cart.add(buildCartItem(3L, 103L, 30L, new BigDecimal("33.34"), 1, BigDecimal.ZERO));
        cartIds = Arrays.asList(1L, 2L, 3L);
        setupBasicMocks(cart);

        Long couponId = 1L;
        SmsCouponHistoryDetail couponDetail = buildCouponDetail(couponId, new BigDecimal("30"), 0);
        when(memberCouponService.listCart(anyList(), eq(1))).thenReturn(Collections.singletonList(couponDetail));

        ConfirmOrderResult.CalcAmount result = orderService.calcConfirmOrderAmount(cartIds, couponId, null);

        assertEquals(new BigDecimal("100.00"), result.getTotalAmount());
        // 优惠券分摊后的总金额应非常接近30（允许0.01的舍入误差）
        BigDecimal couponAmount = result.getCouponAmount();
        assertTrue(couponAmount.subtract(new BigDecimal("30")).abs().compareTo(new BigDecimal("0.01")) <= 0,
                "Coupon amount " + couponAmount + " should be close to 30.00");
        // payAmount = 100 - 0 - ~30 = ~70
        BigDecimal expectedPay = new BigDecimal("100.00")
                .subtract(result.getPromotionAmount())
                .subtract(couponAmount)
                .subtract(result.getIntegrationAmount());
        assertEquals(expectedPay, result.getPayAmount());
    }
}
