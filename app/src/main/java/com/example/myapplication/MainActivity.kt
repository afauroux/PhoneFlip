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
import androidx.compose.material3.ListItem
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

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    sensorReadings.value = sensorReadings.value.copy(
                        gyroscope = Triple(event.values[0], event.values[1], event.values[2])
                    )
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    sensorReadings.value = sensorReadings.value.copy(
                        accelerometer = Triple(event.values[0], event.values[1], event.values[2])
                    )
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
                    hasAccelerometer = accelerometer != null
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
    hasAccelerometer: Boolean = true
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val people = listOf("John", "Bob", "Alice", "John", "Bob", "Alice","John", "Bob", "Alice","John", "Bob", "Alice","John", "Bob", "Alice","John", "Bob", "Alice")

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
                    hasAccelerometer = hasAccelerometer
                )
                LazyColumn {
                    items(people) {
                        ListItem(it)
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

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
@Composable
fun ListItem(name: String) {
    Card(
        modifier = Modifier.fillMaxSize()
            .padding(12.dp)
    ){
        Text(
            text=name,
            modifier=Modifier.padding(12.dp)
        )
    }
}

data class SensorReadings(
    val gyroscope: Triple<Float, Float, Float>? = null,
    val accelerometer: Triple<Float, Float, Float>? = null
)

@Composable
fun SensorReadingsPanel(
    sensorReadings: SensorReadings,
    hasGyroscope: Boolean,
    hasAccelerometer: Boolean
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
        Text(text = "Gyroscope (rad/s)")
        Text(text = gyroText, modifier = Modifier.padding(bottom = 12.dp))
        Text(text = "Accelerometer (m/s²)")
        Text(text = accelText)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}
