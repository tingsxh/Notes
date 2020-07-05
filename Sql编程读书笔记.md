### 基础知识

```sql
modify 和 change 都可以修改字段的属性，但是modify 不能修改列的名字。
在修改字段时，还可以知道字段的位置，after xx 或者 first
alter table change column new_column xxx xxx xxx after xxx

```
* with rollup

* 创建用户，具有查询和插入的权限
```sql
grant select,insert on sakila.* to 'z1'@'localhost' identified by '123';
```

### 数据类型

#### 小数类型
* float(M,D),double(M,D),decimal(M,D) 数据类型，M 代表整数+小数共有多少位，D代表小数有多少位。
对应两个浮点类型的数据来说，不指明精度的话将会按照实际的精度进行存储，而定点型数据则默认是(10,0)的精度，小数点会丢失。

#### 时间类函数

* now() current_timestamp() sysDate() 前两者代表了执行sql 的时间，后者代表了执行到具体函数时到时间。

* DATETIME  8 1000-01-01 00:00:00 9999-12-31 23:59:59 TIMESTAMP 4 19700101080001 2038 年的某个时刻 
* TIMESTAMP的插入和查询都受当地时区的影响，更能反应出实际的日期，而 DATETIME则只能反应出插入时当地的时区，其他时区的人查看数据必然会有误差。  

```sql
使用 DATE_ADD,DATE_SUB 来计算时间到加减
select DATE_ADD(now() INTERVAL 1 DAY) as tomorrow
```
### 视图
* 视图的使用类似于临时表，创建视图的语句如下

```sql
create view test_view AS select * from user_info where age>124 with CHECK OPTION

```






