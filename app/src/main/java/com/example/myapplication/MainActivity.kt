package com.example.myapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    private val sensorReadings = mutableStateOf(SensorReadings())
    private val throwState = mutableStateOf(ThrowState.IDLE)
    private val throwStats = mutableStateOf<List<ThrowStats>>(emptyList())
    private val throwTracker = ThrowTracker()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    sensorReadings.value = sensorReadings.value.copy(
                        gyroscope = Triple(event.values[0], event.values[1], event.values[2])
                    )
                    throwTracker.onGyroscope(event, throwState.value)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    sensorReadings.value = sensorReadings.value.copy(
                        accelerometer = Triple(event.values[0], event.values[1], event.values[2])
                    )
                    val update = throwTracker.onAccelerometer(event)
                    throwState.value = update.currentState
                    update.completedThrow?.let { stats ->
                        throwStats.value = listOf(stats) + throwStats.value
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            MyApplicationTheme {
                MyApplicationApp(
                    sensorReadings = sensorReadings,
                    hasGyroscope = gyroscope != null,
                    hasAccelerometer = accelerometer != null,
                    throwState = throwState,
                    throwStats = throwStats
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.also { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.also { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }
}

@PreviewScreenSizes
@Composable
fun MyApplicationApp(
    sensorReadings: MutableState<SensorReadings> = mutableStateOf(SensorReadings()),
    hasGyroscope: Boolean = true,
    hasAccelerometer: Boolean = true,
    throwState: MutableState<ThrowState> = mutableStateOf(ThrowState.IDLE),
    throwStats: MutableState<List<ThrowStats>> = mutableStateOf(emptyList())
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                SensorReadingsPanel(
                    sensorReadings = sensorReadings.value,
                    hasGyroscope = hasGyroscope,
                    hasAccelerometer = hasAccelerometer,
                    throwState = throwState.value
                )
                LazyColumn {
                    items(throwStats.value) {
                        ThrowCard(it)
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

data class SensorReadings(
    val gyroscope: Triple<Float, Float, Float>? = null,
    val accelerometer: Triple<Float, Float, Float>? = null
)

enum class ThrowState {
    IDLE,
    THROW,
    FREE_FLIGHT,
    CATCH
}

data class ThrowStats(
    val id: Int,
    val shortAxisRotations: Int,
    val longAxisRotations: Int,
    val middleAxisRotations: Int,
    val flightTimeSeconds: Float,
    val maxHeightMeters: Float,
    val peakCatchAccel: Float,
    val catchQuality: String
)

data class ThrowUpdate(
    val currentState: ThrowState,
    val completedThrow: ThrowStats? = null
)

class ThrowTracker {
    private val gravity = 9.81f
    private val throwThreshold = 2.0f * gravity
    private val freeFallThreshold = 0.5f * gravity
    private val catchThreshold = 3.0f * gravity

    private var state: ThrowState = ThrowState.IDLE
    private var lastGyroTimestamp: Long? = null
    private var flightStartTimestamp: Long? = null
    private var lastThrowTimestamp: Long? = null
    private var accumulatedAngles = Triple(0f, 0f, 0f)
    private var peakCatchAccel = 0f
    private var throwCount = 0

    fun onAccelerometer(event: SensorEvent): ThrowUpdate {
        val magnitude = magnitude(event.values[0], event.values[1], event.values[2])
        var completedThrow: ThrowStats? = null

        when (state) {
            ThrowState.IDLE -> {
                if (magnitude > throwThreshold) {
                    state = ThrowState.THROW
                    lastThrowTimestamp = event.timestamp
                }
            }
            ThrowState.THROW -> {
                if (magnitude < freeFallThreshold) {
                    state = ThrowState.FREE_FLIGHT
                    flightStartTimestamp = event.timestamp
                    lastGyroTimestamp = null
                    accumulatedAngles = Triple(0f, 0f, 0f)
                    peakCatchAccel = 0f
                } else if (lastThrowTimestamp != null &&
                    (event.timestamp - lastThrowTimestamp!!) > 500_000_000L
                ) {
                    state = ThrowState.IDLE
                }
            }
            ThrowState.FREE_FLIGHT -> {
                if (magnitude > catchThreshold) {
                    state = ThrowState.CATCH
                    peakCatchAccel = maxOf(peakCatchAccel, magnitude)
                    completedThrow = finalizeThrow(event.timestamp)
                    state = ThrowState.IDLE
                } else {
                    peakCatchAccel = maxOf(peakCatchAccel, magnitude)
                }
            }
            ThrowState.CATCH -> {
                state = ThrowState.IDLE
            }
        }

        return ThrowUpdate(state, completedThrow)
    }

    fun onGyroscope(event: SensorEvent, currentState: ThrowState) {
        if (currentState != ThrowState.FREE_FLIGHT) {
            lastGyroTimestamp = event.timestamp
            return
        }
        val lastTimestamp = lastGyroTimestamp ?: event.timestamp
        val dt = (event.timestamp - lastTimestamp) / 1_000_000_000f
        val x = accumulatedAngles.first + event.values[0] * dt
        val y = accumulatedAngles.second + event.values[1] * dt
        val z = accumulatedAngles.third + event.values[2] * dt
        accumulatedAngles = Triple(x, y, z)
        lastGyroTimestamp = event.timestamp
    }

    private fun finalizeThrow(endTimestamp: Long): ThrowStats {
        throwCount += 1
        val flightTime = if (flightStartTimestamp != null) {
            (endTimestamp - flightStartTimestamp!!) / 1_000_000_000f
        } else {
            0f
        }
        val maxHeight = gravity * flightTime * flightTime / 8f
        val shortAxisRotations = rotationsFromAngle(accumulatedAngles.first)
        val longAxisRotations = rotationsFromAngle(accumulatedAngles.second)
        val middleAxisRotations = rotationsFromAngle(accumulatedAngles.third)
        val catchQuality = catchQualityFromAccel(peakCatchAccel)

        return ThrowStats(
            id = throwCount,
            shortAxisRotations = shortAxisRotations,
            longAxisRotations = longAxisRotations,
            middleAxisRotations = middleAxisRotations,
            flightTimeSeconds = flightTime,
            maxHeightMeters = maxHeight,
            peakCatchAccel = peakCatchAccel,
            catchQuality = catchQuality
        )
    }

    private fun rotationsFromAngle(angleRad: Float): Int {
        val turns = kotlin.math.abs(angleRad) / (2f * Math.PI.toFloat())
        return turns.toInt()
    }

    private fun catchQualityFromAccel(accel: Float): String {
        return when {
            accel < 4f * gravity -> "Perfect"
            accel < 6f * gravity -> "Good"
            accel < 8f * gravity -> "Sketchy"
            else -> "Hard Stop"
        }
    }

    private fun magnitude(x: Float, y: Float, z: Float): Float {
        return kotlin.math.sqrt(x * x + y * y + z * z)
    }
}

@Composable
fun SensorReadingsPanel(
    sensorReadings: SensorReadings,
    hasGyroscope: Boolean,
    hasAccelerometer: Boolean,
    throwState: ThrowState
) {
    val gyroText = if (hasGyroscope) {
        sensorReadings.gyroscope?.let { "X: %.2f, Y: %.2f, Z: %.2f".format(it.first, it.second, it.third) }
            ?: "Waiting for data..."
    } else {
        "Gyroscope not available"
    }

    val accelText = if (hasAccelerometer) {
        sensorReadings.accelerometer?.let { "X: %.2f, Y: %.2f, Z: %.2f".format(it.first, it.second, it.third) }
            ?: "Waiting for data..."
    } else {
        "Accelerometer not available"
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Throw state: ${throwState.name}")
        Text(text = "Gyroscope (rad/s)")
        Text(text = gyroText, modifier = Modifier.padding(bottom = 12.dp))
        Text(text = "Accelerometer (m/s²)")
        Text(text = accelText)
    }
}

@Composable
fun ThrowCard(stats: ThrowStats) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Throw #${stats.id}")
            Text(text = "Short-axis rotations: ${stats.shortAxisRotations}")
            Text(text = "Long-axis rotations: ${stats.longAxisRotations}")
            Text(text = "Middle-axis rotations: ${stats.middleAxisRotations}")
            Text(text = "Flight time: %.2f s".format(stats.flightTimeSeconds))
            Text(text = "Max height: %.2f m".format(stats.maxHeightMeters))
            Text(text = "Peak catch accel: %.2f m/s²".format(stats.peakCatchAccel))
            Text(text = "Catch quality: ${stats.catchQuality}")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}
