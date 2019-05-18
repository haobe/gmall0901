package com.atguigu.gmall.payment;

import com.atguigu.gmall.config.ActiveMQUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.jms.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GmallPaymentApplicationTests {

	@Autowired
	private ActiveMQUtil activeMQUtil;



	@Test
	public void testActivemq() throws JMSException {

		Connection connection = activeMQUtil.getConnection();

		connection.start();
		//创建一个会话session 参数1：是否开启事务 参数2.根据第一个参数来选择手动签收事务，还是自动签收事务
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		//创建一个队列
		Queue queue = session.createQueue("activeTest");
		//将队列放入提供者对象中
		MessageProducer producer = session.createProducer(queue);
		//提供者对象发消息
		ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
		activeMQTextMessage.setText("^_^测试工具类");
		producer.send(activeMQTextMessage);
		//关闭操作
		producer.close();
		session.close();
		connection.close();

	}

}
