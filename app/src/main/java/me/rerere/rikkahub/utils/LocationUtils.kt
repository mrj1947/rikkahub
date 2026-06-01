package me.rerere.rikkahub.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val TAG = "LocationUtils"
private const val REQUEST_LOCATION_PERMISSION_CODE = 1001
private const val GPS_TIMEOUT_MS = 5000L

/**
 * 缓存最近一次获取到的 GPS 位置
 * 用于 PlaceholderTransformer 同步读取
 */
object LocationCache {
    var lastLocation: Location? = null
    var lastRefreshTime: Long = 0L
}

/**
 * 检查位置权限是否已授予
 */
fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 请求位置权限
 */
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
        // 权限已有，直接开始获取 GPS 定位
        requestFreshGpsLocation(context)
    }
}

/**
 * 主动请求一次新鲜 GPS 定位（异步）
 * 使用 GPS_PROVIDER 获取实时 GPS 数据，而不是用缓存位置
 * 获取成功后更新 LocationCache
 */
fun requestFreshGpsLocation(context: Context) {
    if (!hasLocationPermission(context)) {
        Log.w(TAG, "No location permission, cannot request GPS")
        return
    }

    try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return

        // 检查 GPS 是否开启
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "GPS provider is disabled on device")
            // 尝试用 NETWORK_PROVIDER 作为备选
            requestNetworkLocation(locationManager, context)
            return
        }

        Log.d(TAG, "Requesting fresh GPS fix...")

        val gpsListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "GPS fix obtained: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")
                LocationCache.lastLocation = location
                LocationCache.lastRefreshTime = System.currentTimeMillis()
                locationManager.removeUpdates(this)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 先尝试拿一次缓存中的 GPS 位置（如果有的话）
        val cachedGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (cachedGps != null && System.currentTimeMillis() - cachedGps.time < 60000) {
            // 1分钟内的 GPS 缓存可用
            Log.d(TAG, "Using cached GPS fix from ${cachedGps.time}")
            LocationCache.lastLocation = cachedGps
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return
        }

        // 请求一次新的 GPS 位置更新
        locationManager.requestSingleUpdate(
            LocationManager.GPS_PROVIDER,
            gpsListener,
            Looper.getMainLooper()
        )

        // 设置超时：5秒后如果 GPS 还没定位到，就用缓存或网络位置
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (LocationCache.lastLocation == null ||
                LocationCache.lastRefreshTime < System.currentTimeMillis() - 1000) {
                Log.d(TAG, "GPS timeout, falling back to network")
                locationManager.removeUpdates(gpsListener)
                requestNetworkLocation(locationManager, context)
            }
        }, GPS_TIMEOUT_MS)

    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception requesting GPS", e)
    } catch (e: Exception) {
        Log.e(TAG, "Error requesting GPS location", e)
    }
}

/**
 * 通过 NETWORK_PROVIDER 获取位置（基站/WiFi 定位，精度较低）
 */
private fun requestNetworkLocation(locationManager: LocationManager, context: Context) {
    try {
        val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (networkLocation != null) {
            Log.d(TAG, "Got network location: ${networkLocation.latitude}, ${networkLocation.longitude}")
            LocationCache.lastLocation = networkLocation
            LocationCache.lastRefreshTime = System.currentTimeMillis()
        } else {
            Log.w(TAG, "No network location available either")
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception getting network location", e)
    }
}

/**
 * 获取当前位置（同步方法）
 * 优先使用缓存的新鲜 GPS 数据，否则 fallback 到 getLastKnownLocation
 */
fun getCurrentLocation(context: Context): Location? {
    // 如果缓存中有 2 分钟内的新鲜位置，直接返回
    if (LocationCache.lastLocation != null &&
        System.currentTimeMillis() - LocationCache.lastRefreshTime < 120000) {
        return LocationCache.lastLocation
    }

    // 否则尝试获取 GPS 的 last known location
    if (!hasLocationPermission(context)) return null

    return try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

        // 优先 GPS
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            Log.d(TAG, "getCurrentLocation: using GPS provider")
            LocationCache.lastLocation = it
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return it
        }

        // 其次 NETWORK
        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
            Log.d(TAG, "getCurrentLocation: using NETWORK provider")
            LocationCache.lastLocation = it
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return it
        }

        // 最后 PASSIVE
        locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)?.let {
            Log.d(TAG, "getCurrentLocation: using PASSIVE provider")
            LocationCache.lastLocation = it
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return it
        }

        null
    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception getting location", e)
        null
    }
}

/**
 * 格式化位置信息为文本
 */
fun formatLocation(location: Location): String {
    return "%.6f,%.6f".format(location.latitude, location.longitude)
}

/**
 * 获取格式化的完整位置信息
 */
fun getFormattedLocation(context: Context): String? {
    return getCurrentLocation(context)?.let { location ->
        val provider = when {
            location.provider == LocationManager.GPS_PROVIDER -> "GPS"
            location.provider == LocationManager.NETWORK_PROVIDER -> "基站/WiFi"
            location.provider == LocationManager.PASSIVE_PROVIDER -> "被动"
            else -> location.provider
        }
        // 判断精度是否来自 GPS（精度 < 100m 通常意味着 GPS 锁定）
        val accuracyNote = if (location.hasAccuracy() && location.accuracy < 100f) {
            "（GPS 精确定位）"
        } else if (location.hasAccuracy()) {
            "（精度约 ±${location.accuracy.toInt()}m）"
        } else {
            ""
        }
        "Latitude: %.6f, Longitude: %.6f$accuracyNote".format(
            location.latitude,
            location.longitude
        )
    }
}

/**
 * 从 Context 递归查找 Activity
 */
fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("No Activity found in context chain")
}
