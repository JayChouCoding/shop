# SuddenFix 高并发抢购与支付方案

## 页面流转

1. 商品列表/详情页点击预热后进入 `/products/:id/launch`
2. 等待页启动 60 秒倒计时，按钮锁定为黑色
3. 倒计时结束后可一键提交订单
4. 下单成功后跳转 `/orders/:id/processing`
5. 处理中页面轮询 `/order/status/{orderId}`
6. 订单状态进入 `Pending_Payment(10)` 后自动请求 `/pay/alipay/page/{orderId}` 拉起支付

## 分库分表与 ID 设计

- 订单与物流仍按 `order_id` 做 `2 库 16 表`
- 支付改为按 `order_id` 路由，避免按 `order_id` 查询支付流水时广播所有分片
- 本地消息表按 `business_id` 路由到 `2` 个消息分片
- 分片表达式统一使用 `floorMod`，兼容历史负数 ID 的查询与路由

`GeneIdGenerator`

- 生成 63 bit 正数 ID，布局为 `41 bit 时间戳 + 5 bit worker + 13 bit sequence + 4 bit shardSlot`
- 低 4 位固定保留给分片槽位，因此 `id % 16` 直接对应物理分表
- 分片槽位不再直接取 `userId` 末位，避免同一用户下单永远打到同一库同一表
- 符号位始终为 `0`，不会再出现左移后得到负数 ID 的问题

## 订单状态机

- `0 INIT`
- `10 PENDING_PAYMENT`
- `20 PAID`
- `30 SHIPPED`
- `40 COMPLETED`
- `50 CLOSED`

## 商品下单主链路

1. 前端提交 `idempotentKey + products + receiver`
2. 订单服务用 Redis `setIfAbsent` 做幂等
3. 使用 Lua 脚本对 Redis 预热库存做原子扣减
4. 扣减成功后发送 `TOPIC_ON_CREATE`
5. 前端拿到 `orderId` 后进入处理中页面

## 订单异步构建

`OrderCreateListener`

- 组装订单主表与订单明细
- 订单初始状态写为 `INIT`
- 插入两条本地消息
- `TOPIC_ORDER_CREATED`
- `TOPIC_ORDER_CANCEL`
- 事务提交后异步发送真实库存扣减消息 `TOPIC_STOCK_DEDUCTION`

## 支付链路

`OrderCreatedListener` in pay service

- 监听 `TOPIC_ORDER_CREATED`
- 创建支付流水
- 插入 `TOPIC_PAY_CREATED` 本地消息

`PayCreatedListener` in order service

- 监听 `TOPIC_PAY_CREATED`
- 将订单从 `INIT` 更新为 `PENDING_PAYMENT`

## 超时取消

`OrderMsgRelayJob`

- 加分布式锁扫描本地消息表
- 到达 `next_retry_time` 后投递 `TOPIC_ORDER_CANCEL`

`OrderTimeoutCancelListener`

- 只取消 `PENDING_PAYMENT`
- 回滚 Redis 预扣库存
- 发送 `TOPIC_RESTORE_STOCK`
- 如有优惠券则发送 `TOPIC_COUPON_ROLLBACK`

## 支付回调

`PayServiceImpl.handleAlipayNotify`

- 强制支付宝沙盒网关
- 验签
- `out_trade_no + pending_status` 做 Redis 幂等
- 更新支付流水为成功
- 插入 `TOPIC_PAY_SUCCESS` 本地消息

## 支付成功补偿

`PaySuccessListener`

- 订单为 `PENDING_PAYMENT` 时更新订单为已支付
- 并将优惠券状态更新为已使用
- 若订单已 `CLOSED`，则写入：
- `TOPIC_PAY_REFUND`
- `TOPIC_COUPON_ROLLBACK`

## 优惠券分段缓存

`CouponServiceImpl`

- 检查是否预热
- 检查是否售罄
- 检查用户是否已领
- `userId hash % segmentCount` 命中分段
- 先查 Bitmap，再从分段 List 弹 token
- 若分段售罄，置位为 false，再用 `BITPOS` 找下一个可用分段
- 直到成功或全局售罄

## 核心 Redis Key

- `suddenfix:prevent:duplicate:*`
- `suddenfix:goods:prededuction:*`
- `suddenfix:pay:idempotent:*`
- `suddenfix:pay:notify:locked:*`
- `suddenfix:order:schedule`
- `suddenfix:pay:schedule`
- `suddenfix:coupon:stock:*`
- `suddenfix:coupon:bitmap:*`
- `suddenfix:coupon:exist:*`
- `suddenfix:coupon:users:*`

## 核心 Topic

- `TOPIC_ON_CREATE`
- `TOPIC_ORDER_CREATED`
- `TOPIC_PAY_CREATED`
- `TOPIC_ORDER_CANCEL`
- `TOPIC_STOCK_DEDUCTION`
- `TOPIC_RESTORE_STOCK`
- `TOPIC_PAY_SUCCESS`
- `TOPIC_COUPON_ROLLBACK`
- `TOPIC_PAY_REFUND`

## Lua

- 商品库存 Lua：`suddenfix-backend/suddenfix-product/src/main/resources/lua/stock_deduct.lua`
- 订单服务同名脚本：`suddenfix-backend/suddenfix-order/src/main/resources/lua/stock_deduct.lua`
