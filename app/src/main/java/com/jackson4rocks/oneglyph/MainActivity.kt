package com.jackson4rocks.oneglyph

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Black = Color.Black
private val Paper = Color(0xFFF4F4F4)
private val Ink = Color(0xFF101010)
private val Muted = Color(0xFF858585)
private val Line = Color(0xFF2A2A2A)
private val Red = Color(0xFFFF3B30)
private val Panel = Color(0xFF0A0A0A)

private val NDotFamily = runCatching {
    FontFamily(
        Font(
            DeviceFontFamilyName("NDot55All")
        )
    )
}.getOrElse {
    FontFamily.Monospace
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OneGlyphApp()
        }
    }
}

@Composable
private fun OneGlyphApp() {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    val controller =
        remember { GlyphController(context) }

    val store =
        remember { PatternStore(context) }

    var page by remember { mutableStateOf("DOT") }
    var brightness by remember {
        mutableIntStateOf(3200)
    }
    var status by remember {
        mutableStateOf("CONNECTING • STOCK GLYPH")
    }
    var connected by remember {
        mutableStateOf(false)
    }

    var composer by remember {
        mutableStateOf(store.loadComposer())
    }

    var visualizerOn by remember {
        mutableStateOf(store.visualizerEnabled())
    }

    var reminderOn by remember {
        mutableStateOf(
            store.notificationRemindersEnabled()
        )
    }

    var reminderInterval by remember {
        mutableIntStateOf(
            store.reminderIntervalMinutes()
        )
    }

    var chargingOn by remember {
        mutableStateOf(store.chargingEnabled())
    }

    var gameScore by remember {
        mutableIntStateOf(0)
    }

    val audioPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            visualizerOn = granted

            store.setVisualizerEnabled(granted)

            status =
                if (granted) {
                    "VISUALIZER READY"
                } else {
                    "MIC ACCESS REQUIRED FOR VISUALIZER"
                }
        }

    val typography =
        MaterialTheme.typography.copy(
            displayLarge =
                MaterialTheme.typography.displayLarge.copy(
                    fontFamily = NDotFamily
                ),
            displayMedium =
                MaterialTheme.typography.displayMedium.copy(
                    fontFamily = NDotFamily
                ),
            displaySmall =
                MaterialTheme.typography.displaySmall.copy(
                    fontFamily = NDotFamily
                ),
            headlineLarge =
                MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = NDotFamily
                ),
            headlineMedium =
                MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = NDotFamily
                ),
            headlineSmall =
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = NDotFamily
                ),
            titleLarge =
                MaterialTheme.typography.titleLarge.copy(
                    fontFamily = NDotFamily
                ),
            titleMedium =
                MaterialTheme.typography.titleMedium.copy(
                    fontFamily = NDotFamily
                ),
            titleSmall =
                MaterialTheme.typography.titleSmall.copy(
                    fontFamily = NDotFamily
                ),
            bodyLarge =
                MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = NDotFamily
                ),
            bodyMedium =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = NDotFamily
                ),
            bodySmall =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = NDotFamily
                ),
            labelLarge =
                MaterialTheme.typography.labelLarge.copy(
                    fontFamily = NDotFamily
                ),
            labelMedium =
                MaterialTheme.typography.labelMedium.copy(
                    fontFamily = NDotFamily
                ),
            labelSmall =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = NDotFamily
                )
        )

    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = Black,
            surface = Panel,
            primary = Paper,
            onPrimary = Ink,
            secondary = Paper,
            onSecondary = Ink,
            onBackground = Paper,
            onSurface = Paper
        ),
        typography = typography
    ) {
        DisposableEffect(controller) {
            controller.setStatusListener { message ->
                status = message
                connected = controller.isReady()
            }

            onDispose {
                controller.setStatusListener(null)
                controller.close()
            }
        }

        LaunchedEffect(controller) {
            while (true) {
                connected = controller.isReady()
                delay(800L)
            }
        }

        DisposableEffect(
            visualizerOn,
            connected
        ) {
            if (!visualizerOn || !connected) {
                return@DisposableEffect onDispose {}
            }

            val visualizer =
                MusicVisualizer(context) { level ->
                    controller.setBrightness(level)
                }

            if (!visualizer.start()) {
                visualizerOn = false
                store.setVisualizerEnabled(false)
                status = "VISUALIZER UNAVAILABLE"
            }

            onDispose {
                visualizer.stop()
                controller.off()
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        horizontal = 18.dp,
                        vertical = 14.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                Header(
                    connected = connected,
                    device = controller.targetDevice()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Line,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "DOT",
                        "COMPOSER",
                        "MODES"
                    ).forEach { item ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (page == item) {
                                        Paper
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable {
                                    page = item
                                }
                                .padding(
                                    vertical = 10.dp
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Text(
                                item,
                                fontFamily = NDotFamily,
                                fontSize = 11.sp,
                                color =
                                    if (page == item) {
                                        Ink
                                    } else {
                                        Muted
                                    }
                            )
                        }
                    }
                }

                when (page) {
                    "DOT" -> {
                        DotPage(
                            controller = controller,
                            connected = connected,
                            brightness = brightness,
                            onBrightness = {
                                brightness = it
                            },
                            onStatus = {
                                status = it
                            }
                        )
                    }

                    "COMPOSER" -> {
                        ComposerPage(
                            context = context,
                            controller = controller,
                            steps = composer,
                            onSteps = {
                                composer =
                                    it.toMutableList()

                                store.saveComposer(it)
                            },
                            onStatus = {
                                status = it
                            }
                        )
                    }

                    "MODES" -> {
                        ModesPage(
                            context = context,
                            visualizerOn = visualizerOn,
                            reminderOn = reminderOn,
                            reminderInterval =
                                reminderInterval,
                            chargingOn = chargingOn,
                            gameScore = gameScore,
                            onVisualizer = {
                                if (visualizerOn) {
                                    visualizerOn = false
                                    store.setVisualizerEnabled(false)
                                } else if (
                                    context.checkSelfPermission(
                                        Manifest.permission.RECORD_AUDIO
                                    ) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    visualizerOn = true
                                    store.setVisualizerEnabled(true)
                                } else {
                                    audioPermission.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            },
                            onReminder = {
                                reminderOn =
                                    !reminderOn

                                store
                                    .setNotificationRemindersEnabled(
                                        reminderOn
                                    )

                                status =
                                    if (reminderOn) {
                                        "REMINDERS ENABLED"
                                    } else {
                                        "REMINDERS DISABLED"
                                    }
                            },
                            onInterval = {
                                reminderInterval = it
                                store.setReminderIntervalMinutes(it)
                                status =
                                    "REMINDER INTERVAL • " +
                                        it +
                                        " MIN"
                            },
                            onCharging = {
                                chargingOn = !chargingOn
                                store.setChargingEnabled(
                                    chargingOn
                                )

                                status =
                                    if (chargingOn) {
                                        "CHARGING EFFECT ENABLED"
                                    } else {
                                        "CHARGING EFFECT DISABLED"
                                    }
                            },
                            onCamera = {
                                try {
                                    context.startActivity(
                                        Intent(
                                            MediaStore
                                                .INTENT_ACTION_STILL_IMAGE_CAMERA
                                        )
                                    )

                                    CoroutineScope(
                                        Dispatchers.IO
                                    ).launch {
                                        delay(500L)
                                        GlyphAction.playOnce(
                                            context,
                                            GlyphPatterns
                                                .cameraCountdown
                                        )
                                    }

                                    status =
                                        "CAMERA COUNTDOWN STARTED"
                                } catch (_: Exception) {
                                    status =
                                        "NO CAMERA APP FOUND"
                                }
                            },
                            onRingtone = {
                                GlyphAction
                                    .playRingtoneAndPattern(
                                        context,
                                        composer
                                    )

                                status =
                                    "RINGTONE + COMPOSER PLAYING"
                            },
                            onGame = {
                                gameScore += 1

                                CoroutineScope(
                                    Dispatchers.IO
                                ).launch {
                                    GlyphAction.playOnce(
                                        context,
                                        if (gameScore % 3 == 0) {
                                            GlyphPatterns.heartbeat
                                        } else {
                                            GlyphPatterns.double
                                        }
                                    )
                                }

                                status =
                                    "DOT TOY • SCORE " +
                                        gameScore
                            },
                            onStatus = {
                                status = it
                            }
                        )
                    }
                }

                Divider(color = Line)

                Text(
                    status,
                    fontFamily = NDotFamily,
                    fontSize = 10.sp,
                    letterSpacing = .6.sp,
                    color = Muted
                )

                Text(
                    "STOCK GLYPHSERVICE • ONE DOT • LOCAL CONTROL",
                    fontFamily = NDotFamily,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = Color(0xFF595959)
                )
            }
        }
    }
}

