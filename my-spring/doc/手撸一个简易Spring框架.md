@[TOC](目录)

# 前言
相信大家平常开发中已经有过大量使用Spring的经验，爱学习的同学肯定也尝试过阅读Spring源码，了解过Spring的启动流程，但不知道大家有没有和我一样心里总是感觉很“虚”，面试遇到了也是结结巴巴的回答。所以这次准备写一系列文章，手写一个简易的Spring，重新捋一遍<kbd>IOC容器初始化</kbd>、<kbd>DI依赖注入</kbd>、<kbd>AOP</kbd>以及<kbd>Spring MVC启动流程</kbd>，让我们不再“虚”。

# 效果预览
先来看下整体的项目架构，总共31个相关类，类和包的命名尽量贴合原生的Spring。
![项目架构](https://img-blog.csdnimg.cn/20191212103806685.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
## IOC和DI类图
![IOC容器启动和DI涉及到的类图](https://img-blog.csdnimg.cn/20191212110316244.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
## AOP类图
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191212110635543.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
## MVC类图
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191212110726432.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NhYmxlNTg4MQ==,size_16,color_FFFFFF,t_70)
单看UML图会比较复杂，但是我们会分多个部分来拆解分析，再难的东西只要一点一点去理解就好了。框架完成后只需要在项目POM中引用，就可以开发应用了。

# 最后
系列文章一共分为5篇，希望读者在理解文章的同时，最好能动手敲一遍代码加深记忆和理解。最后让我们一起进步吧！
