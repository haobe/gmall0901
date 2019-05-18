package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.config.CookieUtil;
import com.atguigu.gmall.order.service.ManageService;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * 主要操作未登录的购物车数据
 */
@Component
public class CartCookieHandler {

    // 定义购物车名称
    private String COOKIECARTNAME = "CART";
    // 设置cookie 过期时间
    private int COOKIE_CART_MAXAGE=7*24*3600;

    @Reference
    private ManageService manageService;

    /*
    * 1.先看购物车中是否有该商品
    * select * from cartinfo where skuId=? and userId = ?
    * 2.有，数据相加 update
    * 3.没有，添加到数据库 insert
    * 4.将数据放入redis
    * */
    public void addToCart(HttpServletRequest request, HttpServletResponse response, String skuId, String userId, int skuNum) {
        String cookieValue = CookieUtil.getCookieValue(request, COOKIECARTNAME, true);
        //购物车数据 cookieValue  存的是购物车里边的商品对象集合
        //将cookieValue转化为集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();

        //定义一个boolean类型的变量
        boolean ifExist=false;

        if (cookieValue != null && cookieValue.length() > 0){

            cartInfoList = JSON.parseArray(cookieValue, CartInfo.class);



            if (cartInfoList != null && cartInfoList.size() > 0){
                //循环
                for (CartInfo cartInfo : cartInfoList) {
                    //说明购物车中已经有该商品，
                    if (cartInfo.getSkuId().equals(skuId)){
                        cartInfo.setSkuNum(cartInfo.getSkuNum() + skuNum);
                        cartInfo.setSkuPrice(cartInfo.getCartPrice());

                        ifExist=true;
                    }
                }
            }
        }
        if (!ifExist){
            //获取skuInfo对象
            SkuInfo skuInfo = manageService.getSkuInfo(skuId);
            CartInfo cartInfo = new CartInfo();

            cartInfo.setSkuId(skuId);
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());

            cartInfo.setUserId(userId);
            cartInfo.setSkuNum(skuNum);
            cartInfoList.add(cartInfo);
        }
        //将整个集合放到cookie中
        String cartJsonList = JSON.toJSONString(cartInfoList);
        CookieUtil.setCookie(request,response,COOKIECARTNAME,cartJsonList,COOKIE_CART_MAXAGE,true);


    }

    /**
     *
     * 查询cookie中的所有数据
     * @param request
     * @return
     */
    public List<CartInfo> getCartList(HttpServletRequest request) {
        String cookieValue = CookieUtil.getCookieValue(request, COOKIECARTNAME, true);
        //将字符串转化为集合对象n
        List<CartInfo> cartInfoList = JSON.parseArray(cookieValue, CartInfo.class);
        return cartInfoList;

    }

    /**
     * 删除cookie中的数据
     * @param request
     * @param response
     */
    public void deleteCartCookie(HttpServletRequest request, HttpServletResponse response) {
        CookieUtil.deleteCookie(request,response,COOKIECARTNAME);

    }

    /**\\
     * 修改商品选中的状态
     * @param request
     * @param response
     * @param skuId
     * @param isChecked
     */
    public void checkCart(HttpServletRequest request, HttpServletResponse response, String skuId, String isChecked) {
        //先获取购物车数据
        List<CartInfo> cartList = getCartList(request);
        //遍历该集合  将商品的状态付给对应的商品
        for (CartInfo cartInfo : cartList) {
            //判断条件是根据skuId
            if (cartInfo.getSkuId().equals(skuId)){
                //赋值选中状态
                cartInfo.setIsChecked(isChecked);
            }
        }
        //将修改后的购物车集合再放入cookie
        CookieUtil.setCookie(request,response,COOKIECARTNAME,JSON.toJSONString(cartList),COOKIE_CART_MAXAGE,true);


    }
}