@Composable
private fun Header(
    connected: Boolean,
    device: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.Top
    ) {
        Column {
            Text(
                "ONEGLYPH",
                fontFamily = NDotFamily,
                fontSize = 34.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-1).sp,
                color = Paper
            )

            Text(
                "MAKE THE ONE DOT DO MORE.",
                fontFamily = NDotFamily,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                color = Muted
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier
                    .border(
                        1.dp,
                        Line,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(
                        horizontal = 9.dp,
                        vertical = 6.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            if (connected) Red else Muted,
                            RoundedCornerShape(50)
                        )
                )

                Text(
                    "  " +
                        if (connected) {
                            "ONLINE"
                        } else {
                            "LINKING"
                        },
                    fontFamily = NDotFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Paper
                )
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                device,
                fontFamily = NDotFamily,
                fontSize = 9.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun DotPage(
    controller: GlyphController,
    connected: Boolean,
    brightness: Int,
    onBrightness: (Int) -> Unit,
    onStatus: (String) -> Unit
) {
    SectionTitle(
        "DOT CONTROL",
        "ONE CHANNEL. MANY PERSONALITIES."
    )

    DataCard {
        Text(
            "INTENSITY",
            fontSize = 11.sp,
            color = Muted
        )

        Text(
            brightness
                .toString()
                .padStart(4, '0'),
            fontSize = 42.sp,
            color = Paper
        )

        Slider(
            value = brightness.toFloat(),
            onValueChange = {
                onBrightness(it.toInt())
            },
            valueRange = 0f..4095f
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                text = "ON",
                modifier = Modifier.weight(1f),
                enabled = connected
            ) {
                controller.setBrightness(
                    brightness
                )
                onStatus(
                    "DOT ON • " +
                        brightness
                )
            }

            ActionButton(
                text = "OFF",
                modifier = Modifier.weight(1f),
                enabled = connected
            ) {
                controller.stopPattern()
                onStatus("DOT OFF")
            }
        }
    }

    DataCard {
        Text(
            "QUICK PATTERNS",
            fontSize = 11.sp,
            color = Muted
        )

        listOf(
            "SINGLE BLINK" to
                GlyphPatterns.single,
            "DOUBLE BLINK" to
                GlyphPatterns.double,
            "FAST BLINK" to
                GlyphPatterns.fast,
            "SLOW PULSE" to
                GlyphPatterns.slow,
            "HEARTBEAT" to
                GlyphPatterns.heartbeat
        ).forEach { item ->
            OutlinedAction(item.first) {
                if (connected) {
                    controller.playPattern(
                        item.second,
                        2
                    )
                    onStatus(
                        "PLAYING • " +
                            item.first
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerPage(
    context: Context,
    controller: GlyphController,
    steps: MutableList<GlyphStep>,
    onSteps: (List<GlyphStep>) -> Unit,
    onStatus: (String) -> Unit
) {
    SectionTitle(
        "GLYPH COMPOSER",
        "BLINK. PAUSE. REPEAT."
    )

    DataCard {
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    "STEP " +
                        (index + 1),
                    fontSize = 10.sp,
                    color = Muted
                )

                Text(
                    step.brightness
                        .toString()
                        .padStart(4, '0') +
                        " • " +
                        step.durationMs +
                        "MS",
                    fontSize = 9.sp,
                    color = Paper
                )
            }

            Slider(
                value =
                    step.brightness.toFloat(),
                onValueChange = { value ->
                    val next =
                        steps.toMutableList()

                    next[index] =
                        step.copy(
                            brightness =
                                value.toInt()
                        )

                    onSteps(next)
                },
                valueRange = 0f..4095f
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    120L,
                    220L,
                    420L,
                    700L
                ).forEach { duration ->
                    FilterChip(
                        selected =
                            step.durationMs ==
                                duration,
                        onClick = {
                            val next =
                                steps.toMutableList()

                            next[index] =
                                step.copy(
                                    durationMs =
                                        duration
                                )

                            onSteps(next)
                        },
                        label = {
                            Text(
                                duration
                                    .toString() +
                                    "MS",
                                fontSize = 9.sp
                            )
                        }
                    )
                }
            }

            if (steps.size > 1) {
                TextButton(
                    onClick = {
                        val next =
                            steps.toMutableList()

                        next.removeAt(index)

                        onSteps(next)
                    }
                ) {
                    Text(
                        "REMOVE",
                        color = Red,
                        fontSize = 9.sp
                    )
                }
            }

            Divider(color = Line)
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                text = "ADD STEP",
                modifier = Modifier.weight(1f),
                enabled = steps.size < 8
            ) {
                onSteps(
                    steps +
                        GlyphStep(
                            4095,
                            180
                        )
                )

                onStatus(
                    "STEP ADDED"
                )
            }

            ActionButton(
                text = "PLAY",
                modifier = Modifier.weight(1f)
            ) {
                controller.playPattern(
                    steps,
                    2
                )

                onStatus(
                    "PLAYING • CUSTOM"
                )
            }
        }

        OutlinedAction(
            "PLAY WITH RINGTONE"
        ) {
            GlyphAction.playRingtoneAndPattern(
                context,
                steps
            )

            onStatus(
                "RINGTONE + CUSTOM PATTERN"
            )
        }

        OutlinedAction(
            "SAVE TO DEVICE"
        ) {
            onStatus(
                "CUSTOM PATTERN SAVED"
            )
        }
    }

    DataCard {
        Text(
            "STARTER PATTERNS",
            fontSize = 11.sp,
            color = Muted
        )

        listOf(
            "DOUBLE" to
                GlyphPatterns.double,
            "HEARTBEAT" to
                GlyphPatterns.heartbeat,
            "FAST" to
                GlyphPatterns.fast,
            "SLOW" to
                GlyphPatterns.slow
        ).forEach { item ->
            OutlinedAction(
                "LOAD " + item.first
            ) {
                onSteps(
                    item.second
                )

                onStatus(
                    "LOADED • " +
                        item.first
                )
            }
        }
    }
}

@Composable
private fun ModesPage(
    context: Context,
    visualizerOn: Boolean,
    reminderOn: Boolean,
    reminderInterval: Int,
    chargingOn: Boolean,
    gameScore: Int,
    onVisualizer: () -> Unit,
    onReminder: () -> Unit,
    onInterval: (Int) -> Unit,
    onCharging: () -> Unit,
    onGame: () -> Unit,
    onCamera: () -> Unit,
    onRingtone: () -> Unit,
    onStatus: (String) -> Unit
) {
    SectionTitle(
        "SMART MODES",
        "SOFTWARE MAKES THE DOT SMARTER."
    )

    FeatureCard(
        title = "MUSIC VISUALIZER",
        body =
            "React to the audio mix with live brightness changes.",
        active = visualizerOn,
        action = onVisualizer,
        actionText =
            if (visualizerOn) {
                "STOP VISUALIZER"
            } else {
                "START VISUALIZER"
            }
    )

    FeatureCard(
        title = "NOTIFICATION REMINDERS",
        body =
            "Blink again for notifications that remain present.",
        active = reminderOn,
        action = onReminder,
        actionText =
            if (reminderOn) {
                "DISABLE"
            } else {
                "ENABLE"
            }
    )

    DataCard {
        Text(
            "REMINDER INTERVAL",
            fontSize = 10.sp,
            color = Muted
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(7.dp)
        ) {
            listOf(
                1,
                5,
                10,
                15
            ).forEach { minutes ->
                FilterChip(
                    selected =
                        reminderInterval ==
                            minutes,
                    onClick = {
                        onInterval(minutes)
                    },
                    label = {
                        Text(
                            minutes
                                .toString() +
                                "M",
                            fontSize = 9.sp
                        )
                    }
                )
            }
        }

        OutlinedAction(
            "OPEN NOTIFICATION ACCESS"
        ) {
            context.startActivity(
                Intent(
                    Settings
                        .ACTION_NOTIFICATION_LISTENER_SETTINGS
                )
            )
        }
    }

    FeatureCard(
        title = "CHARGING EFFECTS",
        body =
            "A brighter breathing ramp when charging starts.",
        active = chargingOn,
        action = onCharging,
        actionText =
            if (chargingOn) {
                "DISABLE"
            } else {
                "ENABLE"
            }
    )

    DataCard {
        Text(
            "CAMERA COUNTDOWN",
            fontSize = 11.sp,
            color = Muted
        )

        Text(
            "3 BLINKS → 2 BLINKS → 1 BLINK",
            fontSize = 14.sp,
            color = Paper
        )

        Text(
            "OPEN THE CAMERA FIRST, THEN LET THE DOT COUNT.",
            fontSize = 9.sp,
            color = Muted
        )

        OutlinedAction(
            "OPEN CAMERA + COUNTDOWN",
            onClick = onCamera
        )
    }

    DataCard {
        Text(
            "RINGTONE PATTERNS",
            fontSize = 11.sp,
            color = Muted
        )

        Text(
            "CURRENT RINGTONE + YOUR COMPOSER SEQUENCE.",
            fontSize = 9.sp,
            color = Muted
        )

        OutlinedAction(
            "PLAY RINGTONE PATTERN",
            onClick = onRingtone
        )
    }

    DataCard {
        Text(
            "DOT TOY",
            fontSize = 11.sp,
            color = Muted
        )

        Text(
            if (gameScore == 0) {
                "TRIGGER THE DOT. BUILD A SCORE."
            } else {
                "REACTION COUNT • " +
                    gameScore
            },
            fontSize = 15.sp,
            color = Paper
        )

        ActionButton(
            text = "TRIGGER",
            modifier = Modifier.fillMaxWidth(),
            onClick = onGame
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(3.dp)
    ) {
        Text(
            title,
            fontSize = 19.sp,
            color = Paper
        )

        Text(
            subtitle,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = Muted
        )
    }
}

@Composable
private fun DataCard(
    content: @Composable Column.() -> Unit
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = Panel
            ),
        border =
            BorderStroke(
                1.dp,
                Line
            ),
        shape =
            RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    body: String,
    active: Boolean,
    action: () -> Unit,
    actionText: String
) {
    DataCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.Top
        ) {
            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    title,
                    fontSize = 13.sp,
                    color = Paper
                )

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(
                    body,
                    fontSize = 9.sp,
                    color = Muted
                )
            }

            Text(
                if (active) "●" else "○",
                fontSize = 18.sp,
                color =
                    if (active) Red
                    else Muted
            )
        }

        OutlinedAction(
            actionText,
            action
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        shape =
            RoundedCornerShape(5.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Paper,
                contentColor = Ink,
                disabledContainerColor =
                    Color(0xFF252525),
                disabledContentColor =
                    Color(0xFF666666)
            )
    ) {
        Text(
            text,
            fontSize = 10.sp,
            letterSpacing = .8.sp
        )
    }
}

@Composable
private fun OutlinedAction(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier =
            Modifier.fillMaxWidth(),
        onClick = onClick,
        shape =
            RoundedCornerShape(5.dp),
        border =
            BorderStroke(
                1.dp,
                Color(0xFF484848)
            )
    ) {
        Text(
            text,
            fontSize = 10.sp,
            letterSpacing = .8.sp
        )
    }
}
