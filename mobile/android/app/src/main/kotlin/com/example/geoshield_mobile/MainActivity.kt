package com.example.geoshield_mobile

import android.location.Address
import android.location.Geocoder
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

class MainActivity : FlutterActivity() {
    private val GEOCODER_CHANNEL = "com.geoshield.mobile/native_geocoder"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, GEOCODER_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "reverseGeocode") {
                val lat = call.argument<Double>("latitude")
                val lon = call.argument<Double>("longitude")

                if (lat == null || lon == null) {
                    result.error("INVALID_ARGS", "Latitude and longitude required", null)
                    return@setMethodCallHandler
                }

                if (!Geocoder.isPresent()) {
                    result.success(null)
                    return@setMethodCallHandler
                }

                try {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (addresses.isNotEmpty()) {
                                    val address = addresses[0]
                                    val map = hashMapOf(
                                        "locality" to address.locality,
                                        "subLocality" to address.subLocality,
                                        "subAdminArea" to address.subAdminArea,
                                        "adminArea" to address.adminArea
                                    )
                                    runOnUiThread { result.success(map) }
                                } else {
                                    runOnUiThread { result.success(null) }
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                runOnUiThread { result.success(null) }
                            }
                        })
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lon, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val map = hashMapOf(
                                "locality" to address.locality,
                                "subLocality" to address.subLocality,
                                "subAdminArea" to address.subAdminArea,
                                "adminArea" to address.adminArea
                            )
                            result.success(map)
                        } else {
                            result.success(null)
                        }
                    }
                } catch (e: Exception) {
                    result.success(null)
                }
            } else {
                result.notImplemented()
            }
        }
    }
}
