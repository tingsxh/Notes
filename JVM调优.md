## JVM的调试工具

* Jps 查看系统运行的java程序，可使用的参数如下有`-l`  	输出启动类的全名称，` -m` 输出máin 函数的参数对象, `-v` 是输出启动参数。

* Jstat 用于查看gc 信息，常见命令如 `jstat -gc xxxx BB CC` 查询某个进程的gc 次数，每BB秒查询一次，一共查询20次。

  `jstat -gcutil xxx` 查看各区域内存使用情况，查看类加载信息。

* Jmap 常用于导出堆内存的快照文件，`jmap -dump -dump:format=b xxx`  活着`jmap -F xxx`

* Jhat 是内置的一个分析堆内存的一个工具，内部会开启一个web服务支持在浏览器里面查看内存分布情况。

* Jstack 查看线程运行栈帧快照，一般可以用来解决死锁问题。