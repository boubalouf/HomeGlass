package com.homeglass.app.data.ha

import com.homeglass.app.domain.ha.CleartextPolicy
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Client HTTP/WebSocket partagé.
 *
 * Le HTTP en clair est refusé vers une adresse publique ici, au plus près du réseau : même un
 * appel oublié ne peut pas sortir en clair. `pingInterval` garde la liaison temps réel
 * vivante sans trafic applicatif.
 */
object HttpClientFactory {

    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.scheme == "http" && !CleartextPolicy.isPrivateHost(request.url.host)) {
                throw IOException("HTTP en clair refusé vers ${request.url.host}")
            }
            chain.proceed(request)
        }
        .build()
}
