# 未采纳媒体方案清理验证

## 范围

- 删除独立媒体预览设计文档及架构入口。
- 清理 README、文件传输规格、技术原理、历史任务、经验和验证记录中的未来方案。
- 不改变普通文件分块传输、临时落盘、完整性校验和完成后打开行为。

## 代码与依赖审计

在 `app`、`core`、`data`、`network` 和版本目录中检索播放器、ExoPlayer、Media3、ComposeMediaPlayer、VLC、
媒体数据源与接收中预览入口，没有找到实现或依赖。`ChatScreen.FileTransferMessage` 的接收方打开条件仍要求
`FileTransferStatus.COMPLETED`；传输中的接收文件不能从消息卡片打开。

因此本轮没有业务代码或依赖可以删除，只删除未落地的设计与产品计划。发送方在传输期间打开自己的源文件属于普通
系统文件操作，不是接收中媒体播放能力，予以保留。

## 静态验证

```shell
rg -n -i "exoplayer|androidx\\.media|mediaplayer|composemediaplayer|vlc|player" \
  gradle app core data network --glob '!**/build/**'
git diff --check
```

结果：代码与依赖检索无匹配，差异无空白错误。全仓库 Markdown 本地链接检查通过，删除的设计文档没有残留链接。
本轮没有修改可执行代码，因此未重复运行 Gradle 编译或 Android 安装。
