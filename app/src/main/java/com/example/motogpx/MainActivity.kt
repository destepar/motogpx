package com.example.motogpx

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import android.widget.Toast

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private val pathPoints = mutableListOf<LatLng>()

    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TrackingService.LOCATION_BROADCAST) {
                val location = intent.getParcelableExtra<Location>(TrackingService.EXTRA_LOCATION)
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)

                    Toast.makeText(this@MainActivity, "Ubicación recibida", Toast.LENGTH_SHORT).show()

                    pathPoints.add(latLng)

                    Toast.makeText(this@MainActivity, "Total puntos: ${pathPoints.size}", Toast.LENGTH_SHORT).show()

                    updatePolyline()
                }
            }
        }
    }

    companion object {
        const val MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey"
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val NOTIF_PERMISSION_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapViewBundle: Bundle? = savedInstanceState?.getBundle(MAP_VIEW_BUNDLE_KEY)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(mapViewBundle)
        mapView.getMapAsync(this)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        startButton.setOnClickListener {
            Toast.makeText(this, "Start clickeado", Toast.LENGTH_SHORT).show()
            if (hasLocationPermission()) {
                checkNotificationPermissionAndStartService()
            } else {
                requestLocationPermission()
            }
        }

        stopButton.setOnClickListener {
            val intent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent) // para asegurarte que llegue el intent
            } else {
                startService(intent)
            }
        }
    }

    private fun checkNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIF_PERMISSION_REQUEST_CODE)
            } else {
                startTrackingService()
            }
        } else {
            startTrackingService()
        }
    }

    private fun startTrackingService() {
        val intent = Intent(this, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        } else if (requestCode == NOTIF_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startTrackingService()
        } else {
            Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableMyLocation()
    }

    private fun updatePolyline() {
        Toast.makeText(this, "Dibujando línea con ${pathPoints.size} puntos", Toast.LENGTH_SHORT).show()

        if (pathPoints.size < 2) return

        val lastTwo = pathPoints.takeLast(2)
        val polylineOptions = PolylineOptions()
            .addAll(lastTwo)
            .color(0xFF0000FF.toInt())
            .width(5f)

        googleMap.addPolyline(polylineOptions)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastTwo.last(), 16f))
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        val filter = IntentFilter(TrackingService.LOCATION_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(locationReceiver, filter)
        }
        Toast.makeText(this, "Receiver registrado", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(locationReceiver)
        mapView.onPause()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        var mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY)
        if (mapViewBundle == null) {
            mapViewBundle = Bundle()
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle)
        }
        mapView.onSaveInstanceState(mapViewBundle)
    }

    private fun enableMyLocation() {
        if (hasLocationPermission()) {
            googleMap.isMyLocationEnabled = true
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
}
