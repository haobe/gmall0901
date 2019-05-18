package com.atguigu.gmall.list.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.order.service.ListService;
import com.atguigu.gmall.order.service.ManageService;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class ListController {

    @Reference
    private ListService listService;

    @Reference
    private ManageService manageService;

    @RequestMapping("list.html")
    //@ResponseBody  注解有两个功能: 1.将数据转化为json字符串   2.将返回的值直接写到页面上
    public String getList(SkuLsParams skuLsParams, HttpServletRequest request){

        //设置每页显示条数
        skuLsParams.setPageSize(3);

        SkuLsResult skuLsResult = listService.search(skuLsParams);
        //获取商品集合
        List<SkuLsInfo> skuLsInfoList = skuLsResult.getSkuLsInfoList();

        // 渲染平台属性  平台属性值  先获取平台属性值id集合
        List<String> attrValueIdList = skuLsResult.getAttrValueIdList();
        //调用服务层方法
        List<BaseAttrInfo> baseAttrInfoList = manageService.getAttrList(attrValueIdList);

        //制作一个url  后面跟参数
        String urlParam = makeUrlParam(skuLsParams);

        //声明一个集合来存放面包屑
        List<BaseAttrValue> baseAttrValueArrayList = new ArrayList<>();

        //在拼接完url后，要把属性删除，需要把属性集合拿到  然后判断skuLsParams中的数据和baseAttrInfoList里边的数据是否一致，一致就需要把集合中的有关数据删除
        for (Iterator<BaseAttrInfo> iterator = baseAttrInfoList.iterator(); iterator.hasNext(); ) {
            BaseAttrInfo baseAttrInfo =  iterator.next();
            //得到baseAttrValue对象
            List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
            if (attrValueList != null && attrValueList.size() > 0){
                for (BaseAttrValue baseAttrValue : attrValueList) {
                    if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0){
                        //开始做比较判断
                        for (int i = 0; i < skuLsParams.getValueId().length; i++) {
                            String valueId = skuLsParams.getValueId()[i];
                            if (baseAttrValue.getId().equals(valueId)){
                                //删除数据
                                iterator.remove();
                                //break;
                                //构建一个面包屑
                                BaseAttrValue baseAttrValueed = new BaseAttrValue();
                                //平台属性名称 : 平台属性值名称
                                baseAttrValueed.setValueName(baseAttrInfo.getAttrName() + ":" + baseAttrValue.getValueName());
                                //点击面包屑的时候  将平台属性值id传入makeUrlParam方法中
                                String urlParams = makeUrlParam(skuLsParams,baseAttrValue.getId());
                                baseAttrValueed.setUrlParam(urlParams);

                                //将baseAttrValueed放入一个集合中  然后保存再返回页面
                                baseAttrValueArrayList.add(baseAttrValueed);



                            }
                        }
                    }
                }
            }
        }
        //保存totalPages
        request.setAttribute("totalPages",skuLsResult.getTotalPages());
        //保存当前页 pageNo
        request.setAttribute("pageNo",skuLsParams.getPageNo());

        //保存关键字
        request.setAttribute("keyword",skuLsParams.getKeyword());

        //保存参数
        request.setAttribute("urlParam",urlParam);

        //将baseAttrValueArrayList给页面显示
        request.setAttribute("baseAttrValueArrayList",baseAttrValueArrayList);

        //将数据保存  给页面显示
        request.setAttribute("baseAttrInfoList",baseAttrInfoList);

        //将商品信息集合保存  返回到页面
        request.setAttribute("skuLsInfoList",skuLsInfoList);

        return "list";
    }

    //制作参数的方法   因为不确定有多少个面包屑  所以需要可变参数  但是可变参数一般都会放在参数的最后
    private String makeUrlParam(SkuLsParams skuLsParams , String... excludeValueIds) {
        String urlParam="";
        if (skuLsParams.getKeyword() != null && skuLsParams.getKeyword().length() > 0){
            //拼接关键字参数
            urlParam += "keyword=" + skuLsParams.getKeyword();
        }
        if (skuLsParams.getCatalog3Id() != null && skuLsParams.getCatalog3Id().length() > 0){
            //拼接三级分类id参数
            //判断前边是否还有关键字参数 如果有需要加 &
            if (urlParam.length() > 0){
                urlParam += "&";
            }
            urlParam += "catalog3Id=" + skuLsParams.getCatalog3Id();
        }

        if (skuLsParams.getValueId() != null && skuLsParams.getValueId().length > 0){
            //循环拼接参数
            for (String valueId : skuLsParams.getValueId()) {
                //可变长参数不能为空
                if (excludeValueIds != null && excludeValueIds.length > 0){
                    //获取平台属性值
                    String excludeValueId = excludeValueIds[0];
                    if (excludeValueId.equals(valueId)){
                        //continue  结束本次循环开始下次循环
                        continue;
                    }
                }
                if (urlParam.length() > 0){
                    urlParam += "&";
                }
                urlParam += "valueId=" + valueId;

            }


        }

        return urlParam;
    }

}
