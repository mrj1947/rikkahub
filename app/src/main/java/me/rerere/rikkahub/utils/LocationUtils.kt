package me.rerere.rikkahub.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "LocationUtils"
private const val REQUEST_LOCATION_PERMISSION_CODE = 1001
private const val GPS_TIMEOUT_MS = 10_000L
private const val GPS_CACHE_FRESH_MS = 120_000L

// ─── 坐标系类型 ──────────────────────────────────────────────

enum class CoordType {
    WGS84,  // 原生GPS芯片坐标
    GCJ02,  // 国测局坐标（高德/腾讯地图用）
    BD09,   // 百度加密坐标
    UNKNOWN
}

// ─── 位置缓存 ──────────────────────────────────────────────

object LocationCache {
    var lastLocation: Location? = null
    var lastBdLocation: BDLocation? = null
    var lastAddress: String? = null
    var lastRefreshTime: Long = 0L
    var isListening: Boolean = false
    var baiduSdkActive: Boolean = false
    var baiduLastErrorCode: Int? = null
    var lastCoordType: CoordType = CoordType.UNKNOWN

    val providerDescription: String
        get() {
            val loc = lastLocation ?: return "无"
            val bd = lastBdLocation
            if (bd != null && baiduSdkActive) {
                val t = bd.locType
                return when (t) {
                    BDLocation.TypeGpsLocation -> "GPS卫星(百度融合)"
                    BDLocation.TypeNetWorkLocation -> "百度网络定位(WiFi+基站)"
                    BDLocation.TypeOffLineLocation -> "离线定位"
                    BDLocation.TypeNone -> "未知"
                    else -> "百度(${locTypeDescription(t)})"
                }
            }
            return when (loc.provider) {
                LocationManager.GPS_PROVIDER -> "GPS(原生)"
                LocationManager.NETWORK_PROVIDER -> "基站/WiFi(原生)"
                LocationManager.PASSIVE_PROVIDER -> "被动定位(原生)"
                else -> loc.provider ?: "未知"
            }
        }

    val accuracyDescription: String
        get() {
            val loc = lastLocation ?: return ""
            return if (loc.hasAccuracy()) "${loc.accuracy.toInt()}米" else "未知"
        }
}

private fun locTypeDescription(type: Int): String = when (type) {
    61 -> "GPS定位成功"
    161 -> "网络定位成功"
    62 -> "GPS定位失败"
    167 -> "网络定位失败(WiFi/基站不可用)"
    505 -> "KEY验证失败(AK错误)"
    162 -> "隐私协议未同意"
    else -> "未知($type)"
}

// ─── 百度SDK融合定位 ────────────────────────────────────────

private var baiduClient: LocationClient? = null
private var continuousListener: LocationListener? = null

private fun createBaiduClient(context: Context): LocationClient? {
    return try {
        // ═══ 关键修复：百度SDK 9.x 必须先同意隐私协议，否则静默失败返回162 ═══
        LocationClient.setAgreePrivacy(true)
        Log.i(TAG, "Baidu SDK: privacy agreement set (setAgreePrivacy=true)")

        val client = LocationClient(context.applicationContext)
        val option = LocationClientOption().apply {
            locationMode = LocationClientOption.LocationMode.Hight_Accuracy
            coorType = "gcj02"
            scanSpan = 3000
            setIsNeedAddress(true)
            setIsNeedLocationDescribe(true)
            isOpenGps = true
        }
        client.locOption = option
        Log.i(TAG, "Baidu SDK: LocationClient created (GCJ02 + Hight_Accuracy + WiFi scan)")
        client
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create Baidu LocationClient", e)
        LocationCache.baiduSdkActive = false
        null
    }
}

private fun bdLocationToAndroidLocation(bd: BDLocation): Location? {
    if (bd.latitude == 0.0 && bd.longitude == 0.0) return null
    return Location("baidu").apply {
        latitude = bd.latitude
        longitude = bd.longitude
        accuracy = bd.radius
        time = bd.time?.let {
            try { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(it)?.time }
            catch (_: Exception) { null }
        } ?: System.currentTimeMillis()
        if (bd.hasAltitude()) altitude = bd.altitude
        if (bd.speed > 0) speed = bd.speed
        if (bd.direction > 0) bearing = bd.direction
    }
}

