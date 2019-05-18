package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.cart.constant.CartConst;
import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.order.service.CartService;
import com.atguigu.gmall.order.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
public class CartServiceImpl implements CartService{

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Reference
    private ManageService manageService;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 用户登录时，添加购物车
     *
     * @param skuId
     * @param userId
     * @param skuNum
     */
    @Override
    public void addToCart(String skuId, String userId, Integer skuNum) {
        //是否直接添加到数据库
        /*
        * 1.先看购物车中是否有该商品
        * select * from cartinfo where skuId=? and userId = ?
        * 2.有，数据相加 update
        * 3.没有，添加到数据库 insert
        * 4.将数据放入redis
        * */
        CartInfo cartInfo = new CartInfo();
        cartInfo.setSkuId(skuId);
        cartInfo.setUserId(userId);
        CartInfo cartInfoExist  = cartInfoMapper.selectOne(cartInfo);
        if (cartInfoExist != null){
            //更新mysql
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
           //skuPrice进一步赋值
            cartInfoExist.setSkuPrice(cartInfoExist.getCartPrice());
            //修改数据库
            cartInfoMapper.updateByPrimaryKeySelective(cartInfoExist);
            //放入redis



        }else {
            //购物车中没有该商品
            //根据skuId查询商品skuInfo   获取里边的skuName,price,imgUrl
            SkuInfo skuInfo = manageService.getSkuInfo(skuId);

            CartInfo cartInfo1 = new CartInfo();
            cartInfo1.setSkuPrice(skuInfo.getPrice());
            cartInfo1.setCartPrice(skuInfo.getPrice());
            cartInfo1.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo1.setSkuName(skuInfo.getSkuName());
            cartInfo1.setUserId(userId);
            cartInfo1.setSkuId(skuId);
            cartInfo1.setSkuNum(skuNum);

            cartInfoMapper.insertSelective(cartInfo1);
            //将新数据的值赋值给 cartInfoExist
            cartInfoExist = cartInfo1;

            //放入redis

        }
        //放入redis
        //获取jedis对象
        Jedis jedis = redisUtil.getJedis();
        //定义key  格式：user:userId:cart
        //使用哪种数据类型   用hash  易于修改
        //jedis.hset(key,field,value)  jedis.hget(key,field)  field用公共的skuId来做  value是当前的购物车对象
        String cartKey =  CartConst.USER_KEY_PREFIX+userId+ CartConst.USER_CART_KEY_SUFFIX;;
        jedis.hset(cartKey,skuId, JSON.toJSONString(cartInfoExist));
        //将用户的过期时间取得，user:userId:info
        String userInfoKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USERINFOKEY_SUFFIX;
        Long ttl = jedis.ttl(userInfoKey);
        //设置一下购物车的过期时间
        jedis.expire(cartKey,ttl.intValue());


        jedis.close();


    }

