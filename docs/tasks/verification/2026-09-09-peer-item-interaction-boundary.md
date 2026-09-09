# 设备项交互边界修复验证

## 范围

- 首页附近设备卡片的 hover、按压、点击、长按和桌面右键区域。
- 消息转发底部面板中复用设备卡片的 hover、按压和点击区域。

## 结构检查

- 卡片的横向外边距位于交互节点之外，不再参与命中或 indication 绘制。
- `PeerListItem` 使用同一个 Material shape 绘制卡片并裁剪交互层。
- 首页与转发面板均通过独立的 `interactionModifier` 接入手势，业务回调未改变。

## 自动验证

```shell
./gradlew :app:desktopApp:compileKotlin
./gradlew :app:shared:jvmTest
git diff --check
```

结果：三项均通过。桌面编译仍报告 `ChatScreen.kt` 中既有的 `quadraticBezierTo` 弃用警告，和本次修改无关。

## 未执行

- 未安装 Android 应用。
- 未进行桌面人工截图回归；本次通过统一修饰符顺序和圆角裁剪消除已定位的越界路径。
