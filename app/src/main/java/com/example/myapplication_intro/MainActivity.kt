package com.example.myapplication_intro

import android.hardware.GeomagneticField // ✅ ADD
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
import kotlin.math.* // ✅ ADD

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

    // ✅ ADD: state for computed sky solution
    var altAzText by remember { mutableStateOf("") }
    var raDecText by remember { mutableStateOf("") }
    var mLabel by remember { mutableStateOf("") }

    // ✅ ADD: TEMP latitude/longitude (replace with GPS later)
    // Kent, WA-ish — replace with your real location or wire Fused Location
    val latDeg = 47.38
    val lonDeg = -122.24

    DisposableEffect(Unit) {
        val rotatSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nowMs = System.currentTimeMillis()
                val x = event.values.getOrNull(0) ?: 0f
                val y = event.values.getOrNull(1) ?: 0f
                val z = event.values.getOrNull(2) ?: 0f
                lastLine = "Time (ts): $nowMs"

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        rotat = Triple(x, y, z)

                        // ✅ EXACTLY HERE: compute pointing vector → Alt/Az → RA/Dec (J2000) → Messier label
                        val result = computeSkySolutionFromRotationVector(
                            rv = event.values,
                            nowMs = nowMs,
                            latDeg = latDeg,
                            lonDeg = lonDeg
                        )
                        altAzText = "Alt/Az: ${result.altDeg.format(1)}°, ${result.azDeg.format(1)}°"
                        raDecText = "RA/Dec (J2000): ${result.raHms}, ${result.decDms}"
                        mLabel = "Nearest Messier: ${result.nearestMessier}"

                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        gyro = Triple(x, y, z)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        if (rotatSensor != null) {
            sensorManager.registerListener(listener, rotatSensor, 300000)
        }
        if (gyroSensor != null) {
            sensorManager.registerListener(listener, gyroSensor, 300000)
        }

        onDispose { sensorManager.unregisterListener(listener) }
    }

    SensorScreenContent(
        header = header,
        rotat = rotat,
        gyro = gyro,
        lastLine = lastLine,
        altAzText = altAzText, // ✅ ADD
        raDecText = raDecText, // ✅ ADD
        mLabel = mLabel        // ✅ ADD
    )
}

@Composable
private fun SensorScreenContent(
    header: String,
    rotat: Triple<Float, Float, Float>,
    gyro: Triple<Float, Float, Float>,
    lastLine: String,
    altAzText: String,   // ✅ ADD
    raDecText: String,   // ✅ ADD
    mLabel: String       // ✅ ADD
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(header, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Text("Rotation Vector (x, y, z): ${rotat.first}, ${rotat.second}, ${rotat.third}")
        Text("Gyroscope     (x, y, z): ${gyro.first}, ${gyro.second}, ${gyro.third}")

        Spacer(Modifier.height(16.dp))
        Text(lastLine)

        // ✅ ADD: sky outputs
        Spacer(Modifier.height(12.dp))
        Text(altAzText)
        Text(raDecText)
        Text(mLabel)

        Spacer(Modifier.height(24.dp))

        // (optional) cute star from your previous ask
        Canvas(modifier = Modifier.size(100.dp).padding(top = 8.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) / 2.5f
            val innerRadius = radius / 2.5f
            val path = Path()
            val points = mutableListOf<Offset>()
            for (i in 0 until 10) {
                val angle = Math.PI / 2 + i * Math.PI / 5
                val r = if (i % 2 == 0) radius else innerRadius
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y - (r * sin(angle)).toFloat()
                points.add(Offset(x, y))
            }
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
            path.close()
            drawPath(path = path, color = Color(0xFFFFD700))
        }
    }
}

@Preview(showBackground = true, name = "Sensor UI Preview (with star)")
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
                lastLine = "Time (ts): 1733352000000",
                altAzText = "Alt/Az: 45.0°, 120.0°",
                raDecText = "RA/Dec (J2000): 00:42:44, +41:16:09",
                mLabel = "Nearest Messier: m31"
            )
        }
    }
}

/* ===================== SKY MATH BELOW (self-contained) ===================== */

private data class SkyResult(
    val altDeg: Double,
    val azDeg: Double,    // true-azimuth (0=N, 90=E)
    val raRadJ2000: Double,
    val decRadJ2000: Double,
    val raHms: String,
    val decDms: String,
    val nearestMessier: String
)

