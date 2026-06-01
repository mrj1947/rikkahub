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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "LocationUtils"
private const val REQUEST_LOCATION_PERMISSION_CODE = 1001
private const val GPS_TIMEOUT_MS = 10000L
private const val GPS_CACHE_FRESH_MS = 120_000L // 2分钟内的GPS缓存有效

/**
 * 最近一次获取到的 GPS 位置缓存
 * 由持续监听器更新，供同步读取
 */
object LocationCache {
    var lastLocation: Location? = null
    var lastRefreshTime: Long = 0L
    var isListening: Boolean = false

    /** 获取位置的提供者描述 */
    val providerDescription: String
        get() {
            val loc = lastLocation ?: return "无"
            return when (loc.provider) {
                LocationManager.GPS_PROVIDER -> "GPS"
                LocationManager.NETWORK_PROVIDER -> "基站/WiFi"
                LocationManager.PASSIVE_PROVIDER -> "被动定位"
                else -> loc.provider ?: "未知"
            }
        }

    /** 获取位置精度描述 */
    val accuracyDescription: String
        get() {
            val loc = lastLocation ?: return ""
            return if (loc.hasAccuracy()) "${loc.accuracy.toInt()}米" else "未知"
        }
}

// ─── 持续 GPS 监听 ─────────────────────────────────

/**
 * 全局唯一的持续 GPS 监听器
 * 只要位置开关开着就保持监听，一旦有新的 GPS 信号就更新缓存
 */
private var continuousListener: LocationListener? = null

/**
 * 启动持续 GPS 监听
 * 当用户开启位置开关时调用
 * - 如果权限不足，什么都不做
 * - 已经在监听，先停止再重启
 */
fun startContinuousLocationListening(context: Context) {
    if (!hasLocationPermission(context)) {
        Log.w(TAG, "startContinuousLocationListening: no permission")
        return
    }

    // 如果已经在监听，先停掉
    if (LocationCache.isListening) {
        stopContinuousLocationListening(context)
    }

    try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Continuous GPS fix: ${location.latitude},${location.longitude} acc=${location.accuracy} provider=${location.provider}")
                LocationCache.lastLocation = location
                LocationCache.lastRefreshTime = System.currentTimeMillis()
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider enabled: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider disabled: $provider")
            }
        }

        // 请求持续位置更新（1秒最小间隔，5米最小距离）
        // 优先用 GPS_PROVIDER，如果 GPS 没开则用 NETWORK_PROVIDER
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )

        var anyProviderStarted = false
        for (provider in providers) {
            if (locationManager.isProviderEnabled(provider)) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L,           // 最小时间间隔：1秒
                        5f,              // 最小距离变化：5米
                        listener,
                        Looper.getMainLooper()
                    )
                    Log.d(TAG, "Started continuous listening on $provider")
                    anyProviderStarted = true
                } catch (e: SecurityException) {
                    Log.w(TAG, "Security exception starting $provider", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Error starting $provider", e)
                }
            }
        }

        if (anyProviderStarted) {
            continuousListener = listener
            LocationCache.isListening = true
            Log.i(TAG, "Continuous location listening started")
        } else {
            Log.w(TAG, "No location provider available")
        }

        // 同时也尝试获取一次上次的已知位置作为初始缓存
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                LocationCache.lastLocation = lastKnown
                LocationCache.lastRefreshTime = System.currentTimeMillis()
                Log.d(TAG, "Initial cache from lastKnown: ${lastKnown.provider}")
            }
        } catch (_: SecurityException) {}

    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception in startContinuousLocationListening", e)
    } catch (e: Exception) {
        Log.e(TAG, "Error starting continuous location listening", e)
    }
}

/**
 * 停止持续 GPS 监听
 * 当用户关闭位置开关时调用
 */
fun stopContinuousLocationListening(context: Context) {
    val listener = continuousListener ?: return
    try {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager.removeUpdates(listener)
    } catch (e: Exception) {
        Log.w(TAG, "Error stopping location listening", e)
    }
    continuousListener = null
    LocationCache.isListening = false
    Log.i(TAG, "Continuous location listening stopped")
}

