@[TOC](目录)

# 前言
这次我们来完成MVC的模块，最终目标需要达到用户能够使用<kbd>Controller</kbd>，浏览器能够显示模板引擎渲染的结果。为了能够解析前端的HTTP协议请求，需要在项目POM中引入<kbd>Servlet</kbd>。
~~~xml
<!--引入Servlet-->
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>servlet-api</artifactId>
    <version>${servlet.api.version}</version>
    <scope>provided</scope>
</dependency>
~~~

# DispatcherServlet
<kbd>DispatcherServlet</kbd>继承自<kbd>HttpServlet</kbd>，必然需要重写<kbd>doGet</kbd>和<kbd>doPost</kbd>来接收和处理用户的前端请求。又因为<kbd>DispatcherServlet</kbd>在初始化的时候就要先初始化<kbd>ApplicationContext</kbd>以及MVC九大组件(为了达成目的只需实现其中三个即可)，因此还要重写父类<kbd>init()</kbd>方法。
~~~java
package com.lqb.springframework.webmvc.servlet;


import com.lqb.springframework.annotation.Controller;
import com.lqb.springframework.annotation.RequestMapping;
import com.lqb.springframework.context.support.DefaultApplicationContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    /**配置文件地址，从web.xml中获取*/
    private static final String CONTEXT_CONFIG_LOCATION = "contextConfigLocation";

    private DefaultApplicationContext context;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、初始化ApplicationContext
        context = new DefaultApplicationContext(config.getInitParameter(CONTEXT_CONFIG_LOCATION));

        //2、初始化Spring MVC 九大组件
        initStrategies(context);
    }

    //初始化策略
    protected void initStrategies(DefaultApplicationContext context) {
        //多文件上传的组件

        //初始化本地语言环境

        //初始化模板处理器

        //handlerMapping，必须实现
        initHandlerMappings(context);

        //初始化参数适配器，必须实现
        initHandlerAdapters(context);

        //初始化异常拦截器

        //初始化视图预处理器

        //初始化视图转换器，必须实现
        initViewResolvers(context);

        //参数缓存器
    }
}    
~~~

下面我们先来实现这三个组件<kbd>HandlerMapping</kbd>、<kbd>HandlerAdapter</kbd>以及<kbd>ViewResolver</kbd>

# HandlerMapping
<kbd>HandlerMapping</kbd>保存了用户写的<kbd>Controller</kbd>实例、所有浏览器能访问到的方法，以及使用<kbd>@RequestMapping</kbd>定义的URL表达式
~~~java
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
~~~

填充到<kbd>DispatcherServlet</kbd>的流程中，代码逻辑如下：
- 遍历容器中的Bean，找到被<kbd>@Controller</kbd>注解的
- 遍历<kbd>Controller</kbd>的所有方法，找到被<kbd>@RequestMapping</kbd>注解的
- 获取URL表达式，编译成正则
- 将<kbd>HandlerMapping</kbd>添加到集合中保存起来
~~~java
private List<HandlerMapping> handlerMappings = new ArrayList<>();

