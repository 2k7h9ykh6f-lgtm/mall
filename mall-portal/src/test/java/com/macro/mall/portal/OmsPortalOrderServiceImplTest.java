package com.macro.mall.portal;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.mapper.SmsCouponHistoryMapper;
import com.macro.mall.mapper.UmsIntegrationConsumeSettingMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.SmsCoupon;
import com.macro.mall.model.UmsIntegrationConsumeSetting;
import com.macro.mall.model.UmsMember;
import com.macro.mall.model.UmsMemberReceiveAddress;
import com.macro.mall.portal.component.CancelOrderSender;
import com.macro.mall.portal.dao.PortalOrderItemDao;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.domain.SmsCouponHistoryDetail;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.UmsMemberCouponService;
import com.macro.mall.portal.service.UmsMemberReceiveAddressService;
import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.portal.service.impl.OmsPortalOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 确认单/下单金额计算单元测试。
 * 重点验证：优惠券/积分金额分摊、不使用优惠券/积分场景，以及确认单与下单金额口径一致。
 */
@ExtendWith(MockitoExtension.class)
public class OmsPortalOrderServiceImplTest {

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
    @Mock
    private PmsSkuStockMapper skuStockMapper;
    @Mock
    private OmsOrderMapper orderMapper;
    @Mock
    private PortalOrderItemDao orderItemDao;
    @Mock
    private SmsCouponHistoryMapper couponHistoryMapper;
    @Mock
    private RedisService redisService;
    @Mock
    private OmsOrderSettingMapper orderSettingMapper;
    @Mock
    private CancelOrderSender cancelOrderSender;

    @InjectMocks
    private OmsPortalOrderServiceImpl orderService;

    /**
     * 使用优惠券+积分：确认单应返回真实的优惠券优惠、积分抵扣与应付金额明细。
     * 两件商品各100元(总价200)，活动优惠各10元；全场通用券面额30；使用100积分(每100积分抵1元)。
     * 应付 = 200 + 0(运费) - 20(活动) - 30(券) - 1(积分) = 149。
     */
    @Test
    public void testConfirmOrder_withCouponAndIntegration() {
        UmsMember member = member(1L, 1000);
        List<CartPromotionItem> cartList = Arrays.asList(
                cartItem(1L, 11L, "100", 1, "10", 100),
                cartItem(2L, 12L, "100", 1, "10", 100));
        when(memberService.getCurrentMember()).thenReturn(member);
        when(cartItemService.listPromotion(any(), any())).thenReturn(cartList);
        when(memberCouponService.listCart(any(), any())).thenReturn(Arrays.asList(couponDetail(1L, 0, "30")));
        when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting(100, 50, 1));

        OrderParam param = new OrderParam();
        param.setCartIds(Arrays.asList(1L, 2L));
        param.setCouponId(1L);
        param.setUseIntegration(100);

        ConfirmOrderResult.CalcAmount amount = orderService.generateConfirmOrder(param).getCalcAmount();

        assertAmount("200", amount.getTotalAmount());
        assertAmount("0", amount.getFreightAmount());
        assertAmount("20", amount.getPromotionAmount());
        assertAmount("30", amount.getCouponAmount());
        assertAmount("1", amount.getIntegrationAmount());
        assertAmount("149", amount.getPayAmount());

