package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.order.service.CartService;
import com.atguigu.gmall.order.service.ManageService;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Controller
public class CartController {

    @Reference
    private CartService cartService;

    @Reference
    private ManageService manageService;

    @Autowired
    private CartCookieHandler cartCookieHandler;

    @RequestMapping("addToCart")
    @LoginRequire(autoRedirect = false)
    public String addToCart(HttpServletRequest request, HttpServletResponse response){
        //获取userId
        String userId = (String) request.getAttribute("userId");

        String skuId = request.getParameter("skuId");
        String skuNum = request.getParameter("skuNum");

        if (userId != null) {
            //已登录
            cartService.addToCart(skuId, userId, Integer.parseInt(skuNum));
        }else {
            //未登录,将数据添加到cookie中
            cartCookieHandler.addToCart(request,response,skuId,userId,Integer.parseInt(skuNum));
        }
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);

        request.setAttribute("skuNum",skuNum);

        request.setAttribute("skuInfo",skuInfo);

        return "success";
    }


    @RequestMapping("cartList")
    @LoginRequire(autoRedirect = false)
    public String cartList(HttpServletRequest request,HttpServletResponse response){
        String userId = (String) request.getAttribute("userId");

        if(userId != null ){
            //这里要 合并购物车  cookie往redis 合并
            //先获取cookie的数据
            List<CartInfo> cartInfoList = null;
            List<CartInfo> cartListCK = cartCookieHandler.getCartList(request);
            if (cartListCK != null && cartListCK.size() > 0){
                //开始合并
                cartInfoList = cartService.mergeToCartList(cartListCK,userId);
                //合并之后  cookie数据就没用了  删除cookie数据
                cartCookieHandler.deleteCartCookie(request,response);


            }else {
                //查询redis
                cartInfoList = cartService.getCartList(userId);
            }
            //保存到域中
            request.setAttribute("cartInfoList",cartInfoList);

        }else {
            //查看购物车
            //查看cookie
            List<CartInfo> cartInfoList = cartCookieHandler.getCartList(request);
            //保存起来
            request.setAttribute("cartInfoList",cartInfoList);
        }
        return "cartList";
    }

    @RequestMapping("checkCart")
    @LoginRequire(autoRedirect = false)
    public void checkCart(HttpServletRequest request, HttpServletResponse response){
        //获取 skuId, isChecked, userId
        String userId = (String) request.getAttribute("userId");
        String isChecked = request.getParameter("isChecked");
        String skuId = request.getParameter("skuId");
        if (userId != null){
            //登录状态
            cartService.checkCart(skuId,isChecked,userId);
        }else {
            //未登录状态
            cartCookieHandler.checkCart(request,response,skuId,isChecked);
        }
    }

    //结算必须登录
    @RequestMapping("toTrade")
    @LoginRequire(autoRedirect = true)
    public String toTrade(HttpServletRequest request,HttpServletResponse response){
        //可能需要对选中商品的状态进行合并  cookie --> redis
        //得到cookie中的数据
        String userId = (String) request.getAttribute("userId");
        List<CartInfo> cartListCK = cartCookieHandler.getCartList(request);
        if (cartListCK != null && cartListCK.size() > 0){
            //产生合并
            cartService.mergeToCartList(cartListCK,userId);
            //合并之后删除cookie数据
            cartCookieHandler.deleteCartCookie(request,response);

        }
        //重定向到订单页面
        return "redirect://order.gmall.com/trade";
    }





}