private void initHandlerMappings(DefaultApplicationContext context) {
    String[] beanNames = context.getBeanDefinitionNames();

    try {
        for (String beanName : beanNames) {
            Object controller = context.getBean(beanName);
            Class<?> clazz = controller.getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }

            String baseUrl = "";
            //获取Controller的url配置
            if (clazz.isAnnotationPresent(RequestMapping.class)) {
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取Method的url配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {

                //没有加RequestMapping注解的直接忽略
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }

                //映射URL
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String regex = ("/" + baseUrl + "/" + requestMapping.value().replaceAll("\\*", ".*")).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);

                this.handlerMappings.add(new HandlerMapping(controller, method, pattern));
                System.out.println("Mapped " + regex + "," + method);
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}
~~~

# HandlerAdapter
<kbd>HandlerAdapter</kbd>简单讲就是负责接收用户的请求，然后将参数填充到<kbd>Controller</kbd>中的方法中调用。
~~~java
package com.lqb.springframework.webmvc.servlet;

import com.lqb.springframework.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HandlerAdapter {

    ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMapping handlerMapping = (HandlerMapping) handler;

        //把方法的形参列表和request的参数列表所在顺序进行一一对应
        Map<String, Integer> paramIndexMapping = new HashMap<>();

        //提取方法中加了注解的参数
        //把方法上的注解拿到，得到的是一个二维数组
        //因为一个参数可以有多个注解，而一个方法又有多个参数
        Annotation[][] pa = handlerMapping.getMethod().getParameterAnnotations();
        for (int i = 0; i < pa.length; i++) {
            for (Annotation a : pa[i]) {
                if (a instanceof RequestParam) {
                    String paramName = ((RequestParam) a).value();
                    if (!"".equals(paramName.trim())) {
                        paramIndexMapping.put(paramName, i);
                    }
                }
            }
        }

        //提取方法中的request和response参数
        Class<?>[] paramsTypes = handlerMapping.getMethod().getParameterTypes();
        for (int i = 0; i < paramsTypes.length; i++) {
            Class<?> type = paramsTypes[i];
            if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                paramIndexMapping.put(type.getName(), i);
            }
        }

        //获得方法的形参列表
        Map<String, String[]> params = request.getParameterMap();

        //controller的方法实参列表
        Object[] paramValues = new Object[paramsTypes.length];

        for (Map.Entry<String, String[]> parm : params.entrySet()) {
            String value = Arrays.toString(parm.getValue()).replaceAll("\\[|\\]", "")
                    .replaceAll("\\s", ",");

            if (!paramIndexMapping.containsKey(parm.getKey())) {
                continue;
            }

            int index = paramIndexMapping.get(parm.getKey());
            paramValues[index] = parseStringValue(value, paramsTypes[index]);
        }

        //填充HttpServletRequest参数
        if (paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = request;
        }

        //填充HttpServletResponse参数
        if (paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = response;
        }

        //反射调用controller的方法
        Object result = handlerMapping.getMethod().invoke(handlerMapping.getController(), paramValues);
        if (result == null || result instanceof Void) {
            return null;
        }

        //解析controller的方法返回
        Class<?> returnType = handlerMapping.getMethod().getReturnType();
        boolean isModelAndView = returnType == ModelAndView.class;
        if (isModelAndView) {
            return (ModelAndView) result;
        } else if (returnType == Void.class) {
            return null;
        } else if (returnType == String.class) {
            //return (String) result;
        }

        return null;
    }

    /**
     * request中接收的参数都是string类型的，需要转换为controller中实际的参数类型
     * 暂时只支持string、int、double类型
     */
    private Object parseStringValue(String value, Class<?> paramsType) {
        if (String.class == paramsType) {
            return value;
        }

        if (Integer.class == paramsType) {
            return Integer.valueOf(value);
        } else if (Double.class == paramsType) {
            return Double.valueOf(value);
        } else {
            if (value != null) {
                return value;
            }
            return null;
        }
        //还有，继续加if
        //其他类型在这里暂时不实现，希望小伙伴自己来实现
    }
}
~~~

<kbd>ModelAndView</kbd>是<kbd>Controller</kbd>方法返回的类型，封装了模板引擎名称和参数
~~~java
package com.lqb.springframework.webmvc.servlet;

import java.util.Map;

public class ModelAndView {

    //模板名字
    private String viewName;

    //模板中填充的参数
    private Map<String, ?> model;

    public ModelAndView(String viewName) {
        this.viewName = viewName;
    }

    public ModelAndView(String viewName, Map<String, ?> model) {
        this.viewName = viewName;
        this.model = model;
    }

    public String getViewName() {
        return viewName;
    }

    public Map<String, ?> getModel() {
        return model;
    }
}
~~~

填充创建<kbd>HandlerAdapter</kbd>逻辑到<kbd>DispatcherServlet</kbd>的流程中
~~~java
private Map<HandlerMapping, HandlerAdapter> handlerAdapters = new HashMap<>();

private void initHandlerAdapters(DefaultApplicationContext context) {
    //一个HandlerMapping对应一个HandlerAdapter
    for (HandlerMapping handlerMapping : this.handlerMappings) {
        this.handlerAdapters.put(handlerMapping, new HandlerAdapter());
    }
}
~~~

# ViewResolver
<kbd>ViewResolver</kbd>需要根据模板名找到对应的模板，封装成<kbd>View</kbd>
~~~java
package com.lqb.springframework.webmvc.servlet;

import java.io.File;
import java.util.Locale;

public class ViewResolver {
    
    private final String DEFAULT_TEMPLATE_SUFFIX = ".html";

    /**模板根目录*/
    private File templateRootDir;

    public ViewResolver(String templateRoot) {
        String templateRootPath = this.getClass().getClassLoader().getResource(templateRoot).getFile();
        templateRootDir = new File(templateRootPath);
    }

