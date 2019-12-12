package com.lqb.springframework.aop;

public interface AopProxy {

    Object getProxy();

    Object getProxy(ClassLoader classLoader);
}
