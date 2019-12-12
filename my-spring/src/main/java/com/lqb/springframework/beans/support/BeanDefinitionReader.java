package com.lqb.springframework.beans.support;

import com.lqb.springframework.annotation.Component;
import com.lqb.springframework.beans.config.BeanDefinition;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class BeanDefinitionReader {

    //配置文件
    private Properties config = new Properties();

    //配置文件中指定需要扫描的包名
    private final String SCAN_PACKAGE = "scanPackage";

    /**保存了所有Bean的className*/
    private List<String> registyBeanClasses = new ArrayList<>();

    public BeanDefinitionReader(String... locations) {
        try(
                //1.定位，通过URL定位找到配置文件，然后转换为文件流
                InputStream is = this.getClass().getClassLoader()
                        .getResourceAsStream(locations[0].replace("classpath:", ""))
        ) {
            //2.加载，保存为properties
            config.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //3.扫描，扫描资源文件(class)，并保存到集合中
        doScanner(config.getProperty(SCAN_PACKAGE));
    }

    /**
     * 扫描资源文件的递归方法
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = (scanPackage + "." + file.getName().replace(".class", ""));
                registyBeanClasses.add(className);
            }
        }
    }

    /**
     * 把配置文件中扫描到的所有的配置信息转换为BeanDefinition对象
     */
    public List<BeanDefinition> loadBeanDefinitions() {
        List<BeanDefinition> result = new ArrayList<>();
        try {
            for (String className : registyBeanClasses) {
                Class<?> beanClass = Class.forName(className);
                //如果是一个接口，是不能实例化的，不需要封装
                if (beanClass.isInterface()) {
                    continue;
                }

                Annotation[] annotations = beanClass.getAnnotations();
                if (annotations.length == 0) {
                    continue;
                }

                for (Annotation annotation : annotations) {
                    Class<? extends Annotation> annotationType = annotation.annotationType();
                    //只考虑被@Component注解的class
                    if (annotationType.isAnnotationPresent(Component.class)) {
                        //beanName有三种情况:
                        //1、默认是类名首字母小写
                        //2、自定义名字（这里暂不考虑）
                        //3、接口注入
                        result.add(doCreateBeanDefinition(toLowerFirstCase(beanClass.getSimpleName()), beanClass.getName()));

                        Class<?>[] interfaces = beanClass.getInterfaces();
                        for (Class<?> i : interfaces) {
                            //接口和实现类之间的关系也需要封装
                            result.add(doCreateBeanDefinition(i.getName(), beanClass.getName()));
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 相关属性封装到BeanDefinition
     */
    private BeanDefinition doCreateBeanDefinition(String factoryBeanName, String beanClassName) {
        BeanDefinition beanDefinition = new BeanDefinition();
        beanDefinition.setFactoryBeanName(factoryBeanName);
        beanDefinition.setBeanClassName(beanClassName);
        return beanDefinition;
    }

    /**
     * 将单词首字母变为小写
     */
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    public Properties getConfig() {
        return config;
    }

}