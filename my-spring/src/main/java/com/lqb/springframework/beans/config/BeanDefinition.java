package com.lqb.springframework.beans.config;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BeanDefinition {


    private String beanClassName;

    /**是否开启懒加载*/
    private boolean lazyInit = false;

    private String factoryBeanName;

    public BeanDefinition() {
    }

}
