package com.atguigu.gmall.item.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.bean.SkuSaleAttrValue;
import com.atguigu.gmall.bean.SpuSaleAttr;
import com.atguigu.gmall.order.service.ListService;
import com.atguigu.gmall.order.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller

public class ItemController {

    @Reference
    private ManageService manageService;

    @Reference
    private ListService listService;

    @RequestMapping("{skuId}.html")
    //@LoginRequire(autoRedirect = true)
    public String item(@PathVariable String skuId, HttpServletRequest request){
//        必须调用服务层获取skuInfo数据
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);

        //获取销售属性
        List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrListCheckBySku(skuInfo);

        //获取销售属性值id集合
        List<SkuSaleAttrValue> skuSaleAttrValueListBySpu =
                manageService.getSkuSaleAttrValueListBySpu(skuInfo.getSpuId());
        //定义一个字符串
        //202/205
        String key = "";
//        map.put<key,skuId>
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < skuSaleAttrValueListBySpu.size(); i++) {
            SkuSaleAttrValue skuSaleAttrValue = skuSaleAttrValueListBySpu.get(i);
            //什么情况下添加/
            //key = 202/;
            if (key.length() > 0){
                key= key+"|";
            }
            //key = key + 202;
            key += skuSaleAttrValue.getSaleAttrValueId();

            System.out.println("key:" + key);
            //什么时候把key放到map中
            if ((i+1)==skuSaleAttrValueListBySpu.size() || !skuSaleAttrValue
                    .getSkuId().equals(skuSaleAttrValueListBySpu.get(i+1).getSkuId())){

                map.put(key,skuSaleAttrValue.getSkuId());
                key = "";
            }



        }

        System.out.println("key:" + key);
        //将map转化为Json字符串
        String valuesSkuJson  = JSON.toJSONString(map);
        //保存到作用域中
        request.setAttribute("valuesSkuJson" , valuesSkuJson);

        //页面渲染
        request.setAttribute("skuInfo" , skuInfo);
        request.setAttribute("spuSaleAttrList" , spuSaleAttrList);
        //热度计数器
        listService.incrHotScore(skuId);

        return "item";
    }



}
