## Netty 笔记

#### Channel

#### EventLoopGroup

* 一个 EventLoopGroup 包含一个或者多个 EventLoop;
* 一个 EventLoop 在它的生命周期内只和一个 Thread 绑定;
* 所有由 EventLoop 处理的 I/O 事件都将在它专有的 Thread 上被处理;
* 一个 Channel 在它的生命周期内只注册于一个 EventLoop;
* 一个 EventLoop 可能会被分配给一个或多个 Channel。

#### ChannelFuture

* channelFuture 可以实现任务完成后到实时通知

#### ChannelHandler

#### ChannelPipeline

* 被我们称为 ChannelPipeline 的是这些 ChannelHandler 的编排顺序。
* 从一个客户端应用程序 的角度来看，如果事件的运动方向是从客户端到服务器端，那么我们称这些事件为出站的，反之 则称为入站的。