/** Main pipeline from rotation vector to J2000 + Messier */
private fun computeSkySolutionFromRotationVector(
    rv: FloatArray,
    nowMs: Long,
    latDeg: Double,
    lonDeg: Double,
    altitudeMeters: Float = 0f
): SkyResult {

    // 1) Device pointing vector in world ENU (East, North, Up)
    val enu = worldVectorFromRotationVector(rv)

    // 2) Convert world vector → Alt/Az (A from North, clockwise; h altitude)
    val azRadMag = atan2(enu.first, enu.second) // atan2(E, N)
    val altRad = asin(enu.third)                // arcsin(Up)

    // 3) Correct azimuth from magnetic to true using geomagnetic declination
    val field = GeomagneticField(
        latDeg.toFloat(), lonDeg.toFloat(), altitudeMeters, nowMs
    )
    val declRad = Math.toRadians(field.declination.toDouble()) // east-positive
    val azTrueRad = wrapRad(azRadMag + declRad)

    // 4) Time → LST (local sidereal time)
    val jd = unixMsToJulianDate(nowMs)
    val gmst = gmstRadians(jd)
    val lst = wrapRad(gmst + Math.toRadians(lonDeg)) // lon east-positive

    // 5) Alt/Az + Lat + LST → RA/Dec (of date)
    val latRad = Math.toRadians(latDeg)
    val (raRad, decRad) = altAzToRaDec(altRad, azTrueRad, latRad, lst)

    // 6) Precess (of date) → J2000 (use negative T interval to go back to J2000)
    val (raJ, decJ) = precessToJ2000(raRad, decRad, jd)

    // 7) Find nearest Messier (from a small sample; expand list for full coverage)
    val m = findNearestMessier(raJ, decJ)

    return SkyResult(
        altDeg = Math.toDegrees(altRad),
        azDeg = Math.toDegrees(azTrueRad),
        raRadJ2000 = raJ,
        decRadJ2000 = decJ,
        raHms = raRadToHMS(raJ),
        decDms = decRadToDMS(decJ),
        nearestMessier = m
    )
}

/** RotationVector → world ENU unit vector for the phone's "forward" direction (out of screen). */
private fun worldVectorFromRotationVector(rv: FloatArray): Triple<Double, Double, Double> {
    val R = FloatArray(9)
    SensorManager.getRotationMatrixFromVector(R, rv)
    // R transforms device coords → world ENU.
    // Forward (device -Z) in world = R * (0, 0, -1) = (-R[2], -R[5], -R[8])
    val e = (-R[2]).toDouble()
    val n = (-R[5]).toDouble()
    val u = (-R[8]).toDouble()
    // Normalize (usually already ~1)
    val norm = sqrt(e*e + n*n + u*u)
    return Triple(e / norm, n / norm, u / norm)
}

/** Alt/Az + Lat + LST → RA/Dec (of date). Az measured from North through East. */
private fun altAzToRaDec(alt: Double, az: Double, lat: Double, lst: Double): Pair<Double, Double> {
    val sinDec = sin(alt) * sin(lat) + cos(alt) * cos(lat) * cos(az)
    val dec = asin(sinDec)

    val y = -sin(az) * cos(alt)
    val x =  sin(alt) * cos(lat) - cos(alt) * sin(lat) * cos(az)
    val H = atan2(y, x) // hour angle

    val ra = wrapRad(lst - H)
    return Pair(ra, dec)
}

/** Precess from 'of date' to J2000 using IAU 1976-like angles (Meeus-style). */
private fun precessToJ2000(ra: Double, dec: Double, jd: Double): Pair<Double, Double> {
    // Interval centuries FROM target (J2000) TO date is T = (jd-2451545)/36525.
    // To go FROM date back TO J2000, use -T.
    val T = -(jd - 2451545.0) / 36525.0

    val zetaArcsec = 2306.2181 * T + 0.30188 * T*T + 0.017998 * T*T*T
    val zArcsec    = 2306.2181 * T + 1.09468 * T*T + 0.018203 * T*T*T
    val thetaArcsec= 2004.3109 * T - 0.42665 * T*T - 0.041833 * T*T*T

    val zeta = Math.toRadians(zetaArcsec / 3600.0)
    val z    = Math.toRadians(zArcsec / 3600.0)
    val th   = Math.toRadians(thetaArcsec / 3600.0)

    // Spherical → Cartesian
    val x0 = cos(dec) * cos(ra)
    val y0 = cos(dec) * sin(ra)
    val z0 = sin(dec)

    // Apply R3(-z) * R2(theta) * R3(-zeta)
    val x1 =  x0 * cos(-zeta) + y0 * sin(-zeta)
    val y1 = -x0 * sin(-zeta) + y0 * cos(-zeta)
    val z1 =  z0

    val x2 =  x1 * cos(th) + z1 * sin(th)
    val y2 =  y1
    val z2 = -x1 * sin(th) + z1 * cos(th)

    val x3 =  x2 * cos(-z) + y2 * sin(-z)
    val y3 = -x2 * sin(-z) + y2 * cos(-z)
    val z3 =  z2

    val raJ = wrapRad(atan2(y3, x3))
    val decJ = asin(z3)
    return Pair(raJ, decJ)
}

