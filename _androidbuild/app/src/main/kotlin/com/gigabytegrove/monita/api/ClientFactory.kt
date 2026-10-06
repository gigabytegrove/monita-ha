package com.gigabytegrove.monita.api

import com.gigabytegrove.monita.SSLSettings
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.client.ApiClient
import com.gigabytegrove.monita.client.api.InfoApi
import com.gigabytegrove.monita.client.api.OidcApi
import com.gigabytegrove.monita.client.api.UserApi
import com.gigabytegrove.monita.client.auth.ApiKeyAuth
import com.gigabytegrove.monita.client.auth.HttpBasicAuth

internal object ClientFactory {
    private fun unauthorized(
        settings: Settings,
        sslSettings: SSLSettings,
        baseUrl: String
    ): ApiClient {
        return defaultClient(arrayOf(), settings, sslSettings, baseUrl)
    }

    fun basicAuth(
        settings: Settings,
        sslSettings: SSLSettings,
        username: String,
        password: String
    ): ApiClient {
        val client = defaultClient(arrayOf("basicAuth"), settings, sslSettings)
        val auth = client.apiAuthorizations["basicAuth"] as HttpBasicAuth
        auth.username = username
        auth.password = password
        return client
    }

    fun clientToken(settings: Settings, token: String? = settings.token): ApiClient {
        val client = defaultClient(arrayOf("clientTokenHeader"), settings)
        val tokenAuth = client.apiAuthorizations["clientTokenHeader"] as ApiKeyAuth
        tokenAuth.apiKey = token
        return client
    }

    fun infoApi(
        settings: Settings,
        sslSettings: SSLSettings = settings.sslSettings(),
        baseUrl: String = settings.url
    ): InfoApi {
        return unauthorized(settings, sslSettings, baseUrl).createService(InfoApi::class.java)
    }

    fun oidcApi(
        settings: Settings,
        sslSettings: SSLSettings = settings.sslSettings(),
        baseUrl: String = settings.url
    ): OidcApi {
        return unauthorized(settings, sslSettings, baseUrl).createService(OidcApi::class.java)
    }

    fun userApiWithToken(settings: Settings): UserApi {
        return clientToken(settings).createService(UserApi::class.java)
    }

    private fun defaultClient(
        authentications: Array<String>,
        settings: Settings,
        sslSettings: SSLSettings = settings.sslSettings(),
        baseUrl: String = settings.url
    ): ApiClient {
        val client = ApiClient(authentications)
        CertUtils.applySslSettings(client.okBuilder, sslSettings)
        client.adapterBuilder.baseUrl("$baseUrl/")
        return client
    }
}
