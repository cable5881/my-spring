@[TOC](目录)

# 前言
这次我们来完成AOP的流程，有一定的困难，大家做好准备，也希望笔者能用最简洁的语言给大家讲明白。如果读者不太了解Spring的AOP原理，可以先看《[面试问烂的 Spring AOP 原理、SpringMVC 过程](https://www.jianshu.com/p/e18fd44964eb)》这篇文章。文章中有张关于AOP执行的流程图 ，这里把它引用过来，往下阅读前最好能理解这段流程。
![AOP流程](https://img-blog.csdnimg.cn/20191210170902890.png)

# JoinPoint
通知方法的其中一个入参就是<kbd>JoinPoint</kbd>，通过它我们可以拿到当前被代理的对象、方法、参数等，甚至可以设置一个参数用于上下文传递，从接口方法就能判断出来了。
~~~java
package com.lqb.springframework.aop.aspect;

import java.lang.reflect.Method;

public interface JoinPoint {

    Object getThis();

    Object[] getArguments();

    Method getMethod();

    void setUserAttribute(String key, Object value);

    Object getUserAttribute(String key);
}
~~~
它的实现类就是前言中提到的外层拦截器对象，负责执行整个拦截器链，主要逻辑是：先遍历执行完拦截器链，最后才执行被代理的方法。这其实就是责任链模式的实现。
~~~java
package com.lqb.springframework.aop.intercept;


import com.lqb.springframework.aop.aspect.JoinPoint;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodInvocation implements JoinPoint {

    /**代理对象*/
    private Object proxy;

    /**被代理对象的class*/
    private Class<?> targetClass;

    /**被代理的对象*/
    private Object target;

    /**被代理的方法*/
    private Method method;

    /**被代理的方法的入参*/
    private Object [] arguments;

    /**拦截器链*/
    private List<Object> interceptorsAndDynamicMethodMatchers;

    /**用户参数*/
    private Map<String, Object> userAttributes;

    /**记录当前拦截器执行的位置*/
    private int currentInterceptorIndex = -1;

    public MethodInvocation(Object proxy,
                            Object target,
                            Method method,
                            Object[] arguments,
                            Class<?> targetClass,
                            List<Object> interceptorsAndDynamicMethodMatchers) {

        this.proxy = proxy;
        this.target = target;
        this.targetClass = targetClass;
        this.method = method;
        this.arguments = arguments;
        this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
    }

    /**
     * 调度执行拦截器链
     */
    public Object proceed() throws Throwable {
        //拦截器执行完了，最后真正执行被代理的方法
        if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
            return this.method.invoke(this.target,this.arguments);
        }

        //获取一个拦截器
        Object interceptorOrInterceptionAdvice =
                this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
        if (interceptorOrInterceptionAdvice instanceof MethodInterceptor) {
            MethodInterceptor mi = (MethodInterceptor) interceptorOrInterceptionAdvice;
            //执行通知方法
            return mi.invoke(this);
        } else {
            //跳过，调用下一个拦截器
            return proceed();
        }
    }

    @Override
    public Object getThis() {
        return this.target;
    }

    @Override
    public Object[] getArguments() {
        return this.arguments;
    }

    @Override
    public Method getMethod() {
        return this.method;
    }

    @Override
    public void setUserAttribute(String key, Object value) {
        if (value != null) {
            if (this.userAttributes == null) {
                this.userAttributes = new HashMap<>();
            }
            this.userAttributes.put(key, value);
        }
        else {
            if (this.userAttributes != null) {
                this.userAttributes.remove(key);
            }
        }
    }

    @Override
    public Object getUserAttribute(String key) {
        return (this.userAttributes != null ? this.userAttributes.get(key) : null);
    }
}
~~~

# MethodInterceptor
新建拦截器<kbd>MethodInterceptor</kbd>接口
~~~java
package com.lqb.springframework.aop.intercept;

public interface MethodInterceptor {
    Object invoke(MethodInvocation invocation) throws Throwable;
}
~~~

# Advice
在实现拦截器<kbd>MethodInterceptor</kbd>的子类前，先新建<kbd>Advice</kbd>，作为不同通知方法的顶层接口。
~~~java
package com.lqb.springframework.aop.aspect;

public interface Advice {
}
~~~
接着写一个抽象子类来封装不同的通知类型的共同逻辑：调用通知方法前先将入参赋值，这样用户在写通知方法时才能拿到实际值。
~~~java
package com.lqb.springframework.aop.aspect;

import java.lang.reflect.Method;


public abstract class AbstractAspectAdvice implements Advice {

    /**通知方法*/
    private Method aspectMethod;

    /**切面类*/
    private Object aspectTarget;

    public AbstractAspectAdvice(Method aspectMethod, Object aspectTarget) {
        this.aspectMethod = aspectMethod;
        this.aspectTarget = aspectTarget;
    }

	/**
     * 调用通知方法
     */
    public Object invokeAdviceMethod(JoinPoint joinPoint, Object returnValue, Throwable tx) throws Throwable {
        Class<?>[] paramTypes = this.aspectMethod.getParameterTypes();
        if (null == paramTypes || paramTypes.length == 0) {
            return this.aspectMethod.invoke(aspectTarget);
        } else {
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i] == JoinPoint.class) {
                    args[i] = joinPoint;
                } else if (paramTypes[i] == Throwable.class) {
                    args[i] = tx;
                } else if (paramTypes[i] == Object.class) {
                    args[i] = returnValue;
                }
            }
            return this.aspectMethod.invoke(aspectTarget, args);
        }
    }
}
~~~

