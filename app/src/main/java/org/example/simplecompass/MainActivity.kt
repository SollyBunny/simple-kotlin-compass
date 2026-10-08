package org.example.simplecompass

import android.app.AlertDialog
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.simplecompass.ui.theme.SimpleCompassTheme
import kotlin.math.roundToInt

class OrientationViewModel(
    private val sensorManager: SensorManager,
    private val display: android.view.Display
) : ViewModel() {

    private val _orientation = MutableStateFlow(FloatArray(3))
    val orientation = _orientation.asStateFlow()

    private val _accuracy =
        MutableStateFlow(SensorManager.SENSOR_STATUS_UNRELIABLE)
    val accuracy = _accuracy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val rotationMatrix = FloatArray(9)

    private val listener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values
            )

            val remappedMatrix = FloatArray(9)
            when (display.rotation) {
                Surface.ROTATION_0 -> {
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Y,
                        remappedMatrix
                    )
                }

                Surface.ROTATION_90 -> {
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_Y,
                        SensorManager.AXIS_MINUS_X,
                        remappedMatrix
                    )
                }

                Surface.ROTATION_180 -> {
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_MINUS_X,
                        SensorManager.AXIS_MINUS_Y,
                        remappedMatrix
                    )
                }

                Surface.ROTATION_270 -> {
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_MINUS_Y,
                        SensorManager.AXIS_X,
                        remappedMatrix
                    )
                }
            }

            val orientation = FloatArray(3)
            SensorManager.getOrientation(
                remappedMatrix,
                orientation
            )
            _orientation.value = orientation
        }

        override fun onAccuracyChanged(
            sensor: Sensor?,
            accuracy: Int
        ) {
            _accuracy.value = accuracy
        }
    }

    fun start() {
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotation == null) {
            _error.value = "No rotation vector sensor found"
            return
        }

        sensorManager.registerListener(
            listener,
            rotation,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    override fun onCleared() {
        sensorManager.unregisterListener(listener)
        super.onCleared()
    }
}

fun rad2deg(rad: Float) = rad * 180f / Math.PI.toFloat()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val orientationViewModel = OrientationViewModel(
            getSystemService(SENSOR_SERVICE) as SensorManager,
            display
        )
        orientationViewModel.start()

        enableEdgeToEdge()
        setContent {
            SimpleCompassTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) {
                        Compass(orientationViewModel)
                    }
                }
            }
        }
    }

    @Composable
    fun CompassBar(
        heading: Float,
        modifier: Modifier = Modifier
    ) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            val centerX = size.width / 2f
            val barY = size.height - 28.dp.toPx()

            // 90° visible across the screen
            val pxPerDegree = size.width / 90f

            /*
             * The scale itself moves.
             *
             * heading = 0:
             *     N is at the red marker
             *
             * heading = 30:
             *     N moves 30° to the left
             */
            fun xForDegree(degree: Float): Float {
                val relative =
                    ((degree - heading + 540f) % 360f) - 180f

                return centerX + relative * pxPerDegree
            }

            /*
             * =========================
             * MOVING TICKS
             * =========================
             *
             * One tick every 5°.
             *
             * Every 4th tick is bigger:
             * 0, 20, 40, 60...
             */
            for (degree in 0..355 step 5) {
                val x = xForDegree(degree.toFloat())

                // Only draw ticks that are visible
                if (x >= -20f && x <= size.width + 20f) {

                    var size = 0
                    if (degree % 15 == 0) size = 1
                    if (degree % 45 == 0) size = 2
                    if (degree % 90 == 0) size = 3

                    val tickHeight = arrayOf(12, 15, 20, 26)[size].dp.toPx()
                    val strokeWidth = size.dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = Offset(
                            x, barY - tickHeight / 2f
                        ),
                        end = Offset(
                            x, barY + tickHeight / 2f
                        ),
                        strokeWidth = strokeWidth
                    )
                }
            }

            /*
             * =========================
             * MOVING DIRECTION LABELS
             * =========================
             */

            val directions = listOf(
                0f to "N",
                45f to "NE",
                90f to "E",
                135f to "SE",
                180f to "S",
                225f to "SW",
                270f to "W",
                315f to "NW"
            )

            directions.forEach { (degree, label) ->
                val x = xForDegree(degree)

                if (x >= -60f && x <= size.width + 60f) {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = if (label.length == 1) {
                            19.sp.toPx()
                        } else {
                            13.sp.toPx()
                        }
                        typeface =
                            if (label.length == 1) {
                                android.graphics.Typeface.DEFAULT_BOLD
                            } else {
                                android.graphics.Typeface.DEFAULT
                            }
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x,
                        22.dp.toPx(),
                        paint
                    )
                }
            }

            /*
             * =========================
             * ONE DEGREE VALUE
             * =========================
             */
            val degreePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 18.sp.toPx()
            }

            drawContext.canvas.nativeCanvas.drawText(
                "${heading.roundToInt()}°",
                centerX,
                barY + 50.dp.toPx(),
                degreePaint
            )

            /*
             * =========================
             * FIXED RED MARKER
             * =========================
             */

            val markerHeight = 40.dp.toPx()
            val markerStrokeWidth = 5.dp.toPx()
            drawLine(
                color = Color.Red,
                start = Offset(
                    centerX,
                    barY - markerHeight / 2f
                ),
                end = Offset(
                    centerX,
                    barY + markerHeight / 2f
                ),
                strokeWidth = markerStrokeWidth
            )
        }
    }

    @Composable
    fun Compass(orientationViewModel: OrientationViewModel) {
        val error = orientationViewModel.error.collectAsState().value

        if (error != null) {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(error)
                .setPositiveButton("OK") { _, _ ->
                    finish()
                }
                .show()
        }

        val rotation = orientationViewModel.orientation.collectAsState().value

        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val aspect = h / w

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            CompassBar(
                heading = rad2deg(rotation[0]),
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .height(200.dp * aspect)
                        .width(200.dp)
                        .graphicsLayer() {
                            rotationX = rad2deg(rotation[1])
                            rotationY = rad2deg(rotation[2])
                            rotationZ = 0f

                            // Gives the rotation some perspective
                            cameraDistance = 12 * density
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Blue),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Phone")
                }
            }
        }
    }
}
