package com.screenreader.translator.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.data.local.AppPreferences
import com.screenreader.translator.data.local.DatabaseBackupManager
import com.screenreader.translator.engine.translation.BatchLlmTranslator
import com.screenreader.translator.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { AppPreferences.getInstance(context) }
    val batchLlmTranslator = remember { BatchLlmTranslator(preferences) }

    var overlayOpacity by remember { mutableFloatStateOf(preferences.overlayOpacity) }
    var autoSpeak by remember { mutableStateOf(false) }
    var autoCopy by remember { mutableStateOf(false) }
    var selectedOcrLanguageMode by remember { mutableIntStateOf(preferences.ocrLanguageMode) }
    var selectedOcrEngineMode by remember { mutableIntStateOf(preferences.ocrEngineMode) }
    var isOcrEnhanceContrast by remember { mutableStateOf(preferences.isOcrEnhanceContrast) }

    // تنظیمات هوش مصنوعی
    var selectedProvider by remember { mutableStateOf(preferences.llmProvider) }
    var customModelText by remember { mutableStateOf(preferences.customModel) }
    var customEndpointText by remember { mutableStateOf(preferences.customEndpoint) }
    var isAutoLearnEnabled by remember { mutableStateOf(preferences.isAutoLearnEnabled) }

    // کلیدهای اختصاصی هر هوش مصنوعی به صورت مستقل
    val providerKeys = remember {
        mutableStateMapOf(
            AppPreferences.PROVIDER_GEMINI to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_GEMINI),
            AppPreferences.PROVIDER_GROQ to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_GROQ),
            AppPreferences.PROVIDER_OPENROUTER to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_OPENROUTER),
            AppPreferences.PROVIDER_DEEPSEEK to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_DEEPSEEK),
            AppPreferences.PROVIDER_OPENAI to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_OPENAI),
            AppPreferences.PROVIDER_GROK to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_GROK),
            AppPreferences.PROVIDER_CUSTOM to preferences.getApiKeyForProvider(AppPreferences.PROVIDER_CUSTOM)
        )
    }
    val keyVisibilityMap = remember { mutableStateMapOf<String, Boolean>() }

    // وضعیت نمایش دیالوگ آموزش تصویری دریافت API
    var showApiGuideDialog by remember { mutableStateOf(false) }
    var guideDialogInitialProvider by remember { mutableStateOf(selectedProvider) }

    // مدل‌های انتخاب شده برای زنجیره فال‌بک (چند مدلی برای رفع ریت‌لیمیت) به تفکیک هوش فعال
    var selectedModelsList by remember(selectedProvider) {
        val saved = preferences.getSelectedModelsForProvider(selectedProvider)
        val cleaned = saved.filter { model ->
            if (selectedProvider == AppPreferences.PROVIDER_OPENROUTER) {
                !model.contains("gemini-2.0-flash-exp:free") &&
                !model.contains("llama-3.3-70b-instruct:free") &&
                !model.contains("deepseek-r1:free") &&
                !model.contains("deepseek-chat:free") &&
                !model.contains("mistral-small:free")
            } else true
        }
        val initial = if (cleaned.isNotEmpty()) cleaned else listOf(batchLlmTranslator.getDefaultModelForProvider(selectedProvider))
        if (saved != initial) {
            preferences.setSelectedModelsForProvider(selectedProvider, initial)
        }
        mutableStateOf(initial)
    }

    // آرشیو مدل‌های در دسترس برای هوش فعال
    var availableModels by remember(selectedProvider) {
        mutableStateOf(batchLlmTranslator.getPresetModels(selectedProvider))
    }
    var isFetchingModels by remember { mutableStateOf(false) }

    // فیلتر نمایش مدل‌ها: 0 = همه، 1 = فقط رایگان، 2 = فقط سالم و تایید شده
    var modelFilterMode by remember(selectedProvider) {
        mutableIntStateOf(if (selectedProvider == AppPreferences.PROVIDER_OPENROUTER) 1 else 0)
    }
    var modelSearchQuery by remember { mutableStateOf("") }
    var showAllModels by remember { mutableStateOf(false) }

    // وضعیت تست سلامت مدل‌ها و تست همگانی
    var isTestingModels by remember { mutableStateOf(false) }
    var batchTestProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var modelTestStatusMap by remember { mutableStateOf<Map<String, BatchLlmTranslator.ConnectionTestResult>>(emptyMap()) }
    var multiTestResults by remember { mutableStateOf<List<BatchLlmTranslator.ConnectionTestResult>>(emptyList()) }

    // آمار دیتابیس
    var totalDictionaryCount by remember { mutableIntStateOf(0) }
    var totalGlossaryCount by remember { mutableIntStateOf(0) }
    var isExportingDb by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        totalDictionaryCount = ScreenTranslatorApp.database.offlineDictionaryDao().getWordCount()
        totalGlossaryCount = ScreenTranslatorApp.database.glossaryDao().count()
    }

    // دیالوگ آموزش تصویری، مستندات رسمی، لینک‌ها و راهنمای ضدتحریم
    if (showApiGuideDialog) {
        ApiGuideDialog(
            initialProvider = guideDialogInitialProvider,
            onDismiss = { showApiGuideDialog = false }
        )
    }

    // لانچرهای خروجی دیتابیس
    val tsvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/tab-separated-values")
    ) { uri: Uri? ->
        if (uri != null) {
            isExportingDb = true
            coroutineScope.launch {
                val success = DatabaseBackupManager.exportToTsv(context, ScreenTranslatorApp.database, uri)
                isExportingDb = false
                if (success) {
                    Toast.makeText(context, "پایگاه داده با فرمت TSV (اکسل) با موفقیت ذخیره شد!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "خطا در استخراج فایل TSV", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isExportingDb = true
            coroutineScope.launch {
                val success = DatabaseBackupManager.exportToJson(context, ScreenTranslatorApp.database, uri)
                isExportingDb = false
                if (success) {
                    Toast.makeText(context, "پشتیبان کامل JSON پایگاه داده با موفقیت ذخیره شد!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "خطا در استخراج فایل JSON", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // لانچرهای درون‌ریزی و بازگردانی پایگاه داده
    var isImportingDb by remember { mutableStateOf(false) }

    val tsvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingDb = true
            coroutineScope.launch {
                val result = DatabaseBackupManager.importFromTsv(context, ScreenTranslatorApp.database, uri)
                isImportingDb = false
                result.onSuccess { count ->
                    totalDictionaryCount = ScreenTranslatorApp.database.offlineDictionaryDao().getWordCount()
                    totalGlossaryCount = ScreenTranslatorApp.database.glossaryDao().count()
                    Toast.makeText(context, "درون‌ریزی موفق: $count واژه و اصطلاح افزوده شد!", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "خطا در درون‌ریزی TSV: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingDb = true
            coroutineScope.launch {
                val result = DatabaseBackupManager.importFromJson(context, ScreenTranslatorApp.database, uri)
                isImportingDb = false
                result.onSuccess { count ->
                    totalDictionaryCount = ScreenTranslatorApp.database.offlineDictionaryDao().getWordCount()
                    totalGlossaryCount = ScreenTranslatorApp.database.glossaryDao().count()
                    Toast.makeText(context, "بازگردانی موفق: $count آیتم به پایگاه داده اضافه شد!", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "خطا در بازگردانی JSON: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // تنظیمات ظاهر بالن‌های کمیک
    var selectedBubbleTheme by remember { mutableStateOf(preferences.bubbleTheme) }
    var selectedFontScale by remember { mutableFloatStateOf(preferences.bubbleFontSizeScale) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "تنظیمات پیشرفته برنامه",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ۱. کارت جامع تنظیمات هوش مصنوعی، بخش اختصاصی کلیدها، فیلتر مدل‌های رایگان، تست همگانی و زنجیره فال‌بک
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "هوش مصنوعی و ترجمه دسته‌ای مانهوا",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "دارای بخش اختصاصی برای ذخیره کلید هر هوش، اولویت‌بندی مدل‌های رایگان (Free)، تست همگانی و زنجیره فال‌بک",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                val providersList = listOf(
                    Triple(AppPreferences.PROVIDER_GEMINI, "گوگل جمینای (Gemini)", "رایگان با سقف بالا (۱۵ درخواست در دقیقه)، پاسخ فوق سریع ⚡"),
                    Triple(AppPreferences.PROVIDER_GROQ, "گراک‌کلاود (Groq LPU)", "سرعت پردازش فوق‌العاده LPU، کاملاً رایگان (نیاز به VPN/شکن) ⚡"),
                    Triple(AppPreferences.PROVIDER_OPENROUTER, "اپن‌روتر (OpenRouter)", "دسترسی به کل مدل‌های جهان با ۱ کلید + مدل‌های کاملاً رایگان (:free) 🌐"),
                    Triple(AppPreferences.PROVIDER_DEEPSEEK, "دیپ‌سیک (DeepSeek)", "هوش مصنوعی بسیار ارزان و استدلالی (V3 و R1) 🚀"),
                    Triple(AppPreferences.PROVIDER_OPENAI, "چت جی‌پی‌تی (OpenAI)", "مدل‌های استاندارد جهانی GPT-4o و o3-mini 🤖"),
                    Triple(AppPreferences.PROVIDER_GROK, "گراک (xAI Grok)", "هوش مصنوعی xAI ایلان ماسک 🛸"),
                    Triple(AppPreferences.PROVIDER_CUSTOM, "سرویس سفارشی (Custom)", "اتصال به Local LLM (Ollama/LM Studio) یا سرورهای اختصاصی"),
                    Triple(AppPreferences.PROVIDER_NONE, "غیرفعال (آفلاین)", "استفاده اختصاصی از دیکشنری محلی + ML Kit (بدون اینترنت)")
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    providersList.forEach { (provId, title, desc) ->
                        val isSelected = selectedProvider == provId
                        val isFree = provId == AppPreferences.PROVIDER_GEMINI || provId == AppPreferences.PROVIDER_GROQ || provId == AppPreferences.PROVIDER_OPENROUTER || provId == AppPreferences.PROVIDER_NONE
                        val currentKey = providerKeys[provId] ?: ""
                        val hasKey = currentKey.isNotBlank()
                        val isKeyVisible = keyVisibilityMap[provId] ?: false

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SurfaceDark else SurfaceDark.copy(alpha = 0.55f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentCyan) else androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // سطر هدر کارت: رادیوباتن انتخاب + عنوان هوش مصنوعی + نشان رایگان و ثبت کلید
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedProvider = provId
                                            preferences.llmProvider = provId
                                            val saved = preferences.getSelectedModelsForProvider(provId)
                                            selectedModelsList = if (saved.isNotEmpty()) saved else listOf(batchLlmTranslator.getDefaultModelForProvider(provId))
                                            availableModels = batchLlmTranslator.getPresetModels(provId)
                                            modelFilterMode = 0
                                            multiTestResults = emptyList()
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                color = if (isSelected) Color.White else TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (isFree) {
                                                    Surface(
                                                        color = SuccessGreen.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "🎁 رایگان",
                                                            color = SuccessGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (provId != AppPreferences.PROVIDER_NONE) {
                                                    Surface(
                                                        color = if (hasKey) AccentCyan.copy(alpha = 0.2f) else BorderDark.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (hasKey) "✓ کلید ثبت شده" else "بدون کلید",
                                                            color = if (hasKey) AccentCyan else TextSecondary,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text(text = desc, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }

                                // بخش اختصاصی فیلد کلید API برای هر هوش مصنوعی
                                if (provId != AppPreferences.PROVIDER_NONE) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = currentKey,
                                        onValueChange = { newKey ->
                                            providerKeys[provId] = newKey
                                            preferences.setApiKeyForProvider(provId, newKey)
                                            if (selectedProvider == provId) {
                                                modelTestStatusMap = emptyMap()
                                                multiTestResults = emptyList()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("کلید اختصاصی $title", fontSize = 11.sp) },
                                        placeholder = {
                                            Text(
                                                when (provId) {
                                                    AppPreferences.PROVIDER_GROQ -> "کلید GroqCloud (شروع با gsk_...)"
                                                    AppPreferences.PROVIDER_GEMINI -> "کلید Google AI Studio (AIzaSy...)"
                                                    AppPreferences.PROVIDER_OPENROUTER -> "کلید OpenRouter (sk-or-v1-...)"
                                                    AppPreferences.PROVIDER_DEEPSEEK -> "کلید DeepSeek (sk-...)"
                                                    AppPreferences.PROVIDER_OPENAI -> "کلید OpenAI (sk-proj-...)"
                                                    AppPreferences.PROVIDER_GROK -> "کلید xAI Grok (xai-...)"
                                                    else -> "کلید API..."
                                                },
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        },
                                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { keyVisibilityMap[provId] = !isKeyVisible }) {
                                                    Icon(
                                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null,
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    guideDialogInitialProvider = provId
                                                    showApiGuideDialog = true
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.School,
                                                        contentDescription = "آموزش",
                                                        tint = AccentCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (isSelected) AccentCyan else PrimaryBlue,
                                            unfocusedBorderColor = BorderDark,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                if (provId == AppPreferences.PROVIDER_CUSTOM && isSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "آدرس اندپوینت سفارشی (Base URL):", color = TextPrimary, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = customEndpointText,
                                        onValueChange = {
                                            customEndpointText = it
                                            preferences.customEndpoint = it.trim()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        placeholder = { Text("https://openrouter.ai/api/v1/chat/completions", color = TextSecondary, fontSize = 11.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AccentCyan,
                                            unfocusedBorderColor = BorderDark,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }

                                // بخش مدیریت مدل‌ها، فیلتر مدل‌های رایگان و تست همگانی (فقط برای هوش انتخاب‌شده فعال)
                                if (isSelected && provId != AppPreferences.PROVIDER_NONE) {
                                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 10.dp))

                                    val freeCount = availableModels.count { batchLlmTranslator.isModelFree(selectedProvider, it) }
                                    val workingCount = modelTestStatusMap.values.count { it.isSuccess }

                                    val filteredByMode = availableModels.filter { modelName ->
                                        when (modelFilterMode) {
                                            1 -> batchLlmTranslator.isModelFree(selectedProvider, modelName)
                                            2 -> modelTestStatusMap[modelName]?.isSuccess == true
                                            else -> true
                                        }
                                    }
                                    val displayedModels = if (modelSearchQuery.isBlank()) {
                                        filteredByMode
                                    } else {
                                        filteredByMode.filter { it.contains(modelSearchQuery.trim(), ignoreCase = true) }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "زنجیره مدل‌ها جهت رفع ریت‌لیمیت:",
                                            color = AccentCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            color = AccentCyan.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${selectedModelsList.size} مدل فعال",
                                                color = AccentCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "💡 در صورت پر شدن سهمیه مدل اول (خطای ۴۲۹)، ترجمه بدون توقف توسط مدل بعدی انجام می‌شود.",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // تب‌ها و فیلترهای نمایش مدل‌ها (همه / رایگان / سالم)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = modelFilterMode == 0,
                                            onClick = { modelFilterMode = 0 },
                                            label = { Text("🌐 همه (${availableModels.size})", fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryBlue,
                                                selectedLabelColor = Color.White,
                                                containerColor = SurfaceDark,
                                                labelColor = TextSecondary
                                            )
                                        )
                                        FilterChip(
                                            selected = modelFilterMode == 1,
                                            onClick = { modelFilterMode = 1 },
                                            label = { Text("🎁 فقط رایگان ($freeCount)", fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SuccessGreen,
                                                selectedLabelColor = Color.White,
                                                containerColor = SurfaceDark,
                                                labelColor = TextSecondary
                                            )
                                        )
                                        FilterChip(
                                            selected = modelFilterMode == 2,
                                            onClick = { modelFilterMode = 2 },
                                            label = { Text("✅ فقط سالم ($workingCount)", fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF059669),
                                                selectedLabelColor = Color.White,
                                                containerColor = SurfaceDark,
                                                labelColor = TextSecondary
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // فیلد جستجوی سریع در بین مدل‌ها
                                    OutlinedTextField(
                                        value = modelSearchQuery,
                                        onValueChange = { modelSearchQuery = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("🔍 جستجو در مدل‌ها (مثال: nemotron, poolside, gemma)", fontSize = 11.sp) },
                                        trailingIcon = {
                                            if (modelSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { modelSearchQuery = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp), tint = TextSecondary)
                                                }
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AccentCyan,
                                            unfocusedBorderColor = BorderDark,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // دکمه‌های دریافت مدل‌ها و تست همگانی سلامت
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                isFetchingModels = true
                                                coroutineScope.launch {
                                                    val activeKey = providerKeys[selectedProvider] ?: ""
                                                    val list = batchLlmTranslator.fetchAvailableModels(selectedProvider, activeKey, customEndpointText)
                                                    availableModels = list
                                                    isFetchingModels = false
                                                    if (list.size > 30) {
                                                        Toast.makeText(context, "✅ ${list.size} مدل کامل از سرور بارگذاری شد!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "${list.size} مدل بارگذاری شد (در صورت نیاز به مدل‌های بیشتر، اینترنت/دی‌ان‌اس را بررسی کنید)", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            enabled = !isFetchingModels && (selectedProvider == AppPreferences.PROVIDER_OPENROUTER || (providerKeys[selectedProvider] ?: "").isNotBlank()),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                        ) {
                                            if (isFetchingModels) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentCyan, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("بارگذاری...", fontSize = 11.sp)
                                            } else {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("دریافت مدل‌ها", fontSize = 11.sp)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                isTestingModels = true
                                                val modelsToTest = if (modelFilterMode == 1) {
                                                    availableModels.filter { batchLlmTranslator.isModelFree(selectedProvider, it) }
                                                } else {
                                                    availableModels
                                                }
                                                batchTestProgress = 0 to modelsToTest.size
                                                coroutineScope.launch {
                                                    val activeKey = providerKeys[selectedProvider] ?: ""
                                                    val results = batchLlmTranslator.batchTestModels(
                                                        provider = selectedProvider,
                                                        apiKey = activeKey,
                                                        models = modelsToTest,
                                                        customEndpoint = customEndpointText,
                                                        onProgress = { current, total, result ->
                                                            batchTestProgress = current to total
                                                            modelTestStatusMap = modelTestStatusMap + (result.modelName to result)
                                                        }
                                                    )
                                                    multiTestResults = results
                                                    isTestingModels = false
                                                    batchTestProgress = null
                                                    val successCount = results.count { it.isSuccess }
                                                    Toast.makeText(context, "تست به پایان رسید: $successCount مدل سالم هستند", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = !isTestingModels && (providerKeys[selectedProvider] ?: "").isNotBlank(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            modifier = Modifier.weight(1.2f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                        ) {
                                            if (isTestingModels) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تست ${batchTestProgress?.first ?: 0}/${batchTestProgress?.second ?: 0}", fontSize = 11.sp)
                                            } else {
                                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("⚡ تست همگانی مدل‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // دکمه انتخاب تمام مدل‌های سالم رایگان برای زنجیره
                                    OutlinedButton(
                                        onClick = {
                                            val validFree = availableModels.filter { m ->
                                                val isMFree = batchLlmTranslator.isModelFree(selectedProvider, m)
                                                val test = modelTestStatusMap[m]
                                                isMFree && (test == null || test.isSuccess)
                                            }
                                            if (validFree.isNotEmpty()) {
                                                selectedModelsList = validFree
                                                preferences.setSelectedModelsForProvider(selectedProvider, validFree)
                                                Toast.makeText(context, "${validFree.size} مدل رایگان به زنجیره اضافه شدند!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "مدل رایگان سالمی یافت نشد", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = SuccessGreen)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("➕ انتخاب همه مدل‌های سالم رایگان برای زنجیره", fontSize = 11.sp, color = Color.White)
                                    }

                                    // نمایش نوار پیشرفت تست همگانی
                                    if (isTestingModels && batchTestProgress != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { (batchTestProgress!!.first.toFloat() / batchTestProgress!!.second.coerceAtLeast(1).toFloat()) },
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = AccentCyan
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "در حال تست موازی سلامت: ${batchTestProgress!!.first} از ${batchTestProgress!!.second} مدل...",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // چیپ‌های انتخاب چندگانه مدل‌ها با نشان‌های FREE و وضعیت تست
                                    if (displayedModels.isEmpty()) {
                                        Text(
                                            text = "مدلی در این فیلتر یافت نشد. دکمه «دریافت مدل‌ها» را لمس کنید یا عبارت دیگری جستجو نمایید.",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    } else {
                                        val visibleModels = if (showAllModels || displayedModels.size <= 30) {
                                            displayedModels
                                        } else {
                                            displayedModels.take(30)
                                        }

                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            visibleModels.forEach { modelName ->
                                                val isModelSelected = selectedModelsList.contains(modelName)
                                                val isMFree = batchLlmTranslator.isModelFree(selectedProvider, modelName)
                                                val testRes = modelTestStatusMap[modelName]

                                                FilterChip(
                                                    selected = isModelSelected,
                                                    onClick = {
                                                        val current = selectedModelsList.toMutableList()
                                                        if (current.contains(modelName)) {
                                                            if (current.size > 1) {
                                                                current.remove(modelName)
                                                            } else {
                                                                Toast.makeText(context, "حداقل ۱ مدل باید در زنجیره انتخاب شده باشد", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            current.add(modelName)
                                                        }
                                                        selectedModelsList = current
                                                        preferences.setSelectedModelsForProvider(selectedProvider, current)
                                                    },
                                                    leadingIcon = if (isModelSelected) {
                                                        {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint = Color.White
                                                            )
                                                        }
                                                    } else null,
                                                    label = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = modelName,
                                                                fontSize = 11.sp,
                                                                fontWeight = if (isModelSelected) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                            if (isMFree) {
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("🎁", fontSize = 10.sp)
                                                            }
                                                            if (testRes != null) {
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                if (testRes.isSuccess) {
                                                                    Text("✓ ${testRes.latencyMs}ms", fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                                                } else {
                                                                    Text("✗ خطا", fontSize = 9.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = PrimaryBlue,
                                                        selectedLabelColor = Color.White,
                                                        containerColor = SurfaceDark,
                                                        labelColor = TextSecondary
                                                    )
                                                )
                                            }
                                        }

                                        if (displayedModels.size > 30) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = { showAllModels = !showAllModels },
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            ) {
                                                Text(
                                                    text = if (showAllModels) "▲ نمایش کمتر" else "▼ نمایش همه (${displayedModels.size} مدل)...",
                                                    color = AccentCyan,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // افزودن دستی مدل به زنجیره
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customModelText,
                                            onValueChange = { customModelText = it },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentCyan,
                                                unfocusedBorderColor = SurfaceDark,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            placeholder = { Text("افزودن نام مدل دستی به زنجیره...", color = TextSecondary, fontSize = 11.sp) }
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Button(
                                            onClick = {
                                                val trimmed = customModelText.trim()
                                                if (trimmed.isNotBlank()) {
                                                    val current = selectedModelsList.toMutableList()
                                                    if (!current.contains(trimmed)) {
                                                        current.add(trimmed)
                                                        selectedModelsList = current
                                                        preferences.setSelectedModelsForProvider(selectedProvider, current)
                                                    }
                                                    if (!availableModels.contains(trimmed)) {
                                                        availableModels = availableModels + trimmed
                                                    }
                                                    customModelText = ""
                                                    Toast.makeText(context, "مدل «$trimmed» به زنجیره اضافه شد", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("افزودن", fontSize = 11.sp)
                                        }
                                    }

                                    // گزارش نتایج تست سلامت
                                    if (multiTestResults.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            multiTestResults.forEach { res ->
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (res.isSuccess) Color(0xFF064E3B).copy(alpha = 0.85f) else Color(0xFF7F1D1D).copy(alpha = 0.85f)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                                                    contentDescription = null,
                                                                    tint = if (res.isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = res.modelName,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 12.sp,
                                                                    color = Color.White
                                                                )
                                                            }
                                                            if (res.isSuccess) {
                                                                Text(
                                                                    text = "${res.latencyMs}ms",
                                                                    color = AccentCyan,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = res.message,
                                                            color = Color(0xFFE2E8F0),
                                                            fontSize = 11.sp
                                                        )
                                                        if (res.isSuccess && res.sampleTranslation != null) {
                                                            Text(
                                                                text = "نمونه ترجمه: «${res.sampleTranslation}»",
                                                                color = Color(0xFFA7F3D0),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                // سوئیچ یادگیری خودکار کلمات جدید در دیتابیس
                SettingSwitchRow(
                    title = "یادگیری خودکار کلمات در دیتابیس",
                    description = "کلمات و اصطلاحات جدید استخراج شده به طور خودکار به دیتابیس محلی اضافه شوند",
                    isChecked = isAutoLearnEnabled,
                    onCheckedChange = {
                        isAutoLearnEnabled = it
                        preferences.isAutoLearnEnabled = it
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۲. بخش پشتیبان‌گیری و خروجی دیتابیس
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "پایگاه داده و خروجی لغات",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تعداد لغات در لغت‌نامه: $totalDictionaryCount | اصطلاحات مانهوا: $totalGlossaryCount",
                    color = SuccessGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // دکمه خروجی TSV (اکسل)
                Button(
                    onClick = {
                        tsvExportLauncher.launch("manhwa_dictionary_export.tsv")
                    },
                    enabled = !isExportingDb && !isImportingDb,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📤 استخراج جدول اکسل از دیتابیس (TSV)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // دکمه خروجی JSON
                OutlinedButton(
                    onClick = {
                        jsonExportLauncher.launch("manhwa_database_backup.json")
                    },
                    enabled = !isExportingDb && !isImportingDb,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.DataObject, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📦 استخراج کامل پایگاه داده با فرمت JSON")
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "درون‌ریزی و افزودن واژه‌ها به دیتابیس:",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // دکمه درون‌ریزی TSV
                Button(
                    onClick = {
                        tsvImportLauncher.launch(arrayOf("text/*", "text/tab-separated-values", "application/octet-stream"))
                    },
                    enabled = !isExportingDb && !isImportingDb,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isImportingDb) "در حال پردازش و ثبت واژه‌ها..." else "📥 درون‌ریزی لغت‌نامه از فایل اکسل (TSV)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // دکمه درون‌ریزی JSON
                OutlinedButton(
                    onClick = {
                        jsonImportLauncher.launch(arrayOf("application/json", "text/*"))
                    },
                    enabled = !isExportingDb && !isImportingDb,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.RestorePage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isImportingDb) "در حال بازگردانی بکاپ..." else "📥 بازگردانی کامل پایگاه داده از فایل (JSON)")
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                // پاکسازی کش
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val count = ScreenTranslatorApp.repository.clearAllCachesAndAutoLearned()
                            Toast.makeText(context, "کش ترجمه‌ها و $count واژه آموزش‌دیده پاکسازی شدند", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حذف کش‌های ترجمه و یادگیری خودکار")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۳. شخصی‌سازی ظاهر بالن‌های کمیک و مانهوا
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ظاهر بالن‌های کمیک و مانهوا (Comic Bubbles)",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "رنگ پس‌زمینه، کادر و اندازه فونت بالن‌های ترجمه در کتابخوان PDF و تصاویر چپتر",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "تم رنگی بالن‌ها:", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                val bubbleThemes = listOf(
                    AppPreferences.BUBBLE_THEME_WHITE to "⚪ کلاسیک سفید (مانهوای روشن و کمیک استاندارد)",
                    AppPreferences.BUBBLE_THEME_DARK to "⚫ تم تیره مانهوا (Dark Slate با حاشیه فیروزه‌ای)",
                    AppPreferences.BUBBLE_THEME_TRANSLUCENT to "🪟 شیشه‌ای نیمه‌شفاف (دید حداکثری به تصویر)"
                )

                bubbleThemes.forEach { (themeKey, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedBubbleTheme == themeKey,
                            onClick = {
                                selectedBubbleTheme = themeKey
                                preferences.bubbleTheme = themeKey
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                        )
                        Text(
                            text = label,
                            color = if (selectedBubbleTheme == themeKey) Color.White else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selectedBubbleTheme == themeKey) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "اندازه فونت ترجمه داخل بالن: ${(selectedFontScale * 100).toInt()}%",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val fontScales = listOf(
                        0.85f to "کوچک (۸۵٪)",
                        1.0f to "استاندارد (۱۰۰٪)",
                        1.2f to "بزرگ (۱۲۰٪)"
                    )
                    fontScales.forEach { (scale, label) ->
                        FilterChip(
                            selected = Math.abs(selectedFontScale - scale) < 0.05f,
                            onClick = {
                                selectedFontScale = scale
                                preferences.bubbleFontSizeScale = scale
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // پیش‌نمایش زنده بالن کمیک متناسب با تنظیمات انتخابی
                Text(text = "پیش‌نمایش بالن:", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color(0xFF0B0F19), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewBg = when (selectedBubbleTheme) {
                        AppPreferences.BUBBLE_THEME_DARK -> Color(0xFF0F172A)
                        AppPreferences.BUBBLE_THEME_TRANSLUCENT -> Color(0xCC0F172A)
                        else -> Color.White
                    }
                    val previewBorder = when (selectedBubbleTheme) {
                        AppPreferences.BUBBLE_THEME_DARK -> Color(0xFF38BDF8)
                        AppPreferences.BUBBLE_THEME_TRANSLUCENT -> Color(0x9938BDF8)
                        else -> Color(0xFF1E293B)
                    }
                    val previewText = when (selectedBubbleTheme) {
                        AppPreferences.BUBBLE_THEME_WHITE -> Color(0xFF0F172A)
                        else -> Color.White
                    }

                    Surface(
                        color = previewBg,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, previewBorder),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(
                            text = "این قدرت مانهواست... من تسلیم نمی‌شم!",
                            color = previewText,
                            fontSize = (13f * selectedFontScale).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۴. بهینه‌سازی سرعت و موتور اسکن (OCR)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = AccentCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "موتور تشخیص متن (OCR) و هوش دیداری",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "امکان سوییچ بین سرعت آنی آفلاین یا نهایت دقت هوش مصنوعی دیداری برای خواندن پیچیده‌ترین فونت‌های مانهوا",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "موتور اصلی تشخیص متن:",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val ocrEngines = listOf(
                    Triple(AppPreferences.OCR_ENGINE_MLKIT, "⚡ گوگل ML Kit (آفلاین پرسرعت - پیش‌فرض)", "پردازش محلی روی چیپ گوشی در ۱۰۰ میلی‌ثانیه؛ بدون نیاز به اینترنت یا مصرف ترافیک"),
                    Triple(AppPreferences.OCR_ENGINE_VISION, "👁️ هوش دیداری چندوجهی (Vision AI - دقت ۹۹٪)", "دیدن مستقیم تصویر بالن توسط Gemini 2.0 / OpenRouter؛ رفع کامل چسبندگی حروف و فونت‌های فانتزی"),
                    Triple(AppPreferences.OCR_ENGINE_RAPID, "🔍 موتور آفلاین پیشرفته (RapidOCR / PP-OCRv4)", "الگوریتم یادگیری عمیق بهینه برای متون زاویه‌دار و عمودی بدون اینترنت")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ocrEngines.forEach { (engineVal, title, desc) ->
                        val isSelected = selectedOcrEngineMode == engineVal
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SurfaceDark else SurfaceDark.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentCyan) else androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedOcrEngineMode = engineVal
                                        preferences.ocrEngineMode = engineVal
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = desc,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                // سوئیچ تقویت سخت‌افزاری کنتراست
                SettingSwitchRow(
                    title = "تقویت هوشمند کنتراست بالن‌ها",
                    description = "شارپ‌سازی لبه‌های حروف و حذف ترام‌های خاکستری کمیک قبل از اسکن جهت تفکیک حروف چسبیده",
                    isChecked = isOcrEnhanceContrast,
                    onCheckedChange = {
                        isOcrEnhanceContrast = it
                        preferences.isOcrEnhanceContrast = it
                    }
                )

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "زبان‌های فعال در موتور OCR محلی:",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val ocrModes = listOf(
                    Triple(AppPreferences.OCR_LANG_ENGLISH, "⚡ انگلیسی (فوق‌سریع - پیشنهادی)", "تنها موتور لاتین فعال است؛ مناسب کمیک‌ها و مانهواهای ترجمه انگلیسی"),
                    Triple(AppPreferences.OCR_LANG_KOREAN, "🇰🇷 کره‌ای (هانگول)", "ترکیب موتور کره‌ای و لاتین؛ مناسب راوهای اختصاصی کره‌ای"),
                    Triple(AppPreferences.OCR_LANG_CHINESE, "🇨🇳 چینی (هانزی)", "ترکیب موتور چینی و لاتین؛ مناسب منهواهای زبان چینی"),
                    Triple(AppPreferences.OCR_LANG_ALL, "🌐 همه زبان‌ها (همزمان)", "فعال بودن هر ۳ موتور متن‌خوان همزمان در پس‌زمینه")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ocrModes.forEach { (modeVal, title, desc) ->
                        val isSelected = selectedOcrLanguageMode == modeVal
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) SurfaceDark else SurfaceDark.copy(alpha = 0.5f)
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentCyan) else androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedOcrLanguageMode = modeVal
                                        preferences.ocrLanguageMode = modeVal
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = desc,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۵. تنظیمات نمایش لایه شناور
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "تنظیمات نمایش لایه شناور",
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "میزان شفافیت پس‌زمینه کادرهای ترجمه: ${(overlayOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                Slider(
                    value = overlayOpacity,
                    onValueChange = {
                        overlayOpacity = it
                        preferences.overlayOpacity = it
                    },
                    valueRange = 0.5f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue
                    )
                )

                HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 12.dp))

                SettingSwitchRow(
                    title = "کپی خودکار ترجمه در کلیپ‌بورد",
                    description = "با لمس تگ ترجمه روی صفحه، متن به صورت خودکار کپی شود",
                    isChecked = autoCopy,
                    onCheckedChange = { autoCopy = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingSwitchRow(
                    title = "تلفظ خودکار صوتی (TTS)",
                    description = "پخش تلفظ متن پس از باز کردن جزییات",
                    isChecked = autoSpeak,
                    onCheckedChange = { autoSpeak = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۴. درباره برنامه
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "درباره مترجم روی صفحه و کتابخوان مانهوا", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "نسخه ۱.۳.۰ | مجهز به داکیومنت کامل API، زنجیره رفع ریت‌لیمیت و Room FTS5", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * دیالوگ آموزشی جامع، مصور، لینک‌های رسمی، داکیومنت و جدول مقایسه ارائه‌دهندگان
 */
@Composable
private fun ApiGuideDialog(
    initialProvider: String,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var selectedGuideTab by remember {
        mutableIntStateOf(
            when (initialProvider) {
                AppPreferences.PROVIDER_DEEPSEEK -> 1
                AppPreferences.PROVIDER_OPENROUTER -> 2
                AppPreferences.PROVIDER_GROK -> 3
                AppPreferences.PROVIDER_GROQ -> 4
                else -> 0
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("متوجه شدم و بستن راهنما", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "آموزش کامل، مصور و داکیومنت دریافت API",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "بررسی رسمی داکیومنت‌ها، لینک‌های مستقیم دریافت کلید، تصاویر شماتیک و راهنمای ضدتحریم:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ۷ تب کاربردی: ۵ سرویس محبوب + جدول مقایسه داکیومنت + راهنمای ضدتحریم
                ScrollableTabRow(
                    selectedTabIndex = selectedGuideTab,
                    containerColor = SurfaceDark,
                    contentColor = Color.White,
                    edgePadding = 8.dp
                ) {
                    Tab(
                        selected = selectedGuideTab == 0,
                        onClick = { selectedGuideTab = 0 },
                        text = { Text("Gemini (رایگان ⚡)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedGuideTab == 1,
                        onClick = { selectedGuideTab = 1 },
                        text = { Text("DeepSeek (ارزان 🚀)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedGuideTab == 2,
                        onClick = { selectedGuideTab = 2 },
                        text = { Text("OpenRouter (جامع 🌐)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedGuideTab == 3,
                        onClick = { selectedGuideTab = 3 },
                        text = { Text("Grok xAI (ایلان ماسک 🛸)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedGuideTab == 4,
                        onClick = { selectedGuideTab = 4 },
                        text = { Text("Groq Cloud (ابرسرعت LPU ⚡)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedGuideTab == 5,
                        onClick = { selectedGuideTab = 5 },
                        text = { Text("جدول مقایسه داکیومنت 📊", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedGuideTab == 6,
                        onClick = { selectedGuideTab = 6 },
                        text = { Text("رفع تحریم و دی‌ان‌اس 🛡️", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedGuideTab) {
                    0 -> GeminiGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                    1 -> DeepSeekGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                    2 -> OpenRouterGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                    3 -> GrokGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                    4 -> GroqGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                    5 -> ComparisonTableGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                    6 -> AntiSanctionDnsGuideContent(onOpenUrl = { uriHandler.openUri(it) })
                }
            }
        }
    )
}

/**
 * راهنمای کامل گوگل جمینای با ماکت تصویری و لینک داکیومنت
 */
@Composable
private fun GeminiGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // سهمیه رایگان طبق داکیومنت
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46).copy(alpha = 0.4f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "طبق داکیومنت رسمی Google AI: مدل‌های Flash و Flash-8b هر دو رایگان با سهمیه ۱۵ درخواست در دقیقه و ۱۵۰۰ درخواست روزانه هستند!",
                    color = Color(0xFFD1FAE5),
                    fontSize = 11.sp
                )
            }
        }

        // دکمه‌های مستقیم دریافت کلید و داکیومنت
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOpenUrl("https://aistudio.google.com/app/apikey") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("🔑 دریافت کلید API", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onOpenUrl("https://ai.google.dev/gemini-api/docs") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("📖 داکیومنت رسمی", fontSize = 11.sp)
            }
        }

        GuideStepItem(
            stepNumber = "۱",
            title = "ورود به سایت استودیو گوگل",
            description = "آدرس aistudio.google.com را باز کرده و با اکانت جیمیل معمولی خود لاگین کنید (بدون نیاز به کارت بانکی یا شماره خارجی)."
        )

        GuideStepItem(
            stepNumber = "۲",
            title = "ساخت کلید در پروژه جدید",
            description = "روی دکمه آبی Create API key کلیک کرده و گزینه Create API key in new project را انتخاب کنید."
        )

        // ماکت و شماتیک تصویری صفحه گوگل
        VisualMockupCard(
            serviceName = "Google AI Studio",
            urlHeader = "aistudio.google.com/app/apikey",
            buttonLabel = "+ Create API key in new project",
            keyPlaceholder = "AIzaSyDa9xK2mP...88qL4",
            badgeText = "Free Tier • 15 RPM • Active"
        )

        GuideStepItem(
            stepNumber = "۳",
            title = "کپی کردن و چسباندن در برنامه",
            description = "کلید ساخته شده را که با عبارت AIzaSy شروع می‌شود کپی کرده و در کادر تنظیمات برنامه قرار دهید."
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "نکته مهم: گوگل ورود از آی‌پی ایران به پنل استودیو را مسدود کرده است. هنگام باز کردن سایت، قندشکن خود را روشن کنید یا به تب «رفع تحریم و دی‌ان‌اس» بروید.",
                    color = Color(0xFFFEF3C7),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * راهنمای کامل دیپ‌سیک
 */
@Composable
private fun DeepSeekGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOpenUrl("https://platform.deepseek.com/api_keys") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("🔑 پنل کلیدهای DeepSeek", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onOpenUrl("https://api-docs.deepseek.com") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("📖 داکیومنت رسمی", fontSize = 11.sp)
            }
        }

        GuideStepItem(
            stepNumber = "۱",
            title = "ثبت‌نام سریع در پلتفرم دیپ‌سیک",
            description = "وارد platform.deepseek.com شده، با ایمیل ثبت‌نام کنید و کد ۶ رقمی ارسالی را تایید نمایید."
        )

        GuideStepItem(
            stepNumber = "۲",
            title = "ساخت کلید در بخش API Keys",
            description = "از منوی سمت چپ روی API Keys بزنید و دکمه Create new secret key را لمس کنید."
        )

        VisualMockupCard(
            serviceName = "DeepSeek Platform",
            urlHeader = "platform.deepseek.com/api_keys",
            buttonLabel = "+ Create new secret key",
            keyPlaceholder = "sk-d4f8e9a2b5c10...9e8f",
            badgeText = "DeepSeek-V3 / R1 • Ready"
        )

        GuideStepItem(
            stepNumber = "۳",
            title = "کپی کردن و استفاده در برنامه",
            description = "کلید sk-... فقط یک‌بار نمایش داده می‌شود؛ آن را کپی کرده و در برنامه ثبت کنید."
        )
    }
}

/**
 * راهنمای کامل اپن‌روتر
 */
@Composable
private fun OpenRouterGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOpenUrl("https://openrouter.ai/keys") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("🔑 ساخت کلید OpenRouter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onOpenUrl("https://openrouter.ai/docs") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("📖 داکیومنت رسمی", fontSize = 11.sp)
            }
        }

        GuideStepItem(
            stepNumber = "۱",
            title = "ورود با اکانت گوگل یا گیت‌هاب",
            description = "وارد openrouter.ai شوید و روی Sign In کلیک کنید."
        )

        GuideStepItem(
            stepNumber = "۲",
            title = "ساخت کلید در بخش Keys",
            description = "به بخش openrouter.ai/keys بروید، Create Key را بزنید و نام دلخواه بگذارید."
        )

        VisualMockupCard(
            serviceName = "OpenRouter Console",
            urlHeader = "openrouter.ai/keys",
            buttonLabel = "+ Create Key",
            keyPlaceholder = "sk-or-v1-84ef2...92f1",
            badgeText = "Free Models Enabled (:free)"
        )
    }
}

/**
 * راهنمای کامل گراک (xAI) با لینک‌های مستقیم، ماکت تصویری و راهکار OpenRouter
 */
@Composable
private fun GrokGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "هوش مصنوعی گراک (Grok) توسعه‌یافته توسط شرکت xAI ایلان ماسک؛ دارای درک عالی زبان محاوره‌ای، لحن کمیک و سرعت بالا.",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }

        // دکمه‌های مستقیم دریافت کلید و داکیومنت رسمی xAI
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOpenUrl("https://console.x.ai") },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("کنسول رسمی xAI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onOpenUrl("https://docs.x.ai") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("📖 داکیومنت رسمی", fontSize = 11.sp)
            }
        }

        GuideStepItem(
            stepNumber = "۱",
            title = "ورود به کنسول xAI Developer",
            description = "وارد سایت console.x.ai شوید و با اکانت توییتر (X) یا ایمیل خود وارد (Sign In) شوید."
        )

        GuideStepItem(
            stepNumber = "۲",
            title = "ساخت کلید در بخش API Keys",
            description = "از منوی کناری به بخش API Keys بروید، روی «+ Create API Key» کلیک کنید و نامی انتخاب کنید. کلید ساخته شده با پیشوند xai- نمایش داده می‌شود."
        )

        VisualMockupCard(
            serviceName = "xAI Developer Console",
            urlHeader = "console.x.ai/keys",
            buttonLabel = "+ Create API Key",
            keyPlaceholder = "xai-9af8c21e...88e4",
            badgeText = "Grok-2 & Grok-Vision Ready"
        )

        GuideStepItem(
            stepNumber = "۳",
            title = "💡 راهکار جایگزین و آسان بدون کارت اعتباری (OpenRouter)",
            description = "کنسول مستقیم xAI ممکن است در فعال‌سازی نیاز به کارت بین‌المللی داشته باشد. برای دسترسی بی‌دردسر، می‌توانید در OpenRouter (تب کنار) کلید بسازید و مدل Grok-2 را با یک کلید واحد استفاده کنید!"
        )
    }
}

/**
 * راهنمای کامل گراک‌کلاود (Groq) با سرعت استثنایی LPU و کلیدهای gsk_
 */
@Composable
private fun GroqGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF97316).copy(alpha = 0.2f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFB923C), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "سرویس GroqCloud بر پایه تراشه‌های LPU اختصاصی طراحی شده و سریع‌ترین سرعت استنتاج هوش مصنوعی در جهان (بیش از ۵۰۰ توکن در ثانیه) را ارائه می‌دهد!",
                    color = Color(0xFFFFEDD5),
                    fontSize = 11.sp
                )
            }
        }

        // دکمه‌های مستقیم کنسول کلیدها و مستندات Groq
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onOpenUrl("https://console.groq.com/keys") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("کنسول کلیدهای Groq", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { onOpenUrl("https://console.groq.com/docs/models") },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("📖 مدل‌های رسمی", fontSize = 11.sp)
            }
        }

        GuideStepItem(
            stepNumber = "۱",
            title = "ورود به سایت کنسول توسعه‌دهندگان Groq",
            description = "وارد console.groq.com شوید و با دکمه Sign in with Google در چند ثانیه لاگین کنید."
        )

        GuideStepItem(
            stepNumber = "۲",
            title = "ساخت کلید در بخش API Keys",
            description = "به بخش API Keys بروید و روی Create API Key کلیک کنید. کلیدهای این سرویس همیشه با gsk_ شروع می‌شوند."
        )

        VisualMockupCard(
            serviceName = "GroqCloud Console",
            urlHeader = "console.groq.com/keys",
            buttonLabel = "+ Create API Key",
            keyPlaceholder = "gsk_r0r0XsIFeLGS...6Agq",
            badgeText = "LPU Ultra Speed (500 tokens/sec)"
        )

        GuideStepItem(
            stepNumber = "۳",
            title = "مدل‌های پیشنهادی رایگان",
            description = "مدل llama-3.3-70b-versatile یا deepseek-r1-distill-llama-70b بالاترین کیفیت و سرعت خارق‌العاده را ارائه می‌دهند."
        )
    }
}

/**
 * تب جدول مقایسه کامل ارائه‌دهندگان طبق داکیومنت رسمی
 */
@Composable
private fun ComparisonTableGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "📊 مقایسه موشکافانه داکیومنت ارائه‌دهندگان هوش مصنوعی:",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        val tableRows = listOf(
            ComparisonItem(
                name = "Google Gemini",
                status = "۱۰۰٪ رایگان (۱۵۰۰ بار/روز)",
                cardNeeded = "❌ خیر",
                speed = "بسیار سریع (۱۵۰ms)",
                quality = "بسیار بالا و روان",
                docUrl = "https://ai.google.dev/gemini-api/docs"
            ),
            ComparisonItem(
                name = "DeepSeek",
                status = "هدیه اولیه + فوق‌العاده ارزان",
                cardNeeded = "❌ خیر",
                speed = "سریع (۲۵۰ms)",
                quality = "استثنایی و عامیانه",
                docUrl = "https://api-docs.deepseek.com"
            ),
            ComparisonItem(
                name = "OpenRouter",
                status = "دارای مدل‌های کاملاً رایگان (:free)",
                cardNeeded = "❌ خیر",
                speed = "متوسط (۳۵۰ms)",
                quality = "بسیار متنوع",
                docUrl = "https://openrouter.ai/docs"
            ),
            ComparisonItem(
                name = "OpenAI (GPT)",
                status = "پولی (شارژ حداقل ۵ دلار)",
                cardNeeded = "✅ بله",
                speed = "سریع (۲۰۰ms)",
                quality = "بسیار خوب",
                docUrl = "https://platform.openai.com/docs"
            ),
            ComparisonItem(
                name = "xAI (Grok)",
                status = "پولی (نیازمند کارت اعتباری)",
                cardNeeded = "✅ بله",
                speed = "متوسط (۴۰۰ms)",
                quality = "طنز و غیررسمی",
                docUrl = "https://docs.x.ai"
            )
        )

        tableRows.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.name, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { onOpenUrl(item.docUrl) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp), tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("داکیومنت", fontSize = 10.sp, color = PrimaryBlue)
                        }
                    }
                    Text(text = "• وضعیت سهمیه: ${item.status}", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "• نیاز به کارت بانکی خارجی: ${item.cardNeeded}", color = TextSecondary, fontSize = 11.sp)
                    Text(text = "• کیفیت ترجمه مانهوا: ${item.quality} | سرعت: ${item.speed}", color = SuccessGreen, fontSize = 11.sp)
                }
            }
        }
    }
}

private data class ComparisonItem(
    val name: String,
    val status: String,
    val cardNeeded: String,
    val speed: String,
    val quality: String,
    val docUrl: String
)

/**
 * تب اختصاصی راهنمای رفع تحریم، دی‌ان‌اس شکن و الکترو با لینک مستقیم
 */
@Composable
private fun AntiSanctionDnsGuideContent(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "🛡️ راهنمای رفع خطای ۴۰۳ و تحریم گوگل استودیو در ایران:",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "شرکت گوگل پنل ثبت‌نام AI Studio را برای آی‌پی ایران مسدود کرده است. برای ساخت کلید، کافیست یکی از روش‌های رایگان زیر را انتخاب کنید:",
            color = TextSecondary,
            fontSize = 11.sp
        )

        // روش اول: دی‌ان‌اس شکن
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = "روش ۱: استفاده از دی‌ان‌اس شکن (Shecan) - بدون نیاز به فیلترشکن", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "در تنظیمات گوشی به مسیر زیر بروید:\nتنظیمات (Settings) ➔ اتصالات (Connections) ➔ تنظیمات بیشتر شبکه ➔ دی‌ان‌اس خصوصی (Private DNS) ➔ آدرس زیر را وارد کنید:\nfree.shecan.ir",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { onOpenUrl("https://shecan.ir") },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🌐 باز کردن سایت شکن (shecan.ir)", fontSize = 11.sp)
                }
            }
        }

        // روش دوم: دی‌ان‌اس الکترو
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderDark, RoundedCornerShape(8.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(text = "روش ۲: دی‌ان‌اس الکترو (Electro)", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "یا می‌توانید آدرس دی‌ان‌اس خصوصی الکترو را ست نمایید:\nelctro.im",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { onOpenUrl("https://elctro.ir") },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🌐 باز کردن سایت الکترو (elctro.ir)", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * آیتم مرحله‌بندی شده راهنما
 */
@Composable
private fun GuideStepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(PrimaryBlue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * ماکت و شماتیک تصویری مصور دکمه‌ها و عناصر سایت
 */
@Composable
private fun VisualMockupCard(
    serviceName: String,
    urlHeader: String,
    buttonLabel: String,
    keyPlaceholder: String,
    badgeText: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // نوار آدرس مرورگر شبیه‌سازی شده
            Surface(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "https://$urlHeader", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFFBBF24), CircleShape))
                        Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "نمای تصویری صفحه $serviceName:",
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = SuccessGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = SuccessGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // شبیه‌سازی دکمه Create Key
            Surface(
                color = PrimaryBlue,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = buttonLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // شبیه‌سازی کادر کلید تولید شده و دکمه کپی
            Surface(
                color = CardBackground,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = keyPlaceholder, color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copy", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue
            )
        )
    }
}
