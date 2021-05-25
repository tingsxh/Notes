

## BeanFactoryPostProcessor
* 在bean 实例化之前插手容器元数据的修改
* PropertyPlaceholderConfigurer （用来做元数据的替换操作，占位符的用法）
* PropertyOverrideConfigurer (用来做元数据的替换，datasource.maxActive=50,采用bean 名字.属性名称的方式来定义)
* CustomEditorConfigurer (这个用来做特殊元数据类型的转换，例如yyyy/mm/dd 格式转换为日期)


### ApplicationContext
* 统一的资源加载策略
* 国际化支持
* IOC 容器内部事件发布
* 多配置模块加载的简化

### APO 
* 静态（在编译器就将逻辑以字节码的形式植入到class 文件中）
* 动态 （在运行时通过字节码的形式）
* joinpoint(切入点)
* pointcut(切点集合形成的切面)
* advice(织入点)
