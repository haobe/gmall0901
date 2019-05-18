package com.atguigu.gmall.passport.config;

import io.jsonwebtoken.*;

import java.util.Map;

public class JwtUtil {

    //生成token加密
    public static String encode(String key,Map<String,Object> param,String salt){
        if(salt!=null){
            key+=salt;
        }
        JwtBuilder jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS256,key);
        //用户信息
        jwtBuilder = jwtBuilder.setClaims(param);
        //生成token
        String token = jwtBuilder.compact();
        return token;

    }

    //解密
    public  static Map<String,Object> decode(String token , String key, String salt){
        Claims claims=null;
        if (salt!=null){
            key+=salt;
        }
        try {
            claims= Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
        } catch ( JwtException e) {
            return null;
        }
        return  claims;
    }

}
