package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.bean.enums.PaymentStatus;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.service.PaymentService;
import com.atguigu.gmall.payment.config.AlipayConfig;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.alipay.api.AlipayConstants.APP_ID;
import static com.alipay.api.AlipayConstants.FORMAT;
import static com.alipay.api.AlipayConstants.SIGN_TYPE;

@Controller
public class PaymentController {

    @Reference
    private OrderService orderService;

//    @Autowired
    @Reference
    private PaymentService paymentService;

    @Autowired
    private AlipayClient alipayClient;


    @RequestMapping("index")
    public String index(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        //根据orderId 查询orderInfo对象
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        request.setAttribute("orderId",orderId);
        return  "index";
    }

    @RequestMapping(value = "alipay/submit",method = RequestMethod.POST)
    @ResponseBody
    public String aliPay(HttpServletRequest request , HttpServletResponse response){
        String orderId = request.getParameter("orderId");
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        //需要记录交易信息，保存到数据库 payment_info{对账  --支付宝}
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject("新年快乐!!!QAQ");
        //调用服务层保存数据
        paymentService.savePaymentInfo(paymentInfo);

        //需要生成二维码  需要将参数配置放入配置文件中  从配置文件中读取
        //AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do", APP_ID, APP_PRIVATE_KEY, FORMAT, CHARSET, ALIPAY_PUBLIC_KEY, SIGN_TYPE); //获得初始化的AlipayClient

        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();//创建API对应的request
        //同步回调路径
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        //异步回调路径
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址

        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",paymentInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
        map.put("total_amount",paymentInfo.getTotalAmount());
        map.put("subject",paymentInfo.getSubject());
        //把map集合转化为JSON，让代码变得简单
        alipayRequest.setBizContent(JSON.toJSONString(map));
//        alipayRequest.setBizContent("{" +
//                "    \"out_trade_no\":\"20150320010101001\"," +
//                "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
//                "    \"total_amount\":88.88," +
//                "    \"subject\":\"Iphone6 16G\"," +
//                "    \"body\":\"Iphone6 16G\"," +
//                "    \"passback_params\":\"merchantBizType%3d3C%26merchantBizNo%3d2016010101111\"," +
//                "    \"extend_params\":{" +
//                "    \"sys_service_provider_id\":\"2088511833207846\"" +
//                "    }"+
//                "  }");//填充业务参数
        String form="";
        try {
            form = alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        response.setContentType("text/html;charset=utf-8");
        //当生成二维码的时候，发送一个消息队列，这个消息队列中存储着第三方交易编号，时间间隔，检查次数
        paymentService.sendDelayPaymentResult(paymentInfo.getOutTradeNo(),15,3);

        return form;
    }

    //同步回调的控制器  给用户跳转到订单页面
    @RequestMapping("alipay/callback/return")
    public String callbackReturn(){

        return "redirect:" + AlipayConfig.return_order_url;
    }

    //异步回调控制器
    @RequestMapping("alipay/callback/notify")
    public String callbackNotify(@RequestParam Map<String,String> paramMap, HttpServletRequest request) throws AlipayApiException {
        //验签
        //Map<String, String> paramsMap = ... //将异步通知中收到的所有参数都存放到map中
        boolean signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key,
                AlipayConfig.charset, AlipayConfig.sign_type); //调用SDK验证签名
        if(signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            //获取out_trade_no
            String out_trade_no = paramMap.get("out_trade_no");
            //需要保证支付的交易状态为TRADE_SUCCESS 或 TRADE_FINISHED
            String trade_status = paramMap.get("trade_status");

            if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){

                //调用服务层查询paymentInfo
                PaymentInfo paymentInfoQuery = new PaymentInfo();
                paymentInfoQuery.setOutTradeNo(out_trade_no);

                PaymentInfo paymentInfoResult = paymentService.getPaymentInfo(paymentInfoQuery);

                //保证交易记录状态不能为IPAD,CLOSE时为验签失败  查询 paymentInfo中的payment_status状态不能为IPAD,CLOSE
                if (paymentInfoResult.getPaymentStatus() == PaymentStatus.PAID
                        || paymentInfoResult.getPaymentStatus() == PaymentStatus.ClOSED){

                    return "failure";

                }else {

                    PaymentInfo paymentInfoUPD = new PaymentInfo();
                    //将交易状态变为已支付
                    paymentInfoUPD.setPaymentStatus(PaymentStatus.PAID);
                    //回调时间
                    paymentInfoUPD.setCallbackTime(new Date());
                    //修改内容体
                    paymentInfoUPD.setCallbackContent(paramMap.toString());
                    //验签成功之后需要修改状态
                    paymentService.updatePaymentInfo(out_trade_no,paymentInfoUPD);

                    //当支付宝验签成功之后 发送消息给订单
                    paymentService.sendPaymentResult(paymentInfoResult,"success");

                    return "success";
                }
            }


        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "failure";
        }
        return "failure";
    }

    // 发送验证
    //测试 paymentService.sendPaymentResult是否可用
    @RequestMapping("sendPaymentResult")
    @ResponseBody
    public String sendPaymentResult(PaymentInfo paymentInfo,String result){
        paymentService.sendPaymentResult(paymentInfo,result);
        return "sent payment result";
    }

    //主动询问支付结果
    @RequestMapping("queryPaymentResult")
    @ResponseBody
    public String queryPaymentResult(String orderId){
        //使用orderId查询对应的订单信息
        PaymentInfo paymentInfoQuery = new PaymentInfo();
        paymentInfoQuery.setOrderId(orderId);
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(paymentInfoQuery);
        //调用查询方法
        boolean flag = paymentService.checkPayment(paymentInfo);
        return "" + flag;

    }


}
