package com.macro.mall.portal.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.*;
import com.macro.mall.model.*;
import com.macro.mall.portal.dao.HomeDao;
import com.macro.mall.portal.domain.FlashPromotionProduct;
import com.macro.mall.portal.domain.HomeContentResult;
import com.macro.mall.portal.domain.HomeFlashPromotion;
import com.macro.mall.portal.service.HomeService;
import com.macro.mall.portal.util.DateUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 首页内容管理Service实现类
 * Created by macro on 2019/1/28.
 */
@Service
public class HomeServiceImpl implements HomeService {
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 4;
    /**
     * 推荐商品单页最大数量，防止超大分页拖垮数据库
     */
    static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SmsHomeAdvertiseMapper advertiseMapper;
    @Autowired
    private HomeDao homeDao;
    @Autowired
    private SmsFlashPromotionMapper flashPromotionMapper;
    @Autowired
    private SmsFlashPromotionSessionMapper promotionSessionMapper;
    @Autowired
    private PmsProductMapper productMapper;
    @Autowired
    private PmsProductCategoryMapper productCategoryMapper;
    @Autowired
    private CmsSubjectMapper subjectMapper;

    @Override
    public HomeContentResult content() {
        HomeContentResult result = new HomeContentResult();
        //获取首页广告
        result.setAdvertiseList(getHomeAdvertiseList());
        //获取推荐品牌
        result.setBrandList(homeDao.getRecommendBrandList(0,6));
        //获取秒杀信息
        result.setHomeFlashPromotion(getHomeFlashPromotion());
        //获取新品推荐
        result.setNewProductList(homeDao.getNewProductList(0,4));
        //获取人气推荐
        result.setHotProductList(homeDao.getHotProductList(0,4));
        //获取推荐专题
        result.setSubjectList(homeDao.getRecommendSubjectList(0,4));
        return result;
    }

    @Override
    public List<PmsProduct> recommendProductList(Integer pageSize, Integer pageNum,
                                                 Long productCategoryId, Long brandId,
                                                 BigDecimal minPrice, BigDecimal maxPrice,
                                                 String sortBy) {
        int resolvedPageNum = pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        validateRecommendParams(resolvedPageNum, resolvedPageSize, minPrice, maxPrice);
        String orderByClause = resolveRecommendOrderBy(sortBy);

        PageHelper.startPage(resolvedPageNum, resolvedPageSize);
        PmsProductExample example = new PmsProductExample();
        PmsProductExample.Criteria criteria = example.createCriteria();
        criteria.andDeleteStatusEqualTo(0).andPublishStatusEqualTo(1);
        if (productCategoryId != null) {
            criteria.andProductCategoryIdEqualTo(productCategoryId);
        }
        if (brandId != null) {
            criteria.andBrandIdEqualTo(brandId);
        }
        if (minPrice != null) {
            criteria.andPriceGreaterThanOrEqualTo(minPrice);
        }
        if (maxPrice != null) {
            criteria.andPriceLessThanOrEqualTo(maxPrice);
        }
        if (orderByClause != null) {
            example.setOrderByClause(orderByClause);
        }
        return productMapper.selectByExample(example);
    }

