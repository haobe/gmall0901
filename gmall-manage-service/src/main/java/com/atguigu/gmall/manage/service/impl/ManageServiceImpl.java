package com.atguigu.gmall.manage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.manage.constant.ManageConst;
import com.atguigu.gmall.manage.mapper.*;
import com.atguigu.gmall.order.service.ManageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.List;

@Service
public class ManageServiceImpl implements ManageService {

    @Autowired
    private BaseCatalog1Mapper baseCatalog1Mapper;

    @Autowired
    private BaseCatalog2Mapper baseCatalog2Mapper;

    @Autowired
    private BaseCatalog3Mapper baseCatalog3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private SpuInfoMapper spuInfoMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private RedisUtil redisUtil;


    @Override
    public List<BaseCatalog1> getCatalog1() {
        return baseCatalog1Mapper.selectAll();
    }

    @Override
    public List<BaseCatalog2> getCatalog2(String catalog1Id) {
        BaseCatalog2 baseCatalog2 = new BaseCatalog2();
        baseCatalog2.setCatalog1Id(catalog1Id);
        List<BaseCatalog2> baseCatalog2List = baseCatalog2Mapper.select(baseCatalog2);
        return baseCatalog2List;
    }

    @Override
    public List<BaseCatalog3> getCatalog3(String catalog2Id) {
        BaseCatalog3 baseCatalog3 = new BaseCatalog3();
        baseCatalog3.setCatalog2Id(catalog2Id);
        List<BaseCatalog3> baseCatalog3List = baseCatalog3Mapper.select(baseCatalog3);
        return baseCatalog3List;
    }

    @Override
    public List<BaseAttrInfo> getAttrList(String catalog3Id) {
//        BaseAttrInfo baseAttrInfo = new BaseAttrInfo();
//        baseAttrInfo.setCatalog3Id(catalog3Id);
//
//        return baseAttrInfoMapper.select(baseAttrInfo);
        List<BaseAttrInfo> baseAttrInfoList = baseAttrInfoMapper.getBaseAttrInfoListByCatalog3Id(Long.parseLong(catalog3Id));
        return baseAttrInfoList;

    }

    /*
    * 保存和编辑公用一个方法
    * 所以在方法执行前需要做判断
    *
    * */
    @Override
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        //根据id来判断为编辑还是保存
        if (baseAttrInfo.getId() != null && baseAttrInfo.getId().length() > 0){
            //编辑
            baseAttrInfoMapper.updateByPrimaryKeySelective(baseAttrInfo);

        }else {
            //添加  为了保证id自增  必须保证id为null
            baseAttrInfo.setId(null);

            baseAttrInfoMapper.insertSelective(baseAttrInfo);

        }

        //在这里真正的修改是将原数据删除  在插入新数据
        BaseAttrValue baseAttrValue = new BaseAttrValue();
        baseAttrValue.setAttrId(baseAttrInfo.getId());
        baseAttrValueMapper.delete(baseAttrValue);