    public View resolveViewName(String viewName, Locale locale) throws Exception{
        if(null == viewName || "".equals(viewName.trim())){return null;}
        viewName = viewName.endsWith(DEFAULT_TEMPLATE_SUFFIX) ? viewName : (viewName + DEFAULT_TEMPLATE_SUFFIX);
        File templateFile = new File((templateRootDir.getPath() + "/" + viewName).replaceAll("/+","/"));
        return new View(templateFile);
    }
}
~~~
<kbd>View</kbd>封装了模板，提供了渲染的功能
~~~java
package com.lqb.springframework.webmvc.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class View {

    /**模板*/
    private File viewFile;

    /**占位符表达式*/
    private Pattern pattern = Pattern.compile("#\\{[^\\}]+\\}", Pattern.CASE_INSENSITIVE);

    public View(File viewFile) {
        this.viewFile = viewFile;
    }

    /**
     * 渲染
     */
    public void render(Map<String, ?> model,
                       HttpServletRequest request,
                       HttpServletResponse response) throws Exception {

        StringBuilder sb = new StringBuilder();
        RandomAccessFile ra = new RandomAccessFile(this.viewFile, "r");
        String line;

        while (null != (line = ra.readLine())) {
            line = new String(line.getBytes("ISO-8859-1"), "utf-8");
            Matcher matcher = this.pattern.matcher(line);
            //找到下一个占位符
            while (matcher.find()) {
                String paramName = matcher.group();
                paramName = paramName.replaceAll("#\\{|\\}", "");
                Object paramValue = model.get(paramName);
                if (null == paramValue) {
                    continue;
                }
                //替换占位符为实际值
                line = matcher.replaceFirst(makeStringForRegExp(paramValue.toString()));
                //接着匹配下一个占位符
                matcher = pattern.matcher(line);
            }
            sb.append(line);
        }

        response.setCharacterEncoding("utf-8");
        //输出到response
        response.getWriter().write(sb.toString());
    }


    //处理特殊字符
    public static String makeStringForRegExp(String str) {
        return str.replace("\\", "\\\\").replace("*", "\\*")
                .replace("+", "\\+").replace("|", "\\|")
                .replace("{", "\\{").replace("}", "\\}")
                .replace("(", "\\(").replace(")", "\\)")
                .replace("^", "\\^").replace("$", "\\$")
                .replace("[", "\\[").replace("]", "\\]")
                .replace("?", "\\?").replace(",", "\\,")
                .replace(".", "\\.").replace("&", "\\&");
    }
}
~~~

最后填充初始化<kbd>ViewResolver</kbd>的逻辑到<kbd>DispatcherServlet</kbd>的中
~~~java
private List<ViewResolver> viewResolvers = new ArrayList<>();

private void initViewResolvers(DefaultApplicationContext context) {
    //配置文件中拿到模板的存放目录
    String templateRoot = context.getConfig().getProperty("templateRoot");
    String templateRootPath = this.getClass().getClassLoader().getResource(templateRoot).getFile();
    File templateRootDir = new File(templateRootPath);
    String[] templates = templateRootDir.list();
    for (int i = 0; i < templates.length; i ++) {
        this.viewResolvers.add(new ViewResolver(templateRoot));
    }
}
~~~
至此组件都初始化好了，接着利用这些组件来处理用户请求了。


# doDispatch
在<kbd>DispatcherServlet</kbd>中，<kbd>doGet</kbd>和<kbd>doPost</kbd>需要调用<kbd>doDispatch</kbd>来处理用户请求
~~~java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
}

@Override
protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    try {
        this.doDispatch(req, resp);
    } catch (Exception e) {
        resp.getWriter().write("500 Exception,Details:\r\n"
                + Arrays.toString(e.getStackTrace()).replaceAll("\\[|\\]", "")
                .replaceAll(",\\s", "\r\n"));
        e.printStackTrace();
    }
}