// ─── 启动融合定位 ─────────────────────────────────────────────

fun startContinuousLocationListening(context: Context) {
    if (!hasLocationPermission(context)) {
        Log.w(TAG, "startContinuousLocationListening: no permission")
        return
    }

    if (LocationCache.isListening) {
        stopContinuousLocationListening(context)
    }

    // === 优先启动百度SDK ===
    val client = createBaiduClient(context)
    if (client != null) {
        val listener = object : BDAbstractLocationListener() {
            override fun onReceiveLocation(bd: BDLocation?) {
                if (bd == null) {
                    Log.w(TAG, "Baidu SDK: onReceiveLocation returned null")
                    return
                }

                val locType = bd.locType
                when (locType) {
                    61, 161 -> {
                        LocationCache.baiduSdkActive = true
                        LocationCache.baiduLastErrorCode = null
                        LocationCache.lastCoordType = CoordType.GCJ02
                        Log.i(TAG, "Baidu fix SUCCESS: type=${locTypeDescription(locType)} " +
                                "lat=${bd.latitude},lng=${bd.longitude} " +
                                "acc=${bd.radius}m addr=${bd.addrStr ?: "null"}")
                    }
                    505 -> {
                        LocationCache.baiduLastErrorCode = 505
                        LocationCache.baiduSdkActive = false
                        Log.e(TAG, "Baidu SDK ERROR 505: KEY验证失败！请检查AndroidManifest中的com.baidu.lbsapi.API_KEY")
                        return
                    }
                    162 -> {
                        LocationCache.baiduLastErrorCode = 162
                        LocationCache.baiduSdkActive = false
                        Log.e(TAG, "Baidu SDK ERROR 162: 隐私协议未同意！必须先调LocationClient.setAgreePrivacy(true)")
                        return
                    }
                    62 -> {
                        LocationCache.baiduLastErrorCode = 62
                        Log.w(TAG, "Baidu SDK: GPS定位失败(62), 可能在室内，等待网络定位...")
                    }
                    167 -> {
                        LocationCache.baiduLastErrorCode = 167
                        Log.w(TAG, "Baidu SDK: 网络定位失败(167), WiFi/基站不可用")
                        return
                    }
                    else -> {
                        LocationCache.baiduLastErrorCode = locType
                        Log.w(TAG, "Baidu SDK: locType=$locType (${locTypeDescription(locType)})")
                        if (bd.latitude == 0.0 && bd.longitude == 0.0) return
                    }
                }

                LocationCache.lastBdLocation = bd

                val addr = buildString {
                    bd.addrStr?.let { append(it) }
                    if (isBlank()) bd.locationDescribe?.let { append(it) }
                }.takeIf { it.isNotBlank() }
                LocationCache.lastAddress = addr

                val androidLoc = bdLocationToAndroidLocation(bd)
                if (androidLoc != null) {
                    LocationCache.lastLocation = androidLoc
                    LocationCache.lastRefreshTime = System.currentTimeMillis()
                }
            }
        }

        try {
            client.registerLocationListener(listener)
            client.start()
            baiduClient = client
            LocationCache.isListening = true
            Log.i(TAG, "Baidu fusion location STARTED (client.start() called)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Baidu location", e)
            LocationCache.baiduSdkActive = false
            baiduClient = null
        }
    }

    // === 同时启动原生GPS监听作为兜底 ===
    try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        val nativeListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Native GPS fix: ${location.latitude},${location.longitude} " +
                        "acc=${location.accuracy} provider=${location.provider}")

                // 只有百度SDK未激活时，原生GPS才写入缓存
                if (!LocationCache.baiduSdkActive) {
                    val current = LocationCache.lastLocation
                    if (current == null || (location.hasAccuracy() && location.accuracy < (current.accuracy.takeIf { current.hasAccuracy() } ?: 999f))) {
                        LocationCache.lastLocation = location
                        LocationCache.lastRefreshTime = System.currentTimeMillis()
                        LocationCache.lastCoordType = CoordType.WGS84

                        if (LocationCache.lastAddress == null) {
                            reverseGeocodeAsync(context, location)
                        }
                    }
                } else {
                    Log.d(TAG, "Native GPS ignored: Baidu SDK active, using fused location instead")
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider enabled: $provider")
            }
            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider disabled: $provider")
            }
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        var anyNativeStarted = false
        for (provider in providers) {
            if (locationManager.isProviderEnabled(provider)) {
                try {
                    locationManager.requestLocationUpdates(
                        provider, 1000L, 5f, nativeListener, Looper.getMainLooper()
                    )
                    Log.d(TAG, "Started native listening on $provider")
                    anyNativeStarted = true
                } catch (e: SecurityException) {
                    Log.w(TAG, "Security exception starting $provider", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Error starting $provider", e)
                }
            }
        }

        if (anyNativeStarted) {
            continuousListener = nativeListener
            if (baiduClient == null) {
                LocationCache.isListening = true
            }
            Log.i(TAG, "Native location listening started (fallback)")
        }

        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null && LocationCache.lastLocation == null) {
                LocationCache.lastLocation = lastKnown
                LocationCache.lastRefreshTime = System.currentTimeMillis()
                LocationCache.lastCoordType = CoordType.WGS84
                Log.d(TAG, "Initial cache from lastKnown: ${lastKnown.provider}")
            }
        } catch (_: SecurityException) {}

    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception in startContinuousLocationListening", e)
    } catch (e: Exception) {
        Log.e(TAG, "Error starting continuous location listening", e)
    }
}

