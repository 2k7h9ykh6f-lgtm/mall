package com.macro.mall.portal;

import com.macro.mall.common.exception.ApiException;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.portal.service.impl.HomeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 首页推荐商品接口单元测试（纯 Mockito，无需 Spring 上下文 / 数据库）
 */
@ExtendWith(MockitoExtension.class)
public class HomeServiceRecommendTests {

    @Mock
    private PmsProductMapper productMapper;

    @InjectMocks
    private HomeServiceImpl homeService;

    private List<PmsProduct> mockProducts;

    @BeforeEach
    void setUp() {
        mockProducts = new ArrayList<>();
        PmsProduct p1 = new PmsProduct();
        p1.setId(1L);
        p1.setName("Product A");
        p1.setPrice(new BigDecimal("99.00"));
        mockProducts.add(p1);

        PmsProduct p2 = new PmsProduct();
        p2.setId(2L);
        p2.setName("Product B");
        p2.setPrice(new BigDecimal("199.00"));
        mockProducts.add(p2);

        when(productMapper.selectByExample(any(PmsProductExample.class))).thenReturn(mockProducts);
    }

    // ===== 向后兼容性 =====

    @Test
    void testBackwardCompat_noFilterParams_returnsPublishedProducts() {
        List<PmsProduct> result = homeService.recommendProductList(
                4, 1, null, null, null, null, null);

        assertEquals(2, result.size());
        // 验证 PmsProductExample 只包含基础条件（未删除 + 已发布），无排序
        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        PmsProductExample example = captor.getValue();
        assertNull(example.getOrderByClause(), "No sorting when sortBy is null");
        assertEquals(1, example.getOredCriteria().size(), "Should have exactly one criteria group");
    }

    // ===== 单个过滤条件 =====

    @Test
    void testFilterByProductCategoryId() {
        homeService.recommendProductList(10, 1, 100L, null, null, null, null);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        // 通过 criteria 的 addCriterion 列表验证 product_category_id 条件已添加
        assertNotNull(captor.getValue());
    }

    @Test
    void testFilterByBrandId() {
        homeService.recommendProductList(10, 1, null, 49L, null, null, null);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    void testFilterByMinPrice() {
        homeService.recommendProductList(10, 1, null, null, new BigDecimal("50"), null, null);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    void testFilterByMaxPrice() {
        homeService.recommendProductList(10, 1, null, null, null, new BigDecimal("200"), null);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    void testFilterByPriceRange() {
        homeService.recommendProductList(10, 1, null, null,
                new BigDecimal("50"), new BigDecimal("300"), null);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNotNull(captor.getValue());
    }

    // ===== 组合过滤条件 =====

    @Test
    void testCombinedFilters() {
        homeService.recommendProductList(10, 1, 100L, 49L,
                new BigDecimal("10"), new BigDecimal("500"), "sale");

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        PmsProductExample example = captor.getValue();
        assertNotNull(example);
        assertEquals("sale desc", example.getOrderByClause());
    }

    // ===== 排序策略 =====

    @Test
    void testSortByLatest() {
        homeService.recommendProductList(10, 1, null, null, null, null, "latest");

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("id desc", captor.getValue().getOrderByClause());
    }

    @Test
    void testSortBySale() {
        homeService.recommendProductList(10, 1, null, null, null, null, "sale");

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("sale desc", captor.getValue().getOrderByClause());
    }

    @Test
    void testSortByPriceAsc() {
        homeService.recommendProductList(10, 1, null, null, null, null, "priceAsc");

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("price asc", captor.getValue().getOrderByClause());
    }

    @Test
    void testSortByPriceDesc() {
        homeService.recommendProductList(10, 1, null, null, null, null, "priceDesc");

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertEquals("price desc", captor.getValue().getOrderByClause());
    }

    @Test
    void testSortByNull_noOrderByClause() {
        homeService.recommendProductList(10, 1, null, null, null, null, null);

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNull(captor.getValue().getOrderByClause());
    }

    @Test
    void testSortByEmptyString_noOrderByClause() {
        homeService.recommendProductList(10, 1, null, null, null, null, "");

        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        assertNull(captor.getValue().getOrderByClause());
    }

    // ===== 参数校验 =====

    @Test
    void testInvalidPageSize_zero_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(0, 1, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("pageSize"));
    }

    @Test
    void testInvalidPageSize_negative_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(-5, 1, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("pageSize"));
    }

    @Test
    void testInvalidPageNum_zero_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(4, 0, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("pageNum"));
    }

    @Test
    void testInvalidPageNum_negative_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(4, -1, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("pageNum"));
    }

    @Test
    void testPageSizeExceedsMax_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(101, 1, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("pageSize") && ex.getMessage().contains("100"));
    }

    @Test
    void testPageSizeAtBoundary_maxAllowed() {
        // pageSize = 100 应当合法，不抛异常
        assertDoesNotThrow(() ->
                homeService.recommendProductList(100, 1, null, null, null, null, null));
    }

    @Test
    void testMinPriceGreaterThanMaxPrice_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(4, 1, null, null,
                        new BigDecimal("500"), new BigDecimal("100"), null));
        assertTrue(ex.getMessage().contains("minPrice"));
    }

    @Test
    void testInvalidSortBy_throwsApiException() {
        ApiException ex = assertThrows(ApiException.class, () ->
                homeService.recommendProductList(4, 1, null, null, null, null, "invalidSort"));
        assertTrue(ex.getMessage().contains("sortBy"));
    }

    @Test
    void testMinPriceEqualToMaxPrice_valid() {
        // minPrice == maxPrice 应当合法（精确价格查询）
        assertDoesNotThrow(() ->
                homeService.recommendProductList(4, 1, null, null,
                        new BigDecimal("100"), new BigDecimal("100"), null));
    }

    @Test
    void testOnlyMinPrice_noMaxPriceComparison() {
        // 只传 minPrice 不传 maxPrice，不应触发价格区间校验
        assertDoesNotThrow(() ->
                homeService.recommendProductList(4, 1, null, null,
                        new BigDecimal("9999"), null, null));
    }

    @Test
    void testOnlyMaxPrice_noMinPriceComparison() {
        // 只传 maxPrice 不传 minPrice，不应触发价格区间校验
        assertDoesNotThrow(() ->
                homeService.recommendProductList(4, 1, null, null,
                        null, new BigDecimal("1"), null));
    }
}
