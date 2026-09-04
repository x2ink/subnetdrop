package ink.x2.subnetdrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.x2.subnetdrop.AppUiState
import ink.x2.subnetdrop.domain.model.Conversation
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.presentation.HomeSection
import ink.x2.subnetdrop.runtime.RuntimeState

@Composable
fun HomeScreen(
    state: AppUiState,
    modifier: Modifier,
    onSectionSelected: (HomeSection) -> Unit,
    onPeerSelected: (Peer) -> Unit,
    onConversationSelected: (Conversation) -> Unit,
    onRetry: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        HomeHeader(state.localDisplayName)
        RuntimeBanner(state.runtimeState, onRetry)
        when (state.section) {
            HomeSection.NEARBY -> PeerList(state.peers, Modifier.weight(1f), onPeerSelected)
            HomeSection.CHATS -> ConversationList(
                conversations = state.conversations,
                modifier = Modifier.weight(1f),
                onConversationSelected = onConversationSelected,
            )
            HomeSection.SETTINGS -> SettingsPanel(
                deviceId = state.localDeviceId,
                displayName = state.localDisplayName,
                modifier = Modifier.weight(1f),
                onDisplayNameChanged = onDisplayNameChanged,
            )
        }
        SectionSelector(state.section, onSectionSelected)
    }
}

@Composable
private fun HomeHeader(displayName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("SubnetDrop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = displayName ?: "正在准备本机身份…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RuntimeBanner(state: RuntimeState, onRetry: () -> Unit) {
    val message = state.label()
    val isError = state is RuntimeState.Degraded || state is RuntimeState.Failed
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(state is RuntimeState.Running)
            Text(
                text = message,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state is RuntimeState.Failed || state is RuntimeState.Stopped) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

@Composable
private fun SectionSelector(selected: HomeSection, onSelected: (HomeSection) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        SectionItem(
            label = "附近设备",
            icon = Icons.Outlined.Devices,
            selected = selected == HomeSection.NEARBY,
        ) { onSelected(HomeSection.NEARBY) }
        SectionItem(
            label = "聊天",
            icon = Icons.Outlined.ChatBubbleOutline,
            selected = selected == HomeSection.CHATS,
        ) { onSelected(HomeSection.CHATS) }
        SectionItem(
            label = "设置",
            icon = Icons.Outlined.Settings,
            selected = selected == HomeSection.SETTINGS,
        ) { onSelected(HomeSection.SETTINGS) }
    }
}

@Composable
private fun RowScope.SectionItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, maxLines = 1) },
    )
}

@Composable
private fun PeerList(peers: List<Peer>, modifier: Modifier, onPeerSelected: (Peer) -> Unit) {
    if (peers.isEmpty()) {
        EmptyState(
            title = "暂未发现设备",
            detail = "请确认其他设备已打开应用并连接同一 Wi-Fi",
            icon = Icons.Outlined.Devices,
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(peers, key = Peer::id) { peer -> PeerRow(peer, onPeerSelected) }
    }
}

@Composable
private fun PeerRow(peer: Peer, onPeerSelected: (Peer) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onPeerSelected(peer) }) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PeerAvatar(peer.displayName, peer.availability == PeerAvailability.ONLINE)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(peer.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = peer.trustState.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (peer.trustState == TrustState.KEY_CHANGED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "打开")
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    modifier: Modifier,
    onConversationSelected: (Conversation) -> Unit,
) {
    if (conversations.isEmpty()) {
        EmptyState(
            title = "还没有聊天",
            detail = "先在附近设备中完成安全配对",
            icon = Icons.Outlined.ChatBubbleOutline,
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(conversations, key = Conversation::id) { conversation ->
            ConversationRow(conversation, onConversationSelected)
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onConversationSelected: (Conversation) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onConversationSelected(conversation) }) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PeerAvatar(conversation.peerDisplayName, isOnline = false)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = conversation.peerDisplayName,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    text = conversation.lastMessage ?: "开始聊天",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (conversation.unreadCount > 0) {
                UnreadBadge(conversation.unreadCount)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "打开聊天")
        }
    }
}

@Composable
private fun UnreadBadge(unreadCount: Long) {
    Surface(
        modifier = Modifier.padding(end = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
    ) {
        Text(
            text = if (unreadCount > MAX_VISIBLE_UNREAD_COUNT) "$MAX_VISIBLE_UNREAD_COUNT+" else unreadCount.toString(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PeerAvatar(displayName: String, isOnline: Boolean) {
    Box {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        if (isOnline) {
            Spacer(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(ONLINE_COLOR),
            )
        }
    }
}

@Composable
private fun StatusDot(isOnline: Boolean) {
    Spacer(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(if (isOnline) ONLINE_COLOR else MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun EmptyState(title: String, detail: String, icon: ImageVector, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detail,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    deviceId: String?,
    displayName: String?,
    modifier: Modifier,
    onDisplayNameChanged: (String) -> Unit,
) {
    var draftName by remember(displayName) { mutableStateOf(displayName.orEmpty()) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("本机信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            OutlinedTextField(
                value = draftName,
                onValueChange = { if (it.length <= MAX_DISPLAY_NAME_LENGTH) draftName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("设备名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            Button(
                onClick = { onDisplayNameChanged(draftName) },
                enabled = draftName.isNotBlank() && draftName.trim() != displayName,
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("保存并重新发布", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { SettingValue(Icons.Outlined.Devices, "设备 ID", deviceId ?: "尚未就绪") }
        item {
            Text(
                text = "安全与存储",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item { SettingValue(Icons.Outlined.Security, "端到端加密", "HPKE · X25519 · AES-256-GCM") }
        item { SettingValue(Icons.Outlined.Security, "身份签名", "Ed25519") }
        item { SettingValue(Icons.Outlined.Storage, "聊天记录", "仅保存在本机") }
    }
}

@Composable
private fun SettingValue(icon: ImageVector, label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun RuntimeState.label(): String = when (this) {
    RuntimeState.Stopped -> "局域网服务未启动"
    RuntimeState.Starting -> "正在启动发现与加密通信…"
    is RuntimeState.Running -> "已在线 · 仅同一局域网可见"
    is RuntimeState.Degraded -> "服务异常：$reason"
    is RuntimeState.Failed -> "启动失败：$reason"
}

private fun TrustState.label(): String = when (this) {
    TrustState.UNPAIRED -> "未配对 · 点击建立信任"
    TrustState.PENDING -> "等待确认"
    TrustState.TRUSTED -> "已验证 · 端到端加密"
    TrustState.KEY_CHANGED -> "安全密钥已变化，请重新核验"
}

private val ONLINE_COLOR = Color(0xFF2EAD68)
private const val MAX_VISIBLE_UNREAD_COUNT = 99
private const val MAX_DISPLAY_NAME_LENGTH = 64
