package com.jackson4rocks.oneglyph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OneGlyphTheme {
                OneGlyphApp()
            }
        }
    }
}

@Composable
private fun OneGlyphApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { GlyphController(context) }

    var brightness by remember { mutableIntStateOf(2048) }
    var status by remember { mutableStateOf("Connecting to Stock Glyph…") }
    var connected by remember { mutableStateOf(false) }

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
            if (!connected && !status.contains("error", ignoreCase = true)) {
                status = "Connecting to Stock Glyph…"
            }
            delay(750)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "OneGlyph",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Make the one dot do more.",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyLarge
            )

            Card(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Glyph control", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Selected light: Stock Glyph dot",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Device: " + controller.targetDevice(),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text("Brightness: " + brightness + " / 4096")
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { brightness = it.toInt() },
                        valueRange = 0f..4096f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = {
                                controller.setBrightness(brightness)
                                status = "Glyph ON"
                            }
                        ) {
                            Text("ON")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = {
                                controller.stopPattern()
                                status = "Glyph OFF"
                            }
                        ) {
                            Text("OFF")
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Effects", style = MaterialTheme.typography.titleLarge)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = {
                                controller.blink(brightness)
                                status = "Blinking"
                            }
                        ) {
                            Text("Blink")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = {
                                controller.pulse(brightness)
                                status = "Pulsing"
                            }
                        ) {
                            Text("Pulse")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = {
                                controller.heartbeat(brightness)
                                status = "Heartbeat"
                            }
                        ) {
                            Text("Heartbeat")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = {
                                controller.stopPattern()
                                status = "Effect stopped"
                            }
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(28.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Stock integration", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "OneGlyph talks to Nothing OS's GlyphService instead of the ordinary Android LightsManager list. The stock service owns the privileged Glyph light session.",
                        color = Color.Gray
                    )
                    Text(
                        "The Phone (3a) Lite has one physical Glyph Light dot, so this build exposes that dot directly instead of pretending there are multiple lights.",
                        color = Color.Gray
                    )
                    TextButton(
                        onClick = {
                            controller.connect()
                            status = "Reconnecting to Stock Glyph…"
                        }
                    ) {
                        Text("Reconnect")
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = if (connected) Color.LightGray else Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun OneGlyphTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