    /**
     * 校验推荐商品的分页与价格区间参数，非法时抛出{@link com.macro.mall.common.exception.ApiException}。
     * 包级私有静态方法，便于无需Spring上下文的单元测试。
     */
    static void validateRecommendParams(int pageNum, int pageSize, BigDecimal minPrice, BigDecimal maxPrice) {
        if (pageNum < 1) {
            Asserts.fail("pageNum不能小于1");
        }
        if (pageSize < 1) {
            Asserts.fail("pageSize不能小于1");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            Asserts.fail("pageSize不能大于" + MAX_PAGE_SIZE);
        }
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            Asserts.fail("minPrice不能小于0");
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            Asserts.fail("maxPrice不能小于0");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            Asserts.fail("minPrice不能大于maxPrice");
        }
    }

    /**
     * 将排序策略映射为order by子句，仅允许白名单取值（避免SQL注入）。
     * 为空返回null（不排序，保持原有行为）；未知取值抛出异常。
     * 包级私有静态方法，便于无需Spring上下文的单元测试。
     */
    static String resolveRecommendOrderBy(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return null;
        }
        switch (sortBy.trim()) {
            case "latest":
                return "id desc";
            case "sale":
                return "sale desc";
            case "priceAsc":
                return "price asc";
            case "priceDesc":
                return "price desc";
            default:
                Asserts.fail("不支持的排序方式: " + sortBy);
                return null;
        }
    }

    @Override
    public List<PmsProductCategory> getProductCateList(Long parentId) {
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.createCriteria()
                .andShowStatusEqualTo(1)
                .andParentIdEqualTo(parentId);
        example.setOrderByClause("sort desc");
        return productCategoryMapper.selectByExample(example);
    }

    @Override
    public List<CmsSubject> getSubjectList(Long cateId, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum,pageSize);
        CmsSubjectExample example = new CmsSubjectExample();
        CmsSubjectExample.Criteria criteria = example.createCriteria();
        criteria.andShowStatusEqualTo(1);
        if(cateId!=null){
            criteria.andCategoryIdEqualTo(cateId);
        }
        return subjectMapper.selectByExample(example);
    }

    @Override
    public List<PmsProduct> hotProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getHotProductList(offset, pageSize);
    }

    @Override
    public List<PmsProduct> newProductList(Integer pageNum, Integer pageSize) {
        int offset = pageSize * (pageNum - 1);
        return homeDao.getNewProductList(offset, pageSize);
    }

    private HomeFlashPromotion getHomeFlashPromotion() {
        HomeFlashPromotion homeFlashPromotion = new HomeFlashPromotion();
        //获取当前秒杀活动
        Date now = new Date();
        SmsFlashPromotion flashPromotion = getFlashPromotion(now);
        if (flashPromotion != null) {
            //获取当前秒杀场次
            SmsFlashPromotionSession flashPromotionSession = getFlashPromotionSession(now);
            if (flashPromotionSession != null) {
                homeFlashPromotion.setStartTime(flashPromotionSession.getStartTime());
                homeFlashPromotion.setEndTime(flashPromotionSession.getEndTime());
                //获取下一个秒杀场次
                SmsFlashPromotionSession nextSession = getNextFlashPromotionSession(homeFlashPromotion.getStartTime());
                if(nextSession!=null){
                    homeFlashPromotion.setNextStartTime(nextSession.getStartTime());
                    homeFlashPromotion.setNextEndTime(nextSession.getEndTime());
                }
                //获取秒杀商品
                List<FlashPromotionProduct> flashProductList = homeDao.getFlashProductList(flashPromotion.getId(), flashPromotionSession.getId());
                homeFlashPromotion.setProductList(flashProductList);
            }
        }
        return homeFlashPromotion;
    }

    //获取下一个场次信息
    private SmsFlashPromotionSession getNextFlashPromotionSession(Date date) {
        SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
        sessionExample.createCriteria()
                .andStartTimeGreaterThan(date);
        sessionExample.setOrderByClause("start_time asc");
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(sessionExample);
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }

    private List<SmsHomeAdvertise> getHomeAdvertiseList() {
        SmsHomeAdvertiseExample example = new SmsHomeAdvertiseExample();
        example.createCriteria().andTypeEqualTo(1).andStatusEqualTo(1);
        example.setOrderByClause("sort desc");
        return advertiseMapper.selectByExample(example);
    }

    //根据时间获取秒杀活动
    private SmsFlashPromotion getFlashPromotion(Date date) {
        Date currDate = DateUtil.getDate(date);
        SmsFlashPromotionExample example = new SmsFlashPromotionExample();
        example.createCriteria()
                .andStatusEqualTo(1)
                .andStartDateLessThanOrEqualTo(currDate)
                .andEndDateGreaterThanOrEqualTo(currDate);
        List<SmsFlashPromotion> flashPromotionList = flashPromotionMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(flashPromotionList)) {
            return flashPromotionList.get(0);
        }
        return null;
    }

    //根据时间获取秒杀场次
    private SmsFlashPromotionSession getFlashPromotionSession(Date date) {
        Date currTime = DateUtil.getTime(date);
        SmsFlashPromotionSessionExample sessionExample = new SmsFlashPromotionSessionExample();
        sessionExample.createCriteria()
                .andStartTimeLessThanOrEqualTo(currTime)
                .andEndTimeGreaterThanOrEqualTo(currTime);
        List<SmsFlashPromotionSession> promotionSessionList = promotionSessionMapper.selectByExample(sessionExample);
        if (!CollectionUtils.isEmpty(promotionSessionList)) {
            return promotionSessionList.get(0);
        }
        return null;
    }
}
