package com.screenreader.translator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MainDashboardScreen(
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    hasProjectionPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestProjectionPermission: () -> Unit,
    onToggleService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isModelDownloaded by remember { mutableStateOf(false) }
    var isDownloadingModel by remember { mutableStateOf(false) }

    var cacheCount by remember { mutableIntStateOf(0) }
    var historyCount by remember { mutableIntStateOf(0) }

    // باکس تست ترجمه دستی
    var testInputText by remember { mutableStateOf("Welcome to instant screen translation") }
    var testOutputText by remember { mutableStateOf("") }
    var isTestingTranslate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isModelDownloaded = ScreenTranslatorApp.repository.isOfflineModelReady()
        cacheCount = ScreenTranslatorApp.database.translationCacheDao().getCacheCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // هدر اصلی با طراحی مدرن
        HeaderCard()

        Spacer(modifier = Modifier.height(16.dp))

        // کارت وضعیت سوئیچ و فعال‌سازی مترجم شناور
        ServiceControlCard(
            isServiceRunning = isServiceRunning,
            hasOverlayPermission = hasOverlayPermission,
            hasProjectionPermission = hasProjectionPermission,
            onToggleService = onToggleService,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onRequestProjectionPermission = onRequestProjectionPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        // وضعیت مدل هوش مصنوعی آفلاین
        OfflineModelCard(
            isModelDownloaded = isModelDownloaded,
            isDownloadingModel = isDownloadingModel,
            onDownloadClicked = {
                coroutineScope.launch {
                    isDownloadingModel = true
                    val success = ScreenTranslatorApp.repository.downloadOfflineModel()
                    isModelDownloaded = success
                    isDownloadingModel = false
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // کارت آمار و سلامت پایگاه داده قدرتمند Room
        DatabaseStatsCard(cacheCount = cacheCount)

        Spacer(modifier = Modifier.height(16.dp))

        // تست آنی ترجمه و بررسی کش پایگاه داده
        QuickTestCard(
            inputText = testInputText,
            outputText = testOutputText,
            isLoading = isTestingTranslate,
            onInputChange = { testInputText = it },
            onTranslateClick = {
                coroutineScope.launch {
                    isTestingTranslate = true
                    testOutputText = ScreenTranslatorApp.repository.translateText(testInputText)
                    cacheCount = ScreenTranslatorApp.database.translationCacheDao().getCacheCount()
                    isTestingTranslate = false
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // کارت حمایت مالی و مخزن گیت‌هاب
        DonationAndGithubCard(uriHandler = uriHandler, context = context)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.2f))
                    .border(2.dp, PrimaryBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "مترجم هوشمند روی صفحه",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "خواندن متن با OCR و ترجمه مستقیم درجا",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ServiceControlCard(
    isServiceRunning: Boolean,
    hasOverlayPermission: Boolean,
    hasProjectionPermission: Boolean,
    onToggleService: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestProjectionPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "دکمه شناور روی صفحه",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isServiceRunning) "سرویس فعال و آماده ترجمه" else "سرویس شناور غیرفعال است",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isServiceRunning) SuccessGreen else TextSecondary
                    )
                }

                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { checked ->
                        if (checked && (!hasOverlayPermission || !hasProjectionPermission)) {
                            if (!hasOverlayPermission) onRequestOverlayPermission()
                            else onRequestProjectionPermission()
                        } else {
                            onToggleService(checked)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = BorderDark)

            Spacer(modifier = Modifier.height(14.dp))

            // چک‌لیست مجوزها
            Text(
                text = "مجوزهای سیستمی موردنیاز:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionRow(
                title = "نمایش بر روی سایر برنامه‌ها (Overlay)",
                isGranted = hasOverlayPermission,
                onFixClick = onRequestOverlayPermission
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                title = "خواندن و اسکن صفحه (MediaProjection)",
                isGranted = hasProjectionPermission,
                onFixClick = onRequestProjectionPermission
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    isGranted: Boolean,
    onFixClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else DangerRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isGranted) TextPrimary else TextSecondary
            )
        }

        if (!isGranted) {
            TextButton(onClick = onFixClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("فعال‌سازی", color = PrimaryBlue, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun OfflineModelCard(
    isModelDownloaded: Boolean,
    isDownloadingModel: Boolean,
    onDownloadClicked: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مدل آفلاین زبان فارسی",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isModelDownloaded)
                        "مدل هوش مصنوعی محلی فعال است (ترجمه آفلاین 0ms)"
                    else
                        "برای ترجمه بدون نیاز به اینترنت، مدل را دریافت کنید (~30MB)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isModelDownloaded) SuccessGreen else TextSecondary
                )
            }

            if (!isModelDownloaded) {
                Button(
                    onClick = onDownloadClicked,
                    enabled = !isDownloadingModel,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDownloadingModel) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("دانلود مدل", fontSize = 12.sp)
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "آماده آفلاین",
                        color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseStatsCard(cacheCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "پایگاه داده پرسرعت محلی (Room DB)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(label = "کش ترجمه‌ها", value = "$cacheCount رکورد")
                StatItem(label = "دیکشنری آفلاین", value = "۲۲+ کلمه پیش‌فرض")
                StatItem(label = "سرعت بازیابی", value = "< ۱ میلی‌ثانیه")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = AccentCyan, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun QuickTestCard(
    inputText: String,
    outputText: String,
    isLoading: Boolean,
    onInputChange: (String) -> Unit,
    onTranslateClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "تست سریع ترجمه و ثبت در دیتابیس",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("متن انگلیسی") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onTranslateClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("ترجمه و ذخیره در کش دیتابیس")
                }
            }

            AnimatedVisibility(visible = outputText.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "ترجمه فارسی:",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceDark
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = outputText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("ترجمه", outputText)
                                clipboard.setPrimaryClip(clip)
                            }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonationAndGithubCard(uriHandler: androidx.compose.ui.platform.UriHandler, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حمایت مالی و مخزن گیت‌هاب",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "این پروژه به صورت متن‌باز توسعه داده می‌شود. با حمایت مالی می‌توانید به توسعه قابلیت‌های جدید و استمرار پروژه کمک کنید.",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            uriHandler.openUri("https://github.com/martialpeak/ScreenTranslator")
                        } catch (_: Exception) {
                            Toast.makeText(context, "خطا در باز کردن مرورگر", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("گیت‌هاب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        try {
                            uriHandler.openUri("https://donito.me/M_Alone")
                        } catch (_: Exception) {
                            Toast.makeText(context, "خطا در باز کردن مرورگر", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حمایت مالی (Donito)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
