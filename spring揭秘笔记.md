

## BeanFactoryPostProcessor
* 在bean 实例化之前插手容器元数据的修改
* PropertyPlaceholderConfigurer （用来做元数据的替换操作，占位符的用法）
* PropertyOverrideConfigurer (用来做元数据的替换，datasource.maxActive=50,采用bean 名字.属性名称的方式来定义)
* CustomEditorConfigurer (这个用来做特殊元数据类型的转换，例如yyyy/mm/dd 格式转换为日期)
