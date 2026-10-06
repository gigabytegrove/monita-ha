package com.gigabytegrove.monita.login

sealed class LoginState {
    object UrlInput : LoginState()
    object CheckingUrl : LoginState()
    object Ready : LoginState()
    object LoggingIn : LoginState()
    object WaitingForClientName : LoginState()
    object CreatingClient : LoginState()
    object OidcAuthorizing : LoginState()
    object OidcWaitingForCallback : LoginState()
    object OidcExchangingToken : LoginState()
}

sealed class LoginEvent {
    data class OpenBrowser(val url: String) : LoginEvent()
    object LoginSuccess : LoginEvent()
    object ShowClientNameDialog : LoginEvent()
    data class VersionError(val url: String, val code: Int) : LoginEvent()
    data class VersionException(val url: String, val message: String) : LoginEvent()
    object InvalidCredentials : LoginEvent()
    object ClientCreationFailed : LoginEvent()
    object OidcAuthorizeFailed : LoginEvent()
    object OidcTokenExchangeFailed : LoginEvent()
}
