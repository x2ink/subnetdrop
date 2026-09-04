# 局域网发现原理

SubnetDrop 使用主动 UDP 组播公告寻找同一广播域内的候选设备，再用 Ktor WebSocket `PING/PONG` 确认可达性。
发现解决的是“当前去哪里连接”，不是“这个设备是谁”。地址与端口会变化，稳定身份由后续配对阶段的
`deviceId` 和公钥确定。

## 发现报文

UDP 使用组播地址 `224.0.0.167` 和端口 `45893`；聊天与文件仍使用 TCP `45892`。公告是不超过 2 KiB 的严格
JSON，只包含可公开的最小信息：

| 字段 | 含义 | 信任等级 |
|---|---|---|
| `protocolVersion` | 协议主版本，当前为 `1` | 仅用于兼容性过滤 |
| `deviceId` | 候选设备声明的稳定 ID | 不可信，配对后再绑定公钥 |
| `displayName` | 对方声明的显示名称 | 不可信，仅用于 UI |
| `servicePort` | 对方的 TCP/WebSocket 监听端口 | 必须主动探测 |
| `replyRequested` | 是否需要接收方单播回应 | 防止响应循环 |

私钥、信任状态、聊天内容和文件信息都不能进入发现广播。

## 发现流程

```mermaid
sequenceDiagram
    participant A as Device A
    participant Udp as UDP multicast
    participant B as Device B
    A->>Udp: ANNOUNCE at 100 ms / 500 ms / 2 s
    Udp-->>B: Candidate metadata + datagram source IP
    B->>A: UDP unicast response, replyRequested=false
    B->>A: WebSocket PING
    A-->>B: PONG
    B->>B: Upsert peer as ONLINE
    loop Every 5 seconds
        B->>A: PING
        A-->>B: PONG or timeout
    end
    B->>B: Three consecutive failures -> OFFLINE
```

应用启动时会立即并发探测数据库中保存的地址，不必等待组播；组播公告每 30 秒低频重复，以修复丢包和网络变化。
离线只代表当前不可达，不删除历史和信任。再次确认同一 `deviceId` 时更新临时 `host`、`port`、名称和
`lastSeenAt`。全 `/24` 网段扫描尚未启用，避免在无对端时无条件发起 255 个连接；后续只应作为组播失败时的
显式兜底。

## 平台实现

```kotlin
interface PeerDiscovery {
    val events: Flow<DiscoveryEvent>
    suspend fun start(
        localDeviceId: String,
        displayName: String,
        servicePort: Int,
        knownPeers: List<Peer>,
    )
    suspend fun stop()
}
```

| 平台 | 实现 | 特殊处理 |
|---|---|---|
| Android | 共享 `UdpPeerDiscovery` | 获取 Wi-Fi multicast lock，再按网卡绑定组播 Socket |
| macOS / Windows | 共享 `UdpPeerDiscovery` | 按可用 IPv4 网卡绑定组播 Socket |

候选设备通过 `PeerReachabilityProbe` 调用现有 Ktor 传输层。PONG 只证明该地址上的 SubnetDrop 实例当前可达，
不建立信任；配对仍必须核对安全码。PING 路径只读取轻量设备资料，不触发密码学身份生成。

## 数据流与 StateFlow

```mermaid
flowchart LR
    UDP[UDP candidate] --> Probe[WebSocket PING/PONG]
    Probe --> Event[Found / Lost event]
    Event --> Runtime[SubnetDropRuntime]
    Runtime --> DB[(SQLDelight peerEntity)]
    DB --> Flow[SQLDelight Flow]
    Flow --> StateFlow[ViewModel stateIn]
    StateFlow --> UI[Compose collectAsState]
```

数据库是设备状态唯一数据源。Repository 暴露冷 `Flow<List<Peer>>`，ViewModel 用 `stateIn` 转为只读
`StateFlow`；网络层不维护一份供 UI 直接读取的平行设备列表。

## 与 LocalSend 的关系

本方案参考 [LocalSend Protocol v2.2](https://github.com/localsend/protocol/blob/main/README.md) 的“UDP 主动公告、
单播确认、已知地址优先”思路，以及其 [组播突发重试实现](https://github.com/localsend/localsend/blob/main/packages/core/src/multicast/mod.rs)。
SubnetDrop 没有照搬文件投递语义：它复用已有 `/chat` WebSocket 做确认，并增加持续心跳，因为聊天联系人在线状态
需要比一次文件发送扫描更长的生命周期。

## 网络边界

UDP 组播通常不能跨越路由器广播域、访客网络隔离或企业 VLAN。以下情况并非协议 bug：

- Wi-Fi 开启 AP/client isolation；
- VPN 或安全软件拦截本地多播；
- 操作系统防火墙拒绝 UDP `45893` 或 TCP `45892`；
- 多网卡选择了不可达地址；
- 设备休眠或应用停止服务发布。

发现结果必须始终按不可信输入校验。攻击者可以伪造名称、ID、地址或版本，因此任何高价值操作都必须在
[配对与端到端加密](end-to-end-encryption.md)之后进行。
