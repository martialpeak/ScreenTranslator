package com.screenreader.translator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenreader.translator.ScreenTranslatorApp
import com.screenreader.translator.data.local.entity.GlossaryEntity
import com.screenreader.translator.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GlossaryScreen() {
    val coroutineScope = rememberCoroutineScope()
    val glossaryDao = ScreenTranslatorApp.database.glossaryDao()
    val glossaryItems by glossaryDao.getAllGlossaryFlow().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var termInput by remember { mutableStateOf("") }
    var translationInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("گیمینگ") }

    Scaffold(
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن اصطلاح جدید")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "واژه‌نامه و اصطلاحات اختصاصی",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "کلمات و عباراتی که می‌خواهید با ترجمه دلخواه شما جایگزین شوند (مانند کلمات بازی‌ها)",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (glossaryItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هیچ اصطلاح اختصاصی ثبت نشده است", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(glossaryItems, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.originalTerm,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = AccentCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SurfaceDark
                                        ) {
                                            Text(
                                                text = item.category,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.customTranslation,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = item.isActive,
                                        onCheckedChange = { active ->
                                            coroutineScope.launch {
                                                glossaryDao.setStatus(item.id, active)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = PrimaryBlue
                                        )
                                    )
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            glossaryDao.delete(item)
                                            ScreenTranslatorApp.repository.deleteTranslationCompletely(item.originalTerm)
                                        }
                                    }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = DangerRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("افزودن اصطلاح سفارشی") },
            text = {
                Column {
                    OutlinedTextField(
                        value = termInput,
                        onValueChange = { termInput = it },
                        label = { Text("واژه یا عبارت انگلیسی (مثلاً HP)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = translationInput,
                        onValueChange = { translationInput = it },
                        label = { Text("ترجمه فارسی دلخواه (مثلاً جان)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("دسته‌بندی (مثلاً گیمینگ یا فنی)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (termInput.isNotBlank() && translationInput.isNotBlank()) {
                            coroutineScope.launch {
                                glossaryDao.insert(
                                    GlossaryEntity(
                                        originalTerm = termInput.trim(),
                                        customTranslation = translationInput.trim(),
                                        category = categoryInput.trim().ifEmpty { "عمومی" }
                                    )
                                )
                                termInput = ""
                                translationInput = ""
                                showAddDialog = false
                            }
                        }
                    }
                ) {
                    Text("ذخیره در دیتابیس")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
