package com.macro.mall.portal.service;

import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 优惠券可用性判断单元测试
 * 覆盖：全场通用、指定分类、指定商品、过期场景，以及 type 参数过滤行为
 */
@ExtendWith(MockitoExtension.class)
public class UmsMemberCouponServiceTest {

    @Mock
    private UmsMemberService memberService;
    @Mock
    private SmsCouponMapper couponMapper;
    @Mock
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Mock
    private SmsCouponHistoryDao couponHistoryDao;
    @Mock
    private SmsCouponProductRelationMapper couponProductRelationMapper;
    @Mock
    private SmsCouponProductCategoryRelationMapper couponProductCategoryRelationMapper;
    @Mock
    private PmsProductMapper productMapper;

    @InjectMocks
    private UmsMemberCouponServiceImpl memberCouponService;

    private static final Long MEMBER_ID = 1L;

    @BeforeEach
    void setUp() {
        UmsMember member = new UmsMember();
        member.setId(MEMBER_ID);
        member.setNickname("testUser");
        when(memberService.getCurrentMember()).thenReturn(member);
    }

    // ==================== 构造辅助方法 ====================

    private CartPromotionItem buildCartItem(Long productId, Long productCategoryId,
                                            BigDecimal price, int quantity) {
        CartPromotionItem item = new CartPromotionItem();
        item.setProductId(productId);
        item.setProductCategoryId(productCategoryId);
        item.setPrice(price);
        item.setQuantity(quantity);
        item.setReduceAmount(BigDecimal.ZERO);
        return item;
    }

