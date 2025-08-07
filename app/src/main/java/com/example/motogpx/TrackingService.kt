package com.example.motogpx

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TrackingService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "com.example.motogpx.action.START"
        const val ACTION_STOP = "com.example.motogpx.action.STOP"
        const val LOCATION_BROADCAST = "com.example.motogpx.LOCATION_UPDATE"
        const val EXTRA_LOCATION = "location_extra"
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIF_ID = 123456
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAccel: Triple<Float, Float, Float>? = null

    private val pathPoints = mutableListOf<Pair<Location, Triple<Float, Float, Float>?>>() // (Location, Acelerómetro)

    override fun onCreate() {
        super.onCreate()

        // Init sensores
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Init ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val accel = lastAccel
                pathPoints.add(location to accel)
                val intent = Intent(LOCATION_BROADCAST)
                intent.putExtra(EXTRA_LOCATION, location)
                sendBroadcast(intent)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "Servicio iniciado con acción: ${intent?.action}", Toast.LENGTH_SHORT).show()
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking activo")
            .setContentText("Grabando ubicación en segundo plano")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIF_ID, notification)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        pathPoints.clear()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)

        val savedFilePath = saveGpxFile() ?: "No se guardó archivo"
        stopForeground(true)
        stopSelf()
        Toast.makeText(this, "Tracking detenido. Archivo guardado en: $savedFilePath", Toast.LENGTH_LONG).show()
    }

    private fun saveGpxFile(): String? {
        if (pathPoints.isEmpty()) return null

        val gpxContent = buildGpxContent(pathPoints)

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timestamp = sdf.format(Date())

        val fileName = "MotoTrack_$timestamp.gpx"
        val documentsDir = File(getExternalFilesDir(null), "Documents")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val file = File(documentsDir, fileName)
        file.writeText(gpxContent)

        return file.absolutePath
    }

    private fun buildGpxContent(points: List<Pair<Location, Triple<Float, Float, Float>?>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="no" ?> 
<gpx version="1.1" creator="MOTOGPx" xmlns="http://www.topografix.com/GPX/1/1" >
<trk><name>Track</name><trkseg>""")

        points.forEach { (location, accel) ->
            sb.append("\n<trkpt lat=\"${location.latitude}\" lon=\"${location.longitude}\">")
            sb.append("<ele>${location.altitude}</ele>")
            sb.append("<time>${getTime(location)}</time>")
            accel?.let { (x, y, z) ->
                sb.append("<desc>Acelerómetro x:$x y:$y z:$z</desc>")
            }
            sb.append("</trkpt>")
        }

        sb.append("\n</trkseg></trk></gpx>")
        return sb.toString()
    }

    private fun getTime(location: Location): String {
        val date = Date(location.time)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracking Location Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                lastAccel = Triple(it.values[0], it.values[1], it.values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No usado
    }
}
