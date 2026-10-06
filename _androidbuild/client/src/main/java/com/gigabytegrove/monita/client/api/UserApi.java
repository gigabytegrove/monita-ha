package com.gigabytegrove.monita.client.api;

import com.gigabytegrove.monita.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;

import com.gigabytegrove.monita.client.model.CreateUserExternal;
import com.gigabytegrove.monita.client.model.Error;
import com.gigabytegrove.monita.client.model.UpdateUserExternal;
import com.gigabytegrove.monita.client.model.User;
import com.gigabytegrove.monita.client.model.UserPass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserApi {
  /**
   * Create a user.
   * With enabled registration: non admin users can be created without authentication. With disabled registrations: users can only be created by admin users.  Requires elevated authentication.
   * @param body the user to add (required)
   * @return Call&lt;User&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("user")
  Call<User> createUser(
    @retrofit2.http.Body CreateUserExternal body
  );

  /**
   * Return the current user.
   * Requires elevated authentication.
   * @return Call&lt;User&gt;
   */
  @GET("current/user")
  Call<User> currentUser();
    

  /**
   * Deletes a user.
   * Requires elevated authentication.
   * @param id the user id (required)
   * @return Call&lt;Void&gt;
   */
  @DELETE("user/{id}")
  Call<Void> deleteUser(
    @retrofit2.http.Path("id") Long id
  );

  /**
   * Get a user.
   * Requires elevated authentication.
   * @param id the user id (required)
   * @return Call&lt;User&gt;
   */
  @GET("user/{id}")
  Call<User> getUser(
    @retrofit2.http.Path("id") Long id
  );

  /**
   * Return all users.
   * Requires elevated authentication.
   * @return Call&lt;List&lt;User&gt;&gt;
   */
  @GET("user")
  Call<List<User>> getUsers();
    

  /**
   * Update the password of the current user.
   * Requires elevated authentication.
   * @param body the user (required)
   * @return Call&lt;Void&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("current/user/password")
  Call<Void> updateCurrentUser(
    @retrofit2.http.Body UserPass body
  );

  /**
   * Update a user.
   * Requires elevated authentication.
   * @param id the user id (required)
   * @param body the updated user (required)
   * @return Call&lt;User&gt;
   */
  @Headers({
    "Content-Type:application/json"
  })
  @POST("user/{id}")
  Call<User> updateUser(
    @retrofit2.http.Path("id") Long id, @retrofit2.http.Body UpdateUserExternal body
  );

}
