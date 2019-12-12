@[TOC](目录)

# 前言
本篇文章旨在搭建项目，不写太多实际的代码，以后的文章我们一步步再填充进来。

# 新建Maven工程
创建一个空maven工程，pom中暂时不需要引入其他jar包（lombok可自行选择）
~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.lqb</groupId>
    <artifactId>my-spring</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <servlet.api.version>2.4</servlet.api.version>
    </properties>

    <dependencies>
        <!-- 为了代码简洁引入lombok,不需要再写setter和getter(可以不引入)-->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.8</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

</project>
~~~

新建好相关的目录，接下来我们会一步步填充需要的类。
![目录](https://img-blog.csdnimg.cn/20191206111029230.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)

# BeanFactory
<kbd>BeanFactory</kbd>是IOC容器的顶层父接口，大名鼎鼎的 <kbd>ApplicationContext</kbd>就是继承它，它定义了我们最常用的获取Bean的方法。
~~~java
package com.lqb.springframework.core.factory;

public interface BeanFactory {

    Object getBean(String name) throws Exception;

    <T> T getBean(Class<T> requiredType) throws Exception;
}
~~~
# ApplicationContext
<kbd>ApplicationContext</kbd> 我们非常熟悉，继承了<kbd>BeanFactory</kbd>、<kbd>MessageSource</kbd>、<kbd>ApplicationEventPublisher</kbd>等等接口，功能非常强大，但这也导致它的继承体系很庞大（如下图），对我们写一个简单的Spring框架很不利。
![ApplicationContext](https://img-blog.csdnimg.cn/20191206112759248.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
为了方便我们理解，也为了尽可能的简单，我们这里只建一个<kbd>ApplicationContext</kbd>接口且只继承<kbd>BeanFactory</kbd>，其实现类为<kbd>DefaultApplicationContext</kbd>。
~~~java
package com.lqb.springframework.context;

import com.lqb.springframework.core.factory.BeanFactory;

/**
 * 空接口，大家明白就好
 * 原接口需要继承ListableBeanFactory, HierarchicalBeanFactory等等，这里就简单继承BeanFactory 
 **/
public interface ApplicationContext extends BeanFactory {

}
~~~

相信大家都知道，<kbd>ApplicationContext </kbd>实现类中最重要的就是 **refresh()** 方法，它的流程就包括了**IOC容器初始化**、**依赖注入**和**AOP**，方法中的注释已经写的很明白了。
~~~java
package com.lqb.springframework.context.support;

import com.lqb.springframework.context.ApplicationContext;

public class DefaultApplicationContext implements ApplicationContext {

    //配置文件路径
    private String configLocation;

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

        //2、加载配置文件，扫描相关的类，把它们封装成BeanDefinition

        //3、注册，把配置信息放到容器里面(伪IOC容器)
        //到这里为止，容器初始化完毕

        //4、把不是延时加载的类，提前初始化
    }

	@Override
    public Object getBean(String beanName) throws Exception {
        return null;
    }

	@Override
    public <T> T getBean(Class<T> requiredType) throws Exception {
        return (T) getBean(requiredType.getName());
    }
}
~~~
成员变量<kbd>configLocation</kbd>保存了我们的配置文件路径，所以这里就先把这个配置文件先新建出来。在resource目录下需要新建一个配置文件<kbd>application.properties</kbd>，并且指定扫描的包路径。
```bash
scanPackage=com.lqb.app.v2
```


# BeanDefinition
我们原来使用xml作为配置文件时，定义的Bean其实在IOC容器中被封装成了<kbd>BeanDefinition</kbd>，也就是Bean的配置信息，包括className、是否为单例、是否需要懒加载等等。它是一个interface，这里我们直接定义成class。
~~~java
package com.lqb.springframework.beans.config;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BeanDefinition {

    private String beanClassName;

    private boolean lazyInit = false;

    private String factoryBeanName;

    public BeanDefinition() {}
}
~~~

# BeanDefinitionReader
我们需要读取配置文件，扫描相关的类才能解析成<kbd>BeanDefinition</kbd>，这个读取 + 扫描的类就是<kbd>BeanDefinitionReader</kbd>。
~~~java
package com.lqb.springframework.beans.support;

import java.util.Properties;

public class BeanDefinitionReader {

    //配置文件
    private Properties config = new Properties();

    //配置文件中指定需要扫描的包名
    private final String SCAN_PACKAGE = "scanPackage";

    public BeanDefinitionReader(String... locations) {

    }
    
	public Properties getConfig() {
        return config;
    }
}
~~~

# BeanWrapper
当<kbd>BeanDefinition</kbd>的Bean配置信息被读取并实例化成一个实例后，这个实例封装在<kbd>BeanWrapper</kbd>中。
~~~java
package com.lqb.springframework.beans;

public class BeanWrapper {

    /**Bean的实例化对象*/
    private Object wrappedObject;

    public BeanWrapper(Object wrappedObject) {
        this.wrappedObject = wrappedObject;
    }

    public Object getWrappedInstance() {
        return this.wrappedObject;
    }

    public Class<?> getWrappedClass() {
        return getWrappedInstance().getClass();
    }
}
~~~

# annotation
当Bean需要被Spring进行管理时，我们有<kbd>@Component</kbd>、<kbd>@Controller</kbd>、<kbd>@Service</kbd>、<kbd>@Repository</kbd>等注解。需要注入时则通过<kbd>@Autowired</kbd>或<kbd>@Resource</kbd>。这里我们先把常用的都先定义出来，统一放在<kbd>com.xxx.springframework.annotation</kbd>下。
~~~java
package com.lqb.springframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {
    String value() default "";
}
~~~

~~~java
package com.lqb.springframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {
    String value() default "";
}
~~~

~~~java
package com.lqb.springframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface Controller {
    String value() default "";
}
~~~

~~~java
package com.lqb.springframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
@Documented
public @interface Service {
    String value() default "";
}
~~~
~~~java
package com.lqb.springframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
    String value() default "";
}

~~~
~~~java
package com.lqb.springframework.annotation;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    String value() default "";
}
~~~
# 最后
OK，到这里我们就基本搭建好了整个架构，IOC和DI都是基于目前这个框架，等后面写到AOP和MVC再添加一些新的类。整个目录结构如下。下一篇文章我们正式开始手写IOC！
![最终目录](https://img-blog.csdnimg.cn/20191206194834798.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)