private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
    //1、通过从request中拿到URL，去匹配一个HandlerMapping
    HandlerMapping handler = getHandler(req);

    if (handler == null) {
        //没有找到handler返回404
        processDispatchResult(req, resp, new ModelAndView("404"));
        return;
    }

    //2、准备调用前的参数
    HandlerAdapter ha = getHandlerAdapter(handler);

    //3、真正的调用controller的方法
    ModelAndView mv = ha.handle(req, resp, handler);

    //4、渲染页面输出
    processDispatchResult(req, resp, mv);
}
~~~
第一步通过用户访问的URL，拿到<kbd>HandlerMapping</kbd>
~~~java
private HandlerMapping getHandler(HttpServletRequest req) throws Exception {
    if (this.handlerMappings.isEmpty()) {
        return null;
    }

    String url = req.getRequestURI();
    String contextPath = req.getContextPath();
    url = url.replace(contextPath, "").replaceAll("/+", "/");

    for (HandlerMapping handler : this.handlerMappings) {
        try {
            Matcher matcher = handler.getPattern().matcher(url);
            //如果没有匹配上继续下一个匹配
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        } catch (Exception e) {
            throw e;
        }
    }
    return null;
}
~~~

第二步，根据<kbd>HandlerMapping</kbd>拿到<kbd>HandlerAdapter</kbd>
~~~java
private HandlerAdapter getHandlerAdapter(HandlerMapping handler) {
    if (this.handlerAdapters.isEmpty()) {
        return null;
    }
    HandlerAdapter ha = this.handlerAdapters.get(handler);
    return ha;
}
~~~

最后，调用完<kbd>Controller</kbd>方法渲染模板输出到用户
~~~java
private void processDispatchResult(HttpServletRequest req, HttpServletResponse resp, ModelAndView mv) throws Exception {
    if (null == mv) {
        return;
    }

    if (this.viewResolvers.isEmpty()) {
        return;
    }

    for (ViewResolver viewResolver : this.viewResolvers) {
        //根据模板名拿到View
        View view = viewResolver.resolveViewName(mv.getViewName(), null);
        //开始渲染
        view.render(mv.getModel(), req, resp);
        return;
    }
}
~~~

至此，MVC模块已经搞定，下面来展示成果。

# 成果展示
先<kbd>mvn install</kbd>发布到本地仓库，然后在src/main/webapp/WEB-INF目录下新建web.xml
~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://java.sun.com/xml/ns/j2ee"
         xmlns:javaee="http://java.sun.com/xml/ns/javaee"
         xmlns:web="http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
         xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"
         version="2.4">

    <display-name>My Web Application</display-name>

    <servlet>
        <servlet-name>mymvc</servlet-name>
        <!--配置Servlet-->
        <servlet-class>com.lqb.springframework.webmvc.servlet.DispatcherServlet</servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <!--指定配置文件路径-->
            <param-value>application.properties</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>mymvc</servlet-name>
        <url-pattern>/*</url-pattern>
    </servlet-mapping>
</web-app>
~~~

配置文件<kbd>application.properties</kbd>中指定模板位置
~~~shell
# 模板目录
templateRoot=layouts
~~~

在resource目录下新建layouts文件夹，然后我们创建一个叫test.html的模板，包含两个占位符“data1”和“data2”
~~~html
<!DOCTYPE html>
<html lang="zh-cn">
	<head>
		<meta charset="utf-8">
		<title>手写一个Spring</title>
	</head>
	<center>
		<h1>#{data1}大家好，我是#{data2}</h1>
	</center>
</html>
~~~
再创建一个叫404的模板
~~~html
<!DOCTYPE html>
<html lang="zh-cn">
<head>
    <meta charset="utf-8">
    <title>页面去火星了</title>
</head>
<body>
    <font size='25' color='red'>404 Not Found</font><br/>
</body>
</html>
~~~
创建一个<kbd>Controller</kbd>
~~~java
package com.lqb.demo;

import com.lqb.springframework.annotation.Controller;
import com.lqb.springframework.annotation.RequestMapping;
import com.lqb.springframework.webmvc.servlet.ModelAndView;
import java.util.HashMap;

@Controller
public class HelloController {

    @RequestMapping("/hello")
    public ModelAndView hello() {
        HashMap<String, Object> model = new HashMap<>();
        model.put("data1", "hello");
        model.put("data2", "world");
        return new ModelAndView("test", model);
    }
}
~~~
因为我们是一个Web项目，因此需要启动Web Server，我这里是启动了Jetty
![启动jetty](https://img-blog.csdnimg.cn/20191211213630382.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
打开浏览器，地址栏输入<kbd>http://localhost:8080/hello</kbd>，成功输入模板引擎的内容
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191211214000422.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)

# 最后
《手撸一个简易Spring框架》系列正式结束了。通过手写简易Spring我们应该要掌握Spring的IOC启动流程、DI依赖注入以及AOP代理创建过程，另外就是涉及到的设计模式，如AOP中的责任链。
