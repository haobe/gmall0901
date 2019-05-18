package com.atguigu.gmall.order.task;

import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.order.service.OrderService;
import jdk.nashorn.internal.ir.annotations.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@EnableScheduling
@Component
public class OrderTask {

    @Autowired
    private OrderService orderService;

//    //每分钟的第5秒
//    @Scheduled(cron = "5 * * * * ?")
//    public void work1(){
//        System.out.println(Thread.currentThread().getName() + "---------work2--------");
//    }
//    //每隔5秒
//    @Scheduled(cron = "0/5 * * * * ?")
//    public void work2(){
//        System.out.println(Thread.currentThread().getName() + "---------work2--------");
//    }

    //每隔30秒扫描一回
    @Scheduled(cron = "0/30 * * * * ?")
    public void checkOrder(){
        //查询过期订单有哪些
        List<OrderInfo> orderInfoList = orderService.getExpiredOrderList();
        //循环遍历过期订单  对其 进行处理
        if (orderInfoList != null && orderInfoList.size() > 0){
            for (OrderInfo orderInfo : orderInfoList) {
                //处理过期订单
                orderService.execExpiredOrder(orderInfo);
            }


        }



    }






}
