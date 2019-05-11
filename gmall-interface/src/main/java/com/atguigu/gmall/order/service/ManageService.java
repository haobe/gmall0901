package com.atguigu.gmall.order.service;

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

    /**
     * select * from spuImag where spuId = ?
     * 根据spuId查询SpuImage
     * @param spuId
     * @return
     */
    List<SpuImage> getSpuImageList(String spuId);

    /**
     * 根据spuId查询销售属性集合
     * @param spuId
     * @return
     */
    List<SpuSaleAttr> getSpuSaleAttrList(String spuId);

    /**
     * 保存商品数据
     * @param skuInfo
     */
    void saveSkuInfo(SkuInfo skuInfo);

    /**
     * 根据skuId查询skuInfo对象
     * @param skuId
     * @return
     */
    SkuInfo getSkuInfo(String skuId);

    /**
     * 根据spuId查询所有的销售属性，再根据skuId将商品的销售属性值默认选中
     * @param skuInfo
     * @return
     */
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(SkuInfo skuInfo);

    /**
     * 根据spuId查询所有skuId集合
     * @param spuId
     * @return
     */
    public List<SkuSaleAttrValue> getSkuSaleAttrValueListBySpu(String spuId);

    /**
     * 根据平台属性值id查询平台属性
     * @param attrValueIdList
     * @return
     */
    List<BaseAttrInfo> getAttrList(List<String> attrValueIdList);
}
