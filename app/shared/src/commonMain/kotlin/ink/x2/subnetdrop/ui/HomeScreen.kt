package ink.x2.subnetdrop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ink.x2.subnetdrop.AppUiState
import ink.x2.subnetdrop.domain.model.AppLanguage
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.BYTES_PER_GIB
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES
import ink.x2.subnetdrop.domain.model.FileTransferSettings.Companion.MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES
import ink.x2.subnetdrop.domain.model.Peer
import ink.x2.subnetdrop.domain.model.PeerAvailability
import ink.x2.subnetdrop.domain.model.TrustState
import ink.x2.subnetdrop.presentation.HomeSection
import ink.x2.subnetdrop.resources.AppString
import ink.x2.subnetdrop.resources.Res
import ink.x2.subnetdrop.resources.appString
import ink.x2.subnetdrop.resources.github_mark
import ink.x2.subnetdrop.resources.subnetdrop_app_icon
import ink.x2.subnetdrop.runtime.RuntimeState
import ink.x2.subnetdrop.runtime.RuntimeStartupPhase

@Composable
fun HomeScreen(
    state: AppUiState,
    modifier: Modifier,
    onSectionSelected: (HomeSection) -> Unit,
    onPeerSelected: (Peer) -> Unit,
    onDeletePeer: (String, Boolean) -> Unit,
    onRefreshPeers: () -> Unit,
    onRetry: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onSaveDirectoryChanged: (String) -> Unit,
    onIncomingFileConfirmationChanged: (Boolean) -> Unit,
    onMaxFileSizeChanged: (Long) -> Unit,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onSettingsError: (String) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { SectionSelector(state.section, onSectionSelected) },
        floatingActionButton = {
            if (state.section == HomeSection.NEARBY) {
                FloatingActionButton(onClick = onRefreshPeers, shape = RoundedCornerShape(999.dp)) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = appString(AppString.REFRESH_NEARBY_DEVICES),
                    )
                }
            }
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            HomeHeader(state.localDisplayName,state.runtimeState,onRetry)
            when (state.section) {
                HomeSection.NEARBY -> PeerList(
                    peers = state.peers,
                    modifier = Modifier.weight(1f),
                    onPeerSelected = onPeerSelected,
                    onDeletePeer = onDeletePeer,
                )
                HomeSection.SETTINGS -> SettingsPanel(
                    deviceId = state.localDeviceId,
                    displayName = state.localDisplayName,
                    modifier = Modifier.weight(1f),
                    onDisplayNameChanged = onDisplayNameChanged,
                    saveDirectory = state.fileTransferSettings.saveDirectory,
                    requireIncomingFileConfirmation = state.fileTransferSettings.requireIncomingConfirmation,
                    maxFileSizeBytes = state.fileTransferSettings.maxFileSizeBytes,
                    onSaveDirectoryChanged = onSaveDirectoryChanged,
                    onIncomingFileConfirmationChanged = onIncomingFileConfirmationChanged,
                    onMaxFileSizeChanged = onMaxFileSizeChanged,
                    appLanguage = state.appLanguage,
                    onAppLanguageChanged = onAppLanguageChanged,
                    onSettingsError = onSettingsError,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(displayName: String?,runtimeState: RuntimeState, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(Res.drawable.subnetdrop_app_icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(displayName ?: appString(AppString.LOADING_LOCAL_PROFILE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                        maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            RuntimeBanner(runtimeState, onRetry)
        }
    }
}

@Composable
private fun RuntimeBanner(state: RuntimeState, onRetry: () -> Unit) {
    val message = state.label()
//    val isError = state is RuntimeState.Degraded || state is RuntimeState.Failed
    Surface(color = Color.Transparent){
        Row(
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
                TextButton(onClick = onRetry) { Text(appString(AppString.ACTION_RETRY)) }
            }
        }
    }
}

@Composable
private fun SectionSelector(selected: HomeSection, onSelected: (HomeSection) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        SectionItem(
            label = appString(AppString.NEARBY_DEVICES),
            icon = Icons.Outlined.Devices,
            selected = selected == HomeSection.NEARBY,
        ) { onSelected(HomeSection.NEARBY) }
        SectionItem(
            label = appString(AppString.SETTINGS),
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
private fun PeerList(
    peers: List<Peer>,
    modifier: Modifier,
    onPeerSelected: (Peer) -> Unit,
    onDeletePeer: (String, Boolean) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Peer?>(null) }
    if (peers.isEmpty()) {
        EmptyState(
            title = appString(AppString.NO_DEVICES_TITLE),
            detail = appString(AppString.NO_DEVICES_DETAIL),
            icon = Icons.Outlined.Devices,
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(peers, key = Peer::id) { peer ->
                PeerRow(
                    peer = peer,
                    onPeerSelected = onPeerSelected,
                    onDeleteRequested = { deleteTarget = peer },
                )
            }
        }
    }
    deleteTarget?.let { peer ->
        DeletePeerDialog(
            peer = peer,
            onDismiss = { deleteTarget = null },
            onConfirm = { deleteHistory ->
                deleteTarget = null
                onDeletePeer(peer.id, deleteHistory)
            },
        )
    }
}

@Composable
private fun PeerRow(
    peer: Peer,
    onPeerSelected: (Peer) -> Unit,
    onDeleteRequested: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        PeerListItem(
            peer = peer,
            modifier = Modifier.fillMaxWidth(),
            interactionModifier = Modifier
                .platformSecondaryClick { menuExpanded = true }
                .combinedClickable(
                    onClick = { onPeerSelected(peer) },
                    onLongClickLabel = appString(AppString.DELETE_DEVICE),
                    onLongClick = { menuExpanded = true },
                ),
            trailingIcon = Icons.Outlined.ChevronRight,
            trailingContentDescription = appString(AppString.ACTION_OPEN),
        )
        PeerActionMenu(
            peer = peer,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onDeleteRequested = {
                menuExpanded = false
                onDeleteRequested()
            },
        )
    }
}

@Composable
internal fun PeerListItem(
    peer: Peer,
    modifier: Modifier,
    trailingIcon: ImageVector,
    trailingContentDescription: String,
    interactionModifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = modifier
            .clip(shape)
            .then(interactionModifier),
        shape = shape,
    ) {
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
            Icon(trailingIcon, contentDescription = trailingContentDescription)
        }
    }
}

