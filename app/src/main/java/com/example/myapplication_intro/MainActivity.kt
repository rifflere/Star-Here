package com.example.myapplication_intro

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication_intro.ui.theme.MyApplication_introTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        setContent {
            MyApplication_introTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SensorScreen(sensorManager = sensorManager)
                }
            }
        }
    }
}

@Composable
fun SensorScreen(sensorManager: SensorManager) {
    var header by remember { mutableStateOf("Star Here") }
    var rotat by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var gyro by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var lastLine by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val rotatSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ts = System.currentTimeMillis()
                val x = event.values.getOrNull(0) ?: 0f
                val y = event.values.getOrNull(1) ?: 0f
                val z = event.values.getOrNull(2) ?: 0f
                lastLine = "Time (ts): $ts"

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> rotat = Triple(x, y, z)
                    Sensor.TYPE_GYROSCOPE -> gyro = Triple(x, y, z)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        if (rotatSensor != null) {
            sensorManager.registerListener(listener, rotatSensor, 1000000)
        }
        if (gyroSensor != null) {
            sensorManager.registerListener(listener, gyroSensor, 1000000)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    SensorScreenContent(
        header = header,
        rotat = rotat,
        gyro = gyro,
        lastLine = lastLine
    )
}

@Composable
private fun SensorScreenContent(
    header: String,
    rotat: Triple<Float, Float, Float>,
    gyro: Triple<Float, Float, Float>,
    lastLine: String
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(header, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Text("Rotation Vector (x, y, z): ${rotat.first}, ${rotat.second}, ${rotat.third}")
        Text("Gyroscope     (x, y, z): ${gyro.first}, ${gyro.second}, ${gyro.third}")

        Spacer(Modifier.height(16.dp))
        Text(lastLine)

        Spacer(Modifier.height(24.dp))

        // ⭐ Star drawing
        Canvas(modifier = Modifier
            .size(100.dp)
            .padding(top = 8.dp)) {

            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) / 2.5f
            val innerRadius = radius / 2.5f

            val path = Path()
            val points = mutableListOf<Offset>()

            // Calculate 10 alternating outer and inner points
            for (i in 0 until 10) {
                val angle = PI / 2 + i * PI / 5
                val r = if (i % 2 == 0) radius else innerRadius
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y - (r * sin(angle)).toFloat()
                points.add(Offset(x, y))
            }

            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            path.close()

            drawPath(path = path, color = Color(0xFFFFD700)) // gold-yellow star
        }
    }
}

@Preview(showBackground = true, name = "Sensor UI Preview with Star")
@Composable
private fun SensorScreenPreview() {
    MyApplication_introTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SensorScreenContent(
                header = "Star Here",
                rotat = Triple(0.12f, 9.74f, -0.05f),
                gyro = Triple(0.01f, -0.03f, 0.02f),
                lastLine = "Time (ts): 1733352000000"
            )
        }
    }
}
