# 高速文件传输原理

文件传输采用“元数据优先、接收方确认、再传内容”的会话模型。该思路参考 LocalSend，但 SubnetDrop 使用自身
的设备身份、Ed25519 认证模型和帧协议，不与 LocalSend 客户端互操作。

## 为什么先发送 offer

直接推送文件会让接收端在未同意时消耗带宽和磁盘。SubnetDrop 先发送经过签名的名称、大小和 MIME 类型，
接收方明确接受后才创建临时文件和上传会话。offer 最长等待 5 分钟，超时是失败而不是默认接受。

## 传输流程

```mermaid
sequenceDiagram
    participant S as Sender
    participant R as Receiver
    S->>S: Validate file
    S->>R: Signed FILE_OFFER
    R-->>S: Signed DELIVERY_ACK
    R->>R: User accepts or rejects
    R->>S: Signed FILE_DECISION
    S-->>R: Signed DELIVERY_ACK
    alt accepted
        S->>R: Open one upload WebSocket
        S->>R: Signed FILE_STREAM_START
        loop Ordered 512 KiB chunks
            S->>R: Plain binary frame
            S->>S: Update SHA-256
            R->>R: Validate byte count and update SHA-256
        end
        S->>R: Signed FILE_STREAM_COMPLETE with SHA-256
        R->>R: Verify byte count and SHA-256
        R->>R: Rename temporary file to final collision-free name
        R-->>S: Signed DELIVERY_ACK
    else rejected
        S->>S: Mark REJECTED, send no file bytes
    end
```

## 领域接口与状态

```kotlin
interface FileTransferService {
    val incomingOffers: StateFlow<List<IncomingFileOffer>>
    val transfers: StateFlow<List<FileTransfer>>
    suspend fun sendFile(peerId: String, file: LocalFile)
    suspend fun acceptOffer(transferId: String)
    suspend fun rejectOffer(transferId: String)
    suspend fun cancelTransfer(transferId: String)
}
```

```mermaid
stateDiagram-v2
    [*] --> PREPARING
    PREPARING --> WAITING_FOR_ACCEPTANCE
    WAITING_FOR_ACCEPTANCE --> TRANSFERRING: Accepted
    WAITING_FOR_ACCEPTANCE --> REJECTED: Rejected
    TRANSFERRING --> COMPLETED: Length and SHA-256 valid
    PREPARING --> FAILED
    WAITING_FOR_ACCEPTANCE --> FAILED
    TRANSFERRING --> FAILED
    PREPARING --> CANCELLED
    WAITING_FOR_ACCEPTANCE --> CANCELLED
    TRANSFERRING --> CANCELLED
```

## 限制与验证

| 项目 | 当前规则 |
|---|---|
| 信任 | 只允许 `TRUSTED` peer |
| 单次会话 | 1 个文件 |
| 最大文件 | 10 GiB |
| 明文分块 | 512 KiB 原始二进制帧 |
| 文件名 | 最长 255 字符，只允许 leaf name，拒绝路径分隔符、控制字符和空名 |
| 顺序 | 依赖单一 WebSocket 的有序传输，不允许超出声明总量 |
| 总量 | 不得超过 offer 声明字节数 |
| 完成条件 | 实际字节数与 SHA-256 都匹配 |
| 冲突 | 保留已有文件，生成不冲突的最终名称 |

发送侧边读边发并流式计算哈希，不在发送前预扫描文件，也不把整个文件放进内存。接收侧同步计算
摘要并只写临时文件；拒绝、取消、超时、越界或摘要不匹配都必须删除临时数据。

## 平台文件边界

- Android 和桌面统一使用 FileKit 的 Compose Multiplatform launcher。
- Android provider 返回的内容通过 FileKit 复制到应用 cache，再交给 JVM 共享传输实现读取。
- Android 保存到应用专属 external Downloads/SubnetDrop；桌面保存到 `~/Downloads/SubnetDrop`。
- 桌面同名目标不会被覆盖。

## 安全与性能取舍

文件内容不做 HPKE 加密，不进行 Base64/JSON 转换，也不为每个分块等待网络 ACK。文件会话的提议、决策、
开始和完成帧使用 Ed25519 认证，最终摘要能发现内容被篡改，但局域网观察者仍可能读取文件原文。这是为
最大化吞吐而明确接受的产品取舍。

可测试的协议要求见 [文件传输 v1 规格](../spec/file-transfer-v1.md)。
