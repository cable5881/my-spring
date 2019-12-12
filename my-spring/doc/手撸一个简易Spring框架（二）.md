@[TOC](目录)

# 前言
上一篇文章已经将整体的脉络搭建出来了，这次正式开始手写IOC。<kbd>ApplicationContext</kbd>中的<kbd>refresh()</kbd>方法是Spring启动的关键，我们就从这里开始一步步开始填坑。

# 读取配置文件
在<kbd>DefaultApplicationContext</kbd>中，我们先完成第一步，定位和解析配置文件。
~~~java
private void refresh() throws Exception {
    //1、定位，定位配置文件
    reader = new BeanDefinitionReader(this.configLocation);

    //2、加载配置文件，扫描相关的类，把它们封装成BeanDefinition

    //3、注册，把配置信息放到容器里面
    //到这里为止，容器初始化完毕

    //4、把不是延时加载的类，提前初始化
}
~~~
完成<kbd>BeanDefinitionReader</kbd>中的构造方法，流程分为三步走：

- 将我们传入的配置文件路径解析为文件流
- 将文件流保存为Properties，方便我们通过Key-Value的形式来读取配置文件信息
- 根据配置文件中配置好的扫描路径，开始扫描该路径下的所有class文件并保存到集合中（下一节介绍）
~~~java
/**保存了所有Bean的className*/
private List<String> registyBeanClasses = new ArrayList<>();

public BeanDefinitionReader(String... locations) {
    try(
        //1.定位，通过URL定位找到配置文件，然后转换为文件流
        InputStream is = this.getClass().getClassLoader()
                .getResourceAsStream(locations[0].replace("classpath:", ""))) {
                
        //2.加载，保存为properties
        config.load(is);
    } catch (IOException e) {
        e.printStackTrace();
    }

    //3.扫描，扫描资源文件(class)，并保存到集合中
    doScanner(config.getProperty(SCAN_PACKAGE));
}
~~~
# 扫描配置文件
<kbd>doScanner()</kbd>是递归方法，当它发现当前扫描的文件是目录时要发生递归，直到找到一个class文件，然后把它的全类名添加到集合中
~~~java
/**
  * 扫描资源文件的递归方法
  */
private void doScanner(String scanPackage) {
    URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
    File classPath = new File(url.getFile());
    for (File file : classPath.listFiles()) {
        if (file.isDirectory()) {
        	//如果是目录则递归调用，直到找到class
            doScanner(scanPackage + "." + file.getName());
        } else {
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            String className = (scanPackage + "." + file.getName().replace(".class", ""));
            //className保存到集合
            registyBeanClasses.add(className);
        }
    }
}
~~~

# 封装成BeanDefinition
<kbd>refresh()</kbd>中接着填充下一步，将上一步扫描好的class集合封装进<kbd>BeanDefinition</kbd>
~~~java
private void refresh() throws Exception {
    //1、定位，定位配置文件
    reader = new BeanDefinitionReader(this.configLocation);

    //2、加载配置文件，扫描相关的类，把它们封装成BeanDefinition
    List<BeanDefinition> beanDefinitions = reader.loadBeanDefinitions();

    //3、注册，把配置信息放到容器里面
    //到这里为止，容器初始化完毕

    //4、把不是延时加载的类，提前初始化
}
~~~
回到<kbd>BeanDefinitionReader</kbd>中填充<kbd>loadBeanDefinitions()</kbd>方法。逻辑是：扫描class集合，如果是被<kbd>@Component</kbd>注解的class则需要封装成<kbd>BeanDefinition</kbd>，表示着它将来可以被IOC进行管理。
~~~java
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
~~~
<kbd>BeanDefinition</kbd>主要保存两个参数，<kbd>factoryBeanName</kbd>和<kbd>beanClassName</kbd>，一个是保存实现类的类名（首字母小写）或其接口全类名，另一个是保存实现类的全类名，如下图所示。通过保存这两个参数我们可以实现用类名或接口类型来依赖注入。![在这里插入图片描述](https://img-blog.csdnimg.cn/20191209110721430.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
# 注册到容器
将<kbd>BeanDefinition</kbd>保存为以<kbd>factoryBeanName</kbd>为Key的Map
~~~java
//保存factoryBean和BeanDefinition的对应关系
private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();

private void refresh() throws Exception {
    //1、定位，定位配置文件
    reader = new BeanDefinitionReader(this.configLocation);

    //2、加载配置文件，扫描相关的类，把它们封装成BeanDefinition
    List<BeanDefinition> beanDefinitions = reader.loadBeanDefinitions();

    //3、注册，把配置信息放到容器里面
    //到这里为止，容器初始化完毕
    doRegisterBeanDefinition(beanDefinitions);

    //4、把不是延时加载的类，提前初始化
}

private void doRegisterBeanDefinition(List<BeanDefinition> beanDefinitions) throws Exception {
    for (BeanDefinition beanDefinition : beanDefinitions) {
        if (beanDefinitionMap.containsKey(beanDefinition.getFactoryBeanName())) {
            throw new Exception("The \"" + beanDefinition.getFactoryBeanName() + "\" is exists!!");
        }
        beanDefinitionMap.put(beanDefinition.getFactoryBeanName(), beanDefinition);
    }
}
~~~

# 最后
到这里我们的IOC容器就算已经完成了，<kbd>refresh()</kbd>中的第4步是Bean真正实例化的流程，我们下一章再来介绍。最后做一个IOC容器初始化的总结：
- 找到配置文件，封装成Properties
- 读取Properties中的扫描路径<kbd>scanPackage</kbd>变量，扫描该路径下的class并保存到集合中
- 读取上一步扫描好的class集合，封装成<kbd>BeanDefinition</kbd>集合
- 将<kbd>BeanDefinition</kbd>集合注册到容器中