        //先得到平台属性集合
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if (attrValueList != null && attrValueList.size() > 0){
            for (BaseAttrValue attrValue : attrValueList) {
                attrValue.setId(null);
                attrValue.setAttrId(baseAttrInfo.getId());
                baseAttrValueMapper.insertSelective(attrValue);
            }
        }
    }

    /**
     * 根据attrId查询平台属性值集合
     * @param attrId
     * @return
     */
    @Override
    public BaseAttrInfo getAttrInfo(String attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectByPrimaryKey(attrId);

        BaseAttrValue baseAttrValue = new BaseAttrValue();
        baseAttrValue.setAttrId(attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.select(baseAttrValue);

        baseAttrInfo.setAttrValueList(baseAttrValueList);

        return baseAttrInfo;
    }

    /**
     * 利用三级分类id查询商品集合
     * @param spuInfo
     * @return
     */
    @Override
    public List<SpuInfo> getSpuInfoList(SpuInfo spuInfo) {
        List<SpuInfo> spuInfoList = spuInfoMapper.select(spuInfo);
        return spuInfoList;
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        List<BaseSaleAttr> baseSaleAttrList = baseSaleAttrMapper.selectAll();
        return baseSaleAttrList;
    }

    @Override
    public void saveSpuInfo(SpuInfo spuInfo) {
        //先判断什么情况下保存，什么情况下更新  spuInfo表
        if (spuInfo.getId() == null || spuInfo.getId().length() == 0){
            //保存数据
            spuInfo.setId(null);
            spuInfoMapper.insertSelective(spuInfo);
        }else {
            spuInfoMapper.updateByPrimaryKeySelective(spuInfo);
        }

        //spuImage 图片表  先删除在新增
        //delete from spuImage where spuId = ?
        SpuImage spuImage = new SpuImage();
        spuImage.setSpuId(spuInfo.getId());
        spuImageMapper.delete(spuImage);

        //保存数据，先获取数据
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (spuImageList != null && spuImageList.size() > 0) {
            for (SpuImage image : spuImageList) {
                image.setId(null);
                image.setSpuId(spuInfo.getId());
                spuImageMapper.insertSelective(image);
            }
        }
        //销售属性     删除  插入
        SpuSaleAttr spuSaleAttr = new SpuSaleAttr();
        spuSaleAttr.setSpuId(spuInfo.getId());
        spuSaleAttrMapper.delete(spuSaleAttr);

        //销售属性值     删除  插入
        SpuSaleAttrValue spuSaleAttrValue = new SpuSaleAttrValue();
        spuSaleAttrValue.setSpuId(spuInfo.getId());
        spuSaleAttrValueMapper.delete(spuSaleAttrValue);

        //获取数据
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (spuSaleAttrList != null && spuSaleAttrList.size() > 0){
            //循环遍历
            for (SpuSaleAttr saleAttr : spuSaleAttrList) {
                saleAttr.setId(null);
                saleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insertSelective(saleAttr);

                //添加销售属性值
                List<SpuSaleAttrValue> spuSaleAttrValueList = saleAttr.getSpuSaleAttrValueList();
                if (spuSaleAttrValueList != null && spuSaleAttrValueList.size() > 0){
                    //循环遍历
                    for (SpuSaleAttrValue saleAttrValue : spuSaleAttrValueList) {
                        saleAttrValue.setId(null);
                        saleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValueMapper.insertSelective(saleAttrValue);

                    }
                }
            }
        }
    }

    @Override
    public List<SpuImage> getSpuImageList(String spuId) {
        SpuImage spuImage = new SpuImage();
        spuImage.setSpuId(spuId);
        return spuImageMapper.select(spuImage);
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(String spuId) {
        List<SpuSaleAttr>  spuSaleAttrList= spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
        return spuSaleAttrList;
    }

    @Override
    public void saveSkuInfo(SkuInfo skuInfo) {
        //判断什么情况下是插入数据，什么时候编辑数据
        if (skuInfo.getId() == null || skuInfo.getId().length() == 0){
            //插入数据
            skuInfo.setId(null);
            skuInfoMapper.insertSelective(skuInfo);

        }else {
            skuInfoMapper.updateByPrimaryKeySelective(skuInfo);

        }
        //skuImag  先删除数据  再插入数据
        SkuImage skuImage = new SkuImage();
        skuImage.setSkuId(skuInfo.getId());
        skuImageMapper.delete(skuImage);

        //获取数据
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (skuImageList != null && skuImageList.size() > 0){
            //循环插入数据
            for (SkuImage image : skuImageList) {
                image.setId(null);
                image.setSkuId(skuInfo.getId());
                skuImageMapper.insertSelective(image);
            }
        }

        //删除平台属性数据
        SkuAttrValue skuAttrValue = new SkuAttrValue();
        skuAttrValue.setSkuId(skuInfo.getId());
        skuAttrValueMapper.delete(skuAttrValue);
        //插入平台属性数据
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (skuAttrValueList != null && skuAttrValueList.size() > 0){

            for (SkuAttrValue attrValue : skuAttrValueList) {
                attrValue.setId(null);
                attrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insertSelective(attrValue);
            }
        }

        //删除销售属性
        SkuSaleAttrValue skuSaleAttrValue = new SkuSaleAttrValue();
        skuSaleAttrValue.setSkuId(skuInfo.getId());
        skuSaleAttrValueMapper.delete(skuSaleAttrValue);
        //插入销售属性
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (skuSaleAttrValueList != null && skuSaleAttrValueList.size() > 0){

            for (SkuSaleAttrValue saleAttrValue : skuSaleAttrValueList) {
                saleAttrValue.setId(null);
                saleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValueMapper.insertSelective(saleAttrValue);

            }
        }
    }

    /**
     * 根据skuId查询skuInfo对象
     * 用分布式锁解决缓存击穿问题
     *
     * @param skuId
     * @return
     */
    @Override
    public SkuInfo getSkuInfo(String skuId) {
        SkuInfo skuInfo = null;
        try {
            //测试redis
            Jedis jedis = redisUtil.getJedis();
            //存数据的时候用String类型 定义往redis中存的key
            String skuInfoKey = ManageConst.SKUKEY_PREFIX + skuId + ManageConst.SKUKEY_SUFFIX;
            //开始获取数据
            String skuJson = jedis.get(skuInfoKey);
            if (skuJson == null || skuJson.length() == 0){
                //走数据库
                System.out.println("缓存中没有数据！从数据库查!!!");
                //定义缓存锁的Key sku:skuId:lock 设置分布式锁的原因:解决缓存击穿问题
                String skuLockKey=ManageConst.SKUKEY_PREFIX+skuId+ManageConst.SKULOCK_SUFFIX;
                //执行命令
                String lockKey = jedis.set(skuLockKey, "OK", "NX", "PX", ManageConst.SKULOCK_EXPIRE_PX);
                if ("OK".equals(lockKey)){
                    System.out.println("获取分布式锁!");
                    //获取数据库数据  然后放入缓存
                    skuInfo = getSkuInfoDB(skuId);
                    //将数据放入缓存
                    //讲对象转换为字符串
                    String skuRedisStr  = JSON.toJSONString(skuInfo);
                    //放入redis
                    jedis.setex(skuInfoKey,ManageConst.SKUKEY_TIMEOUT,skuRedisStr);
                    jedis.close();
                    return skuInfo;
                }else {
                    System.out.println("等待!!!");
                    //没有获取带缓存锁的线程需要先sleep等待
                    Thread.sleep(1000);
                    return getSkuInfo(skuId);
                }
            }else {
                // 有数据
                skuInfo = JSON.parseObject(skuJson, SkuInfo.class);
                System.out.println("从缓存中查询!!!");
                jedis.close();
                return skuInfo;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        // 从数据库返回数据
        return getSkuInfoDB(skuId);
    }


    private SkuInfo getSkuInfoDB(String skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectByPrimaryKey(skuId);

        //select * from sku_image where sku_id = ?;
        SkuImage skuImage = new SkuImage();
        skuImage.setSkuId(skuId);
        List<SkuImage> skuImageList = skuImageMapper.select(skuImage);
        skuInfo.setSkuImageList(skuImageList);
        //缺少平台属性值对象
        SkuAttrValue skuAttrValue = new SkuAttrValue();
        skuAttrValue.setSkuId(skuId);
        List<SkuAttrValue> skuAttrValueList = skuAttrValueMapper.select(skuAttrValue);
        skuInfo.setSkuAttrValueList(skuAttrValueList);

        return skuInfo;
    }

    /**
     * 根据spuId查询所有的销售属性，再根据skuId将商品的销售属性值默认选中
     *
     * @param skuInfo
     * @return
     */
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(SkuInfo skuInfo) {
        List<SpuSaleAttr> spuSaleAttrList = spuSaleAttrMapper.
                selectSpuSaleAttrListCheckBySku(skuInfo.getId(), skuInfo.getSpuId());
        return spuSaleAttrList;
    }

    /**
     * 根据spuId查询所有skuId集合
     *
     * @param spuId
     * @return
     */
    @Override
    public List<SkuSaleAttrValue> getSkuSaleAttrValueListBySpu(String spuId) {
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.selectSkuSaleAttrValueListBySpu(spuId);
        return skuSaleAttrValueList;

    }

    /**
     * 根据平台属性值id查询平台属性
     *
     * @param attrValueIdList
     * @return
     */
    @Override
    public List<BaseAttrInfo> getAttrList(List<String> attrValueIdList) {
        //将集合里边的数据转化为可用参数  用工具类
        //attrValueIds = 1, 2 ,3
        String attrValueIds  = StringUtils.join(attrValueIdList.toArray(), ",");

        List<BaseAttrInfo> baseAttrInfoList = baseAttrInfoMapper.selectAttrInfoListByIds(attrValueIds);

        return baseAttrInfoList;

    }


}
