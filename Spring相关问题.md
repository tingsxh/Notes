### SpringMvc 处理流程

* DispatchServlet 继承自Servlet，负责接收所有的外部请求，然后使用HandlerMapping url 路径到处理器之间的映射
* HandlerMapping 调用HandlerAdapter 进一步解析用户自定义的处理逻辑
* 处理完用户定义的逻辑之后，返回给dispatchServlet 的对象是 ModelAndView 
* 如果是@RequestController 则直接返回object 对象，否则dispatchServlet 还需要调用viewReslover视图解析器来进行view视图的渲染。

### Spring Bean 的生命周期

* 解析配置文件(或者注解扫描)
* 先实例化BeanFactoryPostProcessor  接口的实现类
* 然后实例化BeanPostProcessor 接口的实现类
* 实例化对象，填充对象属性，包括设置其他依赖的bean对象
* 检查是否实现了一些spring 的拓展接口，例如applicationContextAware
* 处理beanPostProcesser 初始化前置处理器
* 检查是否实现了初始化Bean的接口，执行其afterPropertiesSet方法
* init-method 方法
* 处理beanPostProcesser 初始化后置处理器
* 注册到容器中使用

### BeanFactory 对比 ApplicationContext

* applicationContext 继承自BeanFactory,拥有它所有的属性
* 另外applicationContext 还具备国际化，事件通知，容器bean的预加载。

### BeanFactoryPostProcessor 对比 BeanPostProcessor 

* 前者是做系统配置的扩展用的，后者是对bean 显示初始化前后做扩展使用的。