// ─── 权限相关 ─────────────────────────────────────

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
 * 授权后自动启动持续定位
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
        // 权限已有，启动持续监听
        startContinuousLocationListening(context)
    }
}

// ─── 获取位置（同步读取）────────────────────────────

/**
 * 获取当前位置（同步方法）
 *
 * 优先返回持续监听器缓存的新鲜 GPS 数据
 * 如果缓存过期或不存在，尝试获取一次新鲜的 GPS 定位（最多等 10 秒）
 * 超时后拿 NETWORK 位置兜底
 */
fun getCurrentLocation(context: Context): Location? {
    // 1. 检查缓存是否有新鲜 GPS 数据（2分钟内）
    val cache = LocationCache.lastLocation
    val cacheAge = System.currentTimeMillis() - LocationCache.lastRefreshTime
    if (cache != null && cacheAge < GPS_CACHE_FRESH_MS) {
        Log.d(TAG, "Using cache: ${cache.provider}, age=${cacheAge}ms, acc=${if (cache.hasAccuracy()) cache.accuracy else "N/A"}")
        return cache
    }

    if (!hasLocationPermission(context)) return null

    val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    // 2. 尝试获取最新已知位置（缓存已过期但 lastKnown 是新数据）
    try {
        val gpsLast = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (gpsLast != null && System.currentTimeMillis() - gpsLast.time < GPS_CACHE_FRESH_MS) {
            Log.d(TAG, "getCurrentLocation: lastKnown GPS, acc=${gpsLast.accuracy}")
            LocationCache.lastLocation = gpsLast
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return gpsLast
        }
    } catch (_: SecurityException) {}

    // 3. 如果 GPS 没在持续监听，尝试同步获取一次新鲜定位
    if (!LocationCache.isListening && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        Log.d(TAG, "No continuous listener, trying one-shot GPS...")
        val syncLocation = tryGetGpsSync(locationManager)
        if (syncLocation != null) {
            LocationCache.lastLocation = syncLocation
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return syncLocation
        }
    }

    // 4. 兜底：获取网络位置
    try {
        val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (network != null) {
            Log.d(TAG, "getCurrentLocation: fallback to NETWORK, acc=${if (network.hasAccuracy()) network.accuracy else "N/A"}")
            LocationCache.lastLocation = network
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return network
        }
    } catch (_: SecurityException) {}

    // 5. 最后的兜底：被动位置
    try {
        val passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        if (passive != null) {
            LocationCache.lastLocation = passive
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            return passive
        }
    } catch (_: SecurityException) {}

    return null
}

/**
 * 同步等待一次 GPS 定位（使用 suspendCancellableCoroutine 优雅等待）
 * 最多等 GPS_TIMEOUT_MS 毫秒
 */
private fun tryGetGpsSync(locationManager: LocationManager): Location? {
    // 使用 CountDownLatch 在主线程回调上同步等待
    val latch = java.util.concurrent.CountDownLatch(1)
    var result: Location? = null

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            result = location
            LocationCache.lastLocation = location
            LocationCache.lastRefreshTime = System.currentTimeMillis()
            latch.countDown()
            locationManager.removeUpdates(this)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    try {
        locationManager.requestSingleUpdate(
            LocationManager.GPS_PROVIDER,
            listener,
            Looper.getMainLooper()
        )
        val acquired = latch.await(GPS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (acquired && result != null) {
            Log.d(TAG, "tryGetGpsSync: got fix, acc=${result!!.accuracy}")
            return result
        }
        // 超时，移除监听
        locationManager.removeUpdates(listener)
        Log.w(TAG, "tryGetGpsSync: timeout after ${GPS_TIMEOUT_MS}ms")
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException in tryGetGpsSync", e)
    }

    return null
}

// ─── 格式化输出 ──────────────────────────────────

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
        val accuracyNote = if (location.hasAccuracy() && location.accuracy < 100f) {
            "（GPS 精确定位，精度约${location.accuracy.toInt()}米）"
        } else if (location.hasAccuracy()) {
            "（精度约${location.accuracy.toInt()}米）"
        } else {
            ""
        }
        "Latitude: %.6f, Longitude: %.6f $provider$accuracyNote".format(
            location.latitude,
            location.longitude
        )
    }
}

// ─── Context 工具 ────────────────────────────────

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
