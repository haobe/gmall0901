package com.atguigu.gmall.manage.mapper;

import com.atguigu.gmall.bean.SkuSaleAttrValue;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface SkuSaleAttrValueMapper extends Mapper<SkuSaleAttrValue> {

    /**
     * 根据spuId查询所有SkuSaleAttrValue集合
     * @param spuId
     * @return
     */
    public List<SkuSaleAttrValue> selectSkuSaleAttrValueListBySpu (String spuId);
}
