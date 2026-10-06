package com.gigabytegrove.monita.client.api;

import com.gigabytegrove.monita.client.CollectionFormats.*;

import retrofit2.Call;
import retrofit2.http.*;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;

import com.gigabytegrove.monita.client.model.MonitaInfo;
import com.gigabytegrove.monita.client.model.Health;
import com.gigabytegrove.monita.client.model.VersionInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface InfoApi {
  /**
   * Get health information.
   * 
   * @return Call&lt;Health&gt;
   */
  @GET("health")
  Call<Health> getHealth();
    

  /**
   * Get gotify information.
   * 
   * @return Call&lt;MonitaInfo&gt;
   */
  @GET("gotifyinfo")
  Call<MonitaInfo> getInfo();
    

  /**
   * Get version information.
   * 
   * @return Call&lt;VersionInfo&gt;
   */
  @GET("version")
  Call<VersionInfo> getVersion();
    

}