@Composable
private fun PeerActionMenu(
    peer: Peer,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        PeerMenuHeader(peer)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = appString(AppString.DELETE_DEVICE),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            },
            leadingIcon = {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = onDeleteRequested,
        )
    }
}

@Composable
private fun PeerMenuHeader(peer: Peer) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeerAvatar(peer.displayName, peer.availability == PeerAvailability.ONLINE)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = peer.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = peer.trustState.label(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DeletePeerDialog(
    peer: Peer,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var deleteHistory by remember(peer.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DeleteDialogIcon() },
        title = { Text(appString(AppString.DELETE_DEVICE_TITLE, peer.displayName)) },
        text = {
            Column {
                Text(
                    text = appString(AppString.DELETE_DEVICE_MESSAGE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DeleteHistoryOption(
                    selected = deleteHistory,
                    onSelectedChange = { deleteHistory = it },
                )
            }
        },
        confirmButton = { DeleteConfirmButton { onConfirm(deleteHistory) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString(AppString.ACTION_CANCEL)) }
        },
    )
}

@Composable
private fun DeleteDialogIcon() {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DeleteHistoryOption(selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clickable { onSelectedChange(!selected) },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.error,
                    checkmarkColor = MaterialTheme.colorScheme.onError,
                ),
            )
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(
                    text = appString(AppString.DELETE_CHAT_HISTORY),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = appString(AppString.DELETE_CHAT_HISTORY_DETAIL),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(appString(AppString.DELETE_DEVICE))
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
    saveDirectory: String,
    requireIncomingFileConfirmation: Boolean,
    maxFileSizeBytes: Long,
    onSaveDirectoryChanged: (String) -> Unit,
    onIncomingFileConfirmationChanged: (Boolean) -> Unit,
    onMaxFileSizeChanged: (Long) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onSettingsError: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val openGitHubFailedMessage = appString(AppString.OPEN_GITHUB_FAILED)
    var draftName by remember(displayName) { mutableStateOf(displayName.orEmpty()) }
    var draftMaxFileSizeGiB by remember(maxFileSizeBytes) {
        mutableStateOf((maxFileSizeBytes / BYTES_PER_GIB).toString())
    }
    val minFileSizeGiB = MIN_CONFIGURABLE_MAX_FILE_SIZE_BYTES / BYTES_PER_GIB
    val maxFileSizeGiB = MAX_CONFIGURABLE_MAX_FILE_SIZE_BYTES / BYTES_PER_GIB
    val parsedMaxFileSizeGiB = draftMaxFileSizeGiB.toLongOrNull()
    val isMaxFileSizeValid = parsedMaxFileSizeGiB != null &&
        parsedMaxFileSizeGiB in minFileSizeGiB..maxFileSizeGiB
    val launchDirectoryPicker = rememberSaveDirectoryPickerLauncher(
        currentDirectory = saveDirectory,
        onDirectorySelected = onSaveDirectoryChanged,
        onError = onSettingsError,
    )
    var showLanguageDialog by remember { mutableStateOf(false) }
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            selected = appLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelected = {
                showLanguageDialog = false
                onAppLanguageChanged(it)
            },
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                appString(AppString.LOCAL_DEVICE_INFO),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            OutlinedTextField(
                value = draftName,
                onValueChange = { if (it.length <= MAX_DISPLAY_NAME_LENGTH) draftName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(appString(AppString.DEVICE_NAME)) },
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
                Text(appString(AppString.SAVE_AND_REPUBLISH), modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            SettingValue(
                rememberVectorPainter(Icons.Outlined.Devices),
                appString(AppString.DEVICE_ID),
                deviceId ?: appString(AppString.NOT_READY),
            )
        }
        item {
            Text(
                text = appString(AppString.SETTINGS),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            SettingValue(
                icon = rememberVectorPainter(Icons.Outlined.Language),
                label = appString(AppString.APP_LANGUAGE),
                value = appLanguage.label(),
                modifier = Modifier.clickable { showLanguageDialog = true },
                trailingIcon = Icons.Outlined.ChevronRight,
            )
        }
        item {
            ToggleSetting(
                icon = Icons.Outlined.Security,
                label = appString(AppString.CONFIRM_BEFORE_RECEIVING),
                detail = if (requireIncomingFileConfirmation) {
                    appString(AppString.CONFIRM_EACH_FILE)
                } else {
                    appString(AppString.AUTO_RECEIVE_FILES)
                },
                checked = requireIncomingFileConfirmation,
                onCheckedChange = onIncomingFileConfirmationChanged,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            appString(AppString.SINGLE_FILE_SIZE_LIMIT),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        OutlinedTextField(
                            value = draftMaxFileSizeGiB,
                            onValueChange = { value ->
                                if (value.length <= MAX_FILE_SIZE_INPUT_LENGTH && value.all { it in '0'..'9' }) {
                                    draftMaxFileSizeGiB = value
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text(appString(AppString.SIZE)) },
                            suffix = { Text("GiB") },
                            supportingText = {
                                Text(
                                    appString(
                                        AppString.FILE_SIZE_RANGE,
                                        minFileSizeGiB,
                                        maxFileSizeGiB,
                                    ),
                                )
                            },
                            isError = draftMaxFileSizeGiB.isNotEmpty() && !isMaxFileSizeValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Button(
                            onClick = {
                                parsedMaxFileSizeGiB?.let { onMaxFileSizeChanged(it * BYTES_PER_GIB) }
                            },
                            enabled = isMaxFileSizeValid &&
                                parsedMaxFileSizeGiB * BYTES_PER_GIB != maxFileSizeBytes,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(appString(AppString.SAVE_LIMIT))
                        }
                    }
                }
            }
        }
        item {
            SettingValue(
                icon = rememberVectorPainter(Icons.Outlined.FolderOpen),
                label = appString(AppString.FILE_SAVE_LOCATION),
                value = displaySaveDirectory(
                    saveDirectory,
                    appString(AppString.PUBLIC_DOWNLOADS_LOCATION),
                ),
                modifier = Modifier.clickable(onClick = launchDirectoryPicker),
                trailingIcon = Icons.Outlined.ChevronRight,
            )
        }
        item {
            Text(
                text = appString(AppString.ABOUT),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            SettingValue(
                icon = painterResource(Res.drawable.github_mark),
                iconTint = MaterialTheme.colorScheme.onSurface,
                label = appString(AppString.GITHUB),
                value = SUBNETDROP_REPOSITORY_URL.removePrefix("https://"),
                modifier = Modifier.clickable {
                    runCatching { uriHandler.openUri(SUBNETDROP_REPOSITORY_URL) }
                        .onFailure { onSettingsError(openGitHubFailedMessage) }
                },
                trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                trailingIconDescription = appString(AppString.OPEN_GITHUB),
            )
        }
    }
}

@Composable
private fun LanguageSelectionDialog(
    selected: AppLanguage,
    onDismiss: () -> Unit,
    onSelected: (AppLanguage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString(AppString.SELECT_APP_LANGUAGE)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(language) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = language == selected,
                            onClick = { onSelected(language) },
                        )
                        Text(language.label(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(appString(AppString.ACTION_CANCEL))
            }
        },
    )
}

@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> appString(AppString.LANGUAGE_SYSTEM)
    AppLanguage.SIMPLIFIED_CHINESE -> appString(AppString.LANGUAGE_SIMPLIFIED_CHINESE)
    AppLanguage.ENGLISH -> appString(AppString.LANGUAGE_ENGLISH)
    AppLanguage.JAPANESE -> appString(AppString.LANGUAGE_JAPANESE)
}

@Composable
private fun ToggleSetting(
    icon: ImageVector,
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingValue(
    icon: Painter,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = icon, contentDescription = null, tint = iconTint)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = trailingIconDescription ?: appString(AppString.CHANGE_SETTING, label),
                )
            }
        }
    }
}

