package com.atguigu.gmall.payment.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.bean.enums.PaymentStatus;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.order.service.PaymentService;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.beans.factory.annotation.Autowired;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import java.util.HashMap;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Autowired
    private AlipayClient alipayClient;

    /**
     * 保存交易记录
     *
     * @param paymentInfo
     */
    @Override
    public void savePaymentInfo(PaymentInfo paymentInfo) {
        paymentInfoMapper.insertSelective(paymentInfo);


    }

    /**
     * 根据out_trade_no查询PaymentInfo对象
     *
     * @param paymentInfoQuery
     * @return
     */
    @Override
    public PaymentInfo getPaymentInfo(PaymentInfo paymentInfoQuery) {
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(paymentInfoQuery);
        return paymentInfo;
    }

    /**
     * 修改交易记录状态
     * @param out_trade_no
     * @param paymentInfoUPD
     */
    @Override
    public void updatePaymentInfo(String out_trade_no, PaymentInfo paymentInfoUPD) {

        Example example = new Example(PaymentInfo.class);
        //构造修改条件
        //第一个参数是 类的属性名   第二个参数是类属性对应的表的字段名
        example.createCriteria().andEqualTo("outTradeNo",out_trade_no);
        paymentInfoMapper.updateByExampleSelective(paymentInfoUPD,example);

    }

    /**
     * 发送支付结果的消息
     *
     * @param paymentInfo
     * @param result
     */
    @Override
    public void sendPaymentResult(PaymentInfo paymentInfo, String result) {
        //创建工厂连接
        Connection connection = activeMQUtil.getConnection();

        try {
            //打开连接
            connection.start();
            //创建session
            Session session = connection.createSession(true,Session.SESSION_TRANSACTED);
            //创建队列
            Queue payment_result_queue = session.createQueue("PAYMENT_RESULT_QUEUE");
            //创建消息提供者
            MessageProducer producer = session.createProducer(payment_result_queue);
            //创建消息对象
            MapMessage  mapMessage  = new ActiveMQMapMessage();
            mapMessage.setString("orderId",paymentInfo.getOrderId());
            mapMessage.setString("result",result);
            //发送消息
            producer.send(mapMessage);
            //消息队列中如果有事务  必须提交
            session.commit();

            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            e.printStackTrace();
        }


    }

    /**
     * 查询订单的支付状态
     *
     * @param paymentInfoQuery
     * @return
     */
    @Override
    public boolean checkPayment(PaymentInfo paymentInfoQuery) {
        //AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",paymentInfoQuery.getOutTradeNo());
        //将out_trade_no编程JSON放入方法中
        request.setBizContent(JSON.toJSONString(map));
//        request.setBizContent("{" +
//                "\"out_trade_no\":\"20150320010101001\"," +
//                "\"trade_no\":\"2014112611001004680 073956707\"," +
//                "\"org_pid\":\"2088101117952222\"" +
//                "  }");
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        //调用成功
        if(response.isSuccess()){
            if ("TRADE_SUCCESS".equals(response.getTradeStatus())||"TRADE_FINISHED".equals(response.getTradeStatus())){
                //支付成功  需要更改状态
                System.out.println("支付成功");
                // 改支付状态
                PaymentInfo paymentInfoUpd = new PaymentInfo();
                paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID);
                //调用更新方法
                updatePaymentInfo(paymentInfoQuery.getOutTradeNo(),paymentInfoUpd);
                //发送通知给订单模块
                sendPaymentResult(paymentInfoQuery,"success");
                return true;
            }
        } else {
            System.out.println("调用失败");
        }
        return false;
    }

    /**
     * 发送延迟队列的接口
     *
     * @param outTradeNo
     * @param delaySec
     * @param checkCount
     */
    @Override
    public void sendDelayPaymentResult(String outTradeNo, int delaySec, int checkCount) {
        //创建连接工厂，获取连接
        Connection connection = activeMQUtil.getConnection();
        try {
            connection.start();
            //创建会话
            Session session = connection.createSession(true,Session.SESSION_TRANSACTED);
            //创建队列
            Queue payment_result_check_queue = session.createQueue("PAYMENT_RESULT_CHECK_QUEUE");
            //创建提供者
            MessageProducer producer = session.createProducer(payment_result_check_queue);
            //创建消息对象
            ActiveMQMapMessage mapMessage = new ActiveMQMapMessage();
            mapMessage.setString("outTradeNo",outTradeNo);
            mapMessage.setInt("delaySec",delaySec);
            mapMessage.setInt("checkCount",checkCount);
            //开启延迟队列
            mapMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY,delaySec * 1000);

            //准备发送
            producer.send(mapMessage);
            session.commit();
            //关闭对象
            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            e.printStackTrace();
        }


    }

    /**
     * 根据orderId关闭过期交易记录
     *
     * @param orderId
     */
    @Override
    public void closePayment(String orderId) {
        //设置条件
        Example example = new Example(PaymentInfo.class);
        example.createCriteria().andEqualTo("orderId",orderId);
        //设置要改变的值
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED);
        //调用方法对其进行关闭
        paymentInfoMapper.updateByExampleSelective(paymentInfo,example);

    }
}