# 实现多种通知类型
拦截器本质上就是各种通知方法的封装，因此继承<kbd>AbstractAspectAdvice</kbd>，实现<kbd>MethodInterceptor</kbd>。下面分别实现前置通知、后置通知和异常通知。

**前置通知**
~~~java
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
~~~
**后置通知**
~~~java
package com.lqb.springframework.aop.aspect;

import com.lqb.springframework.aop.intercept.MethodInterceptor;
import com.lqb.springframework.aop.intercept.MethodInvocation;

import java.lang.reflect.Method;

/**
 * 后置通知
 */
public class AfterReturningAdviceInterceptor extends AbstractAspectAdvice implements MethodInterceptor {

    private JoinPoint joinPoint;

    public AfterReturningAdviceInterceptor(Method aspectMethod, Object aspectTarget) {
        super(aspectMethod, aspectTarget);
    }

    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        //先调用下一个拦截器
        Object retVal = mi.proceed();
        //再调用后置通知
        this.joinPoint = mi;
        this.afterReturning(retVal, mi.getMethod(), mi.getArguments(), mi.getThis());
        return retVal;
    }

    private void afterReturning(Object retVal, Method method, Object[] arguments, Object aThis) throws Throwable {
        super.invokeAdviceMethod(this.joinPoint, retVal, null);
    }
}
~~~

**异常通知**
~~~java
package com.lqb.springframework.aop.aspect;

import com.lqb.springframework.aop.intercept.MethodInterceptor;
import com.lqb.springframework.aop.intercept.MethodInvocation;

import java.lang.reflect.Method;

/**
 * 异常通知
 */
public class AfterThrowingAdviceInterceptor extends AbstractAspectAdvice implements MethodInterceptor {


    private String throwingName;

    public AfterThrowingAdviceInterceptor(Method aspectMethod, Object aspectTarget) {
        super(aspectMethod, aspectTarget);
    }

    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        try {
            //直接调用下一个拦截器，如果不出现异常就不调用异常通知
            return mi.proceed();
        } catch (Throwable e) {
            //异常捕捉中调用通知方法
            invokeAdviceMethod(mi, null, e.getCause());
            throw e;
        }
    }

    public void setThrowName(String throwName) {
        this.throwingName = throwName;
    }
}
~~~

# AopProxy
新建创建代理的顶层接口<kbd>AopProxy</kbd>
~~~java
package com.lqb.springframework.aop;

public interface AopProxy {

    Object getProxy();

    Object getProxy(ClassLoader classLoader);
}
~~~
Spring选择代理创建逻辑是，如果被代理的类有实现接口用原生JDK的动态代理，否则使用Cglib的动态代理。所以<kbd>AopProxy</kbd>有两个子类<kbd>JdkDynamicAopProxy</kbd>和<kbd>CglibAopProxy</kbd>来实现这两种创建逻辑。

