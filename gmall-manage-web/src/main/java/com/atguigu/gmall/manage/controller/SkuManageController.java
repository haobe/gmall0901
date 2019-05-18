package com.atguigu.gmall.manage.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.bean.SkuLsInfo;
import com.atguigu.gmall.order.service.ListService;
import com.atguigu.gmall.order.service.ManageService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SkuManageController {

    @Reference
    private ManageService manageService;

    @Reference
    private ListService listService;

    @RequestMapping("saveSkuInfo")
    @ResponseBody
    public String saveSkuInfo(SkuInfo skuInfo){
        manageService.saveSkuInfo(skuInfo);
        return "OK";
    }

    /**
     * 上线
     * @return
     */
    @RequestMapping("onSale")
    @ResponseBody
    public String onSale(String skuId){

        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        //此时skuLsInfo为空  需要赋值
        SkuLsInfo skuLsInfo = new SkuLsInfo();
        //使用工具类  将skuInfo的属性值赋值给skuLsInfo
        BeanUtils.copyProperties(skuInfo,skuLsInfo);
        System.out.println(skuLsInfo.toString());
        listService.saveSkuInfo(skuLsInfo);

        return "OK";
    }


}
