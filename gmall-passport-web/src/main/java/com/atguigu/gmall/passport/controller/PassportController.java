package com.atguigu.gmall.passport.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.passport.config.JwtUtil;
import com.atguigu.gmall.order.service.UserService;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class PassportController {

    @Value("${token.key}")
    private String key;

    @Reference
    private UserService userService;


    @RequestMapping("index")
    public String index(HttpServletRequest request){
        String originUrl = request.getParameter("originUrl");
        //存储originUrl：从什么模块跳转到登录模块的模块地址
        request.setAttribute("originUrl",originUrl);
        return "index";
    }

    @RequestMapping("login")
    @ResponseBody
    public String login(UserInfo userInfo, HttpServletRequest request){
        //从nginx获取salt
        String salt = request.getHeader("X-forwarded-for");
        System.out.println("salt:" + salt);
        //将userInfo 中存的数据和调用service层查到的数据验证
        UserInfo info = userService.login(userInfo);
        if (info != null){
            //放入配置文件中 String key = "atguigu";
            //ip地址从服务器获取  从nginx获取 String ip="192.168.67.201";
            Map map = new HashMap();
            map.put("userId",info.getId());
            map.put("nickName",info.getNickName());
            String token = JwtUtil.encode(key, map, salt);
            System.out.println("token:" + token);
            return token;
        }else {
            return "fail";
        }
    }

    /**
     * 认证中心
     * @param request
     * @return
     */
    @RequestMapping("verify")
    @ResponseBody
    public String verify(HttpServletRequest request){
        //解密tokan需要 salt key token
        //key 在配置文件中  salt，token在request中
        String token = request.getParameter("token");
        String salt = request.getParameter("salt");
        //调用工具类进行解密
        Map<String, Object> map = JwtUtil.decode(token, key, salt);
        if (map != null && map.size() > 0){
            //获取用户id
            String userId = (String) map.get("userId");
            //调用服务层  判断用户是否存在于redis
            UserInfo userInfo = userService.verify(userId);
            if (userInfo != null){
                return "success";
            }else {
                return "fail";
            }
        }
        return "fail";
    }


}
