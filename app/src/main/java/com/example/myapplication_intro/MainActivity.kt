package com.example.myapplication_intro

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication_intro.ui.theme.MyApplication_introTheme

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
    // UI state that will drive recomposition
    var header by remember { mutableStateOf("Star Here") }
    var rotat by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var gyro by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var lastLine by remember { mutableStateOf("") }

    // Register / unregister the listener tied to this composable’s lifecycle
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
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        rotat = Triple(x, y, z)
//                        lastLine = "$ts,ACCEL,$x,$y,$z"
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        gyro = Triple(x, y, z)
//                        lastLine = "$ts,GYRO,$x,$y,$z"
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { /* no-op */ }
        }

        if (rotatSensor != null) {
            sensorManager.registerListener(
                listener, rotatSensor, 1000000
            )
        }
        if (gyroSensor != null) {
            sensorManager.registerListener(
                listener, gyroSensor, 1000000
            )
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Stateless UI call
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
    }
}

@Preview(showBackground = true, name = "Sensor UI Preview")
@Composable
private fun SensorScreenPreview() {
    MyApplication_introTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Fake values so you can sanity check layout and formatting
            SensorScreenContent(
                header = "Star Here",
                rotat = Triple(0.12f, 9.74f, -0.05f),
                gyro = Triple(0.01f, -0.03f, 0.02f),
                lastLine = "Time (ts): 1733352000000"
            )
        }
    }
}
