package com.gigabytegrove.monita.client.api;

import com.gigabytegrove.monita.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;

import com.gigabytegrove.monita.client.model.CurrentUser;
import com.gigabytegrove.monita.client.model.Error;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AuthApi {
  /**
   * Authenticate via basic auth and create a session.
   * 
   * @param name the client name to create (required)
   * @return Call&lt;CurrentUser&gt;
   */
  @retrofit2.http.FormUrlEncoded
  @POST("auth/local/login")
  Call<CurrentUser> localLogin(
    @retrofit2.http.Field("name") String name
  );

  /**
   * End the current session.
   * Clears the session cookie and deletes the associated client.
   * @return Call&lt;Void&gt;
   */
  @POST("auth/logout")
  Call<Void> logout();
    

}