    private SmsCouponHistoryDetail buildCouponDetail(Long couponId, int useType,
                                                      BigDecimal minPoint, Date endTime) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(couponId);
        coupon.setUseType(useType);
        coupon.setMinPoint(minPoint);
        coupon.setEndTime(endTime);
        coupon.setStartTime(new Date(endTime.getTime() - 86400000L * 30));
        coupon.setName("测试优惠券" + couponId);
        coupon.setAmount(new BigDecimal("10.00"));

        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setId(couponId);
        detail.setCouponId(couponId);
        detail.setMemberId(MEMBER_ID);
        detail.setUseStatus(0);
        detail.setCoupon(coupon);
        detail.setProductRelationList(new ArrayList<>());
        detail.setCategoryRelationList(new ArrayList<>());
        return detail;
    }

    private Date futureDate(int days) {
        return new Date(System.currentTimeMillis() + 86400000L * days);
    }

    private Date pastDate(int days) {
        return new Date(System.currentTimeMillis() - 86400000L * days);
    }

    private SmsCouponProductCategoryRelation buildCategoryRelation(Long categoryId) {
        SmsCouponProductCategoryRelation rel = new SmsCouponProductCategoryRelation();
        rel.setProductCategoryId(categoryId);
        return rel;
    }

    private SmsCouponProductRelation buildProductRelation(Long productId) {
        SmsCouponProductRelation rel = new SmsCouponProductRelation();
        rel.setProductId(productId);
        return rel;
    }

    // ==================== 全场通用（useType=0）====================

    @Nested
    @DisplayName("全场通用优惠券（useType=0）")
    class UniversalCouponTests {

        @Test
        @DisplayName("未过期且金额达到门槛 → 可用")
        void available_notExpired_meetsThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 2)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("100.00"), futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            assertNull(result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("无门槛(minPoint=0)且未过期 → 可用")
        void available_zeroMinPoint() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("10.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, BigDecimal.ZERO, futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            assertNull(result.get(0).getUnavailableReason());
        }

        @Test
        @DisplayName("已过期 → 不可用，原因：优惠券已过期")
        void unavailable_expired() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("200.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("100.00"), pastDate(1));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("优惠券已过期", result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("金额未达到门槛 → 不可用，原因：订单金额未达到使用门槛")
        void unavailable_belowThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("50.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("100.00"), futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("订单金额未达到使用门槛（满¥100.00）", result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("50.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("空购物车 + 无门槛 → 可用")
        void available_emptyCart_zeroMinPoint() {
            List<CartPromotionItem> cart = new ArrayList<>();
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, BigDecimal.ZERO, futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(cart, 1);

            assertEquals(1, result.size());
            assertNull(result.get(0).getUnavailableReason());
        }

        @Test
        @DisplayName("金额恰好等于门槛 → 可用")
        void available_exactThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("100.00"), futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            assertNull(result.get(0).getUnavailableReason());
        }
    }

    // ==================== 指定分类（useType=1）====================

    @Nested
    @DisplayName("指定分类优惠券（useType=1）")
    class CategoryCouponTests {

        @Test
        @DisplayName("购物车有匹配分类商品且金额达标 → 可用")
        void available_matchingCategory_meetsThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 2)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 1, new BigDecimal("100.00"), futureDate(30));
            detail.setCategoryRelationList(List.of(buildCategoryRelation(100L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            assertNull(result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("已过期 → 不可用，原因：优惠券已过期")
        void unavailable_expired() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 2)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 1, new BigDecimal("100.00"), pastDate(1));
            detail.setCategoryRelationList(List.of(buildCategoryRelation(100L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("优惠券已过期", result.get(0).getUnavailableReason());
            // matchedAmount 仍然应被计算
            assertEquals(0, new BigDecimal("200.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("购物车没有匹配分类商品 → 不可用，原因：购物车中没有指定分类的商品")
        void unavailable_noMatchingCategory() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 200L, new BigDecimal("100.00"), 2)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 1, new BigDecimal("100.00"), futureDate(30));
            detail.setCategoryRelationList(List.of(buildCategoryRelation(100L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("购物车中没有指定分类的商品", result.get(0).getUnavailableReason());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("分类金额未达到门槛 → 不可用，原因：指定分类商品金额未达到使用门槛")
        void unavailable_belowThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 100L, new BigDecimal("30.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 1, new BigDecimal("100.00"), futureDate(30));
            detail.setCategoryRelationList(List.of(buildCategoryRelation(100L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("指定分类商品金额未达到使用门槛（满¥100.00）", result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("30.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("多个分类关联，购物车含其中一个 → matchedAmount 只算匹配的")
        void available_multipleCategories_oneMatches() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(1L, 200L, new BigDecimal("50.00"), 2),   // 分类200
                    buildCartItem(2L, 300L, new BigDecimal("80.00"), 1)    // 分类300
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 1, new BigDecimal("100.00"), futureDate(30));
            // 优惠券关联分类200和300
            detail.setCategoryRelationList(List.of(
                    buildCategoryRelation(200L),
                    buildCategoryRelation(300L)
            ));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            // matchedAmount = 50*2 + 80*1 = 180
            assertEquals(0, new BigDecimal("180.00").compareTo(result.get(0).getMatchedAmount()));
        }
    }

    // ==================== 指定商品（useType=2）====================

    @Nested
    @DisplayName("指定商品优惠券（useType=2）")
    class ProductCouponTests {

        @Test
        @DisplayName("购物车有匹配商品且金额达标 → 可用")
        void available_matchingProduct_meetsThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(10L, 100L, new BigDecimal("60.00"), 2)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 2, new BigDecimal("100.00"), futureDate(30));
            detail.setProductRelationList(List.of(buildProductRelation(10L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            assertNull(result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("120.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("已过期 → 不可用，原因：优惠券已过期")
        void unavailable_expired() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(10L, 100L, new BigDecimal("200.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 2, new BigDecimal("100.00"), pastDate(1));
            detail.setProductRelationList(List.of(buildProductRelation(10L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("优惠券已过期", result.get(0).getUnavailableReason());
        }

        @Test
        @DisplayName("购物车没有匹配商品 → 不可用，原因：购物车中没有指定商品")
        void unavailable_noMatchingProduct() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(20L, 100L, new BigDecimal("200.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 2, new BigDecimal("100.00"), futureDate(30));
            detail.setProductRelationList(List.of(buildProductRelation(10L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("购物车中没有指定商品", result.get(0).getUnavailableReason());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("商品金额未达到门槛 → 不可用，原因：指定商品金额未达到使用门槛")
        void unavailable_belowThreshold() {
            List<CartPromotionItem> cart = List.of(
                    buildCartItem(10L, 100L, new BigDecimal("30.00"), 1)
            );
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 2, new BigDecimal("100.00"), futureDate(30));
            detail.setProductRelationList(List.of(buildProductRelation(10L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 0);

            assertEquals(1, result.size());
            assertEquals("指定商品金额未达到使用门槛（满¥100.00）", result.get(0).getUnavailableReason());
            assertEquals(0, new BigDecimal("30.00").compareTo(result.get(0).getMatchedAmount()));
        }

        @Test
        @DisplayName("购物车有促销减价时 matchedAmount 应按实付计算")
        void available_matchedAmount_withReduceAmount() {
            CartPromotionItem item = buildCartItem(10L, 100L, new BigDecimal("100.00"), 2);
            item.setReduceAmount(new BigDecimal("20.00"));
            List<CartPromotionItem> cart = List.of(item);

            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 2, new BigDecimal("100.00"), futureDate(30));
            detail.setProductRelationList(List.of(buildProductRelation(10L)));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(new ArrayList<>(cart), 1);

            assertEquals(1, result.size());
            // matchedAmount = (100 - 20) * 2 = 160
            assertEquals(0, new BigDecimal("160.00").compareTo(result.get(0).getMatchedAmount()));
        }
    }

    // ==================== type 参数过滤 ====================

    @Nested
    @DisplayName("type 参数过滤行为")
    class TypeFilterTests {

        @Test
        @DisplayName("type=1 只返回可用优惠券")
        void type1_returnsOnlyAvailable() {
            // 可用：全场通用，未过期，金额达标
            SmsCouponHistoryDetail available = buildCouponDetail(1L, 0, new BigDecimal("50.00"), futureDate(30));
            // 不可用：已过期
            SmsCouponHistoryDetail expired = buildCouponDetail(2L, 0, new BigDecimal("50.00"), pastDate(1));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(available, expired));

            List<CartPromotionItem> cart = new ArrayList<>(List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 1)
            ));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(cart, 1);

            assertEquals(1, result.size());
            assertEquals(Long.valueOf(1L), result.get(0).getCoupon().getId());
            assertNull(result.get(0).getUnavailableReason());
        }

        @Test
        @DisplayName("type=0 返回不可用优惠券并附带原因")
        void type0_returnsOnlyUnavailable_withReason() {
            SmsCouponHistoryDetail available = buildCouponDetail(1L, 0, new BigDecimal("50.00"), futureDate(30));
            SmsCouponHistoryDetail expired = buildCouponDetail(2L, 0, new BigDecimal("50.00"), pastDate(1));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(available, expired));

            List<CartPromotionItem> cart = new ArrayList<>(List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 1)
            ));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(cart, 0);

            assertEquals(1, result.size());
            assertEquals(Long.valueOf(2L), result.get(0).getCoupon().getId());
            assertNotNull(result.get(0).getUnavailableReason());
            assertEquals("优惠券已过期", result.get(0).getUnavailableReason());
        }

        @Test
        @DisplayName("type=其他值 等同type=0，返回不可用优惠券")
        void typeOther_returnsUnavailable() {
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("100.00"), futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<CartPromotionItem> cart = new ArrayList<>(List.of(
                    buildCartItem(1L, 100L, new BigDecimal("50.00"), 1)
            ));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(cart, 2);

            assertEquals(1, result.size());
            assertNotNull(result.get(0).getUnavailableReason());
        }

        @Test
        @DisplayName("全部可用时 type=0 返回空列表")
        void type0_allAvailable_returnsEmpty() {
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("50.00"), futureDate(30));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<CartPromotionItem> cart = new ArrayList<>(List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 1)
            ));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(cart, 0);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("全部不可用时 type=1 返回空列表")
        void type1_allUnavailable_returnsEmpty() {
            SmsCouponHistoryDetail detail = buildCouponDetail(1L, 0, new BigDecimal("100.00"), pastDate(1));
            when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(List.of(detail));

            List<CartPromotionItem> cart = new ArrayList<>(List.of(
                    buildCartItem(1L, 100L, new BigDecimal("100.00"), 1)
            ));

            List<SmsCouponHistoryDetail> result = memberCouponService.listCart(cart, 1);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 混合场景 ====================

    @Nested
    @DisplayName("混合场景：多种类型优惠券共存")
    class MixedCouponTests {

        @Test
        @DisplayName("可用列表和不可用列表各自正确分类，不可用原因各不同")
        void mixedCoupons_correctClassification() {
            // 1. 全场通用 - 可用
            SmsCouponHistoryDetail universal = buildCouponDetail(1L, 0, new BigDecimal("50.00"), futureDate(30));

            // 2. 指定分类 - 购物车没有匹配分类
            SmsCouponHistoryDetail categoryNoMatch = buildCouponDetail(2L, 1, new BigDecimal("50.00"), futureDate(30));
            categoryNoMatch.setCategoryRelationList(List.of(buildCategoryRelation(999L)));

            // 3. 指定商品 - 已过期
            SmsCouponHistoryDetail productExpired = buildCouponDetail(3L, 2, new BigDecimal("50.00"), pastDate(1));
            productExpired.setProductRelationList(List.of(buildProductRelation(10L)));

            // 4. 全场通用 - 金额不达标
            SmsCouponHistoryDetail universalBelowThreshold = buildCouponDetail(4L, 0, new BigDecimal("200.00"), futureDate(30));

            when(couponHistoryDao.getDetailList(MEMBER_ID))
                    .thenReturn(List.of(universal, categoryNoMatch, productExpired, universalBelowThreshold));

            List<CartPromotionItem> cart = new ArrayList<>(List.of(
                    buildCartItem(10L, 100L, new BigDecimal("100.00"), 1)
            ));

            // 验证可用列表
            List<SmsCouponHistoryDetail> enableResult = memberCouponService.listCart(new ArrayList<>(cart), 1);
            assertEquals(1, enableResult.size());
            assertEquals(Long.valueOf(1L), enableResult.get(0).getCoupon().getId());
            assertEquals(0, new BigDecimal("100.00").compareTo(enableResult.get(0).getMatchedAmount()));

            // 验证不可用列表
            List<SmsCouponHistoryDetail> disableResult = memberCouponService.listCart(new ArrayList<>(cart), 0);
            assertEquals(3, disableResult.size());

            // 按 couponId 排序以确保断言顺序稳定
            disableResult.sort(Comparator.comparing(d -> d.getCoupon().getId()));

            // couponId=2: 购物车中没有指定分类的商品
            assertEquals("购物车中没有指定分类的商品", disableResult.get(0).getUnavailableReason());
            // couponId=3: 优惠券已过期
            assertEquals("优惠券已过期", disableResult.get(1).getUnavailableReason());
            // couponId=4: 订单金额未达到使用门槛
            assertTrue(disableResult.get(2).getUnavailableReason().contains("订单金额未达到使用门槛"));

            // 所有不可用券都应有 matchedAmount
            for (SmsCouponHistoryDetail d : disableResult) {
                assertNotNull(d.getMatchedAmount(), "couponId=" + d.getCoupon().getId() + " matchedAmount 不应为null");
            }
        }
    }
}