private const val MAX_FILE_SIZE_INPUT_LENGTH = 4
private const val SUBNETDROP_REPOSITORY_URL = "https://github.com/x2ink/subnetdrop"

@Composable
private fun RuntimeState.label(): String = when (this) {
    RuntimeState.Stopped -> appString(AppString.RUNTIME_NOT_STARTED)
    is RuntimeState.Starting -> when (phase) {
        RuntimeStartupPhase.LOADING_PROFILE -> appString(AppString.LOADING_LOCAL_PROFILE)
        RuntimeStartupPhase.RESETTING_PEERS -> appString(AppString.RUNTIME_RESETTING_PEERS)
        RuntimeStartupPhase.STARTING_TRANSPORT -> appString(AppString.RUNTIME_STARTING_TRANSPORT)
        RuntimeStartupPhase.STARTING_DISCOVERY -> appString(AppString.RUNTIME_STARTING_DISCOVERY)
    }
    is RuntimeState.Running -> appString(AppString.RUNTIME_ONLINE)
    is RuntimeState.Degraded -> reason?.takeIf(String::isNotBlank)?.let {
        appString(AppString.RUNTIME_DEGRADED, it)
    } ?: appString(AppString.RUNTIME_DEGRADED_NO_DETAIL)
    is RuntimeState.Failed -> reason?.takeIf(String::isNotBlank)?.let {
        appString(AppString.RUNTIME_FAILED, it)
    } ?: appString(AppString.RUNTIME_FAILED_NO_DETAIL)
}

@Composable
private fun TrustState.label(): String = when (this) {
    TrustState.UNPAIRED -> appString(AppString.TRUST_UNPAIRED)
    TrustState.PENDING -> appString(AppString.TRUST_PENDING)
    TrustState.TRUSTED -> appString(AppString.TRUST_TRUSTED)
    TrustState.KEY_CHANGED -> appString(AppString.TRUST_KEY_CHANGED)
}

private val ONLINE_COLOR = Color(0xFF2EAD68)
private const val MAX_DISPLAY_NAME_LENGTH = 64