<kbd>JdkDynamicAopProxy</kbd>是利用JDK动态代理来创建代理的，因此需实现<kbd>InvocationHandler</kbd>接口。
~~~java
package com.lqb.springframework.aop;


import com.lqb.springframework.aop.intercept.MethodInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class JdkDynamicAopProxy implements AopProxy, InvocationHandler {

    private AdvisedSupport advised;

    public JdkDynamicAopProxy(AdvisedSupport config) {
        this.advised = config;
    }

    @Override
    public Object getProxy() {
        return getProxy(this.advised.getTargetClass().getClassLoader());
    }

    @Override
    public Object getProxy(ClassLoader classLoader) {
        return Proxy.newProxyInstance(classLoader, this.advised.getTargetClass().getInterfaces(), this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //获取拦截器链
        List<Object> interceptorsAndDynamicMethodMatchers =
                this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, this.advised.getTargetClass());
        //外层拦截器，用于控制拦截器链的执行
        MethodInvocation invocation = new MethodInvocation(
                proxy,
                this.advised.getTarget(),
                method,
                args,
                this.advised.getTargetClass(),
                interceptorsAndDynamicMethodMatchers
        );
        //开始连接器链的调用
        return invocation.proceed();
    }
}
~~~
<kbd>CglibAopProxy</kbd>不作为重点，因此不具体实现，有兴趣的读者可以挑战下。
~~~java
package com.lqb.springframework.aop;


import com.lqb.springframework.aop.support.AdvisedSupport;

public class CglibAopProxy implements AopProxy {

    public CglibAopProxy(AdvisedSupport config) {
    }

    @Override
    public Object getProxy() {
        return null;
    }

    @Override
    public Object getProxy(ClassLoader classLoader) {
        return null;
    }
}
~~~


成员变量<kbd>AdvisedSupport</kbd>封装了创建代理所需要的一切资源，从上面的代码就可以看出它至少封装了被代理的目标实例、拦截器链等，实际上它还负责解析AOP配置和创建拦截器。
~~~java
package com.lqb.springframework.aop.support;


