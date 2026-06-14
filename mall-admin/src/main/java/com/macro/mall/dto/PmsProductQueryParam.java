package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品查询参数
 * Created by macro on 2018/4/27.
 */
@Data
@EqualsAndHashCode
public class PmsProductQueryParam {
    @Schema(title = "上架状态")
    private Integer publishStatus;
    @Schema(title = "审核状态")
    private Integer verifyStatus;
    @Schema(title = "商品名称模糊关键字")
    private String keyword;
    @Schema(title = "商品货号")
    private String productSn;
    @Schema(title = "商品分类编号")
    private Long productCategoryId;
    @Schema(title = "商品品牌编号")
    private Long brandId;
    @Schema(title = "最低价格")
    private BigDecimal minPrice;
    @Schema(title = "最高价格")
    private BigDecimal maxPrice;
    @Schema(title = "创建时间区间起始")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date beginCreateTime;
    @Schema(title = "创建时间区间结束")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endCreateTime;
    @Schema(title = "库存状态：0->缺货；1->有货")
    private Integer stockStatus;
    @Schema(title = "排序方式：1->销量从高到低；2->创建时间从新到旧；3->价格从低到高；4->价格从高到低")
    private Integer sortBy;
}
