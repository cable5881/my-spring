package com.lqb.springframework.context.support;

import com.lqb.springframework.annotation.Autowired;
import com.lqb.springframework.aop.AopProxy;
import com.lqb.springframework.aop.CglibAopProxy;
import com.lqb.springframework.aop.JdkDynamicAopProxy;
import com.lqb.springframework.aop.config.AopConfig;
import com.lqb.springframework.aop.support.AdvisedSupport;
import com.lqb.springframework.beans.BeanWrapper;
import com.lqb.springframework.beans.config.BeanDefinition;
import com.lqb.springframework.beans.support.BeanDefinitionReader;
import com.lqb.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultApplicationContext implements ApplicationContext {

    //配置文件路径
    private String configLocation;

    private BeanDefinitionReader reader;

    /**保存factoryBean和BeanDefinition的对应关系*/
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();

    /**保存了真正实例化的对象*/
    private Map<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

    public DefaultApplicationContext(String configLocation) {
        this.configLocation = configLocation;
        try {
            refresh();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refresh() throws Exception {
        //1、定位，定位配置文件
        reader = new BeanDefinitionReader(this.configLocation);

        //2、加载配置文件，扫描相关的类，把它们封装成BeanDefinition
        List<BeanDefinition> beanDefinitions = reader.loadBeanDefinitions();

        //3、注册，把配置信息放到容器里面
        //到这里为止，容器初始化完毕
        doRegisterBeanDefinition(beanDefinitions);

        //4、把不是延时加载的类，提前初始化
        doAutowired();
    }

    private void doAutowired() {
        for (Map.Entry<String, BeanDefinition> beanDefinitionEntry : beanDefinitionMap.entrySet()) {
            String beanName = beanDefinitionEntry.getKey();
            if (!beanDefinitionEntry.getValue().isLazyInit()) {
                try {
                    getBean(beanName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doRegisterBeanDefinition(List<BeanDefinition> beanDefinitions) throws Exception {
        for (BeanDefinition beanDefinition : beanDefinitions) {
            if (beanDefinitionMap.containsKey(beanDefinition.getFactoryBeanName())) {
                throw new Exception("The \"" + beanDefinition.getFactoryBeanName() + "\" is exists!!");
            }
            beanDefinitionMap.put(beanDefinition.getFactoryBeanName(), beanDefinition);
        }
    }

    @Override
    public Object getBean(String beanName) throws Exception {
        //如果是单例，那么在上一次调用getBean获取该bean时已经初始化过了，拿到不为空的实例直接返回即可
        Object instance = getSingleton(beanName);
        if (instance != null) {
            return instance;
        }

        BeanDefinition beanDefinition = this.beanDefinitionMap.get(beanName);

        //调用反射初始化Bean
        instance = instantiateBean(beanName, beanDefinition);

        //把这个对象封装到BeanWrapper中
        BeanWrapper beanWrapper = new BeanWrapper(instance);

        //把BeanWrapper保存到IOC容器中去
        //注册一个类名（首字母小写，如helloService）
        this.factoryBeanInstanceCache.put(beanName, beanWrapper);
        //注册一个全类名（如com.lqb.HelloService）
        this.factoryBeanInstanceCache.put(beanDefinition.getBeanClassName(), beanWrapper);

        //注入
        populateBean(beanName, new BeanDefinition(), beanWrapper);

        return this.factoryBeanInstanceCache.get(beanName).getWrappedInstance();
    }

    private Object getSingleton(String beanName) {
        BeanWrapper beanWrapper = factoryBeanInstanceCache.get(beanName);
        return beanWrapper == null ? null : beanWrapper.getWrappedInstance();
    }

    private Object instantiateBean(String beanName, BeanDefinition beanDefinition) {
        //1、拿到要实例化的对象的类名
        String className = beanDefinition.getBeanClassName();

        //2、反射实例化，得到一个对象
        Object instance = null;
        try {
            Class<?> clazz = Class.forName(className);
            instance = clazz.newInstance();

            //############填充如下代码###############
            AdvisedSupport config = getAopConfig();
            config.setTargetClass(clazz);
            config.setTarget(instance);

            //符合PointCut的规则的话，将创建代理对象
            if(config.pointCutMatch()) {
                instance = createProxy(config).getProxy();
            }
            //#############填充完毕##############

        } catch (Exception e) {
            e.printStackTrace();
        }

        return instance;
    }

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

    private AopProxy createProxy(AdvisedSupport config) {
        Class targetClass = config.getTargetClass();
        //如果接口数量 > 0则使用JDK原生动态代理
        if(targetClass.getInterfaces().length > 0){
            return new JdkDynamicAopProxy(config);
        }
        return new CglibAopProxy(config);
    }

    private void populateBean(String beanName, BeanDefinition beanDefinition, BeanWrapper beanWrapper) {

        Class<?> clazz = beanWrapper.getWrappedClass();

        //获得所有的成员变量
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            //如果没有被Autowired注解的成员变量则直接跳过
            if (!field.isAnnotationPresent(Autowired.class)) {
                continue;
            }

            Autowired autowired = field.getAnnotation(Autowired.class);
            //拿到需要注入的类名
            String autowiredBeanName = autowired.value().trim();
            if ("".equals(autowiredBeanName)) {
                autowiredBeanName = field.getType().getName();
            }

            //强制访问该成员变量
            field.setAccessible(true);

            try {
                if (this.factoryBeanInstanceCache.get(autowiredBeanName) == null) {
                    continue;
                }
                //将容器中的实例注入到成员变量中
                field.set(beanWrapper.getWrappedInstance(), this.factoryBeanInstanceCache.get(autowiredBeanName).getWrappedInstance());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public <T> T getBean(Class<T> requiredType) throws Exception {
        return (T) getBean(requiredType.getName());
    }

    public String[] getBeanDefinitionNames() {
        return this.beanDefinitionMap.keySet().toArray(new String[this.beanDefinitionMap.size()]);
    }

    public Properties getConfig() {
        return this.reader.getConfig();
    }

}
