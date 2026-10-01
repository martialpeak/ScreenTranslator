package com.screenreader.translator.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.data.local.AppPreferences
import com.screenreader.translator.engine.export.PdfToWebpConverter
import com.screenreader.translator.engine.export.WebpZipExportManager
import com.screenreader.translator.engine.import_.ZipImageImporter
import com.screenreader.translator.engine.ocr.ScreenOcrScanner
import com.screenreader.translator.engine.rendering.ComicBubbleRenderer
import com.screenreader.translator.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * صفحه اختصاصی خوانشگر مانهوا و کمیک (پشتیبانی از فایل PDF، تصاویر چندگانه چپتر، و وب‌تون آنلاین)
 * با قابلیت ترجمه زنده صفحه وب، استخراج دیالوگ‌ها، بالن‌گذاری فارسی و خروجی کم‌حجم PDF
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManhwaReaderScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember { mutableIntStateOf(0) } // 0: PDF, 1: تصاویر چپتر (گالری), 2: وب‌تون
    var webUrl by remember { mutableStateOf("https://www.webtoons.com") }
    var activeWebUrl by remember { mutableStateOf<String?>(null) }

    // وضعیت پردازش صفحات PDF
    var isProcessingPdf by remember { mutableStateOf(false) }
    var currentProcessingPage by remember { mutableIntStateOf(0) }
    var totalPdfPages by remember { mutableIntStateOf(0) }

    // وضعیت پردازش تصاویر چپتر
    var isProcessingImages by remember { mutableStateOf(false) }
    var currentProcessingImage by remember { mutableIntStateOf(0) }
    var totalImages by remember { mutableIntStateOf(0) }

    // وضعیت ترجمه صفحه وب‌تون آنلاین
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isTranslatingWebPage by remember { mutableStateOf(false) }
    var translatedWebBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showTranslatedWebOverlay by remember { mutableStateOf(false) }
    var webPageTitle by remember { mutableStateOf("") }

    // لیست صفحات ترجمه شده آماده نمایش و خروجی PDF
    val renderedPages = remember { mutableStateListOf<Bitmap>() }

    // وضعیت ذخیره خروجی PDF
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }

    // وضعیت خروجی WebP+ZIP
    var isExportingWebpZip by remember { mutableStateOf(false) }

    // وضعیت تبدیل PDF → WebP+ZIP
    var isConvertingPdfToWebp by remember { mutableStateOf(false) }
    var convertProgress by remember { mutableStateOf("") }

    // وضعیت ورود ZIP حاوی تصاویر
    var isImportingZip by remember { mutableStateOf(false) }
    var zipImportProgress by remember { mutableStateOf("") }

    val preferences = remember { AppPreferences.getInstance(context) }

    // نام تم بالن جاری
    val bubbleThemeLabel = when (preferences.bubbleTheme) {
        AppPreferences.BUBBLE_THEME_DARK -> "⚫ تیره مانهوا"
        AppPreferences.BUBBLE_THEME_TRANSLUCENT -> "🪟 شیشه‌ای"
        else -> "⚪ سفید کلاسیک"
    }

    // وضعیت موتور ترجمه فعال
    val providerName = when (preferences.llmProvider) {
        AppPreferences.PROVIDER_GEMINI -> "Gemini Flash ⚡"
        AppPreferences.PROVIDER_DEEPSEEK -> "DeepSeek V3/R1 🚀"
        AppPreferences.PROVIDER_OPENROUTER -> "OpenRouter 🌐"
        AppPreferences.PROVIDER_OPENAI -> "ChatGPT 🤖"
        AppPreferences.PROVIDER_GROK -> "Grok xAI 🛸"
        AppPreferences.PROVIDER_GROQ -> "Groq LPU ⚡"
        AppPreferences.PROVIDER_CUSTOM -> "سفارشی 🌐"
        else -> "آفلاین ML Kit 📚"
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            renderedPages.clear()
            isProcessingPdf = true
            currentProcessingPage = 0
            totalPdfPages = 0

            coroutineScope.launch {
                processPdfFile(
                    context = context,
                    uri = uri,
                    onTotalPages = { total -> totalPdfPages = total },
                    onPageProgress = { current -> currentProcessingPage = current },
                    onPageDone = { bitmap -> renderedPages.add(bitmap) },
                    onFinished = { isProcessingPdf = false }
                )
            }
        }
    }

    val imagesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            renderedPages.clear()
            isProcessingImages = true
            currentProcessingImage = 0
            totalImages = uris.size

            coroutineScope.launch {
                processImageChapter(
                    context = context,
                    uris = uris,
                    onTotalImages = { total -> totalImages = total },
                    onImageProgress = { cur -> currentProcessingImage = cur },
                    onPageDone = { bitmap -> renderedPages.add(bitmap) },
                    onFinished = { isProcessingImages = false }
                )
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            val pagesToExport = if (renderedPages.isNotEmpty()) {
                renderedPages.toList()
            } else if (translatedWebBitmap != null) {
                listOf(translatedWebBitmap!!)
            } else {
                emptyList()
            }

            if (pagesToExport.isEmpty()) {
                Toast.makeText(context, "صفحه‌ای برای ذخیره در PDF موجود نیست", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isExportingPdf = true
            coroutineScope.launch {
                val success = com.screenreader.translator.engine.pdf.PdfExportManager.exportBitmapsToPdf(
                    context = context,
                    pages = pagesToExport,
                    targetUri = uri,
                    onProgress = { cur, tot ->
                        exportProgress = "صفحه $cur از $tot در حال نوشتن..."
                    }
                )
                isExportingPdf = false
                if (success) {
                    Toast.makeText(context, "فایل PDF فشرده و ترجمه‌شده با موفقیت ذخیره شد!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "خطا در ذخیره فایل PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ===== لانچر خروجی WebP+ZIP از صفحات ترجمه‌شده =====
    val webpZipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            val pagesToExport = if (renderedPages.isNotEmpty()) {
                renderedPages.toList()
            } else if (translatedWebBitmap != null) {
                listOf(translatedWebBitmap!!)
            } else {
                emptyList()
            }

            if (pagesToExport.isEmpty()) {
                Toast.makeText(context, "صفحه‌ای برای خروجی WebP موجود نیست", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isExportingWebpZip = true
            coroutineScope.launch {
                val success = WebpZipExportManager.exportBitmapsToWebpZip(
                    context = context,
                    pages = pagesToExport,
                    targetUri = uri,
                    onProgress = { cur, tot ->
                        exportProgress = "تبدیل صفحه $cur از $tot به WebP..."
                    }
                )
                isExportingWebpZip = false
                if (success) {
                    Toast.makeText(context, "✅ ${pagesToExport.size} صفحه به فرمت WebP در ZIP ذخیره شد!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "خطا در ذخیره فایل ZIP", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ===== لانچر تبدیل PDF → WebP+ZIP: مرحله ۱ - انتخاب PDF =====
    var pendingPdfUri by remember { mutableStateOf<Uri?>(null) }

    val pdfToWebpSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { zipUri: Uri? ->
        val pdfUri = pendingPdfUri
        if (zipUri != null && pdfUri != null) {
            isConvertingPdfToWebp = true
            coroutineScope.launch {
                val success = PdfToWebpConverter.convertPdfToWebpZip(
                    context = context,
                    pdfUri = pdfUri,
                    targetZipUri = zipUri,
                    onProgress = { cur, tot ->
                        convertProgress = "تبدیل صفحه $cur از $tot..."
                    }
                )
                isConvertingPdfToWebp = false
                pendingPdfUri = null
                if (success) {
                    Toast.makeText(context, "✅ PDF به WebP+ZIP تبدیل شد!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "خطا در تبدیل PDF به WebP", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pdfToWebpPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingPdfUri = uri
            pdfToWebpSaveLauncher.launch("converted_${System.currentTimeMillis()}.zip")
        }
    }

    // ===== لانچر ورود ZIP حاوی تصاویر برای ترجمه =====
    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            renderedPages.clear()
            isImportingZip = true

            coroutineScope.launch {
                val displayMetrics = context.resources.displayMetrics
                val targetWidth = displayMetrics.widthPixels.coerceIn(1080, 1440)

                // مرحله ۱: استخراج تصاویر از ZIP
                zipImportProgress = "در حال باز کردن فایل ZIP..."
                val bitmaps = ZipImageImporter.extractImagesFromZip(
                    context = context,
                    zipUri = uri,
                    targetWidth = targetWidth,
                    onProgress = { cur, tot ->
                        zipImportProgress = "استخراج تصویر $cur از $tot..."
                    }
                )

                if (bitmaps.isEmpty()) {
                    isImportingZip = false
                    Toast.makeText(context, "هیچ تصویری در ZIP یافت نشد!", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // مرحله ۲: OCR و ترجمه هر تصویر
                val ocrScanner = ScreenOcrScanner()
                val repository = ScreenTranslatorApp.repository
                try {
                    for ((index, bitmap) in bitmaps.withIndex()) {
                        zipImportProgress = "ترجمه تصویر ${index + 1} از ${bitmaps.size}..."

                        val blocks = withContext(Dispatchers.IO) {
                            ocrScanner.scanLongBitmapInSlices(
                                bitmap = bitmap,
                                sliceHeight = 2400,
                                overlap = 400,
                                filterSystemMargins = false,
                                ocrLanguageMode = preferences.ocrLanguageMode
                            )
                        }

                        val finalBitmap = if (blocks.isNotEmpty()) {
                            val translatedBlocks = withContext(Dispatchers.IO) {
                                repository.translateBlocks(blocks, sourceBitmap = bitmap)
                            }
                            withContext(Dispatchers.IO) {
                                ComicBubbleRenderer.drawTranslationsOnBitmap(
                                    sourceBitmap = bitmap,
                                    blocks = translatedBlocks,
                                    theme = preferences.bubbleTheme,
                                    fontScale = preferences.bubbleFontSizeScale
                                )
                            }
                        } else {
                            bitmap
                        }

                        renderedPages.add(finalBitmap)
                    }
                    Toast.makeText(context, "✅ ${bitmaps.size} تصویر از ZIP ترجمه شد!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "خطا در ترجمه تصاویر ZIP: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    ocrScanner.close()
                    isImportingZip = false
                }
            }
        }
    }

    // تابع اسکن و ترجمه صفحه جاری وب‌ویو
    fun translateCurrentWebPage() {
        val wv = webViewRef ?: return
        if (isTranslatingWebPage) return

        val captured = captureWebViewBitmap(wv)
        if (captured == null) {
            Toast.makeText(context, "خطا در عکس‌برداری از صفحه وب", Toast.LENGTH_SHORT).show()
            return
        }

        isTranslatingWebPage = true
        coroutineScope.launch {
            try {
                val ocrScanner = ScreenOcrScanner()
                val repository = ScreenTranslatorApp.repository

                val blocks = withContext(Dispatchers.IO) {
                    ocrScanner.scanLongBitmapInSlices(
                        bitmap = captured,
                        sliceHeight = 2400,
                        overlap = 400,
                        filterSystemMargins = false,
                        ocrLanguageMode = preferences.ocrLanguageMode
                    )
                }

                if (blocks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "هیچ متن یا دیالوگی در این قسمت صفحه وب یافت نشد.", Toast.LENGTH_SHORT).show()
                        isTranslatingWebPage = false
                    }
                    ocrScanner.close()
                    return@launch
                }

                val translatedBlocks = withContext(Dispatchers.IO) {
                    repository.translateBlocks(blocks, sourceBitmap = captured)
                }

                val finalBitmap = withContext(Dispatchers.IO) {
                    ComicBubbleRenderer.drawTranslationsOnBitmap(
                        sourceBitmap = captured,
                        blocks = translatedBlocks,
                        theme = preferences.bubbleTheme,
                        fontScale = preferences.bubbleFontSizeScale
                    )
                }

                ocrScanner.close()

                withContext(Dispatchers.Main) {
                    translatedWebBitmap = finalBitmap
                    showTranslatedWebOverlay = true
                    isTranslatingWebPage = false
                    Toast.makeText(context, "✅ ${blocks.size} دیالوگ ترجمه و جایگذاری شد!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isTranslatingWebPage = false
                    Toast.makeText(context, "خطا در ترجمه: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isBusy = isProcessingPdf || isProcessingImages || isExportingPdf || isTranslatingWebPage || isExportingWebpZip || isConvertingPdfToWebp || isImportingZip

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // نوار تب‌های سه‌گانه انتخاب مود: PDF، تصاویر چندگانه، یا وب‌تون آنلاین
        TabRow(
            selectedTabIndex = selectedMode,
            containerColor = SurfaceDark,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedMode == 0,
                onClick = { selectedMode = 0 },
                text = { Text("📄 فایل PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedMode == 1,
                onClick = { selectedMode = 1 },
                text = { Text("🖼️ تصاویر چپتر", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedMode == 2,
                onClick = { selectedMode = 2 },
                text = { Text("🌐 وب‌تون با لینک", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedMode == 0) {
            // === حالت ۱: کتابخوان فایل PDF مانهوا ===
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ترجمه هوشمند و جایگذاری مستقیم روی PDF مانهوا",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "فایل چپتر را انتخاب کنید؛ دیالوگ‌ها اسکن، ترجمه و با بالن‌های فارسی بازنویسی می‌شوند.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "موتور: $providerName",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "تم بالن: $bubbleThemeLabel",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessingPdf) "در حال پردازش و ترجمه صفحات..." else "📁 انتخاب فایل PDF چپتر",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isProcessingPdf) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (totalPdfPages > 0) currentProcessingPage.toFloat() / totalPdfPages.toFloat() else 0f
                            },
                            color = PrimaryBlue,
                            trackColor = SurfaceDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "صفحه $currentProcessingPage از $totalPdfPages در حال ترجمه هوشمند...",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // دکمه تبدیل مستقیم PDF به WebP+ZIP بدون ترجمه
                    OutlinedButton(
                        onClick = {
                            pdfToWebpPickLauncher.launch(arrayOf("application/pdf"))
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan)
                    ) {
                        Icon(Icons.Default.Transform, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConvertingPdfToWebp) "در حال تبدیل صفحات به WebP..." else "🔄 تبدیل مستقیم PDF به WebP + ZIP (بدون ترجمه)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (isConvertingPdfToWebp) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            color = AccentCyan,
                            trackColor = SurfaceDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = convertProgress.ifEmpty { "در حال تبدیل صفحات PDF به فرمت فشرده WebP در ZIP..." },
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

        } else if (selectedMode == 1) {
            // === حالت ۲: تصاویر چندگانه چپتر (پوشه / گالری) ===
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ترجمه و ساخت PDF از تصاویر چپتر (گالری / پوشه)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عکس‌های دانلود شده یک چپتر (JPG, PNG, WEBP) را انتخاب کنید تا برنامه به ترتیب آنها را اسکن، ترجمه و بالن‌گذاری کند.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "موتور: $providerName",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "تم بالن: $bubbleThemeLabel",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            imagesPickerLauncher.launch(arrayOf("image/*"))
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessingImages) "در حال پردازش و ترجمه تصاویر..." else "🖼️ انتخاب تصاویر چپتر مانهوا",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isProcessingImages) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (totalImages > 0) currentProcessingImage.toFloat() / totalImages.toFloat() else 0f
                            },
                            color = PrimaryBlue,
                            trackColor = SurfaceDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تصویر $currentProcessingImage از $totalImages در حال ترجمه هوشمند...",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // دکمه ورود و ترجمه فایل ZIP تصاویر
                    OutlinedButton(
                        onClick = {
                            zipImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA))
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isImportingZip) "در حال پردازش و ترجمه ZIP..." else "📦 انتخاب فایل ZIP تصاویر (WebP/JPG/PNG)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (isImportingZip) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            color = Color(0xFFA78BFA),
                            trackColor = SurfaceDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = zipImportProgress.ifEmpty { "در حال خواندن و ترجمه تصاویر داخل ZIP..." },
                            color = Color(0xFFA78BFA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

        } else {
            // === حالت ۳: وب‌تون آنلاین همراه با مترجم هوشمند و ذخیره PDF ===
            if (activeWebUrl == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "کتابخوان داخلی وب‌تون با اسکن، ترجمه و ذخیره PDF",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "آدرس صفحه چپتر وب‌تون را وارد کنید؛ می‌توانید همزمان با اسکرول کردن، هر بخش را با ۱ کلیک ترجمه و بالن‌گذاری کرده و به فایل PDF اضافه کنید.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = webUrl,
                            onValueChange = { webUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SurfaceDark,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("https://www.webtoons.com/...", color = TextSecondary) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // دکمه‌های آدرس‌های آماده و محبوب
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Webtoons" to "https://www.webtoons.com",
                                "Asura" to "https://asuracomic.net",
                                "Flame" to "https://flamecomics.xyz"
                            ).forEach { (siteName, targetSite) ->
                                FilterChip(
                                    selected = webUrl.startsWith(targetSite),
                                    onClick = { webUrl = targetSite },
                                    label = { Text(siteName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                var url = webUrl.trim()
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    url = "https://$url"
                                }
                                activeWebUrl = url
                                translatedWebBitmap = null
                                showTranslatedWebOverlay = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("باز کردن وب‌تون در کتابخوان مترجم", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "💡 نکته: برای ترجمه در مرورگرهای خارجی (مثل Chrome یا برنامه Webtoons)، می‌توانید «حباب شناور» را از صفحه اصلی برنامه روشن کنید!",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // نوار ابزار و کنترل‌های پیشرفته مترجم وب‌تون
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (webViewRef?.canGoBack() == true) {
                                            webViewRef?.goBack()
                                        } else {
                                            activeWebUrl = null
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                                }

                                IconButton(
                                    onClick = {
                                        if (webViewRef?.canGoForward() == true) {
                                            webViewRef?.goForward()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "جلو", tint = Color.White)
                                }

                                IconButton(onClick = { webViewRef?.reload() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی", tint = Color.White)
                                }
                            }

                            Text(
                                text = if (webPageTitle.isNotBlank()) webPageTitle else (activeWebUrl ?: ""),
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            )

                            IconButton(
                                onClick = {
                                    activeWebUrl = null
                                    translatedWebBitmap = null
                                    showTranslatedWebOverlay = false
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "بستن", tint = DangerRed)
                            }
                        }

                        // نوار دکمه‌های اکشن ترجمه و ذخیره
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // ۱. دکمه اسکن و ترجمه صفحه جاری
                            Button(
                                onClick = { translateCurrentWebPage() },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isTranslatingWebPage) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("در حال ترجمه...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("⚡ ترجمه این صفحه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // ۲. دکمه سوئیچ بین دیدن ترجمه و دیدن صفحه وب
                            if (translatedWebBitmap != null) {
                                OutlinedButton(
                                    onClick = { showTranslatedWebOverlay = !showTranslatedWebOverlay },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (showTranslatedWebOverlay) AccentCyan else Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (showTranslatedWebOverlay) "مشاهده وب" else "دیدن ترجمه", fontSize = 11.sp)
                                }

                                // ۳. دکمه افزودن این صفحه به لیست چپتر جهت ساخت PDF
                                Button(
                                    onClick = {
                                        translatedWebBitmap?.let { bmp ->
                                            renderedPages.add(bmp)
                                            Toast.makeText(context, "صفحه به چپتر افزوده شد (${renderedPages.size} صفحه آماده است)", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("افزودن (${renderedPages.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // ۴. دکمه ذخیره کل چپتر به صورت PDF
                            if (renderedPages.isNotEmpty() || translatedWebBitmap != null) {
                                Button(
                                    onClick = {
                                        pdfExportLauncher.launch("webtoon_chapter_${System.currentTimeMillis()}.pdf")
                                    },
                                    enabled = !isExportingPdf,
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExportingPdf) "در حال ذخیره..." else "💾 PDF",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // ۵. دکمه ذخیره کل چپتر به صورت WebP+ZIP
                                Button(
                                    onClick = {
                                        webpZipExportLauncher.launch("webtoon_chapter_${System.currentTimeMillis()}.zip")
                                    },
                                    enabled = !isExportingWebpZip,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExportingWebpZip) "در حال ذخیره..." else "📦 ZIP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (isExportingPdf || isExportingWebpZip) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                color = if (isExportingWebpZip) Color(0xFF0284C7) else SuccessGreen,
                                trackColor = CardBackground,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // محتوای نمایش وب‌تون یا تصویر ترجمه‌شده
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // وب‌ویو فعال
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        webPageTitle = view?.title ?: ""
                                    }
                                }
                                loadUrl(activeWebUrl!!)
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                            if (webView.url != activeWebUrl) {
                                webView.loadUrl(activeWebUrl!!)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // لایه نمایش تصویر ترجمه شده با بالن‌های فارسی
                    if (showTranslatedWebOverlay && translatedWebBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = translatedWebBitmap!!.asImageBitmap(),
                                contentDescription = "صفحه ترجمه شده وب‌تون",
                                modifier = Modifier.fillMaxSize()
                            )

                            // نوار شناور پایین برای تعامل سریع با ترجمه
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            translatedWebBitmap?.let { bmp ->
                                                renderedPages.add(bmp)
                                                Toast.makeText(context, "صفحه در چپتر ذخیره شد!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("افزودن به PDF (${renderedPages.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showTranslatedWebOverlay = false },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("بازگشت به وب و اسکرول", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // دکمه شناور سریع (FAB) در گوشه تصویر جهت اسکن سریع با یک لمس
                    if (!showTranslatedWebOverlay) {
                        FloatingActionButton(
                            onClick = { translateCurrentWebPage() },
                            containerColor = PrimaryBlue,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "ترجمه این صفحه", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // نمایش صفحات ترجمه شده برای حالت‌های PDF و تصاویر
        if (selectedMode == 0 || selectedMode == 1) {
            if (renderedPages.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📖 ${renderedPages.size} صفحه ترجمه‌شده",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // دکمه پاکسازی و شروع صفحه جدید
                            OutlinedButton(
                                onClick = { renderedPages.clear() },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("پاکسازی", fontSize = 11.sp)
                            }

                            // دکمه ذخیره خروجی PDF
                            Button(
                                onClick = {
                                    val prefix = if (selectedMode == 0) "pdf" else "chapter"
                                    pdfExportLauncher.launch("manhwa_${prefix}_${System.currentTimeMillis()}.pdf")
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isExportingPdf) "ذخیره..." else "💾 PDF",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // دکمه خروجی WebP + ZIP
                            Button(
                                onClick = {
                                    val prefix = if (selectedMode == 0) "pdf" else "chapter"
                                    webpZipExportLauncher.launch("manhwa_${prefix}_${System.currentTimeMillis()}.zip")
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isExportingWebpZip) "ذخیره..." else "📦 WebP+ZIP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (isExportingPdf || isExportingWebpZip) {
                        LinearProgressIndicator(
                            color = if (isExportingWebpZip) Color(0xFF0284C7) else SuccessGreen,
                            trackColor = CardBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(renderedPages) { index, bitmap ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "صفحه ${index + 1}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else if (!isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedMode == 0) {
                            "یک فایل PDF کمیک را انتخاب کنید تا صفحات آن همراه با بالن‌های ترجمه شده فارسی به صورت اسکرولی نمایش داده شود."
                        } else {
                            "تصاویر یک چپتر را انتخاب کنید تا به ترتیب خوانده شده و صفحات ترجمه با بالن‌های فارسی بازچینی شوند."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * پردازشگر پس‌زمینه رندر PDF، اسکن OCR، ترجمه و رسم مستقیم بالن‌ها روی بیت‌مپ هر صفحه
 */
private suspend fun processPdfFile(
    context: Context,
    uri: Uri,
    onTotalPages: (Int) -> Unit,
    onPageProgress: (Int) -> Unit,
    onPageDone: (Bitmap) -> Unit,
    onFinished: () -> Unit
) = withContext(Dispatchers.IO) {
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    val ocrScanner = ScreenOcrScanner()
    val repository = ScreenTranslatorApp.repository
    val preferences = AppPreferences.getInstance(context)

    try {
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) return@withContext

        renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        withContext(Dispatchers.Main) {
            onTotalPages(pageCount)
        }

        val displayMetrics = context.resources.displayMetrics
        val targetWidth = displayMetrics.widthPixels.coerceIn(1080, 1440)

        for (i in 0 until pageCount) {
            withContext(Dispatchers.Main) {
                onPageProgress(i + 1)
            }

            val page = renderer.openPage(i)
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val targetHeight = (page.height * scale).toInt()

            val pageBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // 1. استخراج فوق‌دقیق کلیه کادرهای متن اصلی کمیک با تکنیک برش‌های پنجره لغزان
            val blocks = ocrScanner.scanLongBitmapInSlices(
                bitmap = pageBitmap,
                sliceHeight = 2400,
                overlap = 400,
                filterSystemMargins = false,
                ocrLanguageMode = preferences.ocrLanguageMode
            )

            val finalBitmap = if (blocks.isNotEmpty()) {
                // 2. ترجمه دیالوگ‌ها به لحن مانهوایی
                val translatedBlocks = repository.translateBlocks(blocks, sourceBitmap = pageBitmap)
                // 3. رسم بالن‌های فارسی ضد تداخل با تم و اندازه فونت انتخابی
                ComicBubbleRenderer.drawTranslationsOnBitmap(
                    sourceBitmap = pageBitmap,
                    blocks = translatedBlocks,
                    theme = preferences.bubbleTheme,
                    fontScale = preferences.bubbleFontSizeScale
                )
            } else {
                pageBitmap
            }

            withContext(Dispatchers.Main) {
                onPageDone(finalBitmap)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "خطا در پردازش PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    } finally {
        renderer?.close()
        pfd?.close()
        ocrScanner.close()
        withContext(Dispatchers.Main) {
            onFinished()
        }
    }
}

/**
 * پردازشگر پس‌زمینه خواندن تصاویر چندگانه یک چپتر، مرتب‌سازی طبیعی، اسکن OCR، ترجمه و بالن‌گذاری
 */
private suspend fun processImageChapter(
    context: Context,
    uris: List<Uri>,
    onTotalImages: (Int) -> Unit,
    onImageProgress: (Int) -> Unit,
    onPageDone: (Bitmap) -> Unit,
    onFinished: () -> Unit
) = withContext(Dispatchers.IO) {
    val ocrScanner = ScreenOcrScanner()
    val repository = ScreenTranslatorApp.repository
    val preferences = AppPreferences.getInstance(context)

    try {
        // مرتب‌سازی طبیعی نام فایل‌ها (مانند page_1, page_2, ..., page_10)
        val sortedUris = uris.sortedWith { u1, u2 ->
            val name1 = getFileName(context, u1)
            val name2 = getFileName(context, u2)
            naturalCompare(name1, name2)
        }

        withContext(Dispatchers.Main) {
            onTotalImages(sortedUris.size)
        }

        val displayMetrics = context.resources.displayMetrics
        val targetWidth = displayMetrics.widthPixels.coerceIn(1080, 1440)

        for ((index, uri) in sortedUris.withIndex()) {
            withContext(Dispatchers.Main) {
                onImageProgress(index + 1)
            }

            val decodedBitmap = decodeSampledBitmapFromUri(context, uri, targetWidth)
            if (decodedBitmap == null) continue

            val blocks = ocrScanner.scanLongBitmapInSlices(
                bitmap = decodedBitmap,
                sliceHeight = 2400,
                overlap = 400,
                filterSystemMargins = false,
                ocrLanguageMode = preferences.ocrLanguageMode
            )

            val finalBitmap = if (blocks.isNotEmpty()) {
                val translatedBlocks = repository.translateBlocks(blocks, sourceBitmap = decodedBitmap)
                ComicBubbleRenderer.drawTranslationsOnBitmap(
                    sourceBitmap = decodedBitmap,
                    blocks = translatedBlocks,
                    theme = preferences.bubbleTheme,
                    fontScale = preferences.bubbleFontSizeScale
                )
            } else {
                decodedBitmap
            }

            withContext(Dispatchers.Main) {
                onPageDone(finalBitmap)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "خطا در پردازش تصاویر چپتر: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    } finally {
        ocrScanner.close()
        withContext(Dispatchers.Main) {
            onFinished()
        }
    }
}

/**
 * بارگذاری امن تصویر از Uri با تنظیم بهینه حافظه جهت جلوگیری از خطای OutOfMemory
 */
private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, targetWidth: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }

        val origW = options.outWidth
        val origH = options.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (origW / (sampleSize * 2) >= targetWidth) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } ?: return null

        val scale = targetWidth.toFloat() / sampledBitmap.width.toFloat()
        val scaledHeight = (sampledBitmap.height * scale).toInt()

        if (sampledBitmap.width == targetWidth) {
            sampledBitmap
        } else {
            val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetWidth, scaledHeight, true)
            if (scaled != sampledBitmap) {
                sampledBitmap.recycle()
            }
            scaled
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * عکس‌برداری مستقیم از صفحه جاری وب‌ویو جهت استخراج متون و بالن‌گذاری
 */
private fun captureWebViewBitmap(webView: WebView): Bitmap? {
    return try {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        webView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * استخراج نام واقعی فایل از روی Content Uri جهت مرتب‌سازی ترتیبی صفحات چپتر
 */
private fun getFileName(context: Context, uri: Uri): String {
    var result = ""
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex) ?: ""
                    }
                }
            }
        }
    } catch (_: Exception) {}
    if (result.isEmpty()) {
        result = uri.lastPathSegment ?: uri.toString()
    }
    return result
}

/**
 * مقایسه عددی-الفبایی طبیعی جهت مرتب‌سازی صحیح شماره صفحات مانهوا (1, 2, ..., 10 به جای 1, 10, 2)
 */
private fun naturalCompare(s1: String, s2: String): Int {
    val regex = "(\\d+)|(\\D+)".toRegex()
    val match1 = regex.findAll(s1).map { it.value }.toList()
    val match2 = regex.findAll(s2).map { it.value }.toList()
    val minSize = minOf(match1.size, match2.size)
    for (i in 0 until minSize) {
        val part1 = match1[i]
        val part2 = match2[i]
        val num1 = part1.toLongOrNull()
        val num2 = part2.toLongOrNull()
        if (num1 != null && num2 != null) {
            val cmp = num1.compareTo(num2)
            if (cmp != 0) return cmp
        } else {
            val cmp = part1.compareTo(part2, ignoreCase = true)
            if (cmp != 0) return cmp
        }
    }
    return match1.size.compareTo(match2.size)
}
