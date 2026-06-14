package com.macro.mall.portal;

import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.SmsCouponProductCategoryRelation;
import com.macro.mall.model.SmsCouponProductRelation;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.SmsCouponHistoryDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.UmsMemberCouponServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link UmsMemberCouponServiceImpl#listCart(List, Integer)} 可用性判断单元测试。
 * <p>
 * 覆盖全场通用、指定分类、指定商品以及过期场景，校验不可用原因(unavailableReason)、
 * 匹配金额(matchedAmount)以及 type=1 仅返回可用券、type 为其它值返回不可用券的行为。
 */
@ExtendWith(MockitoExtension.class)
public class UmsMemberCouponServiceImplTest {

    private static final Long MEMBER_ID = 1L;
    /** 用于查询可用券的 type 取值 */
    private static final Integer TYPE_ENABLE = 1;
    /** 用于查询不可用券的 type 取值 */
    private static final Integer TYPE_DISABLE = 0;

    @Mock
    private UmsMemberService memberService;
    @Mock
    private SmsCouponHistoryDao couponHistoryDao;
    @InjectMocks
    private UmsMemberCouponServiceImpl couponService;

    // ---------------------------------------------------------------------
    // 全场通用 (useType = 0)
    // ---------------------------------------------------------------------

    @Test
    public void wholeStoreCoupon_available_whenTotalReachesMinPoint() {
        givenMemberCoupons(wholeStoreCoupon("100", future()));
        List<CartPromotionItem> cart = Arrays.asList(
                cartItem(100L, 200L, "50", 2),   // 100
                cartItem(101L, 201L, "30", 1));  // 30  => 总额 130

        List<SmsCouponHistoryDetail> enableList = couponService.listCart(cart, TYPE_ENABLE);

        assertEquals(1, enableList.size());
        assertNull(enableList.get(0).getUnavailableReason());
        assertThat(enableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("130"));
        // 可用券不会出现在不可用列表中
        assertTrue(couponService.listCart(cart, TYPE_DISABLE).isEmpty());
    }

    @Test
    public void wholeStoreCoupon_unavailable_whenBelowMinPoint() {
        givenMemberCoupons(wholeStoreCoupon("200", future()));
        List<CartPromotionItem> cart = Arrays.asList(
                cartItem(100L, 200L, "50", 2),   // 100
                cartItem(101L, 201L, "30", 1));  // 30  => 总额 130 < 200

        List<SmsCouponHistoryDetail> disableList = couponService.listCart(cart, TYPE_DISABLE);

        assertEquals(1, disableList.size());
        assertEquals(UmsMemberCouponServiceImpl.UNAVAILABLE_REASON_MIN_POINT,
                disableList.get(0).getUnavailableReason().intValue());
        assertThat(disableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("130"));
        // 不可用券不会出现在可用列表中
        assertTrue(couponService.listCart(cart, TYPE_ENABLE).isEmpty());
    }

    // ---------------------------------------------------------------------
    // 指定分类 (useType = 1)
    // ---------------------------------------------------------------------

