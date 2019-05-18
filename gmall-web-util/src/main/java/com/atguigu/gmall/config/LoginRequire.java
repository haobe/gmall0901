package com.atguigu.gmall.config;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


//注解头有两个参数进行设置，注解在哪里使用
@Target(ElementType.METHOD)//在方法级别上生效
@Retention(RetentionPolicy.RUNTIME) //注解的声明周期
public @interface LoginRequire {
    //自定义一个参数   true表示必须登录  false可以不用登陆
    boolean autoRedirect() default true;


}
