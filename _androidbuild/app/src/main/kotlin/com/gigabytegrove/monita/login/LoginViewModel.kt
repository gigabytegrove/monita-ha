package com.gigabytegrove.monita.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gigabytegrove.monita.Settings
import com.gigabytegrove.monita.api.Callback
import com.gigabytegrove.monita.api.ClientFactory
import com.gigabytegrove.monita.client.ApiClient
import com.gigabytegrove.monita.client.api.ClientApi
import com.gigabytegrove.monita.client.api.UserApi
import com.gigabytegrove.monita.client.model.ClientParams
import com.gigabytegrove.monita.client.model.MonitaInfo
import com.gigabytegrove.monita.client.model.OIDCExternalAuthorizeRequest
import com.gigabytegrove.monita.client.model.OIDCExternalTokenRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.tinylog.kotlin.Logger

internal class LoginViewModel(private val settings: Settings) : ViewModel() {

    private val _state = MutableLiveData<LoginState>(LoginState.UrlInput)
    val state: LiveData<LoginState> = _state

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    var monitaInfo: MonitaInfo? = null
        private set

    private var authenticatedClient: ApiClient? = null

    fun invalidateUrl() {
        monitaInfo = null
        _state.value = LoginState.UrlInput
    }

    fun checkUrl(url: String) {
        _state.value = LoginState.CheckingUrl
        settings.url = url

        try {
            ClientFactory.infoApi(settings, settings.sslSettings(), url)
                .info
                .enqueue(
                    Callback.call(
                        onSuccess = Callback.SuccessBody { info ->
                            monitaInfo = info
                            _state.value = LoginState.Ready
                        },
                        onError = { _ -> tryVersionEndpoint(url) }
                    )
                )
        } catch (e: Exception) {
            tryVersionEndpoint(url)
        }
    }

    private fun tryVersionEndpoint(url: String) {
        try {
            ClientFactory.infoApi(settings, settings.sslSettings(), url)
                .version
                .enqueue(
                    Callback.call(
                        onSuccess = Callback.SuccessBody { version ->
                            monitaInfo = MonitaInfo()
                                .version(version.version)
                                .oidc(false)
                                .register(false)
                            _state.value = LoginState.Ready
                        },
                        onError = { exception ->
                            _state.value = LoginState.UrlInput
                            _events.trySend(
                                LoginEvent.VersionError(url, exception.code)
                            )
                        }
                    )
                )
        } catch (e: Exception) {
            _state.value = LoginState.UrlInput
            _events.trySend(
                LoginEvent.VersionException(url, e.message ?: "")
            )
        }
    }

    fun login(username: String, password: String) {
        _state.value = LoginState.LoggingIn

        val client = ClientFactory.basicAuth(settings, settings.sslSettings(), username, password)
        authenticatedClient = client
        client.createService(UserApi::class.java)
            .currentUser()
            .enqueue(
                Callback.call(
                    onSuccess = {
                        _state.value = LoginState.WaitingForClientName
                        _events.trySend(LoginEvent.ShowClientNameDialog)
                    },
                    onError = {
                        authenticatedClient = null
                        _state.value = LoginState.Ready
                        _events.trySend(LoginEvent.InvalidCredentials)
                    }
                )
            )
    }

    fun createClient(name: String) {
        val client = authenticatedClient ?: return
        _state.value = LoginState.CreatingClient

        client.createService(ClientApi::class.java)
            .createClient(ClientParams().name(name))
            .enqueue(
                Callback.call(
                    onSuccess = Callback.SuccessBody { c ->
                        settings.token = c.token
                        _events.trySend(LoginEvent.LoginSuccess)
                    },
                    onError = {
                        _state.value = LoginState.Ready
                        _events.trySend(LoginEvent.ClientCreationFailed)
                    }
                )
            )
    }

    fun cancelClientCreation() {
        authenticatedClient = null
        _state.value = LoginState.Ready
    }

    fun startOidcAuthorize(clientName: String) {
        _state.value = LoginState.OidcAuthorizing

        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        settings.oidcCodeVerifier = codeVerifier

        val request = OIDCExternalAuthorizeRequest()
            .codeChallenge(codeChallenge)
            .redirectUri(OIDC_REDIRECT_URI)
            .name(clientName)

        Logger.info("OIDC: starting authorization flow")

        ClientFactory.oidcApi(settings)
            .externalAuthorize(request)
            .enqueue(
                Callback.call(
                    onSuccess = Callback.SuccessBody { response ->
                        Logger.info("OIDC: authorization URL received")
                        settings.oidcState = response.state
                        _state.value = LoginState.OidcWaitingForCallback
                        _events.trySend(
                            LoginEvent.OpenBrowser(response.authorizeUrl)
                        )
                    },
                    onError = {
                        Logger.error("OIDC: authorize failed")
                        _state.value = LoginState.Ready
                        _events.trySend(LoginEvent.OidcAuthorizeFailed)
                    }
                )
            )
    }

    fun handleOidcCallback(code: String, oidcState: String) {
        val expectedState = settings.oidcState
        if (expectedState == null || expectedState != oidcState) {
            Logger.warn("OIDC: callback state mismatch")
            _events.trySend(LoginEvent.OidcTokenExchangeFailed)
            return
        }

        val verifier = settings.oidcCodeVerifier
        if (verifier == null) {
            Logger.warn("OIDC: callback missing code verifier")
            return
        }

        settings.oidcCodeVerifier = null
        settings.oidcState = null
        _state.value = LoginState.OidcExchangingToken

        val request = OIDCExternalTokenRequest()
            .code(code)
            .state(oidcState)
            .codeVerifier(verifier)

        Logger.info("OIDC: exchanging authorization code for client token")

        ClientFactory.oidcApi(settings, settings.sslSettings())
            .externalToken(request)
            .enqueue(
                Callback.call(
                    onSuccess = Callback.SuccessBody { response ->
                        settings.token = response.token
                        Logger.info("OIDC: login successful as ${response.user.name}")
                        _events.trySend(LoginEvent.LoginSuccess)
                    },
                    onError = {
                        Logger.error("OIDC: token exchange failed")
                        _state.value = LoginState.Ready
                        _events.trySend(LoginEvent.OidcTokenExchangeFailed)
                    }
                )
            )
    }

    class Factory(private val settings: Settings) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(settings) as T
        }
    }

    companion object {
        const val OIDC_REDIRECT_URI = "gotifymu://oidc/callback"
    }
}
