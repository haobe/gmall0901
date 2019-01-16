package com.atguigu.gmall.manage.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class ManageController {

    @Reference
    private ManageService manageService;


    @RequestMapping("index")
    public String index() {
        return "index";
    }
    @RequestMapping("attrListPage")
    public String attrListPage() {
        return "attrListPage";
    }

    /*
   * 查询一级分类
   * */
    @RequestMapping("getCatalog1")
    @ResponseBody
    public List<BaseCatalog1> getCatalog1(){
       return  manageService.getCatalog1();
    }

    /*
    * 查询二级分类
    * */
    @RequestMapping("getCatalog2")
    @ResponseBody
    public List<BaseCatalog2> getCatalog2(String catalog1Id){
        return manageService.getCatalog2(catalog1Id);

    }


    /*
    * 查询三级分类
    * */
    @RequestMapping("getCatalog3")
    @ResponseBody
    public List<BaseCatalog3> getCatalog3(String catalog2Id){
        return manageService.getCatalog3(catalog2Id);
    }

    /*
    * 根据三级分类id查平台属性集合
    * */
    @RequestMapping("attrInfoList")
    @ResponseBody
    public List<BaseAttrInfo> getAttrList(String catalog3Id){
        return manageService.getAttrList(catalog3Id);

    }


    @RequestMapping("saveAttrInfo")
    @ResponseBody
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo){
        //调用服务层做保存方法
        manageService.saveAttrInfo(baseAttrInfo);

    }

    /**
     *
     *  先从service中拿到平台属性，然后在controller中通过平台属性拿到平台属性值集合
     * @param attrId
     * @return
     */
    @RequestMapping("getAttrValueList")
    @ResponseBody
    public List<BaseAttrValue> getAttrValueList(String attrId){
        BaseAttrInfo attrInfo = manageService.getAttrInfo(attrId);
        return attrInfo.getAttrValueList();

    }

}
