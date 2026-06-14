package com.macro.mall.portal.service;

import com.macro.mall.model.CmsSubject;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductCategory;
import com.macro.mall.portal.domain.HomeContentResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页内容管理Service
 * Created by macro on 2019/1/28.
 */
public interface HomeService {

    /**
     * 获取首页内容
     */
    HomeContentResult content();

    /**
     * 首页商品推荐
     * @param pageSize          每页数量
     * @param pageNum           页码
     * @param productCategoryId 商品分类id（可选）
     * @param brandId           品牌id（可选）
     * @param minPrice          最低价格（可选，包含）
     * @param maxPrice          最高价格（可选，包含）
     * @param sortBy            排序策略（可选）：latest、sale、priceAsc、priceDesc；为空时不排序
     */
    List<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum,
                                          Long productCategoryId, Long brandId,
                                          BigDecimal minPrice, BigDecimal maxPrice,
                                          String sortBy);

    /**
     * 获取商品分类
     * @param parentId 0:获取一级分类；其他：获取指定二级分类
     */
    List<PmsProductCategory> getProductCateList(Long parentId);

    /**
     * 根据专题分类分页获取专题
     * @param cateId 专题分类id
     */
    List<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum);

    /**
     * 分页获取人气推荐商品
     */
    List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize);

    /**
     * 分页获取新品推荐商品
     */
    List<PmsProduct> newProductList(Integer pageNum, Integer pageSize);
}
