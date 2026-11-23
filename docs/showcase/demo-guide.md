# ReLoop 隔离演示

[公开种子](../../deploy/showcase/catalog.json)由节点脚本调用注册、登录与商品发布接口写入隔离库。目录显示十件二手样品，其名称、价格、库存、状态和图片都由真实接口提供。商品图是原创生成素材。

用 PowerShell 从仓库根目录启动独立演示，端口 8188。Compose 项目名为 reloop-showcase，使用自己的 MySQL、Nacos、RocketMQ 卷和网络。

```powershell
if (!(Test-Path deploy/.env)) { .\deploy\New-LocalSecrets.ps1 }
$env:CAMPUS_WEB_PORT = '8188'
docker compose -p reloop-showcase --env-file deploy/.env -f deploy/compose.yml up -d --build --wait
$env:RELOOP_SHOWCASE_PASSWORD = [guid]::NewGuid().ToString('N')
node deploy/showcase/seed.mjs
```

第一次建立合成卖家 showcase_seller 与买家 showcase_buyer。复用保留的演示库时使用首次的演示密码。脚本固定连接 8188，拒绝向含其他商品的目录播种。
安装 frontend 的依赖后执行 node docs/showcase/capture.cjs，可生成 8 张 Edge 截图并执行一次真实模拟交易。该脚本消耗一件键盘库存，记录写到本地忽略目录。桌面 1440×960，手机 390×844。

- [集市](screenshots/desktop/01-market.jpg) · [搜索与分类](screenshots/desktop/02-search.jpg) · [详情](screenshots/desktop/03-product.jpg)
- [购物车](screenshots/desktop/04-cart.jpg) · [订单](screenshots/desktop/05-order.jpg) · [发布](screenshots/desktop/06-publish.jpg) · [个人中心](screenshots/desktop/07-profile.jpg)
- [手机集市](screenshots/mobile/01-market.jpg) · [素材来源](assets-license.md)
