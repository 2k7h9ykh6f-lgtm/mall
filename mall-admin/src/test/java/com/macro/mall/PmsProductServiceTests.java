package com.macro.mall;

import com.github.pagehelper.PageHelper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.service.impl.PmsProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 商品查询增强功能单元测试
 * <p>
 * 使用 Mockito 模拟 PmsProductMapper，验证 PmsProductServiceImpl.list()
 * 方法正确构建 PmsProductExample 的查询条件和排序逻辑。
 */
@ExtendWith(MockitoExtension.class)
public class PmsProductServiceTests {

    @InjectMocks
    private PmsProductServiceImpl productService;

    @Mock
    private PmsProductMapper productMapper;

    private ArgumentCaptor<PmsProductExample> exampleCaptor;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void setUp() {
        exampleCaptor = ArgumentCaptor.forClass(PmsProductExample.class);
        // 让 selectByExample 返回空列表，PageHelper 不会实际生效（无 MyBatis 拦截器）
        when(productMapper.selectByExample(any(PmsProductExample.class)))
                .thenReturn(new ArrayList<>());
    }

    // ===== 原有功能回归测试 =====

    @Test
    void testListWithNoNewParams_backwardCompatible() {
        // 不传新参数，验证原有行为不变
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPublishStatus(1);
        param.setVerifyStatus(1);
        param.setKeyword("iPhone");
        param.setProductSn("SN001");
        param.setBrandId(49L);
        param.setProductCategoryId(7L);

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        PmsProductExample example = exampleCaptor.getValue();
        PmsProductExample.Criteria criteria = example.getOredCriteria().get(0);
        List<String> conditions = extractConditions(criteria);

        // 原有条件全部存在
        assertConditionExists(conditions, "delete_status =");
        assertConditionExists(conditions, "publish_status =");
        assertConditionExists(conditions, "verify_status =");
        assertConditionExists(conditions, "name like");
        assertConditionExists(conditions, "product_sn =");
        assertConditionExists(conditions, "brand_id =");
        assertConditionExists(conditions, "product_category_id =");

        // 新增条件均不存在
        assertConditionNotExists(conditions, "price >=");
        assertConditionNotExists(conditions, "price <=");
        assertConditionNotExists(conditions, "create_time");

        // 默认排序为 id desc
        assertEquals("id desc", example.getOrderByClause());
    }

    @Test
    void testListWithEmptyParams_usesDefaultSort() {
        PmsProductQueryParam param = new PmsProductQueryParam();

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        PmsProductExample example = exampleCaptor.getValue();
        assertEquals("id desc", example.getOrderByClause());

        // 仅有 deleteStatus = 0 这一个条件
        List<String> conditions = extractConditions(example.getOredCriteria().get(0));
        assertEquals(1, conditions.size());
        assertTrue(conditions.get(0).contains("delete_status ="));
    }

    // ===== 价格区间筛选测试 =====

