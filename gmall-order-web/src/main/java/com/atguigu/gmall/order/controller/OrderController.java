package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.order.service.CartService;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.service.UserService;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    /*
    * 根据用户id查询用户地址
    * */
//    @Autowired
    @Reference
    private UserService userService;

    @Reference
    private CartService cartService;

    @Reference
    private OrderService orderService;


//    @RequestMapping("trade")
//    @ResponseBody
//    public List<UserAddress> trade(String userId){
//
//        return  userService.findUserAddressByUserId(userId);
//
//
//    }

    @RequestMapping("trade")
    @LoginRequire(autoRedirect = true)
    public String trade(HttpServletRequest request){
        //根据用户id得到用户地址
        String userId = (String) request.getAttribute("userId");
        //调用方法
        List<UserAddress> userAddressList = userService.findUserAddressByUserId(userId);
        //声明一个订单明细对象集合
        List<OrderDetail> orderDetailList = new ArrayList<>();
        //先获取购物车中被选中的商品列表
        List<CartInfo> cartInfoList = cartService.getCartCheckedList(userId);
        //得到商品列表  将其循环并将里边的值付给orderDetail
        if (cartInfoList != null && cartInfoList.size() > 0){
            //循环赋值
            for (CartInfo cartInfo : cartInfoList) {
                //创建订单明细对象
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setSkuId(cartInfo.getSkuId());
                orderDetail.setSkuName(cartInfo.getSkuName());
                orderDetail.setImgUrl(cartInfo.getImgUrl());
                orderDetail.setSkuNum(cartInfo.getSkuNum());
                orderDetail.setOrderPrice(cartInfo.getCartPrice());
                orderDetailList.add(orderDetail);
            }

        }
        //总价格
        OrderInfo orderInfo = new OrderInfo();
        //将订单明细集合赋值给orderInfo
        orderInfo.setOrderDetailList(orderDetailList);
        //将计算结果赋值给orderInfo.totalAmount
        orderInfo.sumTotalAmount();
        //保存总价格
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());

        //保存订单明细集合对象
        request.setAttribute("orderDetailList",orderDetailList);
        //保存集合
        request.setAttribute("userAddressList",userAddressList);

        //生成一个流水号:将userId作为key来存储编号
        String tradeNo = orderService.getTradeNo(userId);

        request.setAttribute("tradeNo" , tradeNo);

        return "trade";
    }

    @RequestMapping(value = "submitOrder",method = RequestMethod.POST)
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo,HttpServletRequest request){
       //获取userId
        String userId = (String) request.getAttribute("userId");
        //获取tradeNo
        String tradeNo = request.getParameter("tradeNo");
        //调用方法比较
        boolean result = orderService.checkTradeCode(userId, tradeNo);
        //比较失败
        if ( !result ){
            request.setAttribute("errMsg","不能重复提交订单，请重新结算!");
            return "tradeFail";
        }
        //验证库存
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (orderDetailList != null && orderDetailList.size() > 0 ){
            for (OrderDetail orderDetail : orderDetailList) {
                //验证每一个商品是否有足够的库存
                boolean flag = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                if (!flag){
                    request.setAttribute("errMsg","库存不足，请重新下单!");
                    return "tradeFail";
                }
            }
        }
        //验价: 验证当前商品价格是否与此时商品价格一致


        //更新数据
        orderInfo.setUserId(userId);
        //计算总金额
        orderInfo.sumTotalAmount();
        //将总金额放入对象
        orderInfo.setTotalAmount(orderInfo.getTotalAmount());

        String orderId = orderService.saveOrder(orderInfo);

        //删除redis中的流水号
        orderService.delTradeCode(userId);

        return "redirect://payment.gmall.com/index?orderId=" + orderId;
    }


    @RequestMapping("orderSplit")
    @ResponseBody
    public String orderSplit(HttpServletRequest request){

        String orderId = request.getParameter("orderId"); //订单编号
        String wareSkuMap = request.getParameter("wareSkuMap"); //仓库编号与商品的对照关系
        // 定义订单集合
        List<OrderInfo> subOrderInfoList = orderService.splitOrder(orderId,wareSkuMap);
        //声明一个集合来存储map集合
        List<Map> wareMapList=new ArrayList<>();
        //将每个orderInfo对象转化为map
        for (OrderInfo orderInfo : subOrderInfoList) {
            Map map = orderService.initWareOrder(orderInfo);
            wareMapList.add(map);
        }
        return JSON.toJSONString(wareMapList);

    }



}
