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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.jackson4rocks.oneglyph.ui.component.FloatingBottomBar
import com.jackson4rocks.oneglyph.ui.component.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
        surfaceContainer = Color(0xFF141416),
        surfaceContainerHigh = Color(0xFF232326),
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFFAEAEB2),
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
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFE8E8ED),
        surfaceVariant = Color(0xFFD1D1D6),
        onSurfaceVariant = Color(0xFF6E6E6E),
        outline = Color(0xFFBEBEBE),
        error = Red
    )

private val NDotFamily: FontFamily =
    try {
        FontFamily(
            Font(
                DeviceFontFamilyName(
                    "NDot55All"
                )
            )
        )
    } catch (_: Throwable) {
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

        GlyphBackgroundService.ensureRunning(this)
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

    val pages =
        remember {
            listOf("HOME", "PLAY", "COMPOSER", "SETTINGS")
        }

    val pagerState =
        rememberPagerState(
            pageCount = { pages.size }
        )

    val navScope =
        rememberCoroutineScope()

    val contentBackdrop =
        rememberLayerBackdrop()

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
        mutableStateOf(
            store.backgroundMode() ==
                "music_sync"
        )
    }

    var connected by remember {
        mutableStateOf(false)
    }

    var linkDetailText by remember {
        mutableStateOf(
            GlyphBackgroundService.linkDetail(
                context
            )
        )
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

    var beatEngine by remember {
        mutableStateOf("ENGINE • STOPPED")
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
        mutableStateOf(
            store.backgroundMode() ==
                "toy"
        )
    }

    var metroOn by remember {
        mutableStateOf(
            store.backgroundMode() ==
                "metronome"
        )
    }

    var bpm by remember {
        mutableIntStateOf(
            store.metronomeBpm()
        )
    }

    var morseText by remember {
        mutableStateOf("SOS")
    }

    var diceResult by remember {
        mutableIntStateOf(0)
    }

    var notifFlashOn by remember {
        mutableStateOf(
            store.notificationFlashEnabled()
        )
    }

    var lowBatteryOn by remember {
        mutableStateOf(
            store.lowBatteryAlertEnabled()
        )
    }

    val playbackWatcher =
        remember {
            MediaPlaybackWatcher(
                context,
                onChanged = { info ->
                    playback = info

                    status =
                        when {
                            info == null ->
                                if (beatSyncOn) {
                                    "Waiting for music…"
                                } else {
                                    status
                                }

                            info.isPlaying &&
                                beatSyncOn ->
                                "Following the beat."

                            info.isPlaying ->
                                "Music is playing."

                            else ->
                                "Paused — Glyph is off."
                        }
                },
                onAccessError = {
                    if (beatSyncOn) {
                        status =
                            "Turn on Media Access in Settings."
                    }
                }
            )
        }

    val notificationPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // The foreground service still works if notifications are denied,
            // but Android may hide its ongoing notification from the shade.
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
                store.setBackgroundMode(
                    "music_sync"
                )
                toyOn = false
                metroOn = false
                beatSyncOn = true
                requestBackgroundRecoveryAccess(
                    context
                )
                GlyphBackgroundService.startMusicSync(
                    context
                )
                status =
                    "Waiting for music…"
            }
        }

    DisposableEffect(playbackWatcher) {
        onDispose {
            playbackWatcher.close()
        }
    }

    LaunchedEffect(Unit) {
        delay(950L)
        showSplash = false
        ChargingMonitor.sync(context)
    }

    LaunchedEffect(Unit) {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    LaunchedEffect(appEnabled) {
        if (!appEnabled) {
            beatSyncOn = false
            playback = null
            toyOn = false
            metroOn = false
            GlyphBackgroundService.stopPattern(context)
            GlyphBackgroundService.stopService(
                context
            )
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
            requestBackgroundRecoveryAccess(
                context
            )
            GlyphBackgroundService.startMusicSync(
                context
            )
        }
    }

    LaunchedEffect(
        appEnabled,
        mediaAccess
    ) {
        playbackWatcher.close()
        playback = null

        if (
            appEnabled &&
            mediaAccess
        ) {
            playbackWatcher.start()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            connected =
                GlyphBackgroundService.isGlyphReady(context)

            linkDetailText =
                GlyphBackgroundService.linkDetail(
                    context
                )

            beatEngine =
                GlyphBackgroundService.beatStatusText(
                    context
                )

            audioAccess =
                hasAudioAccess(context)

            mediaAccess =
                hasMediaAccess(context)

            if (
                appEnabled &&
                mediaAccess
            ) {
                playbackWatcher.refresh()
            }

            // Poll at 2s instead of 1s: halves UI wakeups and
            // PackageManager checks with no visible downside.
            delay(2000L)
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
            Box(
                modifier =
                    Modifier.fillMaxSize()
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .layerBackdrop(
                                contentBackdrop
                            )
                            .padding(
                                horizontal = 18.dp
                            )
                            .padding(
                                top = 14.dp
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

                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        // All 4 pages stay composed: zero
                        // first-swipe composition jank.
                        beyondViewportPageCount = 1,
                        // Floaty spring settle instead of a hard snap.
                        flingBehavior =
                            PagerDefaults.flingBehavior(
                                state = pagerState,
                                snapAnimationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        verticalAlignment =
                            Alignment.Top
                    ) { index ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    // Carousel fade: outgoing pages dim
                                    // and shrink slightly, all in the
                                    // draw phase (no recomposition).
                                    .graphicsLayer {
                                        val pageOffset =
                                            (
                                                pagerState.currentPage -
                                                    index +
                                                    pagerState.currentPageOffsetFraction
                                                ).coerceIn(-1f, 1f)

                                        val fade =
                                            1f - minOf(
                                                0.35f,
                                                abs(pageOffset) * 0.35f
                                            )

                                        val scale =
                                            1f - minOf(
                                                0.04f,
                                                abs(pageOffset) * 0.04f
                                            )

                                        alpha = fade
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .verticalScroll(
                                        rememberScrollState()
                                    )
                                    .padding(
                                        bottom = 108.dp
                                    ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    14.dp
                                )
                        ) {
                            when (pages[index]) {
                    "HOME" -> {
                        HomePage(
                            context = context,
                            enabled = appEnabled,
                            connected = connected,
                            beatSyncOn = beatSyncOn,
                            audioAccess = audioAccess,
                            mediaAccess = mediaAccess,
                            playback = playback,
                            brightness = brightness,
                            cameraSeconds = cameraSeconds,
                            beatEngine = beatEngine,
                            onBrightness = {
                                brightness = it
                            },
                            onBeatSync = {
                                if (!appEnabled) {
                                    status =
                                        "Turn on OneGlyph in Settings first."
                                } else if (beatSyncOn) {
                                    store.setBackgroundMode(
                                        "none"
                                    )
                                    beatSyncOn = false
                                    GlyphBackgroundService.stopMusicSync(
                                        context
                                    )
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
                                    store.setBackgroundMode(
                                        "music_sync"
                                    )
                                    toyOn = false
                                    metroOn = false
                                    beatSyncOn = true
                                    requestBackgroundRecoveryAccess(
                                        context
                                    )
                                    GlyphBackgroundService.startMusicSync(
                                        context
                                    )
                                    status =
                                        "Music Sync is on."
                                }
                            },
                            onMediaAccess = {
                                openMediaAccess(
                                    context
                                )
                            },
                            onBeatTest = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    GlyphBackgroundService.beatTest(
                                        context
                                    )
                                    status =
                                        "Test pulse sent."
                                }
                            },
                            onBlink = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    GlyphBackgroundService.playPattern(
                                        context,
                                        it
                                    )
                                    status =
                                        "Playing pattern."
                                }
                            },
                            onStop = {
                                GlyphBackgroundService.stopPattern(context)
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
                                        cameraSeconds
                                    )
                                    status =
                                        "Camera countdown started."
                                }
                            }
                        )
                    }

                    "PLAY" -> {
                        PlayPage(
                            context = context,
                            enabled = appEnabled,
                            connected = connected,
                            metroOn = metroOn,
                            bpm = bpm,
                            morseText = morseText,
                            diceResult = diceResult,
                            onBpm = {
                                bpm = it
                                store.setMetronomeBpm(it)
                            },
                            onMorseText = {
                                morseText = it
                            },
                            onMetronome = {
                                if (!appEnabled) {
                                    status =
                                        "Turn on OneGlyph in Settings first."
                                } else if (metroOn) {
                                    store.setBackgroundMode(
                                        "none"
                                    )
                                    metroOn = false
                                    GlyphBackgroundService.stopMetronome(
                                        context
                                    )
                                    status =
                                        "Metronome is off."
                                } else if (
                                    appEnabled &&
                                    connected
                                ) {
                                    store.setBackgroundMode(
                                        "metronome"
                                    )
                                    toyOn = false
                                    beatSyncOn = false
                                    metroOn = true
                                    requestBackgroundRecoveryAccess(
                                        context
                                    )
                                    GlyphBackgroundService.startMetronome(
                                        context,
                                        bpm
                                    )
                                    status =
                                        "Metronome at $bpm BPM."
                                }
                            },
                            onMorse = {
                                if (
                                    appEnabled &&
                                    connected &&
                                    morseText.isNotBlank()
                                ) {
                                    GlyphBackgroundService.playLongPattern(
                                        context,
                                        DotFun.morseSteps(morseText)
                                    )
                                    status =
                                        "Flashing morse."
                                }
                            },
                            onStop = {
                                GlyphBackgroundService.stopPattern(context)
                                status =
                                    "Glyph stopped."
                            },
                            onClock = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    val now =
                                        java.util.Calendar.getInstance()
                                    val hour =
                                        now.get(java.util.Calendar.HOUR)
                                            .let { if (it == 0) 12 else it }
                                    val minute =
                                        now.get(java.util.Calendar.MINUTE)
                                    GlyphBackgroundService.playLongPattern(
                                        context,
                                        DotFun.talkingClockSteps(hour, minute)
                                    )
                                    status =
                                        "The time is $hour:${minute.toString().padStart(2, '0')}."
                                }
                            },
                            onBattery = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    val battery =
                                        context.registerReceiver(
                                            null,
                                            android.content.IntentFilter(
                                                android.content.Intent.ACTION_BATTERY_CHANGED
                                            )
                                        )
                                    val level =
                                        battery?.getIntExtra(
                                            android.os.BatteryManager.EXTRA_LEVEL,
                                            -1
                                        ) ?: -1
                                    val scale =
                                        battery?.getIntExtra(
                                            android.os.BatteryManager.EXTRA_SCALE,
                                            100
                                        ) ?: 100
                                    if (level >= 0 && scale > 0) {
                                        val percent = level * 100 / scale
                                        GlyphBackgroundService.playLongPattern(
                                            context,
                                            DotFun.batteryMeterSteps(percent)
                                        )
                                        status =
                                            "Battery is $percent%."
                                    }
                                }
                            },
                            onDice = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    val rolled = (1..6).random()
                                    diceResult = rolled
                                    GlyphBackgroundService.playPattern(
                                        context,
                                        DotFun.diceSuspenseSteps() +
                                            DotFun.diceResultSteps(rolled)
                                    )
                                    status =
                                        "You rolled $rolled."
                                }
                            },
                            onBreathe = {
                                if (
                                    appEnabled &&
                                    connected
                                ) {
                                    GlyphBackgroundService.playLongPattern(
                                        context,
                                        DotFun.breathingSteps(2)
                                    )
                                    status =
                                        "Breathe in 4, hold 7, out 8."
                                }
                            }
                        )
                    }

                    "COMPOSER" -> {
                        ComposerPage(
                            context = context,
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
                            linkDetail =
                                linkDetailText,
                            notifFlash =
                                notifFlashOn,
                            lowBattery =
                                lowBatteryOn,
                            onAppEnabled = {
                                appEnabled = it
                                store.setAppEnabled(it)

                                if (!it) {
                                    store.setBackgroundMode(
                                        "none"
                                    )
                                    beatSyncOn = false
                                    GlyphBackgroundService.stopPattern(context)
                                    GlyphBackgroundService.stopService(
                                        context
                                    )
                                    toyOn = false
                                    metroOn = false
                                    status =
                                        "OneGlyph is off."
                                } else {
                                    GlyphBackgroundService.ensureRunning(
                                        context
                                    )
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
                            },
                            onNotifFlash = {
                                notifFlashOn = it
                                store.setNotificationFlashEnabled(it)
                                status =
                                    if (it) {
                                        "Notification flash is on."
                                    } else {
                                        "Notification flash is off."
                                    }
                            },
                            onLowBattery = {
                                lowBatteryOn = it
                                store.setLowBatteryAlertEnabled(it)
                            }
                        )
                    }
                }

                            ToySection(
                                toyOn = toyOn,
                                status = status,
                                enabled = appEnabled,
                                connected = connected,
                                onStartToy = {
                                    store.setBackgroundMode(
                                        "toy"
                                    )
                                    beatSyncOn = false
                                    metroOn = false
                                    toyOn = true
                                    requestBackgroundRecoveryAccess(
                                        context
                                    )
                                    GlyphBackgroundService.startToy(
                                        context
                                    )
                                    status =
                                        "Dot toy is on."
                                },
                                onStopToy = {
                                    store.setBackgroundMode(
                                        "none"
                                    )
                                    toyOn = false
                                    GlyphBackgroundService.stopToy(
                                        context
                                    )
                                    GlyphBackgroundService.stopPattern(
                                        context
                                    )
                                    status =
                                        "Dot toy off."
                                }
                            )
                        }
                    }
                }

                BottomPillNav(
                    modifier =
                        Modifier.align(
                            Alignment.BottomCenter
                        ),
                    pagerState = pagerState,
                    backdrop = contentBackdrop,
                    onPage = { target ->
                        navScope.launch {
                            // Bouncy spring: the indicator overshoots
                            // and settles instead of gliding linearly.
                            pagerState.animateScrollToPage(
                                target,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
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

private fun isIgnoringBatteryOptimizations(
    context: Context
): Boolean {
    if (
        android.os.Build.VERSION.SDK_INT < 23
    ) {
        return true
    }

    val powerManager =
        context.getSystemService(
            android.os.PowerManager::class.java
        ) ?: return false

    return powerManager.isIgnoringBatteryOptimizations(
        context.packageName
    )
}

private fun requestBackgroundRecoveryAccess(
    context: Context
) {
    if (
        android.os.Build.VERSION.SDK_INT < 23 ||
        isIgnoringBatteryOptimizations(context)
    ) {
        return
    }

    try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse(
                    "package:" +
                        context.packageName
                )
            )
        )
    } catch (_: Throwable) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                )
            )
        } catch (_: Throwable) {
        }
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
private fun BottomPillNav(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    backdrop: Backdrop,
    onPage: (Int) -> Unit
) {
    val items =
        listOf("Home", "Play", "Composer", "Settings")

    var entered by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        entered = true
    }

    AnimatedVisibility(
        visible = entered,
        enter =
            fadeIn() +
                slideInVertically(
                    initialOffsetY = { it }
                ),
        modifier = modifier
    ) {
        FloatingBottomBar(
            modifier =
                Modifier.padding(
                    bottom = 8.dp
                ),
            selectedIndex =
                pagerState.currentPage,
            onSelected = onPage,
            tabsCount = items.size,
            backdrop = backdrop,
            isBlurEnabled = true
        ) { activateTab ->
            items.forEachIndexed { index, label ->
                FloatingBottomBarItem(
                    selected =
                        index == pagerState.currentPage,
                    onClick = {
                        activateTab(index)
                    },
                    modifier =
                        Modifier.defaultMinSize(
                            minWidth = 76.dp
                        )
                ) {
                    Text(
                        label,
                        fontFamily = NDotFamily,
                        fontSize = 10.sp,
                        color =
                            LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
private fun ToySection(
    toyOn: Boolean,
    status: String,
    enabled: Boolean,
    connected: Boolean,
    onStartToy: () -> Unit,
    onStopToy: () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

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
                enabled &&
                    connected,
            onClick = {
                if (toyOn) {
                    onStopToy()
                } else {
                    onStartToy()
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

@Composable
private fun HomePage(
    context: Context,
    enabled: Boolean,
    connected: Boolean,
    beatSyncOn: Boolean,
    audioAccess: Boolean,
    mediaAccess: Boolean,
    playback: MediaPlaybackInfo?,
    brightness: Int,
    cameraSeconds: Int,
    beatEngine: String,
    onBrightness: (Int) -> Unit,
    onBeatSync: () -> Unit,
    onMediaAccess: () -> Unit,
    onBeatTest: () -> Unit,
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

        if (beatSyncOn) {
            Text(
                beatEngine,
                fontFamily = NDotFamily,
                fontSize = 9.sp,
                color =
                    colors.onSurface.copy(
                        alpha = .55f
                    )
            )
        }

        OutlinedAction(
            "TEST BEAT",
            enabled &&
                connected,
            onClick =
                onBeatTest
        )

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
                GlyphBackgroundService.playPattern(
                    context,
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
private fun PlayPage(
    context: Context,
    enabled: Boolean,
    connected: Boolean,
    metroOn: Boolean,
    bpm: Int,
    morseText: String,
    diceResult: Int,
    onBpm: (Int) -> Unit,
    onMorseText: (String) -> Unit,
    onMetronome: () -> Unit,
    onMorse: () -> Unit,
    onStop: () -> Unit,
    onClock: () -> Unit,
    onBattery: () -> Unit,
    onDice: () -> Unit,
    onBreathe: () -> Unit
) {
    val colors =
        MaterialTheme.colorScheme

    val ready =
        enabled && connected

    UserCard {
        Text(
            "MORSE CODER",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "Type it, dot flashes it.",
            fontFamily =
                NDotFamily,
            fontSize = 22.sp,
            color =
                colors.onSurface
        )

        OutlinedTextField(
            value = morseText,
            onValueChange = {
                onMorseText(it.take(40))
            },
            modifier =
                Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            PrimaryButton(
                text = "PLAY MORSE",
                modifier =
                    Modifier.weight(1f),
                enabled =
                    ready &&
                        morseText.isNotBlank(),
                onClick =
                    onMorse
            )

            PrimaryButton(
                text = "STOP",
                modifier =
                    Modifier.weight(1f),
                enabled = ready,
                onClick =
                    onStop
            )
        }
    }

    UserCard {
        Text(
            "METRONOME",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "$bpm BPM",
            fontFamily =
                NDotFamily,
            fontSize = 32.sp,
            color =
                colors.onSurface
        )

        Slider(
            value =
                bpm.toFloat(),
            onValueChange = {
                onBpm(it.toInt())
            },
            valueRange =
                30f..240f,
            enabled =
                enabled
        )

        PrimaryButton(
            text =
                if (metroOn) {
                    "STOP METRONOME"
                } else {
                    "START METRONOME"
                },
            enabled = ready,
            onClick =
                onMetronome
        )

        SmallNote(
            "Bright flash on every 4th beat. Keeps running in the background."
        )
    }

    UserCard {
        Text(
            "TALKING CLOCK",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "The dot tells the time in flashes.",
            fontFamily =
                NDotFamily,
            fontSize = 15.sp,
            color =
                colors.onSurface
        )

        PrimaryButton(
            text = "FLASH THE TIME",
            enabled = ready,
            onClick =
                onClock
        )
    }

    UserCard {
        Text(
            "BATTERY METER",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "Flashes tens, then ones.",
            fontFamily =
                NDotFamily,
            fontSize = 15.sp,
            color =
                colors.onSurface
        )

        PrimaryButton(
            text = "FLASH BATTERY LEVEL",
            enabled = ready,
            onClick =
                onBattery
        )
    }

    UserCard {
        Text(
            "DOT DICE",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            if (diceResult in 1..6) {
                diceResult.toString()
            } else {
                "—"
            },
            fontFamily =
                NDotFamily,
            fontSize = 32.sp,
            color =
                colors.onSurface
        )

        PrimaryButton(
            text = "ROLL THE DOT",
            enabled = ready,
            onClick =
                onDice
        )
    }

    UserCard {
        Text(
            "BREATHE 4-7-8",
            fontFamily =
                NDotFamily,
            fontSize = 10.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            "Dot swells as you inhale, sinks as you exhale.",
            fontFamily =
                NDotFamily,
            fontSize = 15.sp,
            color =
                colors.onSurface
        )

        PrimaryButton(
            text = "START BREATHING",
            enabled = ready,
            onClick =
                onBreathe
        )
    }
}

@Composable
private fun ComposerPage(
    context: Context,
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
                        "${step.durationMs} ms • " +
                            "${step.brightness * 100 / 4095}%",
                        fontFamily =
                            NDotFamily,
                        fontSize = 9.sp,
                        color =
                            colors.onSurface
                    )
                }

                Text(
                    "Brightness for this blink",
                    fontFamily =
                        NDotFamily,
                    fontSize = 10.sp,
                    color =
                        colors.onSurface
                )

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

                SmallNote(
                    "Changes the Glyph brightness of this blink only; it does not change your screen brightness."
                )

                Text(
                    "Duration",
                    fontFamily =
                        NDotFamily,
                    fontSize = 10.sp,
                    color =
                        colors.onSurface
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
                    GlyphBackgroundService.playPattern(
                        context,
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
    linkDetail: String,
    notifFlash: Boolean,
    lowBattery: Boolean,
    onAppEnabled: (Boolean) -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onChargingEnabled: (Boolean) -> Unit,
    onChargeTarget: (Int) -> Unit,
    onCameraSeconds: (Int) -> Unit,
    onNotifFlash: (Boolean) -> Unit,
    onLowBattery: (Boolean) -> Unit
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

        SettingRow(
            title =
                "Low battery alert",
            description =
                "Flash SOS when the battery runs low.",
            control = {
                Switch(
                    checked =
                        lowBattery,
                    onCheckedChange =
                        onLowBattery
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
            "GLYPH SERVICE",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Text(
            linkDetail,
            fontFamily =
                NDotFamily,
            fontSize = 9.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        SmallNote(
            "Stock Nothing OS Glyph service. " +
                "If this shows MISSING, install on a supported Nothing Phone."
        )
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

        SettingRow(
            title =
                "Notification flash",
            description =
                "Blink twice for new notifications. " +
                    "Paused while Toy, Music Sync or Metronome runs.",
            control = {
                Switch(
                    checked =
                        notifFlash,
                    onCheckedChange =
                        onNotifFlash
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
            // Bundled drawable: loads offline. Cropped to a true
            // circle so no square corners escape the border.
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.leon_profile),
                contentDescription = "Leon Sony",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(
                        1.dp,
                        colors.outline,
                        CircleShape
                    )
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

    UserCard {
        Text(
            "SPECIAL THANKS",
            fontFamily =
                NDotFamily,
            fontSize = 11.sp,
            color =
                colors.onSurface.copy(
                    alpha = .55f
                )
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "saran_archiz",
                fontFamily = NDotFamily,
                fontSize = 20.sp,
                color = colors.onSurface
            )

            Text(
                "Nothing Community",
                fontFamily = NDotFamily,
                fontSize = 9.sp,
                color = colors.onSurface.copy(alpha = .55f)
            )

            SmallNote(
                "For the general idea that sparked OneGlyph."
            )
        }

        HorizontalDivider(
            color =
                colors.outline.copy(
                    alpha = .6f
                )
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "creet_seneseca",
                fontFamily = NDotFamily,
                fontSize = 20.sp,
                color = colors.onSurface
            )

            Text(
                "Reddit",
                fontFamily = NDotFamily,
                fontSize = 9.sp,
                color = colors.onSurface.copy(alpha = .55f)
            )

            SmallNote(
                "For general testing and bug reports."
            )
        }

        SmallNote(
            "I thank you sincerely for your contributions ;)"
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
