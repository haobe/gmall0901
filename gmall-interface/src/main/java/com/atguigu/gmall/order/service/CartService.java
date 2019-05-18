package com.atguigu.gmall.order.service;

import com.atguigu.gmall.bean.CartInfo;

import java.util.List;

public interface CartService {

    /**
     * 添加购物车
     * @param skuId
     * @param userId
     * @param skuNum
     */
    public void addToCart(String skuId,String userId,Integer skuNum);

    /**
     * 根据userId查询用户购物车列表
     * @param userId
     * @return
     */
    List<CartInfo> getCartList(String userId);

    /**
     * 合并购物车
     * @param cartListCK
     * @param userId
     * @return
     */
    List<CartInfo> mergeToCartList(List<CartInfo> cartListCK, String userId);

    /**
     * 选中状态
     * @param skuId
     * @param isChecked
     * @param userId
     */
    void checkCart(String skuId, String isChecked, String userId);

    /**
     * 根据用户id查询被选中的购物车列表
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);
}
