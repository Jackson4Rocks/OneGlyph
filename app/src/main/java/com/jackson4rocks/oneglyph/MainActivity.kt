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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    var lights by remember { mutableStateOf(controller.availableLights()) }
    var selectedId by remember { mutableStateOf(controller.autoSelect()) }
    var brightness by remember { mutableIntStateOf(180) }
    var status by remember {
        mutableStateOf(
            if (lights.isEmpty()) {
                "No controllable lights found. OneGlyph needs the privileged lights permission and a Glyph-aware HAL."
            } else {
                "Ready"
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { controller.close() }
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
                        "Selected light: " + (selectedId?.toString() ?: "none"),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text("Brightness: " + brightness)
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { brightness = it.toInt() },
                        valueRange = 1f..255f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                controller.stopPattern()
                                controller.setBrightness(brightness)
                                status = "Glyph ON"
                            }
                        ) {
                            Text("ON")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                controller.stopPattern()
                                controller.off()
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
                            onClick = {
                                controller.blink(brightness)
                                status = "Blinking"
                            }
                        ) {
                            Text("Blink")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
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
                            onClick = {
                                controller.heartbeat(brightness)
                                status = "Heartbeat"
                            }
                        ) {
                            Text("Heartbeat")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
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
                    Text("Available lights", style = MaterialTheme.typography.titleLarge)

                    if (lights.isEmpty()) {
                        Text(
                            "No lights were returned by LightsManager.",
                            color = Color.Gray
                        )
                    } else {
                        lights.forEach { light ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ID " + light.id + "  •  ordinal " + light.ordinal,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (controller.selectLight(light.id)) {
                                            selectedId = light.id
                                            status = "Selected light " + light.id
                                        }
                                    }
                                ) {
                                    Text(
                                        if (selectedId == light.id) "Selected" else "Select"
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            lights = controller.availableLights()
                            selectedId = controller.autoSelect()
                            status = if (selectedId != null) {
                                "Detected light " + selectedId
                            } else {
                                "No suitable Glyph light detected"
                            }
                        }
                    ) {
                        Text("Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "OneGlyph uses Android's standard LightsManager path. The custom ROM exposes the Glyph driver through the light HAL.",
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun OneGlyphTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
