package com.atguigu.gmall.payment.mq;

import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.order.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

@Component
public class PaymentConsumer {

    @Autowired
    private PaymentService paymentService;


    //监听PAYMENT_RESULT_CHECK_QUEUE 队列中的消息
    @JmsListener(destination = "PAYMENT_RESULT_CHECK_QUEUE",containerFactory = "jmsQueueListener")
    public  void  consumerPaymentResultCheck(MapMessage mapMessage) throws JMSException {//mapMessage发消息的时候用的是这个对象
        //获取消息队列中的数据
        String outTradeNo = mapMessage.getString("outTradeNo");
        int delaySec = mapMessage.getInt("delaySec");
        int checkCount = mapMessage.getInt("checkCount");
        //声明一个paymentInfo对象
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOutTradeNo(outTradeNo);
        //根据outTradeNo查询支付结果
        boolean flag = paymentService.checkPayment(paymentInfo);
        System.out.println("支付结果"+flag);
        //每个15秒检查一回  总共检查三回
        //flag= true 用户已付款  flag=false用户为付款，继续等待，到了时间间隔，会主动再问支付宝结果
        if (!flag && checkCount > 0){
            System.out.println("再次发送 checkCount="+checkCount);
            paymentService.sendDelayPaymentResult(outTradeNo,delaySec,checkCount - 1);
        }



        //主要修改订单状态

    }


}
