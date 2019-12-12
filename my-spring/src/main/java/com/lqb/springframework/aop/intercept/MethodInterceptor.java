package com.lqb.springframework.aop.intercept;

public interface MethodInterceptor {
    Object invoke(MethodInvocation invocation) throws Throwable;
}