    /**
     * 根据userId查询用户购物车列表
     *
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartList(String userId) {
        //直接从redis中查询出来
        List<CartInfo> cartInfoList = new ArrayList<>();
        //需要jedis
        Jedis jedis = redisUtil.getJedis();
        //定义key
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        //通过key取数据，集合中每一个字符串应该是cartInfo对象
        List<String> cartJsons  = jedis.hvals(userCartKey);

        //遍历集合中的数据
        if (cartJsons  != null && cartJsons .size() > 0){
            for (String cartJson : cartJsons) {
                //将字符串转化为对象  然后放入对象集合中
                CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
                cartInfoList.add(cartInfo);
            }
            //给集合排序  一般按照添加时间排序，但是这里表里没有添加时间的字段,只能按照cartInfo.id排序
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {

                    return o1.getId().compareTo(o2.getId());
                }
            });
            return cartInfoList;

        }else {
            //从redis中未取得数据  需要查询数据库
            //通过userId从数据查询数据  然后放入缓存
            List<CartInfo> cartInfoLists = loadCartCache(userId);
            return  cartInfoList;

        }

    }

    /**
     * 合并购物车
     *
     * @param cartListCK
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> mergeToCartList(List<CartInfo> cartListCK, String userId) {
        //合并  cookie --> 数据库
        List<CartInfo> cartInfoListDB = cartInfoMapper.selectCartListWithCurPrice(userId);
        //准备合并  {条件:skuId}
        for (CartInfo cartInfoCK : cartListCK) {
            boolean isMatch =false;
            if (cartInfoListDB != null && cartInfoListDB.size() > 0) {
                for (CartInfo cartInfoDB : cartInfoListDB) {
                    //有相同商品
                    if (cartInfoCK.getSkuId().equals(cartInfoDB.getSkuId())) {
                        //数量相加
                        cartInfoDB.setSkuNum(cartInfoCK.getSkuNum() + cartInfoDB.getSkuNum());
                        //更新数据库
                        cartInfoMapper.updateByPrimaryKeySelective(cartInfoDB);
                        isMatch = true;
                    }
                }
            }
            //没有相同商品
            if (!isMatch){
                //直接插入数据库
                cartInfoCK.setUserId(userId);
                cartInfoMapper.insertSelective(cartInfoCK);
            }
        }
        //返回合并之后 的数据
        List<CartInfo> cartInfoList = loadCartCache(userId);
        //做个合并   被选中的商品
        for (CartInfo cartInfoDB : cartInfoList) {
            for (CartInfo cartInfoCK : cartListCK) {
                //判断条件  skuId相等
                if (cartInfoCK.getSkuId().equals(cartInfoDB.getSkuId())){
                    //cookie中选中状态为 1
                    if ("1".equals(cartInfoCK.getIsChecked())){
                        //将数据库中的商品状态变为1
                        cartInfoDB.setIsChecked(cartInfoCK.getIsChecked());
                        //将redis中被选中的商品进行选中!
                        checkCart(cartInfoCK.getSkuId(),cartInfoCK.getIsChecked(),userId);
                    }
                }
            }
        }

        return cartInfoList;
    }

    /**
     * 选中状态
     *
     * @param skuId
     * @param isChecked
     * @param userId
     */
    @Override
    public void checkCart(String skuId, String isChecked, String userId) {
        // a.获取选中的商品，isChecked=1 ，将原来购物车中的数据对应修改成 isChecked=1
        //获取原来购物车的集合
        Jedis jedis = redisUtil.getJedis();
        //定义Key
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        //获取数据  一个一个取并修改
        String cartJson = jedis.hget(userCartKey, skuId);
        //转化为cartInfo对象
        CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
        //修改对象数据
        cartInfo.setIsChecked(isChecked);
        //将修改后的数据放入redis
        jedis.hset(userCartKey,skuId,JSON.toJSONString(cartInfo));

        //b.将被选中的商品，单独在redis 中存储一份。 为了，能够在结算的时候，直接获取选中的数据
        //定义一个被选中的key  user:userId:checked
        String userCheckedKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CHECKED_KEY_SUFFIX;
        //保存被选中的商品{isChecked = 1}到 redis
        if ("1".equals(isChecked)){
            //保存数据
            jedis.hset(userCheckedKey,skuId,JSON.toJSONString(cartInfo));
        }else {
            //删除数据
            jedis.hdel(userCheckedKey,skuId);
        }
        jedis.close();
    }

    /**
     * 根据用户id查询被选中的购物车列表
     *
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        //获取jedis
        Jedis jedis = redisUtil.getJedis();
        //定义key
        String userCheckedKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CHECKED_KEY_SUFFIX;
        //获取数据
        List<String> cartCheckedList  = jedis.hvals(userCheckedKey);
        //集合字符串循环遍历
        for (String cartJson : cartCheckedList) {
            // cartJson 转化为对象
            CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
            //将其添加到对象集合中
            cartInfoList.add(cartInfo);
        }
        jedis.close();
        return cartInfoList;
    }

    /**
     * 通过用户id查询数据库，然后将数据库数据放入redis
     * @param userId
     * @return
     */
    private List<CartInfo> loadCartCache(String userId) {
        //我们需要查询实时价格   skuInfo.price
        List<CartInfo> cartInfoList = cartInfoMapper.selectCartListWithCurPrice(userId);
        if(cartInfoList == null || cartInfoList.size() == 0){
            return  null;
        }
        //放入redis
        Jedis jedis = redisUtil.getJedis();
        //定义Key
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        HashMap<String , String> map = new HashMap<>();
        //循环放入
        for (CartInfo cartInfo : cartInfoList) {
            //将集合中的每个对象放入redis
            //0jedis.hset(userCartKey,cartInfo.getSkuId(),JSON.toJSONString(cartInfo));
            map.put(cartInfo.getSkuId(),JSON.toJSONString(cartInfo));
        }
        //这个方法可以一次性放入多个
        jedis.hmset(userCartKey,map);
        jedis.close();

        //放回集合对象
        return  cartInfoList;

    }
}
