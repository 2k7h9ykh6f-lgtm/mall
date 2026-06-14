package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.service.OmsCartItemService;
import com.macro.mall.portal.service.OmsPromotionService;
import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车管理Service实现类
 * Created by macro on 2018/8/2.
 */
@Service
public class OmsCartItemServiceImpl implements OmsCartItemService {
    @Autowired
    private OmsCartItemMapper cartItemMapper;
    @Autowired
    private PortalProductDao productDao;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;

    @Override
    public OmsCartItem add(OmsCartItem cartItem) {
        UmsMember currentMember = memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);
        OmsCartItem existCartItem = getCartItem(cartItem);
        int existQuantity = (existCartItem == null || existCartItem.getQuantity() == null) ? 0 : existCartItem.getQuantity();
        int addQuantity = cartItem.getQuantity() == null ? 0 : cartItem.getQuantity();
        //校验商品发布状态及SKU库存（按合并后的总数量校验），并在通过时写入库存提示
        int totalQuantity = existQuantity + addQuantity;
        validateProductAndStock(cartItem, totalQuantity);
        if (existCartItem == null) {
            cartItem.setCreateDate(new Date());
            cartItemMapper.insert(cartItem);
            return cartItem;
        }
        existCartItem.setModifyDate(new Date());
        existCartItem.setQuantity(totalQuantity);
        cartItemMapper.updateByPrimaryKey(existCartItem);
        existCartItem.setStockTip(cartItem.getStockTip());
        return existCartItem;
    }

    /**
     * 根据会员id,商品id和规格获取购物车中商品
     */
    private OmsCartItem getCartItem(OmsCartItem cartItem) {
        OmsCartItemExample example = new OmsCartItemExample();
        OmsCartItemExample.Criteria criteria = example.createCriteria().andMemberIdEqualTo(cartItem.getMemberId())
                .andProductIdEqualTo(cartItem.getProductId()).andDeleteStatusEqualTo(0);
        if (cartItem.getProductSkuId()!=null) {
            criteria.andProductSkuIdEqualTo(cartItem.getProductSkuId());
        }
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    /**
     * 根据购物车id和会员id获取未删除的购物车商品
     */
    private OmsCartItem getCartItemByIdAndMember(Long id, Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        List<OmsCartItem> cartItemList = cartItemMapper.selectByExample(example);
        if (!CollectionUtils.isEmpty(cartItemList)) {
            return cartItemList.get(0);
        }
        return null;
    }

    /**
     * 校验商品发布状态及SKU库存，校验通过后将库存提示写入购物车行；校验失败时抛出ApiException
     *
     * @param cartItem 包含商品id及SKU id的购物车行
     * @param quantity 期望购买的数量（合并后或修改后的总数量）
     */
    private void validateProductAndStock(OmsCartItem cartItem, int quantity) {
        if (quantity <= 0) {
            Asserts.fail("购买数量必须大于0");
        }
        PmsProduct product = productDao.getCartProductInfo(cartItem.getProductId());
        if (product == null || (product.getDeleteStatus() != null && product.getDeleteStatus() == 1)) {
            Asserts.fail("商品不存在或已删除");
        }
        if (product.getPublishStatus() == null || product.getPublishStatus() != 1) {
            Asserts.fail("商品已下架，无法添加到购物车");
        }
        Long productSkuId = cartItem.getProductSkuId();
        if (productSkuId != null) {
            PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(productSkuId);
            if (skuStock == null) {
                Asserts.fail("商品规格不存在");
            }
            int availableStock = calcAvailableStock(skuStock);
            if (availableStock < quantity) {
                Asserts.fail("商品库存不足，当前剩余 " + availableStock + " 件");
            }
            cartItem.setStockTip(buildStockTip(skuStock, availableStock));
        }
    }

    /**
     * 计算SKU可用库存（库存-锁定库存，最小为0）
     */
    private int calcAvailableStock(PmsSkuStock skuStock) {
        int stock = skuStock.getStock() == null ? 0 : skuStock.getStock();
        int lockStock = skuStock.getLockStock() == null ? 0 : skuStock.getLockStock();
        return Math.max(stock - lockStock, 0);
    }

    /**
     * 根据可用库存及预警库存生成购物车行的库存提示
     */
    private String buildStockTip(PmsSkuStock skuStock, int availableStock) {
        Integer lowStock = skuStock.getLowStock();
        if (lowStock != null && lowStock > 0 && availableStock <= lowStock) {
            return "库存紧张，仅剩 " + availableStock + " 件";
        }
        return "库存充足，当前剩余 " + availableStock + " 件";
    }

    @Override
    public List<OmsCartItem> list(Long memberId) {
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0).andMemberIdEqualTo(memberId);
        return cartItemMapper.selectByExample(example);
    }

    @Override
    public List<CartPromotionItem> listPromotion(Long memberId, List<Long> cartIds) {
        List<OmsCartItem> cartItemList = list(memberId);
        if(CollUtil.isNotEmpty(cartIds)){
            cartItemList = cartItemList.stream().filter(item->cartIds.contains(item.getId())).collect(Collectors.toList());
        }
        List<CartPromotionItem> cartPromotionItemList = new ArrayList<>();
        if(!CollectionUtils.isEmpty(cartItemList)){
            cartPromotionItemList = promotionService.calcCartPromotion(cartItemList);
        }
        return cartPromotionItemList;
    }

    @Override
    public OmsCartItem updateQuantity(Long id, Long memberId, Integer quantity) {
        OmsCartItem existCartItem = getCartItemByIdAndMember(id, memberId);
        if (existCartItem == null) {
            Asserts.fail("购物车中不存在该商品");
        }
        //校验商品发布状态及SKU库存（按修改后的数量校验），并写入库存提示
        validateProductAndStock(existCartItem, quantity == null ? 0 : quantity);
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setQuantity(quantity);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        cartItemMapper.updateByExampleSelective(updateCart, example);
        existCartItem.setQuantity(quantity);
        return existCartItem;
    }

    @Override
    public int delete(Long memberId, List<Long> ids) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andIdIn(ids).andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record, example);
    }

    @Override
    public CartProduct getCartProduct(Long productId) {
        return productDao.getCartProduct(productId);
    }

    @Override
    public OmsCartItem updateAttr(OmsCartItem cartItem) {
        //先校验新规格的发布状态与SKU库存，避免在校验失败时误删原购物车行
        OmsCartItem checkItem = new OmsCartItem();
        checkItem.setProductId(cartItem.getProductId());
        checkItem.setProductSkuId(cartItem.getProductSkuId());
        validateProductAndStock(checkItem, cartItem.getQuantity() == null ? 0 : cartItem.getQuantity());
        //删除原购物车信息
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        cartItemMapper.updateByPrimaryKeySelective(updateCart);
        //以新规格重新加入购物车（add会再次校验合并后的库存并写入库存提示）
        cartItem.setId(null);
        return add(cartItem);
    }

    @Override
    public int clear(Long memberId) {
        OmsCartItem record = new OmsCartItem();
        record.setDeleteStatus(1);
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andMemberIdEqualTo(memberId);
        return cartItemMapper.updateByExampleSelective(record,example);
    }
}
