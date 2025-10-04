package com.example.myapplication_intro

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication_intro.ui.theme.MyApplication_introTheme
import java.io.FileWriter

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var gyro: Sensor? = null
    private lateinit var accelText: TextView
    private lateinit var gyroText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplication_introTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OutputData("timestamp_ms,sensor,x,y,z")
                }
            }
        }
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    }

    override fun onSensorChanged(event: SensorEvent) {
        val ts = System.currentTimeMillis()
        val sensorName = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "ACCEL"
            Sensor.TYPE_GYROSCOPE -> "GYRO"
            else -> event.sensor.name
        }
        val x = event.values.getOrNull(0) ?: 0f
        val y = event.values.getOrNull(1) ?: 0f
        val z = event.values.getOrNull(2) ?: 0f

        accelText.text = string.format("x: %f, y: %f, z: %f",x,y,z)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyro,  SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }
}
@Composable
fun OutputData(message: String, modifier: Modifier = Modifier) {
    Surface(color = Color.Cyan) {
        Text(
            text = message,
            modifier = modifier.padding(1.dp)
        )
    }
}

@Preview(showBackground = false)
@Composable
fun GreetingPreview() {
    MyApplication_introTheme {
        OutputData("timestamp_ms,sensor,x,y,z")
    }
}