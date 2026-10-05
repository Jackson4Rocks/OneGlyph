package com.jackson4rocks.oneglyph

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay

private val Red = Color(0xFFFF3B30)

private val DarkScheme =
    darkColorScheme(
        background = Color.Black,
        surface = Color(0xFF0A0A0A),
        primary = Color.White,
        onPrimary = Color.Black,
        secondary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        outline = Color(0xFF303030),
        error = Red
    )

private val LightScheme =
    lightColorScheme(
        background = Color(0xFFF4F4F4),
        surface = Color.White,
        primary = Color.Black,
        onPrimary = Color.White,
        secondary = Color.Black,
        onSecondary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        outline = Color(0xFFBEBEBE),
        error = Red
    )

private val NDotFamily =
    runCatching {
        FontFamily(
            Font(
                DeviceFontFamilyName(
                    "NDot55All"
                )
            )
        )
    }.getOrElse {
        FontFamily.Monospace
    }

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: android.os.Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContent {
            OneGlyphApp()
        }
    }
}

@Composable
private fun OneGlyphApp() {
    val context =
        androidx.compose.ui.platform
            .LocalContext.current

    val store =
        remember {
            PatternStore(context)
        }

    val controller =
        remember {
            GlyphController(context)
        }

    var page by remember {
        mutableStateOf("HOME")
    }

    var showSplash by remember {
        mutableStateOf(true)
    }

    var isDark by remember {
        mutableStateOf(
            store.theme() ==
                OneGlyphTheme.DARK
        )
    }

    var appEnabled by remember {
        mutableStateOf(
            store.appEnabled()
        )
    }

    var beatSyncOn by remember {
        mutableStateOf(false)
    }

    var connected by remember {
        mutableStateOf(false)
    }

    var status by remember {
        mutableStateOf("Ready.")
    }

    var audioAccess by remember {
        mutableStateOf(
            hasAudioAccess(context)
        )
    }

    var mediaAccess by remember {
        mutableStateOf(
            hasMediaAccess(context)
        )
    }

    var playback by remember {
        mutableStateOf<MediaPlaybackInfo?>(null)
    }

    var brightness by remember {
        mutableIntStateOf(3200)
    }

    var chargingEnabled by remember {
        mutableStateOf(
            store.chargingEnabled()
        )
    }

    var chargeTarget by remember {
        mutableIntStateOf(
            store.chargeTarget()
        )
    }

    var cameraSeconds by remember {
        mutableIntStateOf(
            store.cameraSeconds()
        )
    }

    var composer by remember {
        mutableStateOf(
            store.loadComposer()
        )
    }

    var toyOn by remember {
        mutableStateOf(false)
    }

    val audioPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            audioAccess = granted

            if (!granted) {
                status =
                    "Audio access is needed for Beat Sync."
            } else if (
                !hasMediaAccess(context)
            ) {
                status =
                    "Allow Media Access next."
                openMediaAccess(context)
            } else {
                mediaAccess = true
                beatSyncOn = true
                status =
                    "Waiting for music…"
            }
        }

    val beatSync =
        remember {
            BeatSyncController(
                context,
                controller,
                onState = { info ->
                    playback = info

                    status =
                        when {
                            info == null ->
                                "Play music to start syncing."

                            info.isPlaying ->
                                "Following the beat."

                            else ->
                                "Paused — Glyph is off."
                        }
                },
                onError = { message ->
                    beatSyncOn = false
                    status = message
                }
            )
        }

    DisposableEffect(
        controller,
        beatSync
    ) {
        controller.setStatusListener {
            connected =
                controller.isReady()
        }

        onDispose {
            controller.setStatusListener(
                null
            )
            beatSync.close()
            controller.close()
        }
    }

    LaunchedEffect(Unit) {
        delay(950L)
        showSplash = false
        ChargingMonitor.sync(context)
    }

    LaunchedEffect(appEnabled) {
        if (!appEnabled) {
            beatSyncOn = false
            beatSync.close()
            playback = null
            toyOn = false
            controller.stopPattern()
        }
    }

    LaunchedEffect(
        beatSyncOn,
        appEnabled
    ) {
        if (
            beatSyncOn &&
            appEnabled
        ) {
            beatSync.start()
        } else {
            beatSync.close()
            playback = null
            controller.stopPattern()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected =
                controller.isReady()

            audioAccess =
                hasAudioAccess(context)

            mediaAccess =
                hasMediaAccess(context)

            if (
                beatSyncOn &&
                appEnabled
            ) {
                beatSync.refresh()
            }

            delay(1000L)
        }
    }

    if (showSplash) {
        OneGlyphSplash()
        return
    }

    MaterialTheme(
        colorScheme =
            if (isDark) {
                DarkScheme
            } else {
                LightScheme
            },
        typography =
            MaterialTheme.typography.copy(
                displayLarge =
                    MaterialTheme.typography.displayLarge
                        .copy(
                            fontFamily =
                                NDotFamily
                        ),
                titleLarge =
                    MaterialTheme.typography.titleLarge
                        .copy(
                            fontFamily =
                                NDotFamily
                        ),
                titleMedium =
                    MaterialTheme.typography.titleMedium
                        .copy(
                            fontFamily =
                                NDotFamily
                        ),
                bodyLarge =
                    MaterialTheme.typography.bodyLarge
                        .copy(
                            fontFamily =
                                NDotFamily
                        ),
                bodyMedium =
                    MaterialTheme.typography.bodyMedium
                        .copy(
                            fontFamily =
                                NDotFamily
                        ),
                bodySmall =
                    MaterialTheme.typography.bodySmall
                        .copy(
                            fontFamily =
                                NDotFamily
                        )
            )
    ) {
        val colors =
            MaterialTheme.colorScheme

        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            color =
                colors.background
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            horizontal = 18.dp,
                            vertical = 14.dp
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    )
            ) {
                Header(
                    enabled = appEnabled,
                    connected = connected
                )

                TopNavigation(
                    page = page,
                    onPage = {
                        page = it
                    }
                )

                when (page) {
                    "HOME" -> {
                        HomePage(
                            context = context,
                            controller = controller,
                            enabled = appEnabled,
                            connected = connected,
                            beatSyncOn = beatSyncOn,
                            audioAccess = audioAccess,
                            mediaAccess = mediaAccess,
                            playback = playback,
                            brightness = brightness,
                            cameraSeconds = cameraSeconds,
                            onBrightness = {
                                brightness = it
                            },
                            onBeatSync = {
                                if (!appEnabled) {
                                    status =
                                        "Turn on OneGlyph in Settings first."
                                } else if (beatSyncOn) {
                                    beatSyncOn = false
                                    status =
                                        "Beat Sync is off."
                                } else if (!audioAccess) {
                                    audioPermission.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                } else if (!mediaAccess) {
                                    status =
                                        "Allow Media Access first."
                                    openMediaAccess(
                                        context
                                    )
                                } else {
                                    beatSyncOn = true
                                    status =
                                        "Waiting for music…"
                                }
                            },
                            onMediaAccess = {
                                openMediaAccess(
                                    context
                                )
                            },
                            onBlink = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    controller.playPattern(it)
                                    status =
                                        "Playing pattern."
                                }
                            },
                            onStop = {
                                controller.stopPattern()
                                status =
                                    "Glyph stopped."
                            },
                            onCamera = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    CameraCountdown.start(
                                        context,
                                        controller,
                                        cameraSeconds
                                    )
                                    status =
                                        "Camera countdown started."
                                }
                            }
                        )
                    }

                    "COMPOSER" -> {
                        ComposerPage(
                            controller = controller,
                            enabled = appEnabled,
                            connected = connected,
                            steps = composer,
                            onSteps = {
                                composer =
                                    it.toMutableList()
                                store.saveComposer(
                                    it
                                )
                            },
                            onStatus = {
                                status = it
                            }
                        )
                    }

                    else -> {
                        SettingsPage(
                            context = context,
                            appEnabled = appEnabled,
                            dark = isDark,
                            chargingEnabled =
                                chargingEnabled,
                            chargeTarget =
                                chargeTarget,
                            cameraSeconds =
                                cameraSeconds,
                            onAppEnabled = {
                                appEnabled = it
                                store.setAppEnabled(it)

                                if (!it) {
                                    beatSyncOn = false
                                    controller.stopPattern()
                                    status =
                                        "OneGlyph is off."
                                } else {
                                    status =
                                        "OneGlyph is on."
                                }
                            },
                            onDarkMode = {
                                isDark = it
                                store.setTheme(
                                    if (it) {
                                        OneGlyphTheme.DARK
                                    } else {
                                        OneGlyphTheme.LIGHT
                                    }
                                )
                            },
                            onChargingEnabled = {
                                chargingEnabled = it
                                store.setChargingEnabled(it)
                                ChargingMonitor.sync(context)
                            },
                            onChargeTarget = {
                                chargeTarget = it
                                store.setChargeTarget(it)
                                ChargingMonitor.sync(context)
                            },
                            onCameraSeconds = {
                                cameraSeconds = it
                                store.setCameraSeconds(it)
                            }
                        )
                    }
                }

                UserCard {
                    Text(
                        "DOT TOY",
                        fontFamily = NDotFamily,
                        fontSize = 10.sp,
                        color =
                            colors.onSurface.copy(
                                alpha = .55f
                            )
                    )

                    Text(
                        if (toyOn) {
                            "Flash mode is running."
                        } else {
                            "Make the dot go a little crazy."
                        },
                        fontFamily = NDotFamily,
                        fontSize = 16.sp,
                        color = colors.onSurface
                    )

                    PrimaryButton(
                        text =
                            if (toyOn) {
                                "TURN OFF TOY"
                            } else {
                                "TURN ON TOY"
                            },
                        enabled =
                            appEnabled &&
                                connected,
                        onClick = {
                            if (toyOn) {
                                toyOn = false
                                controller.stopPattern()
                                status =
                                    "Dot toy off."
                            } else {
                                beatSyncOn = false
                                toyOn = true
                                controller.fastFlashLoop()
                                status =
                                    "Dot toy is flashing."
                            }
                        }
                    )
                }

                Text(
                    status,
                    fontFamily = NDotFamily,
                    fontSize = 10.sp,
                    color =
                        colors.onBackground.copy(
                            alpha = 0.55f
                        )
                )
            }
        }
    }
}

