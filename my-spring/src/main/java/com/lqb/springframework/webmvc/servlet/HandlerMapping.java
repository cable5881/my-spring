package com.lqb.springframework.webmvc.servlet;

import lombok.Data;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

@Data
public class HandlerMapping {

    //保存方法对应的实例
    private Object controller;

    //保存映射的方法
    private Method method;

    //URL的正则匹配
    private Pattern pattern;

    public HandlerMapping(Object controller, Method method, Pattern pattern) {
        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
    }

}