    @Test
    void testListWithMinPriceOnly() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("100"));

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "price >=");
        assertConditionNotExists(conditions, "price <=");
    }

    @Test
    void testListWithMaxPriceOnly() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMaxPrice(new BigDecimal("500"));

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionNotExists(conditions, "price >=");
        assertConditionExists(conditions, "price <=");
    }

    @Test
    void testListWithPriceRange() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("100"));
        param.setMaxPrice(new BigDecimal("500"));

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "price >=");
        assertConditionExists(conditions, "price <=");
    }

    // ===== 创建时间范围筛选测试 =====

    @Test
    void testListWithCreateTimeRange() throws ParseException {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setBeginCreateTime(SDF.parse("2024-01-01 00:00:00"));
        param.setEndCreateTime(SDF.parse("2024-12-31 23:59:59"));

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "create_time >=");
        assertConditionExists(conditions, "create_time <=");
    }

    @Test
    void testListWithBeginCreateTimeOnly() throws ParseException {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setBeginCreateTime(SDF.parse("2024-06-01 00:00:00"));

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "create_time >=");
        assertConditionNotExists(conditions, "create_time <=");
    }

    // ===== 库存状态筛选测试 =====

    @Test
    void testListWithStockStatus_inStock() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockStatus(0);

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "(stock > 0 AND (low_stock IS NULL OR stock > low_stock))");
    }

    @Test
    void testListWithStockStatus_lowStock() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockStatus(1);

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "(stock > 0 AND low_stock IS NOT NULL AND stock <= low_stock)");
    }

    @Test
    void testListWithStockStatus_outOfStock() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockStatus(2);

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        assertConditionExists(conditions, "(stock = 0 OR stock IS NULL)");
    }

    @Test
    void testListWithInvalidStockStatus_ignored() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockStatus(99);

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        List<String> conditions = extractConditions(exampleCaptor.getValue().getOredCriteria().get(0));
        // 无效的 stockStatus 不会添加任何库存相关条件
        assertConditionNotExists(conditions, "stock");
    }

    // ===== 排序测试 =====

    @Test
    void testListSortByPrice() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy("price");

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        assertEquals("price desc, id desc", exampleCaptor.getValue().getOrderByClause());
    }

    @Test
    void testListSortByCreateTime() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy("createTime");

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        assertEquals("create_time desc, id desc", exampleCaptor.getValue().getOrderByClause());
    }

    @Test
    void testListSortBySale() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy("sale");

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        assertEquals("sale desc, id desc", exampleCaptor.getValue().getOrderByClause());
    }

    @Test
    void testListSortByInvalidValue_defaultSort() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setSortBy("invalidField");

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        assertEquals("id desc", exampleCaptor.getValue().getOrderByClause());
    }

    @Test
    void testListWithNullSortBy_defaultSort() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        // sortBy 默认就是 null

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        assertEquals("id desc", exampleCaptor.getValue().getOrderByClause());
    }

    // ===== 组合筛选测试 =====

    @Test
    void testListCombinedFilters() throws ParseException {
        // 同时使用所有新旧参数
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setPublishStatus(1);
        param.setVerifyStatus(1);
        param.setKeyword("Nike");
        param.setProductSn("NK001");
        param.setBrandId(49L);
        param.setProductCategoryId(19L);
        param.setMinPrice(new BigDecimal("200"));
        param.setMaxPrice(new BigDecimal("1000"));
        param.setBeginCreateTime(SDF.parse("2024-01-01 00:00:00"));
        param.setEndCreateTime(SDF.parse("2024-12-31 23:59:59"));
        param.setStockStatus(0);
        param.setSortBy("sale");

        productService.list(param, 10, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        PmsProductExample example = exampleCaptor.getValue();
        List<String> conditions = extractConditions(example.getOredCriteria().get(0));

        // 验证所有条件都正确生成
        assertConditionExists(conditions, "delete_status =");
        assertConditionExists(conditions, "publish_status =");
        assertConditionExists(conditions, "verify_status =");
        assertConditionExists(conditions, "name like");
        assertConditionExists(conditions, "product_sn =");
        assertConditionExists(conditions, "brand_id =");
        assertConditionExists(conditions, "product_category_id =");
        assertConditionExists(conditions, "price >=");
        assertConditionExists(conditions, "price <=");
        assertConditionExists(conditions, "create_time >=");
        assertConditionExists(conditions, "create_time <=");
        assertConditionExists(conditions, "(stock > 0 AND (low_stock IS NULL OR stock > low_stock))");

        // 验证排序
        assertEquals("sale desc, id desc", example.getOrderByClause());
    }

    @Test
    void testListCombined_priceRangeAndSort() {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setMinPrice(new BigDecimal("50"));
        param.setMaxPrice(new BigDecimal("200"));
        param.setSortBy("price");

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        PmsProductExample example = exampleCaptor.getValue();
        List<String> conditions = extractConditions(example.getOredCriteria().get(0));

        assertConditionExists(conditions, "price >=");
        assertConditionExists(conditions, "price <=");
        assertEquals("price desc, id desc", example.getOrderByClause());
    }

    @Test
    void testListCombined_stockStatusAndCreateTime() throws ParseException {
        PmsProductQueryParam param = new PmsProductQueryParam();
        param.setStockStatus(2);
        param.setBeginCreateTime(SDF.parse("2024-06-01 00:00:00"));
        param.setSortBy("createTime");

        productService.list(param, 5, 1);

        verify(productMapper).selectByExample(exampleCaptor.capture());
        PmsProductExample example = exampleCaptor.getValue();
        List<String> conditions = extractConditions(example.getOredCriteria().get(0));

        assertConditionExists(conditions, "(stock = 0 OR stock IS NULL)");
        assertConditionExists(conditions, "create_time >=");
        assertConditionNotExists(conditions, "create_time <=");
        assertEquals("create_time desc, id desc", example.getOrderByClause());
    }

    // ===== 辅助方法 =====

    /**
     * 从 Criteria 中提取所有 condition 字符串列表
     */
    private List<String> extractConditions(PmsProductExample.Criteria criteria) {
        return criteria.getAllCriteria().stream()
                .map(PmsProductExample.Criterion::getCondition)
                .collect(Collectors.toList());
    }

    private void assertConditionExists(List<String> conditions, String expected) {
        assertTrue(
                conditions.stream().anyMatch(c -> c.contains(expected)),
                "Expected condition containing '" + expected + "' but got: " + conditions
        );
    }

    private void assertConditionNotExists(List<String> conditions, String unexpected) {
        assertFalse(
                conditions.stream().anyMatch(c -> c.contains(unexpected)),
                "Did not expect condition containing '" + unexpected + "' but found one in: " + conditions
        );
    }
}
