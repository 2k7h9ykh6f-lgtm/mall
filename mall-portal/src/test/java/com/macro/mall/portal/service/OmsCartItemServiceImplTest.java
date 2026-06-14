package com.macro.mall.portal.service;

import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.dto.CartItemResult;
import com.macro.mall.portal.exception.CartValidationException;
import com.macro.mall.portal.service.impl.OmsCartItemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 购物车Service单元测试
 */
@ExtendWith(MockitoExtension.class)
class OmsCartItemServiceImplTest {

    @Mock
    private OmsCartItemMapper cartItemMapper;

    @Mock
    private PortalProductDao productDao;

    @Mock
    private OmsPromotionService promotionService;

    @Mock
    private UmsMemberService memberService;

    @InjectMocks
    private OmsCartItemServiceImpl cartItemService;

    private UmsMember mockMember;

    @BeforeEach
    void setUp() {
        mockMember = new UmsMember();
        mockMember.setId(1L);
        mockMember.setNickname("测试用户");
    }

    // ========== 辅助方法 ==========

    /**
     * 构建测试用CartProduct
     */
    private CartProduct buildCartProduct(Long productId, Integer publishStatus, List<PmsSkuStock> skuStocks) {
        CartProduct product = new CartProduct();
        product.setId(productId);
        product.setPublishStatus(publishStatus);
        product.setSkuStockList(skuStocks);
        return product;
    }

    /**
     * 构建测试用PmsSkuStock
     */
    private PmsSkuStock buildSkuStock(Long skuId, Long productId, Integer stock) {
        PmsSkuStock sku = new PmsSkuStock();
        sku.setId(skuId);
        sku.setProductId(productId);
        sku.setStock(stock);
        return sku;
    }

    /**
     * 构建测试用OmsCartItem
     */
    private OmsCartItem buildCartItem(Long productId, Long skuId, Integer quantity) {
        OmsCartItem item = new OmsCartItem();
        item.setProductId(productId);
        item.setProductSkuId(skuId);
        item.setQuantity(quantity);
        return item;
    }

    // ========== add 方法测试 ==========

