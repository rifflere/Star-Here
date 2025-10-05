package com.example.myapplication_intro

import android.annotation.SuppressLint
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication_intro.ui.theme.MyApplication_introTheme
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
    var TimeData by remember { mutableStateOf("") }
    var gpsData by remember { mutableStateOf("") }


    // state for computed sky solution
    var altAzText by remember { mutableStateOf("") }
    var raDecText by remember { mutableStateOf("") }
    var mLabel by remember { mutableStateOf(messierDisplayData("-","-","-")) }
    var iLabel by remember { mutableStateOf(irsaData("-","-")) }

    // TEMP latitude/longitude (replace with GPS later)
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
                TimeData = "$nowMs"

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        rotat = Triple(x, y, z)

                        // compute pointing vector → Alt/Az → RA/Dec (J2000) → Messier label
                        val result = computeSkySolutionFromRotationVector(
                            rv = event.values,
                            nowMs = nowMs,
                            latDeg = latDeg,
                            lonDeg = lonDeg
                        )
                        altAzText = "Alt/Az: ${result.altDeg.format(1)}°, ${result.azDeg.format(1)}°"
                        raDecText = "RA/Dec (J2000): ${result.raHms}, ${result.decDms}"
                        mLabel = result.nearestMessier//"Nearest Messier: ${result.nearestMessier.name} \n${result.nearestMessier.tag} \n${result.nearestMessier.link}"
                        gpsData = "Lat: $latDeg\n\tLon: $lonDeg"
                        iLabel = result.iLabel
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
        TimeData = TimeData,
        gpsData = gpsData,
        altAzText = altAzText,
        raDecText = raDecText,
        mLabel = mLabel,
        iLabel = iLabel
    )
}

@Composable
private fun SensorScreenContent(
    header: String,
    rotat: Triple<Float, Float, Float>,
    gyro: Triple<Float, Float, Float>,
    TimeData: String,
    gpsData: String,
    altAzText: String,
    raDecText: String,
    mLabel: messierDisplayData,
    iLabel: irsaData
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("\n")
        Text(header, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        Text("Local Sensors:", style = MaterialTheme.typography.titleMedium)
        Text("Rotation Vector (x, y, z): \n\t${rotat.first}, ${rotat.second}, ${rotat.third}")
        Text("Gyroscope     (x, y, z): \n\t${gyro.first}, ${gyro.second}, ${gyro.third}")
        Text("Time (ms): \n\t${TimeData}")
        Text("GPS Location (deg): \n\t${gpsData}")
        // sky outputs
        Spacer(Modifier.height(12.dp))
        Text("Calculated frame:", style = MaterialTheme.typography.titleMedium)
        Text(altAzText)
        Text(raDecText)
        Spacer(Modifier.height(16.dp))
        Text("NASA information on location:", style = MaterialTheme.typography.titleMedium)
        Text(buildAnnotatedString {
            append("Nearest Messier: ")
            withLink(
                LinkAnnotation.Url(
                    url = mLabel.link,
                    TextLinkStyles(style = SpanStyle(color = Color.Blue))
                )
            ) {
                append("${mLabel.tag}: ${mLabel.name}")
            }
        }
        )
        Text(buildAnnotatedString {
            append("IRSA data: ")
            withLink(
                LinkAnnotation.Url(
                    url = iLabel.link,
                    TextLinkStyles(style = SpanStyle(color = Color.Blue))
                )
            ) {
                append(iLabel.link)
            }
        })


        Spacer(Modifier.height(24.dp))

        // star graphic
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
                TimeData = "Time (ts): 1733352000000",
                gpsData = "Lat: 41, Long: 72",
                altAzText = "Alt/Az: 45.0°, 120.0°",
                raDecText = "RA/Dec (J2000): 00:42:44, +41:16:09",
                mLabel = messierDisplayData("-", "-", "-"),
                iLabel = irsaData("-", "-")
            )
        }
    }
}

/* ===================== SKY MATH BELOW (self-contained) ===================== */

private data class irsaData(
    val coord : String,
    val link: String
)

