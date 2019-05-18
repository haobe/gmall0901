package com.atguigu.gmall.payment.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;

import javax.jms.*;

//测试类 消息的提供者
public class ProducerTest {

    //psvm
    public static void main(String[] args) throws JMSException {
//        创建一个连接工厂
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://192.168.78.129:61616");
        //获取连接
        Connection connection = connectionFactory.createConnection();
        //打开连接
        connection.start();
        //创建一个会话session 参数1：是否开启事务 参数2.根据第一个参数来选择手动签收事务，还是自动签收事务
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        //创建一个队列
        Queue queue = session.createQueue("atguigu");
        //将队列放入提供者对象中
        MessageProducer producer = session.createProducer(queue);
        //提供者对象发消息
        ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
        activeMQTextMessage.setText("^_^hei");
        producer.send(activeMQTextMessage);
        //关闭操作
        producer.close();
        session.close();
        connection.close();


    }
}
