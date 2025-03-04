package com.example.individualassignment_54

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.individualassignment_54.ui.theme.IndividualAssignment_54Theme
import kotlinx.coroutines.launch
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.pow

//A viewmodel to retain the sensor data across any reconfigurations
class orientationViewModel: ViewModel() {
    //accel. data
    var ax by mutableStateOf(0f)
    var ay by mutableStateOf(0f)
    var az by mutableStateOf(0f)
    //mag. data
    var mx by mutableStateOf(0f)
    var my by mutableStateOf(0f)
    var mz by mutableStateOf(0f)
    //gyro. data
    var pitch by mutableStateOf(0f)
    var roll by mutableStateOf(0f)
    var time by mutableStateOf(0L)
}

class MainActivity : ComponentActivity(), SensorEventListener {

    //make vars for the sensor manager and all the sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    //declare the viewmodel (it must be initialized  in onCreate() )
    private lateinit var ortn: orientationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {

        //initialize viewModel
        ortn = ViewModelProvider(this)[orientationViewModel::class]

        // Initialize Sensor Manager and sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IndividualAssignment_54Theme {

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MakeScreen(ax = ortn.ax, ay = ortn.ay, az = ortn.az,
                            roll = ortn.roll, pitch = ortn.pitch)
                    }
            }
        }
    }
    //set up the listener for each sensor on resume
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        //I'm trying to calculate total position based on rate of change and time.
        //That requires accurate measurements. As such, I used a higher sensor rate for the
        //gyroscope.
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    //unregister listener on pause
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    //a lot to do if the sensor changes...
    override fun onSensorChanged(event: SensorEvent?) {
        var aPitch: Double = 0.0
        var aRoll: Double = 0.0
        var gPitch: Double = 0.0
        var gRoll: Double = 0.0
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    //on acc. update, just write the data to the viewModel
                    ortn.ax = it.values[0]
                    ortn.ay = it.values[1]
                    ortn.az = it.values[2]
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    //on mag. update, write data to viewModel too
                    ortn.mx = it.values[0]
                    ortn.my = it.values[1]
                    ortn.mz = it.values[2]
                }
                Sensor.TYPE_GYROSCOPE -> {
                    //ALOT needs to happen when the gyroscope shifts
                    //Not only do I need to check the readings, but I also need to
                    //calculate position shift from the timestamp of the event.
                    val p = it.values[2]
                    val r = it.values[0]
                    if (ortn.time == 0L) ortn.time = it.timestamp
                    //The gyro measures in rad/sec but the timestamp is in
                    //ns. So I need to divide by 10^9
                    val timeDiff = (it.timestamp - ortn.time) / 1000000000
                    ortn.time = it.timestamp
                    //convert the shift to degrees
                    gPitch = Math.toDegrees((p * timeDiff).toDouble())
                    gRoll = Math.toDegrees((r * timeDiff).toDouble())

                    //determine rough orientation based on existing accelerometer data
                    //(trigonometry time)
                    aRoll = Math.toDegrees(
                        Math.atan2(
                            ortn.ay.toDouble(),
                            Math.sqrt((ortn.ax * ortn.ax + ortn.az * ortn.az).toDouble())
                        )
                    )
                    aPitch = Math.toDegrees(
                        Math.atan2(
                            ortn.ax.toDouble(),
                            Math.sqrt((ortn.ay * ortn.ay + ortn.az * ortn.az).toDouble())
                        )
                    )

                    /*
                    THIS WAS GIVEN BY CHATGPT.
                    I was having trouble figuring out how to combine both the accelerometer and gyroscope
                    data to create an accurate measurement. The acc. keeps it relative to the ground and
                    doesn't skew but it's not precise and its slower. The gyro. is fast but has no reference
                    point and skews. StackOverflow answers were hard to follow. I asked AI for how to
                    calculate the final position and it recommended this math.
                    Essentially, we're taking a weighted average of the positions as determined
                    by both sensors. This particular ratio was suggested and tested well.
                     */
                    ortn.roll = ((0.98 * (ortn.roll + gRoll)) + (0.02 * aRoll)).toFloat()
                    ortn.pitch = ((0.98 * (ortn.pitch + gPitch)) + (0.02 * aPitch)).toFloat()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}

enum class hit{
    UP,
    DOWN,
    LEFT,
    RIGHT,
    NONE
}

