package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.enums.OrderStatus;
import com.atguigu.gmall.bean.enums.ProcessStatus;
import com.atguigu.gmall.config.ActiveMQUtil;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.service.PaymentService;
import com.atguigu.gmall.utils.HttpClientUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import javax.jms.Queue;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Reference
    private PaymentService paymentService;

    /**
     * 保存订单信息
     *
     * @param orderInfo
     * @return
     */
    @Override
    public String saveOrder(OrderInfo orderInfo) {

        //创建时间
        orderInfo.setCreateTime(new Date());
        //过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        //延长一天的时间
        orderInfo.setExpireTime(calendar.getTime());
        //进程状态，订单状态
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        //第三方交易编号  随机生成
        String outTradeNo="ATGUIGU"+System.currentTimeMillis()+""+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        //保存orderInfo数据
        orderInfoMapper.insertSelective(orderInfo);
        //保存orderDetail
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (orderDetailList != null && orderDetailList.size() > 0){
            //循环
            for (OrderDetail orderDetail : orderDetailList) {
                orderDetail.setOrderId(orderInfo.getId());
                orderDetailMapper.insertSelective(orderDetail);

            }
        }
        //返回order.id
        return orderInfo.getId();
    }

    /**
     * 生成流水号
     *
     * @param userId
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        //获取jedis
        Jedis jedis = redisUtil.getJedis();
        //定义Key由userId组成
        String tradeNoKey =  "user:" + userId + ":tradeCode";
        String uuid = UUID.randomUUID().toString();
        jedis.setex(tradeNoKey,10*60,uuid);
        jedis.close();
        return uuid;
    }

    /**
     * 验证流水号
     * @param userId
     * @param tradeCodeNo
     * @return
     */
    @Override
    public  boolean checkTradeCode(String userId,String tradeCodeNo){
        Jedis jedis = redisUtil.getJedis();
        String tradeNoKey =  "user:" + userId + ":tradeCode";
        String tradeCode  = jedis.get(tradeNoKey);
        jedis.close();
        if (tradeCode!=null && tradeCode.equals(tradeCodeNo)){
            return  true;
        }else{
            return false;
        }
    }

    /**
     * 删除流水号
     *
     * @param userId
     */
    @Override
    public void delTradeCode(String userId) {
        Jedis jedis = redisUtil.getJedis();
        String tradeNoKey =  "user:"+userId+":tradeCode";
        jedis.del(tradeNoKey);
        jedis.close();
    }

    /**
     * 查询库存
     *
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public boolean checkStock(String skuId, Integer skuNum) {
        //调用库存查询接口  远程调用
        String result = HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + skuId + "&num=" + skuNum);
        if ("1".equals(result)){
            return true;
        }else {
            return false;
        }
    }

    /**
     * 根据orderId查询OrderInfo对象
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(String orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);
        //通过orderId查询orderDetail
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.select(orderDetail);
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    /**
     * 修改订单状态
     *
     * @param orderId
     * @param processStatus
     */
    @Override
    public void updateOrderStatus(String orderId, ProcessStatus processStatus) {

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus);
        orderInfo.setOrderStatus(processStatus.getOrderStatus());

        //修改状态
        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);

    }

    /**
     * 根据订单id  发送消息给库存
     *
     * @param orderId
     */
    @Override
    public void sendOrderStatus(String orderId) {
        //获取连接
        Connection connection = activeMQUtil.getConnection();
        //获取JSON字符串  将orderIndo， orderDetail拼接成字符串
        String orderJson = initWareOrder(orderId);
        try {
            //打开连接
            connection.start();
            // 获取会话
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            //获取队列
            Queue order_result_queue = session.createQueue("ORDER_RESULT_QUEUE");
            //创建消息提供者
            MessageProducer producer = session.createProducer(order_result_queue);

            //创建消息对象
            ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
            //JSON字符串
            activeMQTextMessage.setText(orderJson);
            //消息提供者  发送消息队列
            producer.send(activeMQTextMessage);

            //提交事务
            session.commit();
            //关闭
            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            e.printStackTrace();
        }


    }

    /**
     * 查询过期订单
     *
     * @return
     */
    @Override
    public List<OrderInfo> getExpiredOrderList() {
        //超时  未支付订单
        Example example = new Example(OrderInfo.class);
        example.createCriteria().andEqualTo("processStatus",ProcessStatus.UNPAID).andLessThan("expireTime",new Date());

        return orderInfoMapper.selectByExample(example);
    }

    /**
     * 处理过期订单
     *
     * @param orderInfo
     */
    @Async   //使用线程池
    @Override
    public void execExpiredOrder(OrderInfo orderInfo) {
        //更新过期订单  将其状态设置为关闭
        updateOrderStatus(orderInfo.getId(),ProcessStatus.CLOSED);
        //与orderInfo有关的表也得关闭
        paymentService.closePayment(orderInfo.getId());

    }

    /**
     * 拆单方法，根据仓库和对应的商品id进行拆单
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    public List<OrderInfo> splitOrder(String orderId, String wareSkuMap) {
        //声明子订单集合
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        //  先查询原始订单
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        //  wareSkuMap 反序列化  得到仓库id  商品Id
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        //  遍历拆单方案
        for (Map map : maps) {
            //从当前map中取出对应的仓库编号和商品id集合
            String wareId = (String) map.get("wareId");
            List<String> skuIds = (List<String>) map.get("skuIds");
            //  生成订单主表，从原始订单复制，新的订单号，父订单
            OrderInfo subOrderInfo = new OrderInfo();
            //将属性进行拷贝
            BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
            //子订单id不能与复订单相同
            subOrderInfo.setId(null);
            //给子订单设置父订单id
            subOrderInfo.setParentOrderId(orderId);
            //设置一个仓库id
            subOrderInfo.setWareId(wareId);

            //  明细表 根据拆单方案中的skuids进行匹配，得到那个的子订单
           //获取原始订单明细集合
            List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
            // 创建一个子订单明细集合
            List<OrderDetail> subOrderDetailList = new ArrayList<>();
            for (OrderDetail orderDetail : orderDetailList) {
                for (String skuId : skuIds) {
                    if (skuId.equals(orderDetail.getSkuId())){
                        //明细子订单id也需要赋值为null
                        orderDetail.setId(null);
                        subOrderDetailList.add(orderDetail);
                    }
                }
            }
            //将新的子订单明细集合放到子订单集合中
            subOrderInfo.setOrderDetailList(subOrderDetailList);
            //需要总金额
            subOrderInfo.sumTotalAmount();
            //将子订单保存到数据库
            saveOrder(subOrderInfo);
            //将新的子订单对象放到子订单集合中
            subOrderInfoList.add(subOrderInfo);
        }
        //原始订单需要修改状态
        updateOrderStatus(orderId,ProcessStatus.SPLIT);
        //返回新生成的子订单列表
        return subOrderInfoList;



    }

    //根据orderId查询orderInfo对象 将orderInfo对象封装成map  并将map转化为json
    private String initWareOrder(String orderId) {
        //根据orderId查找orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);
        //将orderInfo转化为map
        Map map = initWareOrder(orderInfo);
        //将map转化为json字符串
        return JSON.toJSONString(map);
    }

    public Map initWareOrder(OrderInfo orderInfo) {

        Map<String,Object> map = new HashMap<>();
        map.put("orderId",orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody","测试数据");
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");
        //拆单是需要判断是否在同一个仓库
        map.put("wareId",orderInfo.getWareId());

        //需要一个订单明细集合
        List<Map> detailList = new ArrayList<>();
        //获取订单明细集合
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        //循环遍历数据
        for (OrderDetail orderDetail : orderDetailList) {
            Map detailMap = new HashMap();
            detailMap.put("skuId",orderDetail.getSkuId());
            detailMap.put("skuName",orderDetail.getSkuName());
            detailMap.put("skuNum",orderDetail.getSkuNum());
            //将map放入几个
            detailList.add(detailMap);
        }
        map.put("details",detailList );
        return map;
    }
}
