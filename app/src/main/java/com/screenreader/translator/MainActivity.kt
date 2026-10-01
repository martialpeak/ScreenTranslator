package com.screenreader.translator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.screenreader.translator.service.FloatingBubbleService
import com.screenreader.translator.service.ScreenCaptureService
import com.screenreader.translator.ui.screens.GlossaryScreen
import com.screenreader.translator.ui.screens.HistoryScreen
import com.screenreader.translator.ui.screens.MainDashboardScreen
import com.screenreader.translator.ui.screens.ManhwaReaderScreen
import com.screenreader.translator.ui.screens.SettingsScreen
import com.screenreader.translator.ui.theme.DarkBackground
import com.screenreader.translator.ui.theme.PrimaryBlue
import com.screenreader.translator.ui.theme.ScreenTranslatorTheme
import com.screenreader.translator.ui.theme.SurfaceDark

class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasProjectionPermission by mutableStateOf(false)
    private var isServiceRunning by mutableStateOf(false)

    // مجوز همین الان گرفته شده و سرویس کپچر هنوز در حال راه‌اندازی است
    private var projectionJustGranted = false

    // دریافت نتیجه مجوز کپچر صفحه (MediaProjection)
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            hasProjectionPermission = true
            projectionJustGranted = true
            startCaptureService(result.resultCode, result.data!!)
            startBubbleServiceInternal()
            Toast.makeText(this, "دکمه شناور روی صفحه فعال شد", Toast.LENGTH_SHORT).show()
        } else {
            hasProjectionPermission = false
            Toast.makeText(this, "برای خواندن متن روی صفحه نیاز به تایید مجوز است", Toast.LENGTH_LONG).show()
        }
    }

    // دریافت نتیجه اعلان‌ها در اندروید ۱۳+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        requestNotificationPermission()

        setContent {
            ScreenTranslatorTheme {
                MainAppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        isServiceRunning = FloatingBubbleService.isRunning

        // همگام‌سازی وضعیت مجوز با وضعیت واقعی سرویس کپچر (بعد از recreate شدن Activity،
        // یا وقتی کاربر از نوار اعلان/سیستم پروجکشن را متوقف کرده است)
        if (projectionJustGranted) {
            projectionJustGranted = false
        } else {
            hasProjectionPermission = ScreenCaptureService.isProjectionReady
        }
    }

    private fun checkPermissions() {
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestProjectionPermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START_PROJECTION
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startBubbleServiceInternal() {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
    }

    private fun toggleBubbleService(start: Boolean) {
        if (start) {
            if (!hasOverlayPermission) {
                requestOverlayPermission()
                return
            }
            if (!hasProjectionPermission || !ScreenCaptureService.isProjectionReady) {
                requestProjectionPermission()
                return
            }
            startBubbleServiceInternal()
            Toast.makeText(this, "دکمه شناور روی صفحه فعال گردید", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(this, FloatingBubbleService::class.java).apply {
                action = FloatingBubbleService.ACTION_STOP_SERVICE
            }
            startService(intent)
            isServiceRunning = false
            // با توقف حباب، سرویس کپچر و MediaProjection هم متوقف می‌شوند
            hasProjectionPermission = false
        }
    }

    @Composable
    private fun MainAppNavigation() {
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text("داشبورد") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                        label = { Text("خوانشگر") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("تاریخچه") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                        label = { Text("واژه‌نامه") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("تنظیمات") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> MainDashboardScreen(
                        isServiceRunning = isServiceRunning,
                        hasOverlayPermission = hasOverlayPermission,
                        hasProjectionPermission = hasProjectionPermission,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestProjectionPermission = { requestProjectionPermission() },
                        onToggleService = { toggleBubbleService(it) }
                    )
                    1 -> ManhwaReaderScreen()
                    2 -> HistoryScreen()
                    3 -> GlossaryScreen()
                    4 -> SettingsScreen()
                }
            }
        }
    }
}