private data class messierDisplayData(
    val tag: String,
    val name: String,
    val link: String
)
private data class SkyResult(
    val altDeg: Double,
    val azDeg: Double,    // true-azimuth (0=N, 90=E)
    val raRadJ2000: Double,
    val decRadJ2000: Double,
    val raHms: String,
    val decDms: String,
    val nearestMessier: messierDisplayData,
    val iLabel: irsaData
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

    // Build IRSA link from raJ and decJ
    val iLabel = irsaData("$raJ, $decJ", "https://irsa.ipac.caltech.edu/cgi-bin/Radar/nph-discovery?objstr=${raJ}${decJ}%20Equ%20J2000&mode=cone&radius=5&radunits=arcmin")

    /*
    * https://irsa.ipac.caltech.edu/cgi-bin/Radar/nph-discovery?objstr=00%3A42%3A44.3%20%2B41%3A16%3A08%20Equ%20J2000&mode=cone&radius=5&radunits=arcmin
    * */

    return SkyResult(
        altDeg = Math.toDegrees(altRad),
        azDeg = Math.toDegrees(azTrueRad),
        raRadJ2000 = raJ,
        decRadJ2000 = decJ,
        raHms = raRadToHMS(raJ),
        decDms = decRadToDMS(decJ),
        nearestMessier = m,
        iLabel = iLabel
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

/** Find nearest Messier. */
private data class MObj(val tag: String, val raRad: Double, val decRad: Double, val name: String, val link: String)
private val messierMini = listOf(
    // NOTE: rough J2000 positions; good enough for demo
    MObj("m1",  degToRad( 83.6331 ), degToRad( 22.0145 ), "Crab Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-1/"), // Crab Nebula, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-1/
    MObj("m2",  degToRad( 323.3625 ), degToRad( -0.8233 ), "Globular Cluster in Aquarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-2/"), // Globular Cluster in Aquarius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-2/
    MObj("m3",  degToRad( 205.5484 ), degToRad( 28.3772 ), "Globular Cluster in Canes Venatici", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-3/"), // Globular Cluster in Canes Venatici, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-3/
    MObj("m4",  degToRad( 245.8967 ), degToRad( -26.5258 ), "Globular Cluster in Scorpius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-4/"), // Globular Cluster in Scorpius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-4/
    MObj("m5",  degToRad( 229.6383 ), degToRad( 2.0819 ), "Globular Cluster in Serpens", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-5/"), // Globular Cluster in Serpens, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-5/
    MObj("m6",  degToRad( 265.0800 ), degToRad( -32.2525 ), "Butterfly Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-6/"), // Butterfly Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-6/
    MObj("m7",  degToRad( 268.4625 ), degToRad( -34.7933 ), "Ptolemy Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-7/"), // Ptolemy Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-7/
    MObj("m8",  degToRad( 270.9250 ), degToRad( -24.3800 ), "Lagoon Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-8/"), // Lagoon Nebula, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-8/
    MObj("m9",  degToRad( 262.8042 ), degToRad( -18.5167 ), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-9/"), // Globular Cluster in Ophiuchus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-9/
    MObj("m10", degToRad( 254.2875 ), degToRad( -4.0997 ), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-10/"), // Globular Cluster in Ophiuchus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-10/
    MObj("m11", degToRad( 282.7708 ), degToRad( -6.2700 ), "Wild Duck Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-11/"), // Wild Duck Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-11/
    MObj("m12", degToRad( 251.8083 ), degToRad( -1.9483 ), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-12/"), // Globular Cluster in Ophiuchus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-12/
    MObj("m13", degToRad( 250.4217 ), degToRad( 36.4611 ), "Great Hercules Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-13/"), // Great Hercules Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-13/
    MObj("m14", degToRad( 264.4000 ), degToRad( -3.2458 ), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-14/"), // Globular Cluster in Ophiuchus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-14/
    MObj("m15", degToRad( 322.4938 ), degToRad( 12.1667 ), "Pegasus Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-15/"), // Pegasus Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-15/
    MObj("m16", degToRad( 274.7000 ), degToRad( -13.8067 ), "Eagle Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-16/"), // Eagle Nebula, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-16/
    MObj("m17", degToRad( 275.2000 ), degToRad( -16.1750 ), "Omega Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-17/"), // Omega Nebula, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-17/
    MObj("m18", degToRad( 275.4750 ), degToRad( -17.1042 ), "Open Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-18/"), // Open Cluster in Sagittarius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-18/
    MObj("m19", degToRad( 255.6579 ), degToRad( -26.2678 ), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-19/"), // Globular Cluster in Ophiuchus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-19/
    MObj("m20", degToRad( 270.6554 ), degToRad( -23.0144 ), "Trifid Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-20/"), // Trifid Nebula, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-20/
    MObj("m21", degToRad( 270.6500 ), degToRad( -22.4861 ), "Open Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-21/"), // Open Cluster in Sagittarius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-21/
    MObj("m22", degToRad( 279.1000 ), degToRad( -23.9047 ), "Sagittarius Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-22/"), // Sagittarius Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-22/
    MObj("m23", degToRad( 269.1500 ), degToRad( -19.0167 ), "Open Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-23/"), // Open Cluster in Sagittarius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-23/
    MObj("m24", degToRad( 273.5583 ), degToRad( -18.4633 ), "Sagittarius Star Cloud", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-24/"), // Sagittarius Star Cloud, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-24/
    MObj("m25", degToRad( 279.1000 ), degToRad( -19.2500 ), "Open Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-25/"), // Open Cluster in Sagittarius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-25/
    MObj("m26", degToRad( 281.2500 ), degToRad( -9.3833 ), "Open Cluster in Scutum", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-26/"), // Open Cluster in Scutum, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-26/
    MObj("m27", degToRad( 299.9000 ), degToRad( 22.7219 ), "Dumbbell Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-27/"), // Dumbbell Nebula, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-27/
    MObj("m28", degToRad( 276.1375 ), degToRad( -24.8697 ), "Globular Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-28/"), // Globular Cluster in Sagittarius, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-28/
    MObj("m29", degToRad( 305.9833 ), degToRad( 38.5333 ), "Open Cluster in Cygnus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-29/"), // Open Cluster in Cygnus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-29/
    MObj("m30", degToRad( 325.0925 ), degToRad( -23.1797 ), "Globular Cluster in Capricornus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-30/"), // Globular Cluster in Capricornus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-30/
    MObj("m31", degToRad( 10.6847 ), degToRad( 41.2690 ), "Andromeda Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-31/"), // Andromeda Galaxy
    MObj("m32", degToRad( 10.6743 ), degToRad( 40.8652 ), "Dwarf Elliptical Galaxy in Andromeda", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-32/"), // Dwarf Elliptical Galaxy in Andromeda
    MObj("m33", degToRad( 23.4621 ), degToRad( 30.6602 ), "Triangulum Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-33/"), // Triangulum Galaxy
    MObj("m34", degToRad( 40.5000 ), degToRad( 42.7833 ), "Open Cluster in Perseus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-34/"), // Open Cluster in Perseus
    MObj("m35", degToRad( 92.2250 ), degToRad( 24.3333 ), "Open Cluster in Gemini", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-35/"), // Open Cluster in Gemini
    MObj("m36", degToRad( 84.0833 ), degToRad( 34.1367 ), "Open Cluster in Auriga", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-36/"), // Open Cluster in Auriga
    MObj("m37", degToRad( 88.0750 ), degToRad( 32.5533 ), "Open Cluster in Auriga", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-37/"), // Open Cluster in Auriga
    MObj("m38", degToRad( 82.1883 ), degToRad( 35.8500 ), "Open Cluster in Auriga", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-38/"), // Open Cluster in Auriga
    MObj("m39", degToRad( 322.9958 ), degToRad( 48.4333 ), "Open Cluster in Cygnus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-39/"), // Open Cluster in Cygnus
    MObj("m40", degToRad( 183.7792 ), degToRad( 58.0833 ), "Double Star in Ursa Major", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-40/"), // Double Star in Ursa Major
    MObj("m41", degToRad( 101.5000 ), degToRad( -20.7500 ), "Open Cluster in Canis Major", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-41/"), // Open Cluster in Canis Major
    MObj("m42", degToRad( 83.8221 ), degToRad( -5.3911 ), "Orion Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-42/"), // Orion Nebula
    MObj("m43", degToRad( 83.8750 ), degToRad( -5.2667 ), "De Mairan’s Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-43/"), // De Mairan’s Nebula
    MObj("m44", degToRad( 130.0250 ), degToRad( 19.9833 ), "Beehive Cluster (Praesepe)", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-44/"), // Beehive Cluster
    MObj("m45", degToRad( 56.7500 ), degToRad( 24.1167 ), "Pleiades", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-45/"), // Pleiades
    MObj("m46", degToRad( 114.1500 ), degToRad( -14.8167 ), "Open Cluster in Puppis", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-46/"), // Open Cluster in Puppis
    MObj("m47", degToRad( 114.1500 ), degToRad( -14.4833 ), "Open Cluster in Puppis", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-47/"), // Open Cluster in Puppis
    MObj("m48", degToRad( 123.1500 ), degToRad( -5.8000 ), "Open Cluster in Hydra", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-48/"), // Open Cluster in Hydra
    MObj("m49", degToRad( 187.4458 ), degToRad( 8.0000 ), "Elliptical Galaxy in Virgo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-49/"), // Elliptical Galaxy in Virgo
    MObj("m50", degToRad( 105.7333 ), degToRad( -8.3333 ), "Open Cluster in Monoceros", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-50/"), // Open Cluster in Monoceros
    MObj("m51", degToRad( 202.4708 ), degToRad( 47.1953 ), "Whirlpool Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-51/"), // Whirlpool Galaxy
    MObj("m52", degToRad( 351.2000 ), degToRad( 61.5833 ), "Open Cluster in Cassiopeia", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-52/"), // Open Cluster in Cassiopeia
    MObj("m53", degToRad( 198.2308 ), degToRad( 18.1683 ), "Globular Cluster in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-53/"), // Globular Cluster in Coma Berenices
    MObj("m54", degToRad( 283.7625 ), degToRad( -30.4797 ), "Globular Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-54/"), // Globular Cluster in Sagittarius
    MObj("m55", degToRad( 294.9983 ), degToRad( -30.9647 ), "Globular Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-55/"), // Globular Cluster in Sagittarius
    MObj("m56", degToRad( 289.1479 ), degToRad( 30.1833 ), "Globular Cluster in Lyra", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-56/"), // Globular Cluster in Lyra
    MObj("m57", degToRad( 283.3963 ), degToRad( 33.0283 ), "Ring Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-57/"), // Ring Nebula
    MObj("m58", degToRad( 189.4300 ), degToRad( 11.8183 ), "Spiral Galaxy in Virgo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-58/"), // Spiral Galaxy in Virgo
    MObj("m59", degToRad( 190.4908 ), degToRad( 11.6469 ), "Elliptical Galaxy in Virgo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-59/"), // Elliptical Galaxy in Virgo
    MObj("m60", degToRad( 190.9167 ), degToRad( 11.5500 ), "Elliptical Galaxy in Virgo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-60/"), // Elliptical Galaxy in Virgo
    MObj("m61", degToRad( 185.4788 ), degToRad( 4.4733 ), "Spiral Galaxy in Virgo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-61/"), // Spiral Galaxy in Virgo
    MObj("m62", degToRad( 255.3000 ), degToRad( -30.1111 ), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-62/"), // Globular Cluster in Ophiuchus
    MObj("m63", degToRad( 198.9567 ), degToRad( 42.0292 ), "Sunflower Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-63/"), // Sunflower Galaxy
    MObj("m64", degToRad( 194.1829 ), degToRad( 21.6825 ), "Black Eye Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-64/"), // Black Eye Galaxy
    MObj("m65", degToRad( 169.7250 ), degToRad( 13.0925 ), "Spiral Galaxy in Leo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-65/"), // Spiral Galaxy in Leo
    MObj("m66", degToRad( 170.0625 ), degToRad( 12.9919 ), "Spiral Galaxy in Leo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-66/"), // Spiral Galaxy in Leo
    MObj("m67", degToRad( 132.8250 ), degToRad( 11.8167 ), "Open Cluster in Cancer", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-67/"), // Open Cluster in Cancer
    MObj("m68", degToRad( 189.8667 ), degToRad( -26.7447 ), "Globular Cluster in Hydra", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-68/"), // Globular Cluster in Hydra
    MObj("m69", degToRad( 277.8463 ), degToRad( -32.3483 ), "Globular Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-69/"), // Globular Cluster in Sagittarius
    MObj("m70", degToRad( 280.8033 ), degToRad( -32.2922 ), "Globular Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-70/"), // Globular Cluster in Sagittarius
    MObj("m71", degToRad( 298.4433 ), degToRad( 18.7797 ), "Globular Cluster in Sagitta", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-71/"), // Globular Cluster in Sagitta
    MObj("m72", degToRad( 313.3667 ), degToRad( -12.5378 ), "Globular Cluster in Aquarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-72/"), // Globular Cluster in Aquarius
    MObj("m73", degToRad( 314.7500 ), degToRad( -12.6333 ), "Asterism in Aquarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-73/"), // Asterism in Aquarius
    MObj("m74", degToRad( 24.1742 ), degToRad( 15.7833 ), "Phantom Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-74/"), // Phantom Galaxy
    MObj("m75", degToRad( 302.4842 ), degToRad( -21.9225 ), "Globular Cluster in Sagittarius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-75/"), // Globular Cluster in Sagittarius
    MObj("m76", degToRad( 25.5917 ), degToRad( 51.5733 ), "Little Dumbbell Nebula", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-76/"), // Little Dumbbell Nebula
    MObj("m77", degToRad( 40.6692 ), degToRad( 0.0133 ), "Cetus A Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-77/"), // Cetus A Galaxy
    MObj("m78", degToRad( 86.7000 ), degToRad( 0.0500 ), "Diffuse Nebula in Orion", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-78/"), // Diffuse Nebula in Orion
    MObj("m79", degToRad( 81.0450 ), degToRad( -24.5242 ), "Globular Cluster in Lepus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-79/"), // Globular Cluster in Lepus
    MObj("m80", degToRad( 244.2600 ), degToRad( -22.9750 ), "Globular Cluster in Scorpius", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-80/"), // Globular Cluster in Scorpius
    MObj("m81", degToRad( 148.8883 ), degToRad( 69.0653 ), "Bode’s Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-81/"), // Bode’s Galaxy
    MObj("m82", degToRad( 148.9683 ), degToRad( 69.6797 ), "Cigar Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-82/"), // Cigar Galaxy
    MObj("m83", degToRad( 204.2533 ), degToRad( -29.8650 ), "Southern Pinwheel Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-83/"), // Southern Pinwheel Galaxy
    MObj("m84", degToRad( 186.2650 ), degToRad( 12.8875 ), "Elliptical Galaxy in Virgo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-84/"), // Elliptical Galaxy in Virgo
    MObj("m85", degToRad(13.1464), degToRad(18.1847), "Lenticular Galaxy in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-85/"), // Messier 85 – Lenticular Galaxy in Coma Berenices, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-85/
    MObj("m86", degToRad(12.8694), degToRad(12.9247), "Elliptical Galaxy in Virgo Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-86/"), // Messier 86 – Elliptical Galaxy in Virgo Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-86/
    MObj("m87", degToRad(12.3911), degToRad(12.3911), "(Virgo A) – Supermassive Black Hole Galaxy", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-87/"), // Messier 87 (Virgo A) – Supermassive Black Hole Galaxy, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-87/
    MObj("m88", degToRad(12.3750), degToRad(13.1583), "Spiral Galaxy in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-88/"), // Messier 88 – Spiral Galaxy in Coma Berenices, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-88/
    MObj("m89", degToRad(12.3583), degToRad(12.5572), "Elliptical Galaxy in Virgo Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-89/"), // Messier 89 – Elliptical Galaxy in Virgo Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-89/
    MObj("m90", degToRad(12.3486), degToRad(13.1611), "Spiral Galaxy in Virgo Cluster", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-90/"), // Messier 90 – Spiral Galaxy in Virgo Cluster, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-90/
    MObj("m91", degToRad(13.1464), degToRad(18.1847), "Barred Spiral Galaxy in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-91/"), // Messier 91 – Barred Spiral Galaxy in Coma Berenices, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-91/
    MObj("m92", degToRad(15.0847), degToRad(43.2222), "Globular Cluster in Hercules", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-92/"), // Messier 92 – Globular Cluster in Hercules, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-92/
    MObj("m93", degToRad(13.6250), degToRad(-23.8700), "Open Cluster in Puppis", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-93/"), // Messier 93 – Open Cluster in Puppis, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-93/
    MObj("m94", degToRad(12.8722), degToRad(41.7222), "Spiral Galaxy in Canes Venatici", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-94/"), // Messier 94 – Spiral Galaxy in Canes Venatici, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-94/
    MObj("m95", degToRad(10.7000), degToRad(11.7000), "Lenticular Galaxy in Leo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-95/"), // Messier 95 – Lenticular Galaxy in Leo, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-95/
    MObj("m96", degToRad(10.7000), degToRad(11.7000), "Spiral Galaxy in Leo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-96/"), // Messier 96 – Spiral Galaxy in Leo, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-96/
    MObj("m97", degToRad(11.5000), degToRad(55.0000), "Planetary Nebula (Owl Nebula)", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-97/"), // Messier 97 – Planetary Nebula (Owl Nebula), https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-97/
    MObj("m98", degToRad(12.0000), degToRad(14.0000), "Spiral Galaxy in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-98/"), // Messier 98 – Spiral Galaxy in Coma Berenices, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-98/
    MObj("m99", degToRad(12.0000), degToRad(14.0000), "Spiral Galaxy in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-99/"), // Messier 99 – Spiral Galaxy in Coma Berenices, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-99/
    MObj("m100", degToRad(12.0000), degToRad(14.0000), "Spiral Galaxy in Coma Berenices", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-100/"), // Messier 100 – Spiral Galaxy in Coma Berenices, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-100/
    MObj("m101", degToRad(12.0000), degToRad(14.0000), "Spiral Galaxy in Ursa Major", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-101/"), // Messier 101 – Spiral Galaxy in Ursa Major, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-101/
    MObj("m102", degToRad(12.0000), degToRad(14.0000), "Lenticular Galaxy in Draco", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-102/"), // Messier 102 – Lenticular Galaxy in Draco, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-102/
    MObj("m103", degToRad(12.0000), degToRad(14.0000), "Open Cluster in Cassiopeia", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-103/"), // Messier 103 – Open Cluster in Cassiopeia, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-103/
    MObj("m104", degToRad(12.6664), degToRad(-11.6231), "Sombrero Galaxy (Spiral Galaxy in Corvus)", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-104/"), // Messier 104 – Sombrero Galaxy (Spiral Galaxy in Corvus), https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-104/
    MObj("m105", degToRad(10.7000), degToRad(11.7000), "Elliptical Galaxy in Leo", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-105/"), // Messier 105 – Elliptical Galaxy in Leo, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-105/
    MObj("m106", degToRad(12.3369), degToRad(47.1611), "Spiral Galaxy in Canes Venatici", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-106/"), // Messier 106 – Spiral Galaxy in Canes Venatici, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-106/
    MObj("m107", degToRad(16.1717), degToRad(13.1797), "Globular Cluster in Ophiuchus", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-107/"), // Messier 107 – Globular Cluster in Ophiuchus, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-107/
    MObj("m108", degToRad(11.2167), degToRad(55.5336), "Spiral Galaxy in Ursa Major", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-108/"), // Messier 108 – Spiral Galaxy in Ursa Major, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-108/
    MObj("m109", degToRad(11.9825), degToRad(53.2311), "Barred Spiral Galaxy in Ursa Major", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-109/"), // Messier 109 – Barred Spiral Galaxy in Ursa Major, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-109/
    MObj("m110", degToRad(0.6733), degToRad(41.6850), "Dwarf Elliptical Galaxy in Andromeda", "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-110/") // Messier 110 – Dwarf Elliptical Galaxy in Andromeda, https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog/messier-110/
)

private fun findNearestMessier(raJ: Double, decJ: Double): messierDisplayData {
    var best = messierDisplayData("-", "-", "-")
    var bestAng = Double.POSITIVE_INFINITY
    for (m in messierMini) {
        val ang = angularSeparation(raJ, decJ, m.raRad, m.decRad)
        if (ang < bestAng) {
            bestAng = ang
            best = messierDisplayData(m.tag, m.name, m.link)
        }
    }
    // Optional: only accept if within N degrees
    return if (Math.toDegrees(bestAng) <= 10.0) best else messierDisplayData("-", "-", "-")
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

@SuppressLint("DefaultLocale")
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

