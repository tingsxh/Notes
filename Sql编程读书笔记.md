### 时间类函数

* now() current_timestamp() sysDate() 前两者代表了执行sql 的时间，后者代表了执行到具体函数时到时间。

```sql
使用 DATE_ADD,DATE_SUB 来计算时间到加减
select DATE_ADD(now() INTERVAL 1 DAY) as tomorrow
```



