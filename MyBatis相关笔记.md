## mybatis 原生使用细节记录

### 关于枚举与自定义类型的映射

* 按照官网的说法，mybatis 是提供了枚举类型转换处理器的，就是如下这两个typeHandler` EnumOrdinalTypeHandler` and `EnumTypeHandler`

#### 使用，无论是注解还是xml 使用起来大同小异

* 插入使用

- xml

```xml
<insert id="saveUserInEnum" parameterType="com.xiaohui.mybatis.domain.User">
        insert into user(name,age,time,sex,sexStr)
        values(#{name},#{age},#{time,typeHandler=com.xiaohui.mybatis.MyTypeHandler},#{sex,typeHandler=com.xiaohui.mybatis.MyEnumHandler},
        #{sexStr,typeHandler=org.apache.ibatis.type.EnumTypeHandler})
 </insert>
```

- 注解

  ```java
  @Insert(" insert into user(name,age,time,sex) values(#{name},#{age},#{time,typeHandler=com.xiaohui.mybatis.MyTypeHandler},#{sex,typeHandler=com.xiaohui.mybatis.MyEnumHandler})")
      Integer saveUserInAnnotation(User user);
  ```

* 查询使用

  ```xml
      <resultMap type="com.xiaohui.mybatis.domain.User" id="userMap">
          <result column="sex" property="sex" typeHandler="com.xiaohui.mybatis.MyEnumHandler"/>
          <result column="time" property="time" typeHandler="com.xiaohui.mybatis.MyTypeHandler"/>
  </resultMap>
  ```

  ```java
    @Results(
              @Result(
                      column = "",property = "",typeHandler = com.xiaohui.mybatis.MyEnumHandler.class
              )
      )
      Integer saveUser(User user);
  ```

* 前者`EnumOrdinalTypeHandler`支持将枚举类型映射成数据库字段中的 int 类型，不过需要注意一点，解释如下

  ```java
  public enum SexEnum {
      MAN(1), //这里面的code 是不会生效的，EnumOrdinalTypeHandler 会按照枚举顺序值来映射成整形，也就是说，MAN 类型对应到数据库就是0
      WOMAN(2); //WOMAN 类型对应到数据库就是1
      SexEnum(Integer code) {
          this.code = code;
      }
      private Integer code;
      public Integer getCode() {
          return code;
      }
      public void setCode(Integer code) {
          this.code = code;
      }
  }
  ```

* 关于后者`EnumTypeHandler`，是直接将枚举字段的字符串映射至数据库中的字段`MAN` 对应着‘MAN’

* 如果想使用自己的code 或者说自己的规则生产字符串，则需要自定义typeHandler,示例如下

```java
//这里是定义你需要映射成数据库的哪种字段
@MappedJdbcTypes(JdbcType.INTEGER)
public class MyEnumHandler extends BaseTypeHandler<SexEnum> {
    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int i, SexEnum sexEnum, JdbcType jdbcType) throws SQLException {
        preparedStatement.setInt(i, sexEnum.getCode());
    }
    @Override
    public SexEnum getNullableResult(ResultSet resultSet, String s) throws SQLException {
        Integer code = resultSet.getInt(s);
        if (code != null) {
            return SexEnum.findByCode(code);
        }
        return null;
    }
    @Override
    public SexEnum getNullableResult(ResultSet resultSet, int i) throws SQLException {
        Integer code = resultSet.getInt(i);
        if (code != null) {
            return SexEnum.findByCode(code);
        }
        return null;
    }
    @Override
    public SexEnum getNullableResult(CallableStatement callableStatement, int i) throws SQLException {
        Integer code = callableStatement.getInt(i);
        if (code != null) {
            return SexEnum.findByCode(code);
        }
        return null;
    }
}
```

* 最后不要忘记将自定义的typeHandler 处理器注册至config 当中

### 存储过程的使用

* 

### 关于缓存

* 一级缓存，默认只在一个session(会话中生效)，可能会引起脏读，需要禁用的话可以如下配置

* ```xml
  <setting name="localCacheScope" value="STATEMENT"/>
  ```

* 一级缓存的大致原理图，可见下图：，其实就是每个session 内部的executor 里面维护了一个类hashmap 来存储，每次先去里面查询一下，有的话就直接返回。

* 在集成了spring 之后，每一次查询都是一个新的session 除非在同一个事务里面

* **二级缓存相比于一级缓存，解决了缓存只在session 中共享的问题，但却还是无法解决多表联查以及分布式场景下的脏读问题。**因为二级缓存是基于nameSpace 维度的

* **二级缓存还存在 xml 与注解方式混用导致缓存不清空的问题**

* 所以不建议开启mybatis的缓存策略

##### mybatis 几个核心角色

* sqlsession 封装了所有必须的方法,是面向用户开放的操作对象
* executor 每个sqlSeesion 实现类里都具备一个executor ，它是真正的操作数据库的家伙，所有数据库的操作都是委托于它来执行的
* 还有缓存里面用到的一堆装饰器类，**待研究**

##### Executor

* SimpleExecutor、BatchExecutor、ReuseExecutor

##### StatementHandler 

##### ResultSetHandler 

#### 插件机制

MyBatis 允许你在映射语句执行过程中的某一点进行拦截调用。默认情况下，MyBatis 允许使用插件来拦截的方法调用包括：

* Executor (update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
* ParameterHandler (getParameterObject, setParameters)
* ResultSetHandler (handleResultSets, handleOutputParameters)
* StatementHandler (prepare, parameterize, batch, update, query)

拦截的配置：

1. 首先继承Interceptor 接口，实现intercept 接口，调用`Plugin.wrap(target, this);` 进行装载。
2. 原理也是通过动态代理技术来实现拦截

### Mybatis 工作原理

1. 解析配置文件生成相应是sqlSessionFactory
2. 创建sqlSession，在spring 中，所有的mybatis 接口都代理为了`MapperFactoryBean` bean.
3. 调用Executor 执行sql,里面回完成sql 解析与拼接，先通过sql 解析将#{} 类似的占位符替换为`?` 占位符，然后通过prepareStatement 设置参数，和jdbc 一致。
4. 执行结果的转换，转换为接口方法对应的映射关系

### 关于配置多数据源

* 需要手动定义多个dataSource,sqlSessionFactory
* 至于分布式事务可以区分为是单服务体内的分布式事务，还是多服务内的分布式事务，前者可以使用JTA来[解决](<https://blog.csdn.net/jy02268879/article/details/84398657>)，[或者](<https://blog.csdn.net/WI_232995/article/details/78124885>)后者考虑使用MQ的方式来解决类似RocketMq