fun stopContinuousLocationListening(context: Context) {
    try {
        baiduClient?.stop()
    } catch (e: Exception) {
        Log.w(TAG, "Error stopping Baidu client", e)
    }
    baiduClient = null
    LocationCache.baiduSdkActive = false

    val listener = continuousListener
    if (listener != null) {
        try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping native location listening", e)
        }
    }
    continuousListener = null
    LocationCache.isListening = false
    LocationCache.lastCoordType = CoordType.UNKNOWN
    Log.i(TAG, "All location listening stopped")
}

// ─── 逆地理编码（原生兜底用） ────────────────────────────────

private fun reverseGeocodeAsync(context: Context, location: Location) {
    Thread {
        try {
            if (!Geocoder.isPresent()) return@Thread
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val desc = buildString {
                    addr.countryName?.let { append(it) }
                    addr.adminArea?.let { append(" ").append(it) }
                    addr.locality?.let { append(" ").append(it) }
                    addr.subLocality?.let { append(" ").append(it) }
                    addr.thoroughfare?.let { append(" ").append(it) }
                }.trim()
                if (desc.isNotBlank()) {
                    LocationCache.lastAddress = desc
                    Log.d(TAG, "Reverse geocode: $desc")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed", e)
        }
    }.start()
}

// ─── 权限相关 ──────────────────────────────────────────────

fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}

fun requestLocationPermission(context: Context) {
    if (!hasLocationPermission(context)) {
        Log.d(TAG, "Requesting location permission")
        ActivityCompat.requestPermissions(
            context.findActivity(),
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_LOCATION_PERMISSION_CODE
        )
    } else {
        startContinuousLocationListening(context)
    }
}

// ─── 获取位置（同步读取） ──────────────────────────────────

