package com.macro.mall.portal.service.impl;

import com.macro.mall.common.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 首页推荐商品的参数校验与排序映射单元测试。
 * 仅测试纯逻辑，不依赖Spring上下文与数据库，任何环境均可运行。
 */
class HomeServiceRecommendTests {

    @Test
    void resolveRecommendOrderBy_mapsWhitelistedStrategies() {
        assertEquals("id desc", HomeServiceImpl.resolveRecommendOrderBy("latest"));
        assertEquals("sale desc", HomeServiceImpl.resolveRecommendOrderBy("sale"));
        assertEquals("price asc", HomeServiceImpl.resolveRecommendOrderBy("priceAsc"));
        assertEquals("price desc", HomeServiceImpl.resolveRecommendOrderBy("priceDesc"));
        // 前后空白应被忽略
        assertEquals("price asc", HomeServiceImpl.resolveRecommendOrderBy("  priceAsc  "));
    }

    @Test
    void resolveRecommendOrderBy_returnsNullForBlank() {
        // 兼容原有行为：不传排序则不追加order by
        assertNull(HomeServiceImpl.resolveRecommendOrderBy(null));
        assertNull(HomeServiceImpl.resolveRecommendOrderBy(""));
        assertNull(HomeServiceImpl.resolveRecommendOrderBy("   "));
    }

    @Test
    void resolveRecommendOrderBy_throwsForUnknownStrategy() {
        assertThrows(ApiException.class, () -> HomeServiceImpl.resolveRecommendOrderBy("foo"));
        // 列名不在白名单内也应拒绝
        assertThrows(ApiException.class, () -> HomeServiceImpl.resolveRecommendOrderBy("price"));
    }

    @Test
    void validateRecommendParams_passesForValidInput() {
        assertDoesNotThrow(() -> HomeServiceImpl.validateRecommendParams(1, 4, null, null));
        // 边界：pageSize 等于上限、minPrice 等于 maxPrice
        assertDoesNotThrow(() -> HomeServiceImpl.validateRecommendParams(
                2, HomeServiceImpl.MAX_PAGE_SIZE, new BigDecimal("100"), new BigDecimal("100")));
        // 仅传价格区间一侧
        assertDoesNotThrow(() -> HomeServiceImpl.validateRecommendParams(1, 4, new BigDecimal("10"), null));
        assertDoesNotThrow(() -> HomeServiceImpl.validateRecommendParams(1, 4, null, new BigDecimal("10")));
        // 价格为0合法
        assertDoesNotThrow(() -> HomeServiceImpl.validateRecommendParams(1, 4, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void validateRecommendParams_throwsForInvalidPaging() {
        assertThrows(ApiException.class, () -> HomeServiceImpl.validateRecommendParams(0, 4, null, null));
        assertThrows(ApiException.class, () -> HomeServiceImpl.validateRecommendParams(1, 0, null, null));
        assertThrows(ApiException.class,
                () -> HomeServiceImpl.validateRecommendParams(1, HomeServiceImpl.MAX_PAGE_SIZE + 1, null, null));
    }

    @Test
    void validateRecommendParams_throwsForInvalidPriceRange() {
        assertThrows(ApiException.class, () -> HomeServiceImpl.validateRecommendParams(1, 4, new BigDecimal("-1"), null));
        assertThrows(ApiException.class, () -> HomeServiceImpl.validateRecommendParams(1, 4, null, new BigDecimal("-1")));
        assertThrows(ApiException.class,
                () -> HomeServiceImpl.validateRecommendParams(1, 4, new BigDecimal("5000"), new BigDecimal("1000")));
    }
}
