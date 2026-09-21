package com.example.vivomock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlin.math.*

class MainActivity : Activity() {
    companion object {
        private const val REQ_LOCATION = 1001
        private const val SPEED_KMH = 9.3
        private const val STEP_MS = 200L
    }

    private lateinit var locationManager: LocationManager
    private lateinit var mapView: WebView
    private lateinit var joystick: JoystickView
    private lateinit var statusText: TextView
    private lateinit var coordText: TextView
    private lateinit var startButton: Button
    private lateinit var realButton: Button

    private var currentLat = 0.0
    private var currentLon = 0.0
    private var havePosition = false
    private var mocking = false
    private var vx = 0f
    private var vy = 0f
    private var lastThreeFingerToggle = 0L

    private val mover = object : Runnable {
        override fun run() {
            if (mocking && havePosition) {
                val mag = hypot(vx.toDouble(), vy.toDouble())
                if (mag > 0.05) {
                    val meters = (SPEED_KMH / 3.6) * (STEP_MS / 1000.0) * min(1.0, mag)
                    val east = (vx.toDouble() / mag) * meters
                    val north = (-vy.toDouble() / mag) * meters
                    moveByMeters(north, east)
                    pushMockLocation()
                    updateUiPosition(centerMap = false)
                }
            }
            mapView.postDelayed(this, STEP_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mapView = findViewById(R.id.mapView)
        joystick = findViewById(R.id.joystick)
        statusText = findViewById(R.id.statusText)
        coordText = findViewById(R.id.coordText)
        startButton = findViewById(R.id.startButton)
        realButton = findViewById(R.id.realButton)

        setupMap()
        joystick.onVectorChanged = { x, y -> vx = x; vy = y }
        startButton.setOnClickListener { toggleMocking() }
        realButton.setOnClickListener { loadRealPosition() }

        requestOrLoadLocation()
        mapView.post(mover)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.pointerCount >= 3 && ev.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastThreeFingerToggle > 700) {
                lastThreeFingerToggle = now
                joystick.visibility = if (joystick.visibility == android.view.View.VISIBLE)
                    android.view.View.GONE else android.view.View.VISIBLE
                Toast.makeText(this, if (joystick.visibility == android.view.View.VISIBLE) "Joystick sichtbar" else "Joystick verborgen", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMap() {
        mapView.settings.javaScriptEnabled = true
        mapView.settings.domStorageEnabled = true
        mapView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (havePosition) updateUiPosition(centerMap = true)
            }
        }
        mapView.addJavascriptInterface(MapBridge(), "AndroidBridge")
        mapView.loadUrl("file:///android_asset/map.html")
    }

    inner class MapBridge {
        @JavascriptInterface
        fun teleport(lat: Double, lon: Double) {
            runOnUiThread {
                currentLat = lat.coerceIn(-85.0, 85.0)
                currentLon = normalizeLon(lon)
                havePosition = true
                if (mocking) pushMockLocation()
                updateUiPosition(centerMap = true)
                statusText.text = "Position per Karte gesetzt"
            }
        }
    }

    private fun requestOrLoadLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
        } else loadRealPosition()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadRealPosition()
        else statusText.text = "Standortberechtigung fehlt"
    }

    @SuppressLint("MissingPermission")
    private fun loadRealPosition() {
        if (mocking) stopMocking()
        statusText.text = "Suche aktuelle Position …"

        val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (best != null) useRealLocation(best)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                useRealLocation(location)
                runCatching { locationManager.removeUpdates(this) }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in API") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        runCatching { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener) }
            .onFailure { runCatching { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener) } }
    }

    private fun useRealLocation(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        havePosition = true
        statusText.text = "Echte Position geladen"
        updateUiPosition(centerMap = true)
    }

    private fun toggleMocking() {
        if (!havePosition) {
            Toast.makeText(this, "Noch keine Startposition verfügbar", Toast.LENGTH_SHORT).show()
            return
        }
        if (mocking) stopMocking() else startMocking()
    }

    private fun startMocking() {
        try {
            runCatching { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            mocking = true
            pushMockLocation()
            startButton.text = "STOP"
            statusText.text = "Mock aktiv · 9,3 km/h"
        } catch (e: SecurityException) {
            mocking = false
            statusText.text = "App zuerst als Mock-Location-App auswählen"
            Toast.makeText(this, "Entwickleroptionen → App für simulierte Standorte → Mock Joystick Test", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            mocking = false
            statusText.text = "Mock konnte nicht gestartet werden: ${e.javaClass.simpleName}"
        }
    }

    private fun stopMocking() {
        mocking = false
        vx = 0f; vy = 0f
        runCatching { locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false) }
        runCatching { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        startButton.text = "START"
        statusText.text = "Mock gestoppt"
    }

    private fun pushMockLocation() {
        if (!mocking) return
        val loc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = currentLat
            longitude = currentLon
            altitude = 0.0
            accuracy = 2.5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            speed = (SPEED_KMH / 3.6).toFloat()
        }
        runCatching { locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc) }
    }

    private fun moveByMeters(northMeters: Double, eastMeters: Double) {
        val earth = 6_378_137.0
        val dLat = northMeters / earth
        val cosLat = cos(Math.toRadians(currentLat)).coerceAtLeast(0.01)
        val dLon = eastMeters / (earth * cosLat)
        currentLat = (currentLat + Math.toDegrees(dLat)).coerceIn(-85.0, 85.0)
        currentLon = normalizeLon(currentLon + Math.toDegrees(dLon))
    }

    private fun normalizeLon(lon: Double): Double {
        var x = lon
        while (x > 180) x -= 360
        while (x < -180) x += 360
        return x
    }

    private fun updateUiPosition(centerMap: Boolean) {
        coordText.text = "%.6f, %.6f".format(currentLat, currentLon)
        val js = if (centerMap)
            "window.setMarker(${currentLat},${currentLon},true);"
        else
            "window.setMarker(${currentLat},${currentLon},false);"
        mapView.evaluateJavascript(js, null)
    }

    override fun onDestroy() {
        mapView.removeCallbacks(mover)
        if (mocking) stopMocking()
        super.onDestroy()
    }
}
