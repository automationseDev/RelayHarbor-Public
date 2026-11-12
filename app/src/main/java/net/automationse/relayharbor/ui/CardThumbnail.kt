package net.automationse.relayharbor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.automationse.relayharbor.data.PublicThumbnailUrl
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Thumbnails load only while a message is composed, over authenticated TLS.
 * Never follow redirects or fetch local/LAN hosts. Keep memory and traffic bounded.
 * The remote image host receives the usual network request/IP address.
 */
internal object ThumbnailFetcher {
    private const val MAX_DOWNLOAD_BYTES = 1_500_000
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    private fun isPublicIp(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isMulticastAddress
        ) return false
        val raw = address.address
        if (address is Inet4Address) {
            val first = raw[0].toInt() and 255
            val second = raw[1].toInt() and 255
            if (first == 0 || first >= 224 ||
                (first == 100 && second in 64..127) ||
                (first == 169 && second == 254) ||
                (first == 192 && second == 0) ||
                (first == 198 && second in 18..19)
            ) return false
        }
        if (address is Inet6Address) {
            val first = raw[0].toInt() and 255
            if ((first and 0xfe) == 0xfc) return false // IPv6 unique-local
        }
        return true
    }

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val safe = PublicThumbnailUrl.validate(url) ?: return@withContext null
        runCatching {
            val host = URL(safe).host
            val records = InetAddress.getAllByName(host)
            if (records.isEmpty() || records.any { !isPublicIp(it) }) {
                return@runCatching null
            }
            val connection = URL(safe).openConnection() as HttpsURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif")
            try {
                if (connection.responseCode != 200) return@runCatching null
                val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                if (type !in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) {
                    return@runCatching null
                }
                if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) return@runCatching null
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                connection.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (bytes.size() + n > MAX_DOWNLOAD_BYTES) return@runCatching null
                        bytes.write(buffer, 0, n)
                    }
                }
                val data = bytes.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                    bounds.outWidth > 20000 || bounds.outHeight > 20000
                ) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 960 ||
                    bounds.outHeight / sample > 640
                ) sample *= 2
                val result = BitmapFactory.decodeByteArray(
                    data, 0, data.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
                if (result != null) cache.put(safe, result)
                result
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

@Composable
internal fun CardThumbnail(url: String, title: String) {
    var bitmap by remember(url) { mutableStateOf(ThumbnailFetcher.cached(url)) }
    var loading by remember(url) { mutableStateOf(bitmap == null) }

    LaunchedEffect(url) {
        if (bitmap == null) bitmap = ThumbnailFetcher.load(url)
        loading = false
    }

    val image = bitmap
    when {
        image != null -> Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "$title のサムネイル",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(160.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        loading -> Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
