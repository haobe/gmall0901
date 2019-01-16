package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.*;

import java.util.List;

public interface ManageService {
    /*
    * 查询一级分类
    * */
    public List<BaseCatalog1> getCatalog1();

    /*
    * 查询二级分类
    * */
    public List<BaseCatalog2> getCatalog2(String catalog1Id);


    /*
    * 查询三级分类
    * */
    public List<BaseCatalog3> getCatalog3(String catalog2Id);

    /*
    * 根据三级分类id查平台属性集合
    * */
    public List<BaseAttrInfo> getAttrList(String catalog3Id);

    /*
    * 保存平台属性和平台属性值
    * */
    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    /**
     * 根据attrId查询平台属性值集合
     * @param attrId
     * @return
     */
    BaseAttrInfo getAttrInfo(String attrId);

    /**
     * 利用三级分类id查询商品集合
     * @param spuInfo
     * @return
     */
    List<SpuInfo> getSpuInfoList(SpuInfo spuInfo);


    /**
     *查询所有的销售属性数据
     * @return
     */
    List<BaseSaleAttr> getBaseSaleAttrList();

    /**
     * 保存psuInfo
     * @param spuInfo
     */
    void saveSpuInfo(SpuInfo spuInfo);
}
