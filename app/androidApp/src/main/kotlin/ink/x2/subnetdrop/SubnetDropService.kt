package ink.x2.subnetdrop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.text.format.Formatter
import ink.x2.subnetdrop.domain.port.FileTransferService
import ink.x2.subnetdrop.runtime.SubnetDropRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class SubnetDropService : Service() {
    private val runtime by inject<SubnetDropRuntime>()
    private val fileTransferService by inject<FileTransferService>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        serviceScope.launch { runtime.start() }
        observeTransferNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        // Complete socket shutdown before a sticky replacement can reuse the process-level Runtime singleton.
        runBlocking(Dispatchers.Default) {
            withTimeoutOrNull(SERVICE_SHUTDOWN_TIMEOUT_MS) { runtime.stop() }
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.background_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.background_service_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @OptIn(FlowPreview::class)
    private fun observeTransferNotifications() {
        serviceScope.launch {
            fileTransferService.transfers
                .map { transfers -> transfers.toNotificationState() }
                .distinctUntilChanged()
                .sample(NOTIFICATION_UPDATE_INTERVAL_MS.milliseconds)
                .collect { state ->
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        createNotification(state),
                    )
                }
        }
    }

    private fun createNotification(
        state: FileTransferNotificationState = FileTransferNotificationState.Idle,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            CONTENT_INTENT_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_subnetdrop)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .applyNotificationState(state)
            .build()
    }

    private fun Notification.Builder.applyNotificationState(
        state: FileTransferNotificationState,
    ): Notification.Builder = apply {
        when (state) {
            FileTransferNotificationState.Idle -> {
                setContentTitle(getString(R.string.background_service_notification_title))
                setContentText(getString(R.string.background_service_notification_text))
            }
            is FileTransferNotificationState.Active -> {
                setContentTitle(activeTransferTitle(state))
                setContentText(activeTransferProgress(state))
                setProgress(NOTIFICATION_PROGRESS_MAX, state.progress, state.totalBytes == 0L)
            }
        }
    }

    private fun activeTransferTitle(state: FileTransferNotificationState.Active): String {
        if (state.fileCount == 1) {
            val title = when (state.direction) {
                NotificationTransferDirection.INCOMING -> R.string.background_service_receiving_file
                NotificationTransferDirection.OUTGOING -> R.string.background_service_sending_file
                NotificationTransferDirection.MIXED -> R.string.background_service_transferring_file
            }
            return getString(title, state.fileName.orEmpty())
        }
        val title = when (state.direction) {
            NotificationTransferDirection.INCOMING -> R.string.background_service_receiving_files
            NotificationTransferDirection.OUTGOING -> R.string.background_service_sending_files
            NotificationTransferDirection.MIXED -> R.string.background_service_transferring_files
        }
        return getString(title, state.fileCount)
    }

    private fun activeTransferProgress(state: FileTransferNotificationState.Active): String {
        if (state.totalBytes == 0L) return getString(R.string.background_service_preparing_transfer)
        return getString(
            R.string.background_service_transfer_progress,
            Formatter.formatShortFileSize(this, state.transferredBytes),
            Formatter.formatShortFileSize(this, state.totalBytes),
            state.percentage,
        )
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "subnetdrop_background_service"
        private const val NOTIFICATION_ID = 45892
        private const val CONTENT_INTENT_REQUEST_CODE = 1
        private const val SERVICE_SHUTDOWN_TIMEOUT_MS = 3_000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SubnetDropService::class.java))
        }
    }
}
