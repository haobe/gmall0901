package com.atguigu.gmall.config;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.utils.HttpClientUtil;
import io.jsonwebtoken.impl.Base64UrlCodec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

@Component
public class AuthInterceptor extends HandlerInterceptorAdapter {

    //进入控制器之前
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取newToken  登录成功的时候才有newToken产生
        String token = request.getParameter("newToken");
        //登录的时候,token不为空
        if (token != null){
            //放入cookie中
            //以前的方式
//          Cookie cookie = new Cookie("token",token);
//          //cookie放入作用域中
//          response.addCookie(cookie);
//          //跳转页面
//          response.sendRedirect("index.jsp");
            //现在的方式   放入工具类
            CookieUtil.setCookie(request,response,"token",token,WebConst.COOKIE_MAXAGE,false);
        }
        //如果token为空  说明request.getParameter("newToken");没有取到值  当用户登陆后，跳转其他业务时，request.getParameter("newToken");没有值
        //在登录时，已经将token放入cookie中，所以，将cookie中的值取出来，赋值给token
        if (token == null){
            token = CookieUtil.getCookieValue(request,"token",false);
        }

        //再判断token
        if (token != null){
            //eyJhbGciOiJIUzI1NiJ9.eyJuaWNrTmFtZSI6IuWwmuehheiwtyIsInVzZXJJZCI6IjEifQ.NImM-yfQP67BFnkduLjdy4zm-Q0iULJVRMtg_Y2UeGI
            //给token解密
            Map map = getUserMapByToken(token);
            String nickName = (String) map.get("nickName");
            //保存到作用域中
            request.setAttribute("nickName",nickName);
        }
        //在拦截器上判断  控制器上是否有自定义注解  并且为 true
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        LoginRequire methodAnnotation = handlerMethod.getMethodAnnotation(LoginRequire.class);
        if (methodAnnotation != null){
            //做一个认证
            //获取 salt
            String salt = request.getHeader("x-forwarded-for");
            //调用verify控制器
            String result = HttpClientUtil.doGet(WebConst.VERIFY_ADDRESS + "?token=" + token + "&salt=" + salt);

            if ("success" .equals(result)){
                //记录用户的id
                //给token解密
                Map map = getUserMapByToken(token);
                String userId = (String) map.get("userId");
                //保存到作用域中
                request.setAttribute("userId",userId);
                //放行
                return true;
            }else {
                if (methodAnnotation.autoRedirect()){
                    //必须要登录!跳转到登录页面
                    String requestURI = request.getRequestURI();
                    System.out.println("requestURI:" + requestURI);
                    String requestURL = request.getRequestURL().toString();
                    System.out.println("requestURL:" + requestURL);

                    //对url进行编码
                    String encodeURL = URLEncoder.encode(requestURL, "UTF-8");
                    //http%3A%2F%2Fitem.gmall.com%2F55.html
                    System.out.println("encodeURL:" + encodeURL);

                    //开始准备跳转到登录
                    //http://passport.atguigu.com/index?originUrl=http%3A%2F%2Fitem.gmall.com%2F55.html
                    response.sendRedirect(WebConst.LOGIN_ADDRESS + "?originUrl=" + encodeURL );

                    return false;
                }
            }


        }


        return true;
    }

    private Map getUserMapByToken(String token) {
        //想办法取得中间部分 获取用户信息
        String tokenUserInfo = StringUtils.substringBetween(token, ".");
        //tokenUserInfo=eyJuaWNrTmFtZSI6IuWwmuehheiwtyIsInVzZXJJZCI6IjEifQ
        //通过base64进行解密
        Base64UrlCodec base64UrlCodec = new Base64UrlCodec();
        //得到是字节数组
        byte[] bytes = base64UrlCodec.decode(tokenUserInfo);
        //将字节数组转化为字符串
        String tokenJson = null;
        try {
            tokenJson  = new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        //将字符串转为map
        Map map = JSON.parseObject(tokenJson, Map.class);
        return map;
    }


}
