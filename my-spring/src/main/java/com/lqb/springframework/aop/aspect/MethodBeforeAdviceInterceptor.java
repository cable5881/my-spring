package com.lqb.springframework.aop.aspect;

import com.lqb.springframework.aop.intercept.MethodInterceptor;
import com.lqb.springframework.aop.intercept.MethodInvocation;

import java.lang.reflect.Method;

/**
 * 前置通知
 */
public class MethodBeforeAdviceInterceptor extends AbstractAspectAdvice implements MethodInterceptor {

    private JoinPoint joinPoint;

    public MethodBeforeAdviceInterceptor(Method aspectMethod, Object aspectTarget) {
        super(aspectMethod, aspectTarget);
    }

    private void before(Method method, Object[] args, Object target) throws Throwable {
        super.invokeAdviceMethod(this.joinPoint, null, null);
    }

    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        this.joinPoint = mi;
        //在调用下一个拦截器前先执行前置通知
        before(mi.getMethod(), mi.getArguments(), mi.getThis());
        return mi.proceed();
    }
}