class gameWall(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
){
    fun hitDetect(x: Float, y: Float, radius: Float): hit {
        //I don't know how to program a physics engine. My original idea
        //for checking ball/wall collision did not work well. I got this
        //math from online.
        //Calculate the closest point on the rectangle to the circle.
        //Then see if the distance between then is less than the radius.
        val closeX = max(this.x, min(x, this.x + this.w))
        val closeY = max(this.y, min(y, this.y + this.h))
        val distanceSquared = (x - closeX).pow(2) + (y - closeY).pow(2)
        if(distanceSquared <= radius.pow(2)) {  //that means they overlap
            if(x < this.x) return hit.RIGHT
            else if(x > this.x + this.w) return hit.LEFT
            else if(x < this.y) return hit.DOWN
            else return hit.UP
        } else {
            return hit.NONE
        }
    }
}
data class gameBoard(
    val startX: Float,
    val startY: Float,
    val walls: Array<gameWall>,
    val endX: Float,
    val endY: Float
)

fun sampleGameBoard0(radius: Float, window: WindowInfo): gameBoard{
    val ret = gameBoard(
        radius + 20,
        radius + 20,
        arrayOf(
            gameWall(x = 0f, y = -10f, w = (window.widthDp).toFloat(), h = 20f),
            gameWall(x = -10f, y = 0f, w = 20f, h = (window.heightDp).toFloat()),
            gameWall(x = (window.widthDp).toFloat() - 10, y = 0f, w = 20f, h = (window.heightDp).toFloat()),
            gameWall(x = 0f, y = (window.heightDp).toFloat() - 10, w = (window.widthDp).toFloat(), h = 20f)
        ),
        window.widthDp.toFloat() - 50,
        window.heightDp.toFloat() - 50
    )

    return ret
}

fun checkWin(ballX: Float, ballY: Float, winX: Float, winY: Float, winR: Float): Boolean {
    return (ballX > winX-winR &&
            ballX < winX+winR &&
            ballY > winY-winR &&
            ballY < winY+winR)
}

@Composable
fun MakeScreen(ax: Float, ay: Float, az: Float,
               pitch: Float, roll: Float){
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val window = calculateCurrentWindowInfo()
        val radius = 70f
        val winnerCircleRadius = radius
        val ballSpeed = 40
        var ballPos by remember {
            mutableStateOf(
                Offset(
                    x = radius + 20,
                    y = radius + 20
                )
            )
        }

        val board by remember {
            mutableStateOf(sampleGameBoard0(radius, window))
        }

        var newX = ballPos.x + (pitch / 45) * (-1*ballSpeed)
        var newY = ballPos.y + (roll / 45) * ballSpeed
        for (wall in board.walls) {
            val hitDir = wall.hitDetect(ballPos.x, ballPos.y, radius)
            if (hitDir == hit.UP) newY = max(ballPos.y, newY)
            if (hitDir == hit.DOWN) newY = min(ballPos.y, newY)
            if (hitDir == hit.LEFT) newX = max(ballPos.x, newX)
            if (hitDir == hit.RIGHT) newX = min(ballPos.x, newX)
        }

        ballPos = Offset(newX, newY)

        LaunchedEffect(ballPos) {
            if (checkWin(newX, newY, board.endX, board.endY, winnerCircleRadius)) {
                snackbarHostState.showSnackbar(
                    message = "Well done! You win!",
                    duration = SnackbarDuration.Indefinite
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            drawCircle(
                color = Color.DarkGray,
                radius = radius,
                center = ballPos
            )

            drawCircle(
                color = Color.Green,
                radius = winnerCircleRadius,
                center = Offset(board.endX, board.endY)
            )

            for (wall in board.walls) {
                drawRect(
                    color = Color.LightGray,
                    topLeft = Offset(x = wall.x, y = wall.y),
                    size = Size(
                        width = wall.w,
                        height = wall.h
                    )
                )
            }
        }
    }
}

//Borrowed from example code. Retrieves stats about current device window.
@Composable
fun calculateCurrentWindowInfo(): WindowInfo {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val window = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val orientation = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        Orientation.PORTRAIT
    } else {
        //slight change: two possible landscapes (right and left)
        if(window.defaultDisplay.rotation == android.view.Surface.ROTATION_90) {
            Orientation.LANDSCAPE_R
        } else if(window.defaultDisplay.rotation == android.view.Surface.ROTATION_270) {
            Orientation.LANDSCAPE_L
        } else {
            Orientation.PORTRAIT
        }
    }

    return WindowInfo(
        widthDp = screenWidth,
        heightDp = screenHeight,
        orientation = orientation
    )
}
//Borrowed from example code. Stores window stats.
data class WindowInfo(
    val widthDp: Int,
    val heightDp: Int,
    val orientation: Orientation
)
//Borrowed from example code. Represents screen orientation.
//Slight change made to account for two types of landscape mode
enum class Orientation {
    PORTRAIT,
    LANDSCAPE_R,
    LANDSCAPE_L
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    IndividualAssignment_54Theme {

    }
}