import com.lqb.springframework.aop.aspect.AfterReturningAdviceInterceptor;
import com.lqb.springframework.aop.aspect.AfterThrowingAdviceInterceptor;
import com.lqb.springframework.aop.aspect.MethodBeforeAdviceInterceptor;
import com.lqb.springframework.aop.config.AopConfig;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdvisedSupport {

    /**被代理的类class*/
    private Class<?> targetClass;

    /**被代理的对象实力*/
    private Object target;

    /**被代理的方法对应的拦截器集合*/
    private Map<Method, List<Object>> methodCache;

    /**AOP外部配置*/
    private AopConfig config;

    /**切点正则表达式*/
    private Pattern pointCutClassPattern;

    public AdvisedSupport(AopConfig config) {
        this.config = config;
    }

    public Class<?> getTargetClass() {
        return this.targetClass;
    }

    public Object getTarget() {
        return this.target;
    }

    /**
     * 获取拦截器
     */
    public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, Class<?> targetClass) throws Exception {
        List<Object> cached = methodCache.get(method);
        if (cached == null) {
            Method m = targetClass.getMethod(method.getName(), method.getParameterTypes());
            cached = methodCache.get(m);
            this.methodCache.put(m, cached);
        }

        return cached;
    }

    public void setTargetClass(Class<?> targetClass) {
        this.targetClass = targetClass;
        parse();
    }

    /**
     * 解析AOP配置，创建拦截器
     */
    private void parse() {
        //编译切点表达式为正则
        String pointCut = config.getPointCut()
                .replaceAll("\\.", "\\\\.")
                .replaceAll("\\\\.\\*", ".*")
                .replaceAll("\\(", "\\\\(")
                .replaceAll("\\)", "\\\\)");
        //pointCut=public .* com.lqb.demo.service..*Service..*(.*)
        String pointCutForClassRegex = pointCut.substring(0, pointCut.lastIndexOf("\\(") - 4);
        pointCutClassPattern = Pattern.compile("class " + pointCutForClassRegex.substring(
                pointCutForClassRegex.lastIndexOf(" ") + 1));

        try {
            //保存切面的所有通知方法
            Map<String, Method> aspectMethods = new HashMap<>();
            Class aspectClass = Class.forName(this.config.getAspectClass());
            for (Method m : aspectClass.getMethods()) {
                aspectMethods.put(m.getName(), m);
            }

            //遍历被代理类的所有方法，为符合切点表达式的方法创建拦截器
            methodCache = new HashMap<>();
            Pattern pattern = Pattern.compile(pointCut);
            for (Method m : this.targetClass.getMethods()) {
                String methodString = m.toString();
                //为了能正确匹配这里去除函数签名尾部的throws xxxException
                if (methodString.contains("throws")) {
                    methodString = methodString.substring(0, methodString.lastIndexOf("throws")).trim();
                }

                Matcher matcher = pattern.matcher(methodString);
                if (matcher.matches()) {
                    //执行器链
                    List<Object> advices = new LinkedList<>();

                    //创建前置拦截器
                    if (!(null == config.getAspectBefore() || "".equals(config.getAspectBefore()))) {
                        //创建一个Advivce
                        MethodBeforeAdviceInterceptor beforeAdvice = new MethodBeforeAdviceInterceptor(
                                aspectMethods.get(config.getAspectBefore()),
                                aspectClass.newInstance()
                        );
                        advices.add(beforeAdvice);
                    }
                    //创建后置拦截器
                    if (!(null == config.getAspectAfter() || "".equals(config.getAspectAfter()))) {
                        AfterReturningAdviceInterceptor returningAdvice = new AfterReturningAdviceInterceptor(
                                aspectMethods.get(config.getAspectAfter()),
                                aspectClass.newInstance()
                        );
                        advices.add(returningAdvice);
                    }
                    //创建异常拦截器
                    if (!(null == config.getAspectAfterThrow() || "".equals(config.getAspectAfterThrow()))) {
                        AfterThrowingAdviceInterceptor throwingAdvice = new AfterThrowingAdviceInterceptor(
                                aspectMethods.get(config.getAspectAfterThrow()),
                                aspectClass.newInstance()
                        );
                        throwingAdvice.setThrowName(config.getAspectAfterThrowingName());
                        advices.add(throwingAdvice);
                    }

                    //保存被代理方法和执行器链的对应关系
                    methodCache.put(m, advices);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void setTarget(Object target) {
        this.target = target;
    }

    /**
     * 判断一个类是否需要被代理 
     */
    public boolean pointCutMatch() {
        return pointCutClassPattern.matcher(this.targetClass.toString()).matches();
    }
}
~~~

<kbd>AopConfig</kbd>保存了配置好切面、切点以及通知。
~~~java
package com.lqb.springframework.aop.config;

import lombok.Data;

@Data
public class AopConfig {

    //切点表达式
    private String pointCut;

    //前置通知方法
    private String aspectBefore;

    //后置通知方法
    private String aspectAfter;

    //切面类
    private String aspectClass;

    //异常通知方法
    private String aspectAfterThrow;

    //抛出的异常类型
    private String aspectAfterThrowingName;

}
~~~
终于AOP所需要的一切都已经准备好了，下面在容器启动主流程中将创建代理的逻辑填充进来。

# 对象实例化前创建代理
思考一下，创建的流程应该从哪里开始呢？AOP核心是动态代理，也就是说我们从IOC中通过<kbd>getBean()</kbd>拿到的必定是被代理过的实例，所以我们应该在Bean被加载到IOC容器前就应该创建好代理类。因此在<kbd>DefaultApplicationContext</kbd>的<kbd>instantiateBean()</kbd>方法中填充如下代码。
~~~java
private Object instantiateBean(String beanName, BeanDefinition beanDefinition) {
   //1、拿到要实例化的对象的类名
    String className = beanDefinition.getBeanClassName();

    //2、反射实例化，得到一个对象
    Object instance = null;
    try {
        Class<?> clazz = Class.forName(className);
        instance = clazz.newInstance();

        //############填充如下代码###############
        //获取AOP配置
        AdvisedSupport config = getAopConfig();
        config.setTargetClass(clazz);
        config.setTarget(instance);

        //符合PointCut的规则的话，将创建代理对象
        if(config.pointCutMatch()) {
        	//创建代理
            instance = createProxy(config).getProxy();
        }
        //#############填充完毕##############

    } catch (Exception e) {
        e.printStackTrace();
    }

    return instance;
}
~~~
获取AOP配置代码
~~~java
private AdvisedSupport getAopConfig() {
    AopConfig config = new AopConfig();
    config.setPointCut(this.reader.getConfig().getProperty("pointCut"));
    config.setAspectClass(this.reader.getConfig().getProperty("aspectClass"));
    config.setAspectBefore(this.reader.getConfig().getProperty("aspectBefore"));
    config.setAspectAfter(this.reader.getConfig().getProperty("aspectAfter"));
    config.setAspectAfterThrow(this.reader.getConfig().getProperty("aspectAfterThrow"));
    config.setAspectAfterThrowingName(this.reader.getConfig().getProperty("aspectAfterThrowingName"));
    return new AdvisedSupport(config);
}
~~~
创建代理代码
~~~java
private AopProxy createProxy(AdvisedSupport config) {
    Class targetClass = config.getTargetClass();
    //如果接口数量 > 0则使用JDK原生动态代理
    if(targetClass.getInterfaces().length > 0){
        return new JdkDynamicAopProxy(config);
    }
    return new CglibAopProxy(config);
}
~~~
# 成果展示
最后我们来检验一下AOP是否好用。将工程<kbd>mvn clean install</kbd>发布到本地仓库，还是利用我们上一篇文章最后的测试工程。

创建一个切面
~~~java
package com.lqb.demo.aspect;

import com.lqb.springframework.aop.aspect.JoinPoint;

import java.util.Arrays;

public class LogAspect {

    //在调用一个方法之前，执行before方法
    public void before(JoinPoint joinPoint){
        joinPoint.setUserAttribute("startTime_" + joinPoint.getMethod().getName(),System.currentTimeMillis());
        //这个方法中的逻辑，是由我们自己写的
        System.out.println(("Invoker Before Method!!!" +
                "\nTargetObject:" +  joinPoint.getThis() +
                "\nArgs:" + Arrays.toString(joinPoint.getArguments())));
    }

    //在调用一个方法之后，执行after方法
    public void after(JoinPoint joinPoint){
        System.out.println(("Invoker After Method!!!" +
                "\nTargetObject:" +  joinPoint.getThis() +
                "\nArgs:" + Arrays.toString(joinPoint.getArguments())));
        long startTime = (Long) joinPoint.getUserAttribute("startTime_" + joinPoint.getMethod().getName());
        long endTime = System.currentTimeMillis();
        System.out.println("use time :" + (endTime - startTime));
    }

    public void afterThrowing(JoinPoint joinPoint, Throwable ex){
        System.out.println(("出现异常" +
                "\nTargetObject:" +  joinPoint.getThis() +
                "\nArgs:" + Arrays.toString(joinPoint.getArguments()) +
                "\nThrows:" + ex.getMessage()));
    }
}
~~~
然后在配置文件中加入AOP的配置
~~~shell
scanPackage=com.lqb.demo

#切面表达式,expression#
pointCut=public .* com.lqb.demo..*Service..*(.*)
#切面类#
aspectClass=com.lqb.demo.aspect.LogAspect
#切面前置通知#
aspectBefore=before
#切面后置通知#
aspectAfter=after
#切面异常通知#
aspectAfterThrow=afterThrowing
#切面异常类型#
aspectAfterThrowingName=java.lang.Exception
~~~

再次运行Main方法
~~~java
package com.lqb.demo;

import com.lqb.springframework.context.ApplicationContext;
import com.lqb.springframework.context.support.DefaultApplicationContext;

public class Main {
    public static void main(String[] args) throws Exception {
        ApplicationContext applicationContext = new DefaultApplicationContext("application.properties");
        IHelloService helloService = (IHelloService) applicationContext.getBean("helloService");
        helloService.sayHello();
    }
}
~~~
"hello world"前后成功输出植入的代码
![成果展示](https://img-blog.csdnimg.cn/20191210220320609.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
# 最后
AOP流程较为复杂，最好能够画一画时序图，通过断点调试跟踪代码来理解。
