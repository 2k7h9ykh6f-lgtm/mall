package com.macro.mall.portal.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.mapper.OmsCartItemMapper;
import com.macro.mall.model.OmsCartItem;
import com.macro.mall.model.OmsCartItemExample;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.dao.PortalProductDao;
import com.macro.mall.portal.domain.CartProduct;
import com.macro.mall.portal.domain.CartPromotionItem;
import com.macro.mall.portal.dto.CartItemResult;
import com.macro.mall.portal.exception.CartValidationException;
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
    private OmsPromotionService promotionService;
    @Autowired
    private UmsMemberService memberService;

    /**
     * 校验商品发布状态和SKU库存
     *
     * @param productId     商品ID
     * @param productSkuId  SKU ID
     * @param requestedQty  请求的总数量
     * @return 匹配的SKU库存记录（用于生成库存提示）
     * @throws CartValidationException 校验失败时抛出
     */
    private PmsSkuStock validateCartItem(Long productId, Long productSkuId, int requestedQty) {
        CartProduct cartProduct = productDao.getCartProduct(productId);
        if (cartProduct == null) {
            throw new CartValidationException("商品不存在");
        }
        if (cartProduct.getPublishStatus() == null || cartProduct.getPublishStatus() != 1) {
            throw new CartValidationException("商品已下架");
        }
        PmsSkuStock targetSku = null;
        if (cartProduct.getSkuStockList() != null) {
            for (PmsSkuStock sku : cartProduct.getSkuStockList()) {
                if (sku.getId().equals(productSkuId)) {
                    targetSku = sku;
                    break;
                }
            }
        }
        if (targetSku == null) {
            throw new CartValidationException("商品SKU不存在");
        }
        int availableStock = targetSku.getStock() == null ? 0 : targetSku.getStock();
        if (availableStock < requestedQty) {
            throw new CartValidationException("库存不足，当前库存：" + availableStock + "件");
        }
        return targetSku;
    }

    @Override
    public CartItemResult add(OmsCartItem cartItem) {
        UmsMember currentMember = memberService.getCurrentMember();
        cartItem.setMemberId(currentMember.getId());
        cartItem.setMemberNickname(currentMember.getNickname());
        cartItem.setDeleteStatus(0);

        // 检查购物车中是否已存在该商品
        OmsCartItem existCartItem = getCartItem(cartItem);

        // 计算总数量（已有商品则累加）
        int totalQuantity = cartItem.getQuantity();
        if (existCartItem != null) {
            totalQuantity += existCartItem.getQuantity();
        }

        // 校验商品发布状态和SKU库存
        PmsSkuStock sku = validateCartItem(cartItem.getProductId(), cartItem.getProductSkuId(), totalQuantity);

        int count;
        OmsCartItem resultItem;
        if (existCartItem == null) {
            cartItem.setCreateDate(new Date());
            count = cartItemMapper.insert(cartItem);
            resultItem = cartItem;
        } else {
            existCartItem.setQuantity(totalQuantity);
            existCartItem.setModifyDate(new Date());
            count = cartItemMapper.updateByPrimaryKey(existCartItem);
            resultItem = existCartItem;
        }

        if (count > 0) {
            return new CartItemResult(resultItem, sku.getStock());
        }
        throw new CartValidationException("添加购物车失败");
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
    public CartItemResult updateQuantity(Long id, Long memberId, Integer quantity) {
        // 先查询购物车商品以获取productId和productSkuId
        OmsCartItemExample queryExample = new OmsCartItemExample();
        queryExample.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        List<OmsCartItem> cartItems = cartItemMapper.selectByExample(queryExample);
        if (CollectionUtils.isEmpty(cartItems)) {
            throw new CartValidationException("购物车商品不存在");
        }
        OmsCartItem existingItem = cartItems.get(0);

        // 校验商品发布状态和SKU库存（quantity为目标总量，非增量）
        PmsSkuStock sku = validateCartItem(existingItem.getProductId(), existingItem.getProductSkuId(), quantity);

        // 执行数量更新
        OmsCartItem updateRecord = new OmsCartItem();
        updateRecord.setQuantity(quantity);
        updateRecord.setModifyDate(new Date());
        OmsCartItemExample example = new OmsCartItemExample();
        example.createCriteria().andDeleteStatusEqualTo(0)
                .andIdEqualTo(id).andMemberIdEqualTo(memberId);
        int count = cartItemMapper.updateByExampleSelective(updateRecord, example);

        if (count > 0) {
            existingItem.setQuantity(quantity);
            return new CartItemResult(existingItem, sku.getStock());
        }
        throw new CartValidationException("更新购物车数量失败");
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
    public CartItemResult updateAttr(OmsCartItem cartItem) {
        //删除原购物车信息
        OmsCartItem updateCart = new OmsCartItem();
        updateCart.setId(cartItem.getId());
        updateCart.setModifyDate(new Date());
        updateCart.setDeleteStatus(1);
        cartItemMapper.updateByPrimaryKeySelective(updateCart);
        cartItem.setId(null);
        // 重新添加时继承add()的校验逻辑
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