fun getCurrentLocation(context: Context): Location? {
    val cache = LocationCache.lastLocation
    val cacheAge = System.currentTimeMillis() - LocationCache.lastRefreshTime
    if (cache != null && cacheAge < GPS_CACHE_FRESH_MS) {
        Log.d(TAG, "Using cache: source=${LocationCache.providerDescription}, " +
                "coord=${LocationCache.lastCoordType}, age=${cacheAge}ms, " +
                "acc=${if (cache.hasAccuracy()) cache.accuracy else "N/A"}")
        return cache
    }

    if (LocationCache.baiduSdkActive && LocationCache.lastBdLocation == null) {
        Log.d(TAG, "Baidu SDK active but no fix yet, cache is stale or empty")
    }

    if (!hasLocationPermission(context)) return null

    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    if (!LocationCache.baiduSdkActive) {
        try {
            val gpsLast = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLast != null && System.currentTimeMillis() - gpsLast.time < GPS_CACHE_FRESH_MS) {
                Log.d(TAG, "getCurrentLocation: lastKnown GPS (WGS84), acc=${gpsLast.accuracy}")
                LocationCache.lastLocation = gpsLast
                LocationCache.lastRefreshTime = System.currentTimeMillis()
                LocationCache.lastCoordType = CoordType.WGS84
                return gpsLast
            }
        } catch (_: SecurityException) {}

        if (!LocationCache.isListening && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d(TAG, "No continuous listener, trying one-shot GPS...")
            val syncLocation = tryGetGpsSync(locationManager)
            if (syncLocation != null) {
                LocationCache.lastLocation = syncLocation
                LocationCache.lastRefreshTime = System.currentTimeMillis()
                LocationCache.lastCoordType = CoordType.WGS84
                return syncLocation
            }
        }

        try {
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (network != null) {
                Log.d(TAG, "getCurrentLocation: fallback to NETWORK (WGS84)")
                LocationCache.lastLocation = network
                LocationCache.lastRefreshTime = System.currentTimeMillis()
                LocationCache.lastCoordType = CoordType.WGS84
                return network
            }
        } catch (_: SecurityException) {}
    }

    return null
}

private fun tryGetGpsSync(locationManager: LocationManager): Location? {
    val latch = CountDownLatch(1)
    val resultRef = AtomicReference<Location?>(null)

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            resultRef.set(location)
            LocationCache.lastLocation = location
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            LocationCache.lastCoordType = CoordType.WGS84
            latch.countDown()
            locationManager.removeUpdates(this)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    try {
        locationManager.requestSingleUpdate(
            LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper()
        )
        val acquired = latch.await(GPS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (acquired && resultRef.get() != null) {
            Log.d(TAG, "tryGetGpsSync: got fix, acc=${resultRef.get()!!.accuracy}")
            return resultRef.get()
        }
        locationManager.removeUpdates(listener)
        Log.w(TAG, "tryGetGpsSync: timeout after ${GPS_TIMEOUT_MS}ms")
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException in tryGetGpsSync", e)
    }

    return null
}

// ─── 格式化输出 ─────────────────────────────────────────────

fun formatLocation(location: Location): String {
    return "%.6f,%.6f".format(location.latitude, location.longitude)
}

fun getFormattedLocation(context: Context): String? {
    val location = getCurrentLocation(context) ?: return null

    val coordTypeName = when (LocationCache.lastCoordType) {
        CoordType.GCJ02 -> "GCJ02(国测局坐标)"
        CoordType.WGS84 -> "WGS84(GPS原生坐标)"
        CoordType.BD09 -> "BD09(百度加密坐标)"
        CoordType.UNKNOWN -> "未知坐标系"
    }

    val provider = LocationCache.providerDescription
    val accuracyNote = if (location.hasAccuracy() && location.accuracy < 100f) {
        "（GPS精确定位，精度约${location.accuracy.toInt()}米）"
    } else if (location.hasAccuracy()) {
        "（精度约${location.accuracy.toInt()}米）"
    } else {
        ""
    }

    val coords = "Latitude: %.6f, Longitude: %.6f".format(
        location.latitude, location.longitude
    )

    val coordNote = " [坐标系: $coordTypeName]"

    val baiduWarning = when (LocationCache.baiduLastErrorCode) {
        505 -> " [百度SDK错误: Key验证失败(505)，请检查API_KEY]"
        162 -> " [百度SDK错误: 隐私协议未同意(162)]"
        62 -> " [百度SDK: GPS定位失败(62)，已回退到网络/原生定位]"
        167 -> " [百度SDK: 网络定位失败(167)，WiFi/基站不可用]"
        else -> ""
    }

    val addressText = LocationCache.lastAddress
    val addrPart = if (!addressText.isNullOrBlank()) {
        "\n地址: $addressText"
    } else {
        ""
    }

    return "$coords $provider$coordNote$accuracyNote$addrPart$baiduWarning"
}

// ─── Context 工具 ──────────────────────────────────────────

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("No Activity found in context chain")
}
