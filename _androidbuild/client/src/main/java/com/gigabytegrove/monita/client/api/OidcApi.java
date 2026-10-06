package com.gigabytegrove.monita.client.api;

import com.gigabytegrove.monita.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;

import com.gigabytegrove.monita.client.model.Error;
import com.gigabytegrove.monita.client.model.OIDCExternalAuthorizeRequest;
import com.gigabytegrove.monita.client.model.OIDCExternalAuthorizeResponse;
import com.gigabytegrove.monita.client.model.OIDCExternalTokenRequest;
import com.gigabytegrove.monita.client.model.OIDCExternalTokenResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OidcApi {
  /**
   * Initiate the OIDC authorization flow for a native app.
   * The app generates a PKCE code_verifier and code_challenge, then calls this endpoint. The server forwards the code_challenge to the OIDC provider and returns the authorization URL for the app to open in a browser.
   * @param body  (required)
   * @return Call&lt;OIDCExternalAuthorizeResponse&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("auth/oidc/external/authorize")
  Call<OIDCExternalAuthorizeResponse> externalAuthorize(
    @retrofit2.http.Body OIDCExternalAuthorizeRequest body
  );

  /**
   * Exchange an authorization code for a gotify client token.
   * After the user authenticates with the OIDC provider and the app receives the authorization code via redirect, the app calls this endpoint with the code and PKCE code_verifier. The server exchanges the code with the OIDC provider and returns a gotify client token.
   * @param body  (required)
   * @return Call&lt;OIDCExternalTokenResponse&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("auth/oidc/external/token")
  Call<OIDCExternalTokenResponse> externalToken(
    @retrofit2.http.Body OIDCExternalTokenRequest body
  );

  /**
   * Handle the OIDC provider callback (browser).
   * Exchanges the authorization code for tokens, resolves the user, creates a gotify client, sets a session cookie, and redirects to the UI.
   * @param code the authorization code from the OIDC provider (required)
   * @param state the state parameter for CSRF protection (required)
   * @return Call&lt;Void&gt;
   */
  @GET("auth/oidc/callback")
  Call<Void> oidcCallback(
    @retrofit2.http.Query("code") String code, @retrofit2.http.Query("state") String state
  );

  /**
   * Start the OIDC flow to elevate an existing client session (browser).
   * Redirects the user to the OIDC provider&#39;s authorization endpoint. After successful authentication, the referenced client session is elevated for the requested duration.
   * @param id the client id to elevate (required)
   * @param durationSeconds how long the elevation should last, in seconds (required)
   * @return Call&lt;Error&gt;
   */
  @GET("auth/oidc/elevate")
  Call<Error> oidcElevate(
    @retrofit2.http.Query("id") Long id, @retrofit2.http.Query("durationSeconds") Integer durationSeconds
  );

  /**
   * Start the OIDC login flow (browser).
   * Redirects the user to the OIDC provider&#39;s authorization endpoint. After authentication, the provider redirects back to the callback endpoint.
   * @param name the client name to create after login (required)
   * @return Call&lt;Error&gt;
   */
  @GET("auth/oidc/login")
  Call<Error> oidcLogin(
    @retrofit2.http.Query("name") String name
  );

}