private fun hasAudioAccess(
    context: Context
): Boolean =
    context.checkSelfPermission(
        Manifest.permission.RECORD_AUDIO
    ) ==
        PackageManager.PERMISSION_GRANTED

private fun hasMediaAccess(
    context: Context
): Boolean {
    val enabled =
        Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()

    val component =
        ComponentName(
            context,
            GlyphMediaSessionService::class.java
        )

    return enabled
        .split(":")
        .mapNotNull {
            ComponentName
                .unflattenFromString(it)
        }
        .any {
            it == component
        }
}

private fun openMediaAccess(
    context: Context
) {
    context.startActivity(
        Intent(
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        )
    )
}

@Composable
private fun Header(
    enabled: Boolean,
    connected: Boolean
) {
    val colors =
        MaterialTheme.colorScheme

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column {
            Text(
                "ONEGLYPH",
                fontFamily =
                    NDotFamily,
                fontSize = 32.sp,
                letterSpacing =
                    (-1).sp,
                color =
                    colors.onBackground
            )

            Text(
                "MAKE THE ONE DOT DO MORE.",
                fontFamily =
                    NDotFamily,
                fontSize = 9.sp,
                letterSpacing =
                    1.1.sp,
                color =
                    colors.onBackground.copy(
                        alpha = 0.55f
                    )
            )
        }

        Row(
            modifier =
                Modifier
                    .border(
                        1.dp,
                        colors.outline,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(
                        horizontal = 9.dp,
                        vertical = 6.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Box(
                modifier =
                    Modifier
                        .size(7.dp)
                        .background(
                            when {
                                !enabled ->
                                    colors.outline

                                connected ->
                                    Red

                                else ->
                                    colors.outline
                            },
                            CircleShape
                        )
            )

            Text(
                when {
                    !enabled -> "  OFF"
                    connected -> "  READY"
                    else -> "  LINKING"
                },
                fontFamily =
                    NDotFamily,
                fontSize = 9.sp,
                letterSpacing =
                    .8.sp,
                color =
                    colors.onBackground
            )
        }
    }
}

@Composable
private fun TopNavigation(
    page: String,
    onPage: (String) -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    colors.outline,
                    RoundedCornerShape(6.dp)
                )
                .padding(4.dp),
        horizontalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            "HOME" to "Home",
            "COMPOSER" to "Composer",
            "SETTINGS" to "Settings"
        ).forEach { item ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            if (
                                page ==
                                    item.first
                            ) {
                                colors.primary
                            } else {
                                Color.Transparent
                            },
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            onPage(
                                item.first
                            )
                        }
                        .padding(
                            vertical = 10.dp
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    item.second,
                    fontFamily =
                        NDotFamily,
                    fontSize = 10.sp,
                    color =
                        if (
                            page ==
                                item.first
                        ) {
                            colors.onPrimary
                        } else {
                            colors.onBackground.copy(
                                alpha = .6f
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    context: Context,
    controller: GlyphController,
    enabled: Boolean,
    connected: Boolean,
    beatSyncOn: Boolean,
    audioAccess: Boolean,
    mediaAccess: Boolean,
    playback: MediaPlaybackInfo?,
    brightness: Int,
    cameraSeconds: Int,
    onBrightness: (Int) -> Unit,
    onBeatSync: () -> Unit,
    onMediaAccess: () -> Unit,
    onBlink: (List<GlyphStep>) -> Unit,
    onStop: () -> Unit,
    onCamera: () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    UserCard {
        Text(
            "MUSIC",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            when {
                playback?.isPlaying == true ->
                    "The dot is dancing."

                playback != null ->
                    "Music paused."

                else ->
                    "Play something."
            },
            fontFamily =
                NDotFamily,
            fontSize = 22.sp,
            color =
                colors.onSurface
        )

        if (playback != null) {
            Text(
                playback.title
                    .ifBlank {
                        "Now playing"
                    },
                fontFamily =
                    NDotFamily,
                fontSize = 14.sp,
                color =
                    colors.onSurface
            )

            Text(
                playback.artist
                    .ifBlank {
                        "Media player"
                    },
                fontFamily =
                    NDotFamily,
                fontSize = 10.sp,
                color =
                    colors.onSurface.copy(
                        alpha = .55f
                    )
            )

            if (playback.durationMs > 0) {
                val progress =
                    (
                        playback.positionMs.toFloat() /
                            playback.durationMs.toFloat()
                    ).coerceIn(0f, 1f)

                LinearProgressIndicator(
                    progress = {
                        progress
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }

        PrimaryButton(
            text =
                if (beatSyncOn) {
                    "TURN OFF BEAT SYNC"
                } else {
                    "TURN ON BEAT SYNC"
                },
            enabled =
                enabled,
            onClick =
                onBeatSync
        )

        if (!audioAccess) {
            SmallNote(
                "Beat Sync needs Audio access so it can analyze the music."
            )
        }

        if (!mediaAccess) {
            OutlinedAction(
                "ALLOW MEDIA ACCESS",
                onClick =
                    onMediaAccess
            )
        }

        SmallNote(
            when {
                playback?.isPlaying == true ->
                    "Drums, kicks and strong rhythm hits make the dot blink."

                playback != null ->
                    "When music pauses, the Glyph turns off."

                else ->
                    "OneGlyph follows the media Android says is currently playing."
            }
        )
    }

    UserCard {
        Text(
            "BLINK",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            QuickButton(
                "Blink",
                Modifier.weight(1f),
                enabled && connected
            ) {
                onBlink(
                    GlyphPatterns.blink
                )
            }

            QuickButton(
                "Double",
                Modifier.weight(1f),
                enabled && connected
            ) {
                onBlink(
                    GlyphPatterns.doubleBlink
                )
            }

            QuickButton(
                "Heart",
                Modifier.weight(1f),
                enabled && connected
            ) {
                onBlink(
                    GlyphPatterns.heartbeat
                )
            }
        }

        OutlinedAction(
            "STOP GLYPH",
            enabled &&
                connected,
            onClick =
                onStop
        )
    }

    UserCard {
        Text(
            "CAMERA",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "Slow flashes become faster as the timer gets close to zero.",
            fontFamily =
                NDotFamily,
            fontSize = 15.sp,
            color =
                colors.onSurface
        )

        Text(
            "${cameraSeconds} seconds",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        PrimaryButton(
            text =
                "START CAMERA COUNTDOWN",
            enabled =
                enabled &&
                    connected,
            onClick =
                onCamera
        )
    }

    UserCard {
        Text(
            "MANUAL CONTROL",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            brightness.toString(),
            fontFamily =
                NDotFamily,
            fontSize = 32.sp,
            color =
                colors.onSurface
        )

        Slider(
            value =
                brightness.toFloat(),
            onValueChange = {
                onBrightness(
                    it.toInt()
                )
            },
            valueRange =
                0f..4095f,
            enabled =
                enabled &&
                    connected
        )

        OutlinedAction(
            "TEST BLINK",
            enabled =
                enabled &&
                    connected,
            onClick = {
                controller.playPattern(
                    listOf(
                        GlyphStep(
                            brightness,
                            220
                        ),
                        GlyphStep(
                            0,
                            220
                        )
                    )
                )
            }
        )
    }
}

@Composable
private fun ComposerPage(
    controller: GlyphController,
    enabled: Boolean,
    connected: Boolean,
    steps: MutableList<GlyphStep>,
    onSteps: (List<GlyphStep>) -> Unit,
    onStatus: (String) -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    UserCard {
        Text(
            "GLYPH COMPOSER",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "Make your own blink pattern.",
            fontFamily =
                NDotFamily,
            fontSize = 21.sp,
            color =
                colors.onSurface
        )

        steps.forEachIndexed { index, step ->
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        5.dp
                    )
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Text(
                        "BLINK ${index + 1}",
                        fontFamily =
                            NDotFamily,
                        fontSize = 9.sp,
                        color =
                            colors.onSurface.copy(
                                alpha = .55f
                            )
                    )

                    Text(
                        "${step.durationMs} ms",
                        fontFamily =
                            NDotFamily,
                        fontSize = 9.sp,
                        color =
                            colors.onSurface
                    )
                }

                Slider(
                    value =
                        step.brightness
                            .toFloat(),
                    onValueChange = {
                        val next =
                            steps.toMutableList()

                        next[index] =
                            step.copy(
                                brightness =
                                    it.toInt()
                            )

                        onSteps(
                            next
                        )
                    },
                    valueRange =
                        0f..4095f,
                    enabled =
                        enabled &&
                            connected
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    listOf(
                        100L,
                        180L,
                        300L,
                        500L
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

                                onSteps(
                                    next
                                )
                            },
                            enabled =
                                enabled &&
                                    connected,
                            label = {
                                Text(
                                    "${duration} ms",
                                    fontSize = 8.sp
                                )
                            }
                        )
                    }
                }

                if (
                    steps.size > 1
                ) {
                    TextButton(
                        enabled =
                            enabled &&
                                connected,
                        onClick = {
                            val next =
                                steps.toMutableList()

                            next.removeAt(
                                index
                            )

                            onSteps(
                                next
                            )
                        }
                    ) {
                        Text(
                            "Remove",
                            color =
                                colors.error
                        )
                    }
                }
            }

            HorizontalDivider(
                color =
                    colors.outline.copy(
                        alpha = .6f
                    )
            )
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            PrimaryButton(
                text = "ADD BLINK",
                modifier =
                    Modifier.weight(1f),
                enabled =
                    enabled &&
                        connected &&
                        steps.size < 8,
                onClick = {
                    onSteps(
                        steps +
                            GlyphStep(
                                4095,
                                180
                            )
                    )
                }
            )

            PrimaryButton(
                text = "PLAY",
                modifier =
                    Modifier.weight(1f),
                enabled =
                    enabled &&
                        connected,
                onClick = {
                    controller.playPattern(
                        steps,
                        2
                    )
                    onStatus(
                        "Playing your pattern."
                    )
                }
            )
        }

        OutlinedAction(
            "SAVE",
            enabled = enabled,
            onClick = {
                onSteps(
                    steps
                )
                onStatus(
                    "Pattern saved."
                )
            }
        )
    }
}

@Composable
private fun SettingsPage(
    context: Context,
    appEnabled: Boolean,
    dark: Boolean,
    chargingEnabled: Boolean,
    chargeTarget: Int,
    cameraSeconds: Int,
    onAppEnabled: (Boolean) -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onChargingEnabled: (Boolean) -> Unit,
    onChargeTarget: (Int) -> Unit,
    onCameraSeconds: (Int) -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    UserCard {
        SettingRow(
            title = "OneGlyph",
            description =
                "Turn off all Glyph effects from the app.",
            control = {
                Switch(
                    checked =
                        appEnabled,
                    onCheckedChange =
                        onAppEnabled
                )
            }
        )
    }

    UserCard {
        Text(
            "APPEARANCE",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            FilterChip(
                selected =
                    dark,
                onClick = {
                    onDarkMode(true)
                },
                label = {
                    Text("Dark")
                }
            )

            FilterChip(
                selected =
                    !dark,
                onClick = {
                    onDarkMode(false)
                },
                label = {
                    Text("Light")
                }
            )
        }
    }

    UserCard {
        SettingRow(
            title =
                "Charging effects",
            description =
                "Blink 4 times when charging starts.",
            control = {
                Switch(
                    checked =
                        chargingEnabled,
                    onCheckedChange =
                        onChargingEnabled
                )
            }
        )

        Text(
            "Blink again when the battery reaches:",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            listOf(
                80,
                90,
                100
            ).forEach { percent ->
                FilterChip(
                    selected =
                        chargeTarget ==
                            percent,
                    onClick = {
                        onChargeTarget(
                            percent
                        )
                    },
                    label = {
                        Text(
                            "${percent}%"
                        )
                    }
                )
            }
        }

        SmallNote(
            "Set this to your phone's charging limit."
        )

        SmallNote(
            "At the selected level, the Glyph blinks 9 times."
        )
    }

    UserCard {
        Text(
            "CAMERA TIMER",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            listOf(
                3,
                5,
                10
            ).forEach { seconds ->
                FilterChip(
                    selected =
                        cameraSeconds ==
                            seconds,
                    onClick = {
                        onCameraSeconds(
                            seconds
                        )
                    },
                    label = {
                        Text(
                            "${seconds}s"
                        )
                    }
                )
            }
        }
    }

    UserCard {
        Text(
            "MEDIA ACCESS",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "OneGlyph uses Media Access to know what Android says is playing and whether it is paused.",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface
        )

        OutlinedAction(
            "OPEN MEDIA ACCESS",
            onClick = {
                openMediaAccess(
                    context
                )
            }
        )
    }

    UserCard {
        Text(
            "PROJECT MAINTAINER",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.leon_profile),
                contentDescription = "Leon Sony",
                modifier = Modifier
                    .size(56.dp)
                    .border(
                        1.dp,
                        colors.outline,
                        CircleShape
                    )
                    .padding(2.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    "Leon Sony",
                    fontFamily = NDotFamily,
                    fontSize = 20.sp,
                    color = colors.onSurface
                )

                Text(
                    "Jackson4Rocks",
                    fontFamily = NDotFamily,
                    fontSize = 9.sp,
                    color = colors.onSurface.copy(alpha = .55f)
                )
            }
        }

        SmallNote(
            "OneGlyph is an independent community project."
        )

        OutlinedAction(
            "OPEN PROJECT",
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://github.com/Jackson4Rocks/OneGlyph"
                        )
                    )
                )
            }
        )
    }
}