/** Find nearest Messier from a tiny demo list. Expand this with full M1–M110 later. */
private data class MObj(val tag: String, val raRad: Double, val decRad: Double, val name: String)
private val messierMini = listOf(
    // NOTE: rough J2000 positions; good enough for demo
    MObj("m31",  degToRad( 10.6847 ), degToRad( 41.2690 ), "Andromeda Galaxy"), // Andromeda Galaxy
    MObj("m42",  hToRad(5.0 + 35.0/60.0), degToRad(-5.45 ), "Orion Nebula"), // Orion Nebula
    MObj("m45",  hToRad(3.0 + 47.0/60.0), degToRad(24.1  ), "Pleiades"), // Pleiades
    MObj("m13",  hToRad(16.0 + 41.0/60.0), degToRad(36.46), "Hecules Cluster"), // Hercules Cluster
    MObj("m57",  hToRad(18.0 + 53.0/60.0), degToRad(33.03), "Ring Nebula"), // Ring Nebula
    MObj("m1",   hToRad(5.0 + 34.0/60.0), degToRad(22.01), "Crab Nebula"),  // Crab Nebula
    MObj("m51",  hToRad(13.0 + 29.0/60.0), degToRad(47.20), "Whirlpool"), // Whirlpool
    MObj("m33",  hToRad(1.0 + 33.0/60.0), degToRad(30.66), "Triangulum")   // Triangulum
)

private fun findNearestMessier(raJ: Double, decJ: Double): String {
    var best = "—"
    var bestAng = Double.POSITIVE_INFINITY
    for (m in messierMini) {
        val ang = angularSeparation(raJ, decJ, m.raRad, m.decRad)
        if (ang < bestAng) {
            bestAng = ang
            best = m.tag
        }
    }
    // Optional: only accept if within N degrees
    return if (Math.toDegrees(bestAng) <= 5.0) best else "—"
}

/* ===================== helpers ===================== */

private fun unixMsToJulianDate(ms: Long): Double = ms / 86400000.0 + 2440587.5

private fun gmstRadians(jd: Double): Double {
    val T = (jd - 2451545.0) / 36525.0
    // Meeus-like GMST (deg)
    var gmstDeg = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
            0.000387933 * T*T - T*T*T / 38710000.0
    gmstDeg = ((gmstDeg % 360.0) + 360.0) % 360.0
    return Math.toRadians(gmstDeg)
}

private fun wrapRad(a: Double): Double {
    var x = a % (2 * Math.PI)
    if (x < 0) x += 2 * Math.PI
    return x
}

private fun angularSeparation(ra1: Double, dec1: Double, ra2: Double, dec2: Double): Double {
    // Vincenty formula on unit sphere
    val s = sin((dec2 - dec1) / 2)
    val t = sin((ra2 - ra1) / 2)
    val h = s*s + cos(dec1) * cos(dec2) * t*t
    return 2 * asin(min(1.0, sqrt(h)))
}

private fun raRadToHMS(ra: Double): String {
    val hours = Math.toDegrees(ra) / 15.0
    val h = floor(hours).toInt()
    val m = floor((hours - h) * 60.0).toInt()
    val s = ((hours - h) * 60.0 - m) * 60.0
    return String.format("%02d:%02d:%02.0f", h, m, s)
}

private fun decRadToDMS(dec: Double): String {
    val sign = if (dec >= 0) "+" else "-"
    val absDeg = abs(Math.toDegrees(dec))
    val d = floor(absDeg).toInt()
    val m = floor((absDeg - d) * 60.0).toInt()
    val s = ((absDeg - d) * 60.0 - m) * 60.0
    return String.format("%s%02d:%02d:%02.0f", sign, d, m, s)
}

private fun degToRad(d: Double) = Math.toRadians(d)
private fun hToRad(h: Double) = Math.toRadians(h * 15.0)
private fun Double.format(n: Int) = "%.${n}f".format(this)
