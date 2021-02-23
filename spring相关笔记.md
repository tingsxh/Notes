### spring 相关笔记

#### IOC

* spring 容器的启动步骤主要包括解析xml、生成beanDefinition类，初始化，调用aware 回调接口，调用beanPostProcesser、调用 实现了initializingbean 接口的是方法，afterPropertiesSet 方法，坚持是否有init-method 方法，调用beanPostProcesser 后置处理器，初始化完成之后将自身注册到bean容齐之中。

#### AOP

* 使用动态代理的技术来为类方法的执行做横切面操作。常用的动态代理技术 jdk 自带的动态代理需要代理类实现有接口，但是cglib 没有这个限制`Proxy.newProxyInstance`

* AOP与IOC的协作关系是通过实现beanPostProcesser 来介入bean的初始化流程的。

#### spring 初始化当中用到的缓存

| 缓存                  | 用途                                                         |
| :-------------------- | :----------------------------------------------------------- |
| singletonObjects      | 用于存放完全初始化好的 bean，从该缓存中取出的 bean 可以直接使用 |
| earlySingletonObjects | 存放原始的 bean 对象（尚未填充属性），用于解决循环依赖       |
| singletonFactories    | 存放 bean 工厂对象，用于解决循环依赖（该工厂用于产生原始bean对象） |

* spring 解决循环依赖问题主要依靠三点：
* 创建原始 bean 实例 → createBeanInstance(beanName, mbd, args)
2. 添加原始对象工厂对象到 singletonFactories 缓存中 
        → addSingletonFactory(beanName, new ObjectFactory<Object>{...})
3. 填充属性，解析依赖 → populateBean(beanName, mbd, instanceWrapper)