@Composable
private fun OneGlyphSplash() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {
        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {
            for (i in 0 until 72) {
                val x =
                    ((i * 47) % 1000) / 1000f
                val y =
                    ((i * 83 + 113) % 1000) / 1000f
                val radius =
                    1.2f +
                        ((i * 7) % 4) * 0.45f
                val alpha =
                    0.18f +
                        ((i * 13) % 5) * 0.08f

                drawCircle(
                    color =
                        Color.White.copy(
                            alpha = alpha
                        ),
                    radius = radius,
                    center =
                        androidx.compose.ui.geometry.Offset(
                            size.width * x,
                            size.height * y
                        )
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(
                        horizontal = 30.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {
            androidx.compose.foundation.Image(
                painter =
                    painterResource(
                        R.drawable.ic_oneglyph
                    ),
                contentDescription =
                    "OneGlyph",
                modifier =
                    Modifier.size(104.dp)
            )

            Text(
                "OneGlyph",
                fontFamily = NDotFamily,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Text(
                "Make the one dot useful.",
                fontFamily = NDotFamily,
                fontSize = 11.sp,
                color = Color.White.copy(
                    alpha = .62f
                )
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Column(
            modifier =
                Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    4.dp
                )
        ) {
            Text(
                title,
                fontFamily =
                    NDotFamily,
                fontSize = 15.sp,
                color =
                    colors.onSurface
            )

            Text(
                description,
                fontFamily =
                    NDotFamily,
                fontSize = 9.sp,
                color =
                    colors.onSurface.copy(
                        alpha = .55f
                    )
            )
        }

        control()
    }
}

@Composable
private fun UserCard(
    content:
        @Composable
        ColumnScope.() -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    colors.surface
            ),
        border =
            BorderStroke(
                1.dp,
                colors.outline
            ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                ),
            content =
                content
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    Button(
        modifier =
            modifier.fillMaxWidth(),
        enabled =
            enabled,
        onClick =
            onClick,
        shape =
            RoundedCornerShape(6.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    colors.primary,
                contentColor =
                    colors.onPrimary,
                disabledContainerColor =
                    colors.surface.copy(
                        alpha = .65f
                    ),
                disabledContentColor =
                    colors.onSurface.copy(
                        alpha = .35f
                    )
            )
    ) {
        Text(
            text,
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            letterSpacing = .7.sp
        )
    }
}

@Composable
private fun QuickButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    OutlinedButton(
        modifier =
            modifier,
        enabled =
            enabled,
        onClick =
            onClick,
        shape =
            RoundedCornerShape(
                6.dp
            ),
        border =
            BorderStroke(
                1.dp,
                colors.outline
            )
    ) {
        Text(
            text,
            fontFamily =
                NDotFamily,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun OutlinedAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    OutlinedButton(
        modifier =
            Modifier.fillMaxWidth(),
        enabled =
            enabled,
        onClick =
            onClick,
        shape =
            RoundedCornerShape(
                6.dp
            ),
        border =
            BorderStroke(
                1.dp,
                colors.outline
            )
    ) {
        Text(
            text,
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            letterSpacing = .6.sp
        )
    }
}

@Composable
private fun SmallNote(
    text: String
) {
    val colors =
        MaterialTheme.colorScheme

    Text(
        text,
        fontFamily =
            NDotFamily,
        fontSize = 9.sp,
        color =
            colors.onSurface.copy(
                alpha = .55f
            )
    )
}
