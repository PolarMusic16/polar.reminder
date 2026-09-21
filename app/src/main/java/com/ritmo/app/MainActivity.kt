package com.ritmo.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Paleta de los anillos: de más "lleno" a más urgente.
private val Ok = Color(0xFF00DAFF)
private val Warn = Color(0xFF008DFC)
private val Late = Color(0xFF0044FF)

private val EMOJIS = listOf(
    "💧", "🥛", "🫗", "🌱", "🧴", "🧹", "🧺", "🍽️", "🗑️", "🚿", "🪥", "💊",
    "🏃", "🧘", "📚", "✉️", "📞", "💸", "🚗", "🐕", "🐈", "🛏️",
    "🧊", "☕", "🪟", "🔧", "🌡️", "🧽", "💤", "🎸", "📝", "⏱"
)

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* si dice que no, la app sigue funcionando igual, solo sin avisos */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.ensureChannel(this)
        // Por si el proceso se reinició sin pasar por BootReceiver, deja las alarmas al día.
        AlarmScheduler.rescheduleAll(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { RitmoApp() }
    }

    override fun onStop() {
        super.onStop()
        WidgetSync.refresh(this)
    }
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

@Composable
fun RitmoApp() {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7FB79C),
            background = Color(0xFF131311),
            surface = Color(0xFF1C1C1A),
            surfaceVariant = Color(0xFF32322E),
            onBackground = Color(0xFFF0EFEC),
            onSurface = Color(0xFFF0EFEC)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3F6F5B),
            background = Color(0xFFF5F4F1),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE4E2DC),
            onBackground = Color(0xFF1A1917),
            onSurface = Color(0xFF1A1917)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(color = MaterialTheme.colorScheme.background) { HomeScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var allItems by remember { mutableStateOf(Store.load(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var exactAlarmOk by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }

    fun reload() {
        allItems = Store.load(context)
        now = System.currentTimeMillis()
        exactAlarmOk = AlarmScheduler.canScheduleExact(context)
    }

    // Refresca los anillos cada 20 segundos.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }

    // Vuelve a comprobar el permiso de alarmas exactas al volver de Ajustes.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) exactAlarmOk = AlarmScheduler.canScheduleExact(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    val sorted = remember(allItems, now) { Store.byUrgency(allItems) }
    val lateCount = sorted.count { it.isLate(now) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PolarReminder", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                allItems.isEmpty() -> "Sin recordatorios todavía"
                                lateCount == 1 -> "1 recordatorio te está esperando"
                                lateCount > 1 -> "$lateCount recordatorios te están esperando"
                                else -> "Lo próximo: ${sorted.first().title} ${TimeText.status(sorted.first(), now)}"
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 20.sp) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Añadir ejemplos") },
                            onClick = {
                                showMenu = false
                                Store.save(context, allItems + Store.demoItems())
                                reload()
                            })
                        DropdownMenuItem(
                            text = { Text("Exportar copia") },
                            onClick = {
                                showMenu = false
                                shareBackup(context, Store.exportJson(context))
                            })
                        DropdownMenuItem(
                            text = { Text("Importar copia") },
                            onClick = { showMenu = false; showImport = true })
                        DropdownMenuItem(
                            text = { Text("Borrar todo") },
                            onClick = { showMenu = false; confirmWipe = true })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Text("+", fontSize = 26.sp)
            }
        }
    ) { pad ->
      Column(modifier = Modifier.fillMaxSize().padding(pad)) {
        if (!exactAlarmOk) {
            ExactAlarmBanner(onClick = { openExactAlarmSettings(context) })
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🫧\n\nTodavía no tienes recordatorios.\nPulsa + para crear el primero.\n\nPor ejemplo: regar las plantas cada 5 días.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sorted, key = { it.id }) { item ->
                        ReminderCard(
                            item = item,
                            now = now,
                            onComplete = {
                                val previous = item.last
                                Store.complete(context, item.id)
                                reload()
                                scope.launch {
                                    val res = snackbar.showSnackbar(
                                        message = "Hecho ✓ ${item.title}",
                                        actionLabel = "Deshacer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        Store.upsert(context, item.copy(last = previous))
                                        reload()
                                    }
                                }
                            },
                            onEdit = { editing = item; showEditor = true }
                        )
                    }
                }
            }
        }
      }
    }

    if (showEditor) {
        EditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { r ->
                Store.upsert(context, r)
                showEditor = false
                reload()
            },
            onDelete = { r ->
                Store.delete(context, r.id)
                showEditor = false
                reload()
            }
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val n = Store.importJson(context, text)
                showImport = false
                reload()
                scope.launch {
                    snackbar.showSnackbar(
                        if (n < 0) "No se pudo leer esa copia" else "Importados $n recordatorios"
                    )
                }
            }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se eliminarán todos los recordatorios. Esto no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.save(context, emptyList())
                    confirmWipe = false
                    reload()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun ExactAlarmBanner(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 0.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔔", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Activa las notificaciones puntuales", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Toca aquí para permitir alarmas exactas y que avisen a la hora justa.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
fun ReminderCard(
    item: Reminder,
    now: Long,
    onComplete: () -> Unit,
    onEdit: () -> Unit
) {
    val fraction = item.fraction(now)
    val late = fraction <= 0f
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { onComplete() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).size(34.dp)
            ) { Text("✎", fontSize = 14.sp) }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
            ) {
                Ring(fraction = fraction, emoji = item.emoji, size = 96.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    TimeText.status(item, now),
                    fontSize = 11.sp,
                    color = if (late) Late else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = if (late) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                Text(
                    TimeText.every(item),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun Ring(fraction: Float, emoji: String, size: Dp) {
    val color = when {
        fraction <= 0f -> Late
        fraction < 0.25f -> Warn
        else -> Ok
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.105f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = if (fraction <= 0f) 360f else 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(emoji.ifEmpty { "⏱" }, fontSize = 30.sp)
    }
}

@Composable
fun EditorDialog(
    initial: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "💧") }
    var every by remember { mutableStateOf((initial?.every ?: 7).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: Reminder.UNIT_DAY) }
    var resetNow by remember { mutableStateOf(initial == null) }
    var unitMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo recordatorio" else "Editar recordatorio") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label = { Text("Símbolo") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(EMOJIS) { e ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { emoji = e }
                            ) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = every,
                        onValueChange = { v -> every = v.filter { it.isDigit() }.take(3) },
                        label = { Text("Repetir cada") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box {
                        OutlinedButton(onClick = { unitMenu = true }) {
                            Text(TimeText.unitLabel(unit, true))
                        }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            Reminder.UNITS.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(TimeText.unitLabel(u, true)) },
                                    onClick = { unit = u; unitMenu = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { resetNow = !resetNow }
                ) {
                    Text(if (resetNow) "☑" else "☐", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Empezar el ciclo ahora mismo", fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim()
                if (t.isEmpty()) return@TextButton
                val n = (every.toIntOrNull() ?: 1).coerceIn(1, 999)
                val base = initial ?: Reminder(title = t)
                val updated = base.copy(
                    title = t,
                    emoji = emoji.ifBlank { "⏱" },
                    every = n,
                    unit = unit,
                    last = if (resetNow) System.currentTimeMillis() else base.last
                )
                onSave(updated)
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) { Text("Eliminar", color = Late) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar copia") },
        text = {
            Column {
                Text(
                    "Pega aquí el JSON exportado desde PolarReminder (app o web).",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("JSON") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun shareBackup(context: Context, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Copia de seguridad de PolarReminder")
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Guardar copia de seguridad"))
}
