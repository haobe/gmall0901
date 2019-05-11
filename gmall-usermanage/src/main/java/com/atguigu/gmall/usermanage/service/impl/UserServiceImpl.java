package com.atguigu.gmall.usermanage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.order.service.UserService;
import com.atguigu.gmall.usermanage.mapper.UserAddressMapper;
import com.atguigu.gmall.usermanage.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import redis.clients.jedis.Jedis;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Autowired
    private RedisUtil redisUtil;

    public String USERKEY_PREFIX="user:";

    public String USERINFOKEY_SUFFIX=":info";

    public int USERKEY_TIMEOUT=60*60*24;


    @Override
    public List<UserInfo> findAll() {
        return userInfoMapper.selectAll();
    }

    @Override
    public List<UserAddress> findUserAddressByUserId(String userId) {

        UserAddress userAddress = new UserAddress();

        userAddress.setUserId(userId);

        List<UserAddress> userAddresses = userAddressMapper.select(userAddress);

        return userAddresses;
    }

    /**
     * 登录
     *
     * @param userInfo
     * @return
     */
    @Override
    public UserInfo login(UserInfo userInfo) {
        //获取jedis
        Jedis jedis = redisUtil.getJedis();
        //数据库密码是加密的  所以在这里不加密密码是查不到数据的
        //获取明文密码
        String passwd = userInfo.getPasswd();
        //密码要加密
        String newPassword = DigestUtils.md5DigestAsHex(passwd.getBytes());

        userInfo.setPasswd(newPassword);
        //从数据库中查到userInfo
        UserInfo info = userInfoMapper.selectOne(userInfo);
        //将用户信息存到redis
        //定义往redis存数据的key
        String userKey = USERKEY_PREFIX + info.getId() + USERINFOKEY_SUFFIX;
        if (info != null){
            //将info放到redis
            jedis.setex(userKey,USERKEY_TIMEOUT, JSON.toJSONString(info));
            jedis.close();
            return info;
        }
        return null;
    }

    /**
     * 根据用户id认证redis中是否存在用户信息
     *
     * @param userId
     * @return
     */
    @Override
    public UserInfo verify(String userId) {
        //先获取jedis
        Jedis jedis = redisUtil.getJedis();
        //定义key
        String userKey = USERKEY_PREFIX + userId + USERINFOKEY_SUFFIX;
        //获取数据
        String userJson = jedis.get(userKey);
        //将字符串转化为对象
        if (userJson != null && userJson.length() > 0 ){
            //如果得到当前用户，可以延长用户的过期时间
            jedis.expire(userKey,USERKEY_TIMEOUT);

            UserInfo userInfo = JSON.parseObject(userJson, UserInfo.class);

            return userInfo;
        }

        jedis.close();
        return null;
    }



}