    @Test
    public void categoryCoupon_available_whenCategoryAmountReachesMinPoint() {
        givenMemberCoupons(categoryCoupon("50", future(), 200L));
        List<CartPromotionItem> cart = Arrays.asList(
                cartItem(100L, 200L, "50", 2),   // 分类 200 => 100
                cartItem(101L, 201L, "30", 1));  // 分类 201 不计入

        List<SmsCouponHistoryDetail> enableList = couponService.listCart(cart, TYPE_ENABLE);

        assertEquals(1, enableList.size());
        assertNull(enableList.get(0).getUnavailableReason());
        assertThat(enableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    public void categoryCoupon_unavailable_whenNoMatchingCategory() {
        givenMemberCoupons(categoryCoupon("10", future(), 999L)); // 分类 999 不在购物车中
        List<CartPromotionItem> cart = Arrays.asList(
                cartItem(100L, 200L, "50", 2),
                cartItem(101L, 201L, "30", 1));

        List<SmsCouponHistoryDetail> disableList = couponService.listCart(cart, TYPE_DISABLE);

        assertEquals(1, disableList.size());
        assertEquals(UmsMemberCouponServiceImpl.UNAVAILABLE_REASON_CATEGORY_MISMATCH,
                disableList.get(0).getUnavailableReason().intValue());
        assertThat(disableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("0"));
    }

    // ---------------------------------------------------------------------
    // 指定商品 (useType = 2)
    // ---------------------------------------------------------------------

    @Test
    public void productCoupon_available_whenProductAmountReachesMinPoint() {
        givenMemberCoupons(productCoupon("20", future(), 101L));
        List<CartPromotionItem> cart = Arrays.asList(
                cartItem(100L, 200L, "50", 2),   // 商品 100 不计入
                cartItem(101L, 201L, "30", 1));  // 商品 101 => 30

        List<SmsCouponHistoryDetail> enableList = couponService.listCart(cart, TYPE_ENABLE);

        assertEquals(1, enableList.size());
        assertNull(enableList.get(0).getUnavailableReason());
        assertThat(enableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("30"));
    }

    @Test
    public void productCoupon_unavailable_whenNoMatchingProduct() {
        givenMemberCoupons(productCoupon("10", future(), 888L)); // 商品 888 不在购物车中
        List<CartPromotionItem> cart = Arrays.asList(
                cartItem(100L, 200L, "50", 2),
                cartItem(101L, 201L, "30", 1));

        List<SmsCouponHistoryDetail> disableList = couponService.listCart(cart, TYPE_DISABLE);

        assertEquals(1, disableList.size());
        assertEquals(UmsMemberCouponServiceImpl.UNAVAILABLE_REASON_PRODUCT_MISMATCH,
                disableList.get(0).getUnavailableReason().intValue());
        assertThat(disableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("0"));
    }

    // ---------------------------------------------------------------------
    // 过期场景
    // ---------------------------------------------------------------------

    @Test
    public void coupon_unavailable_whenExpired_evenIfAmountQualifies() {
        // 门槛仅为 10，购物车金额满足门槛，但优惠券已过期，过期原因优先于门槛
        givenMemberCoupons(wholeStoreCoupon("10", past()));
        List<CartPromotionItem> cart = Arrays.asList(cartItem(100L, 200L, "50", 2)); // 100 >= 10

        List<SmsCouponHistoryDetail> disableList = couponService.listCart(cart, TYPE_DISABLE);

        assertEquals(1, disableList.size());
        assertEquals(UmsMemberCouponServiceImpl.UNAVAILABLE_REASON_EXPIRED,
                disableList.get(0).getUnavailableReason().intValue());
        assertThat(disableList.get(0).getMatchedAmount()).isEqualByComparingTo(new BigDecimal("100"));
        assertTrue(couponService.listCart(cart, TYPE_ENABLE).isEmpty());
    }

    // ---------------------------------------------------------------------
    // type 参数行为
    // ---------------------------------------------------------------------

    @Test
    public void listCart_withTypeOtherThanOne_returnsUnavailableList() {
        givenMemberCoupons(wholeStoreCoupon("200", future())); // 50 < 200 => 不可用
        List<CartPromotionItem> cart = Arrays.asList(cartItem(100L, 200L, "50", 1)); // 总额 50

        // type=1 仅返回可用券
        assertTrue(couponService.listCart(cart, 1).isEmpty());
        // type=0 返回不可用券
        assertEquals(1, couponService.listCart(cart, 0).size());
        // 其它值同样返回不可用券
        assertEquals(1, couponService.listCart(cart, 5).size());
    }

    // ---------------------------------------------------------------------
    // 测试辅助方法
    // ---------------------------------------------------------------------

    private void givenMemberCoupons(SmsCouponHistoryDetail... details) {
        when(memberService.getCurrentMember()).thenReturn(member(MEMBER_ID));
        when(couponHistoryDao.getDetailList(MEMBER_ID)).thenReturn(new ArrayList<>(Arrays.asList(details)));
    }

    private UmsMember member(Long id) {
        UmsMember member = new UmsMember();
        member.setId(id);
        return member;
    }

    private CartPromotionItem cartItem(Long productId, Long categoryId, String price, int quantity) {
        CartPromotionItem item = new CartPromotionItem();
        item.setProductId(productId);
        item.setProductCategoryId(categoryId);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        // 购物车促销减免金额，生产环境恒有值，置为 0 以参与原始单价计算
        item.setReduceAmount(BigDecimal.ZERO);
        return item;
    }

    private SmsCoupon coupon(int useType, String minPoint, Date endTime) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setUseType(useType);
        coupon.setMinPoint(new BigDecimal(minPoint));
        coupon.setEndTime(endTime);
        return coupon;
    }

    private SmsCouponHistoryDetail wholeStoreCoupon(String minPoint, Date endTime) {
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCoupon(coupon(0, minPoint, endTime));
        detail.setCategoryRelationList(new ArrayList<>());
        detail.setProductRelationList(new ArrayList<>());
        return detail;
    }

    private SmsCouponHistoryDetail categoryCoupon(String minPoint, Date endTime, Long categoryId) {
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCoupon(coupon(1, minPoint, endTime));
        SmsCouponProductCategoryRelation relation = new SmsCouponProductCategoryRelation();
        relation.setProductCategoryId(categoryId);
        List<SmsCouponProductCategoryRelation> categoryRelationList = new ArrayList<>();
        categoryRelationList.add(relation);
        detail.setCategoryRelationList(categoryRelationList);
        detail.setProductRelationList(new ArrayList<>());
        return detail;
    }

    private SmsCouponHistoryDetail productCoupon(String minPoint, Date endTime, Long productId) {
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCoupon(coupon(2, minPoint, endTime));
        SmsCouponProductRelation relation = new SmsCouponProductRelation();
        relation.setProductId(productId);
        List<SmsCouponProductRelation> productRelationList = new ArrayList<>();
        productRelationList.add(relation);
        detail.setProductRelationList(productRelationList);
        detail.setCategoryRelationList(new ArrayList<>());
        return detail;
    }

    private Date future() {
        return new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000);
    }

    private Date past() {
        return new Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
    }
}