    @Test
    @DisplayName("add: 商品已下架时应抛出异常")
    void add_productOffShelf_throwsException() {
        OmsCartItem item = buildCartItem(1L, 10L, 1);
        CartProduct product = buildCartProduct(1L, 0, List.of(buildSkuStock(10L, 1L, 100)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(1L)).thenReturn(product);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.add(item));
        assertEquals("商品已下架", ex.getMessage());
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add: 商品不存在时应抛出异常")
    void add_productNotFound_throwsException() {
        OmsCartItem item = buildCartItem(999L, 10L, 1);

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(999L)).thenReturn(null);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.add(item));
        assertEquals("商品不存在", ex.getMessage());
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add: SKU不存在时应抛出异常")
    void add_skuNotFound_throwsException() {
        OmsCartItem item = buildCartItem(1L, 999L, 1);
        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 100)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(1L)).thenReturn(product);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.add(item));
        assertEquals("商品SKU不存在", ex.getMessage());
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add: 新商品库存不足时应抛出异常")
    void add_insufficientStockNew_throwsException() {
        OmsCartItem item = buildCartItem(1L, 10L, 5);
        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 3)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(1L)).thenReturn(product);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.add(item));
        assertTrue(ex.getMessage().contains("库存不足"));
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add: 已有商品累加后库存不足时应抛出异常")
    void add_insufficientStockExisting_throwsException() {
        // 购物车已有8件，再加5件=13件，但库存只有10件
        OmsCartItem newItem = buildCartItem(1L, 10L, 5);

        OmsCartItem existingItem = new OmsCartItem();
        existingItem.setId(100L);
        existingItem.setMemberId(1L);
        existingItem.setProductId(1L);
        existingItem.setProductSkuId(10L);
        existingItem.setQuantity(8);

        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 10)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(1L)).thenReturn(product);
        // getCartItem查询返回已存在的商品
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(List.of(existingItem));

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.add(newItem));
        assertTrue(ex.getMessage().contains("库存不足"));
        verify(cartItemMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    @DisplayName("add: 新商品库存充足时应返回库存充足提示")
    void add_validNew_stockSufficient() {
        OmsCartItem item = buildCartItem(1L, 10L, 2);
        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 50)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(1L)).thenReturn(product);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());
        when(cartItemMapper.insert(any(OmsCartItem.class))).thenReturn(1);

        CartItemResult result = cartItemService.add(item);

        assertNotNull(result);
        assertEquals("库存充足", result.getStockHint());
        assertEquals(50, result.getAvailableStock());
        assertNotNull(result.getCartItem());
        verify(cartItemMapper).insert(any(OmsCartItem.class));
    }

    @Test
    @DisplayName("add: 已有商品累加后库存充足且库存较低时应返回低库存提示")
    void add_validExisting_lowStock() {
        OmsCartItem newItem = buildCartItem(1L, 10L, 2);

        OmsCartItem existingItem = new OmsCartItem();
        existingItem.setId(100L);
        existingItem.setMemberId(1L);
        existingItem.setProductId(1L);
        existingItem.setProductSkuId(10L);
        existingItem.setQuantity(3);

        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 5)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(productDao.getCartProduct(1L)).thenReturn(product);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(List.of(existingItem));
        when(cartItemMapper.updateByPrimaryKey(any(OmsCartItem.class))).thenReturn(1);

        CartItemResult result = cartItemService.add(newItem);

        assertNotNull(result);
        assertEquals("仅剩5件，请尽快购买", result.getStockHint());
        assertEquals(5, result.getAvailableStock());
        // 验证是更新已有商品而非插入
        verify(cartItemMapper).updateByPrimaryKey(any(OmsCartItem.class));
        verify(cartItemMapper, never()).insert(any());
    }

    // ========== updateQuantity 方法测试 ==========

    @Test
    @DisplayName("updateQuantity: 购物车商品不存在时应抛出异常")
    void updateQuantity_cartItemNotFound_throwsException() {
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(Collections.emptyList());

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.updateQuantity(999L, 1L, 5));
        assertEquals("购物车商品不存在", ex.getMessage());
    }

    @Test
    @DisplayName("updateQuantity: 数量超过库存时应抛出异常")
    void updateQuantity_exceedsStock_throwsException() {
        OmsCartItem existingItem = new OmsCartItem();
        existingItem.setId(100L);
        existingItem.setMemberId(1L);
        existingItem.setProductId(1L);
        existingItem.setProductSkuId(10L);
        existingItem.setQuantity(3);

        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 5)));

        // 第一次selectByExample查询购物车商品
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(List.of(existingItem));
        when(productDao.getCartProduct(1L)).thenReturn(product);

        // 请求数量10，超过库存5
        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.updateQuantity(100L, 1L, 10));
        assertTrue(ex.getMessage().contains("库存不足"));
        verify(cartItemMapper, never()).updateByExampleSelective(any(), any());
    }

    @Test
    @DisplayName("updateQuantity: 合法更新时应返回带库存提示的结果")
    void updateQuantity_valid_returnsResult() {
        OmsCartItem existingItem = new OmsCartItem();
        existingItem.setId(100L);
        existingItem.setMemberId(1L);
        existingItem.setProductId(1L);
        existingItem.setProductSkuId(10L);
        existingItem.setQuantity(3);

        CartProduct product = buildCartProduct(1L, 1, List.of(buildSkuStock(10L, 1L, 20)));

        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class)))
                .thenReturn(List.of(existingItem));
        when(productDao.getCartProduct(1L)).thenReturn(product);
        when(cartItemMapper.updateByExampleSelective(any(OmsCartItem.class), any(OmsCartItemExample.class)))
                .thenReturn(1);

        CartItemResult result = cartItemService.updateQuantity(100L, 1L, 5);

        assertNotNull(result);
        assertEquals("库存充足", result.getStockHint());
        assertEquals(20, result.getAvailableStock());
        assertEquals(5, result.getCartItem().getQuantity());
    }

    // ========== updateAttr 方法测试 ==========

    @Test
    @DisplayName("updateAttr: 新规格对应商品已下架时应抛出异常")
    void updateAttr_productOffShelf_throwsException() {
        OmsCartItem item = buildCartItem(2L, 20L, 1);
        item.setId(100L);

        // 商品已下架
        CartProduct product = buildCartProduct(2L, 0, List.of(buildSkuStock(20L, 2L, 100)));

        when(memberService.getCurrentMember()).thenReturn(mockMember);
        when(cartItemMapper.updateByPrimaryKeySelective(any(OmsCartItem.class))).thenReturn(1);
        when(productDao.getCartProduct(2L)).thenReturn(product);
        when(cartItemMapper.selectByExample(any(OmsCartItemExample.class))).thenReturn(Collections.emptyList());

        CartValidationException ex = assertThrows(CartValidationException.class,
                () -> cartItemService.updateAttr(item));
        assertEquals("商品已下架", ex.getMessage());
    }
}
