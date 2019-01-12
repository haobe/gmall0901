package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.UserInfo;

import java.util.List;

public interface UserService {

    /*
    * 查询所有用户
    * */
    List<UserInfo> findAll();

    /*
    * 根据用户id查询用户地址
    * */
    List<UserAddress> findUserAddressByUserId(String userId);


}