        // 应付金额明细需与汇总金额逐项对应
        ConfirmOrderResult.PayAmountBreakdown breakdown = amount.getPayAmountBreakdown();
        assertNotNull(breakdown, "payAmountBreakdown should not be null");
        assertAmount("200", breakdown.getTotalAmount());
        assertAmount("0", breakdown.getFreightAmount());
        assertAmount("20", breakdown.getPromotionAmount());
        assertAmount("30", breakdown.getCouponAmount());
        assertAmount("1", breakdown.getIntegrationAmount());
        assertAmount("149", breakdown.getPayAmount());
    }

    /**
     * 不使用优惠券、不使用积分：券与积分抵扣均为0，应付 = 总价 - 活动优惠。
     */
    @Test
    public void testConfirmOrder_withoutCouponOrIntegration() {
        UmsMember member = member(1L, 1000);
        List<CartPromotionItem> cartList = Arrays.asList(
                cartItem(1L, 11L, "100", 1, "10", 100),
                cartItem(2L, 12L, "100", 1, "10", 100));
        when(memberService.getCurrentMember()).thenReturn(member);
        when(cartItemService.listPromotion(any(), any())).thenReturn(cartList);
        when(memberCouponService.listCart(any(), any())).thenReturn(Collections.emptyList());
        when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting(100, 50, 1));

        OrderParam param = new OrderParam();
        param.setCartIds(Arrays.asList(1L, 2L));
        param.setCouponId(null);
        param.setUseIntegration(null);

        ConfirmOrderResult.CalcAmount amount = orderService.generateConfirmOrder(param).getCalcAmount();

        assertAmount("200", amount.getTotalAmount());
        assertAmount("0", amount.getFreightAmount());
        assertAmount("20", amount.getPromotionAmount());
        assertAmount("0", amount.getCouponAmount());
        assertAmount("0", amount.getIntegrationAmount());
        assertAmount("180", amount.getPayAmount());
        assertTrue("免运费".equals(amount.getFreightDescription()),
                "freightDescription should be 免运费 but was " + amount.getFreightDescription());
        assertAmount("180", amount.getPayAmountBreakdown().getPayAmount());
    }

    /**
     * 金额分摊 + 口径一致：相同入参下，下单应付金额必须等于确认单应付金额，
     * 且优惠券/积分金额按单价占比分摊到各商品且求和不丢钱。
     * 商品100/300(总价400)，活动优惠各10；券面额40；使用200积分(抵2元)。
     * 券分摊: 100/400*40=10, 300/400*40=30; 积分分摊: 100/400*2=0.5, 300/400*2=1.5。
     * 应付 = 400 - 20 - 40 - 2 = 338。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testGenerateOrderMatchesConfirmAndSplitsAmounts() {
        UmsMember member = member(1L, 1000);
        List<CartPromotionItem> cartList = Arrays.asList(
                cartItem(1L, 11L, "100", 1, "10", 100),
                cartItem(2L, 12L, "300", 1, "10", 100));
        when(memberService.getCurrentMember()).thenReturn(member);
        when(cartItemService.listPromotion(any(), any())).thenReturn(cartList);
        when(memberCouponService.listCart(any(), any())).thenReturn(Arrays.asList(couponDetail(1L, 0, "40")));
        when(integrationConsumeSettingMapper.selectByPrimaryKey(1L)).thenReturn(setting(100, 50, 1));
        PmsSkuStock skuStock = new PmsSkuStock();
        skuStock.setLockStock(0);
        when(skuStockMapper.selectByPrimaryKey(any())).thenReturn(skuStock);
        when(memberReceiveAddressService.getItem(any())).thenReturn(new UmsMemberReceiveAddress());
        when(redisService.incr(anyString(), anyLong())).thenReturn(1L);
        OmsOrderSetting orderSetting = new OmsOrderSetting();
        orderSetting.setNormalOrderOvertime(60);
        when(orderSettingMapper.selectByPrimaryKey(1L)).thenReturn(orderSetting);

        OrderParam param = new OrderParam();
        param.setCartIds(Arrays.asList(1L, 2L));
        param.setCouponId(1L);
        param.setUseIntegration(200);
        param.setMemberReceiveAddressId(1L);
        param.setPayType(1);

        BigDecimal confirmPayAmount = orderService.generateConfirmOrder(param).getCalcAmount().getPayAmount();

        Map<String, Object> orderResult = orderService.generateOrder(param);
        OmsOrder order = (OmsOrder) orderResult.get("order");
        List<OmsOrderItem> orderItemList = (List<OmsOrderItem>) orderResult.get("orderItemList");

        // 口径一致：确认单应付金额 == 下单应付金额
        assertTrue(order.getPayAmount().compareTo(confirmPayAmount) == 0,
                "confirm payAmount " + confirmPayAmount + " != order payAmount " + order.getPayAmount());
        assertAmount("338", order.getPayAmount());
        assertAmount("40", order.getCouponAmount());
        assertAmount("2", order.getIntegrationAmount());

        // 金额分摊：按单价占比分摊到各商品，求和等于券面额/积分抵扣
        assertAmount("10", orderItemList.get(0).getCouponAmount());
        assertAmount("30", orderItemList.get(1).getCouponAmount());
        assertAmount("0.5", orderItemList.get(0).getIntegrationAmount());
        assertAmount("1.5", orderItemList.get(1).getIntegrationAmount());
    }

    private static UmsMember member(long id, int integration) {
        UmsMember member = new UmsMember();
        member.setId(id);
        member.setIntegration(integration);
        return member;
    }

    private static CartPromotionItem cartItem(long productId, long skuId, String price, int quantity,
                                              String reduceAmount, int realStock) {
        CartPromotionItem item = new CartPromotionItem();
        item.setProductId(productId);
        item.setProductSkuId(skuId);
        item.setProductCategoryId(1L);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setReduceAmount(new BigDecimal(reduceAmount));
        item.setRealStock(realStock);
        item.setIntegration(0);
        item.setGrowth(0);
        return item;
    }

    private static SmsCouponHistoryDetail couponDetail(long couponId, int useType, String amount) {
        SmsCoupon coupon = new SmsCoupon();
        coupon.setId(couponId);
        coupon.setUseType(useType);
        coupon.setAmount(new BigDecimal(amount));
        SmsCouponHistoryDetail detail = new SmsCouponHistoryDetail();
        detail.setCoupon(coupon);
        return detail;
    }

    private static UmsIntegrationConsumeSetting setting(int useUnit, int maxPercentPerOrder, int couponStatus) {
        UmsIntegrationConsumeSetting setting = new UmsIntegrationConsumeSetting();
        setting.setUseUnit(useUnit);
        setting.setMaxPercentPerOrder(maxPercentPerOrder);
        setting.setCouponStatus(couponStatus);
        return setting;
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual, "amount should not be null");
        assertTrue(actual.compareTo(new BigDecimal(expected)) == 0,
                "expected " + expected + " but was " + actual);
    }
}
