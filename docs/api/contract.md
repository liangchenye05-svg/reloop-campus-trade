# HTTP 接口契约

所有接口前缀为 `/api`。标准请求和响应使用 JSON，受保护接口使用 `Authorization: Bearer <token>`。请求可携带 `X-Trace-Id`（32 位十六进制）。统一响应包含 `code`、`message`、`data` 和 `traceId`。成功为 HTTP 200 和 `code=OK`。非法参数为 400，未认证为 401，越权为 403，资源缺失为 404，库存、状态、账号冲突或幂等键冲突为 409。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | /auth/register | username、displayName、password |
| POST | /auth/login | username、password，返回 token 和 user |
| GET | /auth/me | 查询本人账号 |
| GET | /products | q、category、page 和 size，可公开查询 |
| GET | /products/{id} | 查询在售详情，含卖家公开资料 |
| GET | /products/mine | 当前卖家的商品列表 |
| POST | /products | title、description、category、price、stock、imageUrl |
| PUT | /products/{id} | 保存商品信息和可售库存 |
| PATCH | /products/{id}/status | status 为 ACTIVE / INACTIVE，管理员可下架 |
| GET | /cart | 查询购物车条目和实时状态 |
| PUT | /cart/{productId} | 设置购物车数量 quantity |
| DELETE | /cart/{productId} | 移除条目 |
| POST | /orders | productIds，须带 Idempotency-Key，重复提交返回同一订单 |
| GET | /orders | 查询本人订单，支持分页 |
| GET | /orders/{id} | 买家查看订单和明细 |
| POST | /orders/{id}/pay | 模拟支付 |
| POST | /orders/{id}/cancel | 买家取消待支付订单 |
| POST | /orders/{id}/complete | 买家确认完成 |
| GET | /orders/notices | 分页查看订单站内通知 |

## 字段约定

- 分类为 BOOKS、ELECTRONICS、DAILY、SPORTS、OTHER。商品状态为 ACTIVE、INACTIVE。
- 订单状态为 PENDING_PAYMENT、PAID、COMPLETED、CANCELLED、EXPIRED。金额保留两位小数，时间为 UTC。订单列表每条包含摘要，订单详情包含商品快照。
- 商品单价为 0.01 至 9999999999.99 元，库存为 0 至 100000。购物车每款数量为 1 至 99。结算请求最多 50 个商品 ID。
- 页码从 1 开始，默认每页 12 条，最大 100 条。返回 data.items、data.total、data.page 和 data.size。分类之外的全文关键字查询使用包含匹配。
- 登录名为 3 至 32 位 ASCII 字母、数字、下划线，大小写等价。密码最少 8 字符，UTF-8 长度最多 72 字节。登录成功返回有效期 2 小时的 JWT。
- 幂等键为 1 至 64 位字母、数字、下划线和连字符。按买家隔离，重复请求按归一化商品集合比较，返回最新订单状态。
- 商品详情的 seller 包含卖家 ID 和昵称。身份服务无法提供冷缓存商品的资料时返回 503 和 IDENTITY_UNAVAILABLE。
- 响应头携带 X-Trace-Id。调用方如传入合法值，网关、业务服务和事件优先沿用该值。
