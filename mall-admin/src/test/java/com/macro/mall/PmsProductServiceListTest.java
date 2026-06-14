package com.macro.mall;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * 商品查询条件构建单元测试。
 * 通过 Mockito 捕获传给 {@link PmsProductMapper#selectByExample} 的 {@link PmsProductExample}，
 * 校验筛选条件与排序子句，无需连接数据库。
 */
@ExtendWith(MockitoExtension.class)
public class PmsProductServiceListTest {

    @Mock
    private PmsProductMapper productMapper;

    @InjectMocks
    private PmsProductServiceImpl productService;

    @AfterEach
    public void clearPage() {
        // list() 调用了 PageHelper.startPage 会写入 ThreadLocal，单测中拦截器不会执行，需手动清理避免泄漏到其他用例。
        PageHelper.clearPage();
    }

    /**
     * 执行一次 list 查询并捕获生成的 Example。
     */
    private PmsProductExample executeAndCapture(PmsProductQueryParam param) {
        productService.list(param, 5, 1);
        ArgumentCaptor<PmsProductExample> captor = ArgumentCaptor.forClass(PmsProductExample.class);
        verify(productMapper).selectByExample(captor.capture());
        return captor.getValue();
    }

    private List<PmsProductExample.Criterion> allCriteria(PmsProductExample example) {
        List<PmsProductExample.Criterion> result = new ArrayList<>();
        for (PmsProductExample.Criteria criteria : example.getOredCriteria()) {
            result.addAll(criteria.getAllCriteria());
        }
        return result;
    }

    private boolean hasCondition(PmsProductExample example, String condition) {
        return allCriteria(example).stream().anyMatch(c -> condition.equals(c.getCondition()));
    }

    private Object valueOf(PmsProductExample example, String condition) {
        return allCriteria(example).stream()
                .filter(c -> condition.equals(c.getCondition()))
                .map(PmsProductExample.Criterion::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 不传任何新增参数时：行为与原接口一致——只过滤未删除商品、无任何价格/时间/库存条件、无排序子句。
     */
    @Test
    public void testDefaultQueryPreservesOriginalBehavior() {
        PmsProductExample example = executeAndCapture(new PmsProductQueryParam());

        assertNull(example.getOrderByClause(), "默认查询不应设置排序子句");
        assertTrue(hasCondition(example, "delete_status ="), "默认仍应过滤未删除商品");
        assertEquals(0, valueOf(example, "delete_status ="));
        // 不应引入任何新增筛选条件
        assertFalse(hasCondition(example, "price >="));
        assertFalse(hasCondition(example, "price <="));
        assertFalse(hasCondition(example, "create_time >="));
        assertFalse(hasCondition(example, "create_time <="));
        assertFalse(hasCondition(example, "stock >"));
        assertFalse(hasCondition(example, "stock <="));
    }

    /**
     * 组合筛选：价格区间 + 创建时间区间 + 库存状态 + 原有的上架/审核/关键字条件应全部生效。
     */
    @Test
    public void testCombinedFilters() {
        BigDecimal min = new BigDecimal("100");
        BigDecimal max = new BigDecimal("500");
        Date begin = new Date(1_700_000_000_000L);
        Date end = new Date(1_700_864_000_000L);

        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPublishStatus(1);
        param.setVerifyStatus(1);
        param.setKeyword("耳机");
        param.setBrandId(6L);
        param.setMinPrice(min);
        param.setMaxPrice(max);
        param.setBeginCreateTime(begin);
        param.setEndCreateTime(end);
        param.setStockStatus(1);

        PmsProductExample example = executeAndCapture(param);

        assertEquals(min, valueOf(example, "price >="));
        assertEquals(max, valueOf(example, "price <="));
        assertEquals(begin, valueOf(example, "create_time >="));
        assertEquals(end, valueOf(example, "create_time <="));
        assertEquals(0, valueOf(example, "stock >"), "库存状态1(有货)应转为 stock > 0");
        assertEquals(1, valueOf(example, "publish_status ="));
        assertEquals(1, valueOf(example, "verify_status ="));
        assertEquals("%耳机%", valueOf(example, "name like"));
        assertEquals(6L, valueOf(example, "brand_id ="));
        // 未指定排序，保持默认顺序
        assertNull(example.getOrderByClause());
    }

    /**
     * 库存状态 0(缺货) 应转换为 stock <= 0，且不产生 stock > 条件。
     */
    @Test
    public void testStockStatusOutOfStock() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockStatus(0);

        PmsProductExample example = executeAndCapture(param);

        assertTrue(hasCondition(example, "stock <="));
        assertEquals(0, valueOf(example, "stock <="));
        assertFalse(hasCondition(example, "stock >"));
    }

    /**
     * 只传 minPrice 时只生成下界条件，不应生成上界条件。
     */
    @Test
    public void testPriceLowerBoundOnly() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("9.90"));

        PmsProductExample example = executeAndCapture(param);

        assertTrue(hasCondition(example, "price >="));
        assertFalse(hasCondition(example, "price <="));
    }

    @Test
    public void testSortBySaleDesc() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy(1);
        assertEquals("sale desc", executeAndCapture(param).getOrderByClause());
    }

    @Test
    public void testSortByCreateTimeDesc() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy(2);
        assertEquals("create_time desc", executeAndCapture(param).getOrderByClause());
    }

    @Test
    public void testSortByPriceAscThenDesc() {
        PmsProductQueryParam ascParam = new PmsProductQueryParam();
        ascParam.setSortBy(3);
        assertEquals("price asc", executeAndCapture(ascParam).getOrderByClause());
    }

    @Test
    public void testSortByPriceDesc() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy(4);
        assertEquals("price desc", executeAndCapture(param).getOrderByClause());
    }

    /**
     * 未知排序值应被忽略，保持默认顺序（无排序子句），从而不破坏原有行为。
     */
    @Test
    public void testUnknownSortByKeepsDefaultOrder() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy(99);
        assertNull(executeAndCapture(param).getOrderByClause());
    }
}
