package com.gigabytegrove.monita.api

import com.gigabytegrove.monita.client.model.Application
import com.gigabytegrove.monita.client.model.Message
import com.gigabytegrove.monita.client.model.PagedMessages
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.PUT
import retrofit2.http.Path

internal interface MonitaApi {
    @GET("current/user")
    fun currentUser(): Call<MonitaCurrentUser>

    @POST("client/{id}/elevate")
    fun elevateClient(
        @Path("id") id: Long,
        @Body body: MonitaElevateRequest
    ): Call<Void>

    @GET("user")
    fun adminUsers(): Call<List<MonitaAdminUser>>

    @POST("user")
    fun createUser(@Body body: MonitaUserCreate): Call<MonitaAdminUser>

    @POST("user/{id}")
    fun updateUser(
        @Path("id") id: Long,
        @Body body: MonitaUserUpdate
    ): Call<MonitaAdminUser>

    @DELETE("user/{id}")
    fun deleteUser(@Path("id") id: Long): Call<Void>

    @GET("group")
    fun adminGroups(): Call<List<MonitaGroup>>

    @POST("group")
    fun createGroup(@Body body: MonitaGroupMutation): Call<MonitaGroup>

    @PUT("group/{id}")
    fun updateGroup(
        @Path("id") id: Long,
        @Body body: MonitaGroupMutation
    ): Call<MonitaGroup>

    @DELETE("group/{id}")
    fun deleteGroup(@Path("id") id: Long): Call<Void>

    @GET("group/{id}/members")
    fun groupMembers(@Path("id") id: Long): Call<List<MonitaGroupMember>>

    @POST("group/{id}/members")
    fun addGroupMember(
        @Path("id") id: Long,
        @Body body: MonitaGroupMemberMutation
    ): Call<Void>

    @DELETE("group/{id}/members/{userId}")
    fun removeGroupMember(
        @Path("id") id: Long,
        @Path("userId") userId: Long
    ): Call<Void>

    @GET("audit")
    fun audit(@Query("limit") limit: Int = 60): Call<List<MonitaAuditEvent>>

    @PUT("application/{id}/auto-assign")
    fun setAutoAssign(
        @Path("id") id: Long,
        @Body body: MonitaToggle
    ): Call<MonitaToggle>

    @PUT("application/{id}/member-posting")
    fun setMemberPosting(
        @Path("id") id: Long,
        @Body body: MonitaToggle
    ): Call<MonitaToggle>

    @POST("application")
    fun createChannel(@Body body: MonitaChannelMutation): Call<Application>

    @PUT("application/{id}")
    fun updateChannel(
        @Path("id") id: Long,
        @Body body: MonitaChannelMutation
    ): Call<Application>

    @DELETE("application/{id}")
    fun deleteChannel(@Path("id") id: Long): Call<Void>

    @Multipart
    @POST("application/{id}/image")
    fun uploadChannelImage(
        @Path("id") id: Long,
        @Part file: MultipartBody.Part
    ): Call<Application>

    @DELETE("application/{id}/image")
    fun deleteChannelImage(@Path("id") id: Long): Call<Application>

    @Multipart
    @POST("application/{id}/chat-message")
    fun sendChatMessage(
        @Path("id") id: Long,
        @Part("message") message: RequestBody,
        @Part("priority") priority: RequestBody,
        @Part("extras") extras: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Call<Message>

    @GET("message/{messageId}/attachment/{attachmentId}")
    fun messageAttachment(
        @Path("messageId") messageId: Long,
        @Path("attachmentId") attachmentId: Long,
        @Query("applicationId") applicationId: Long
    ): Call<ResponseBody>

    @PUT("message/{id}/assignment")
    fun assignMessage(
        @Path("id") id: Long,
        @Body body: MonitaAssignmentMutation
    ): Call<MonitaMessageWorkflow>

    @PUT("message/{id}/status")
    fun setMessageStatus(
        @Path("id") id: Long,
        @Body body: MonitaStatusMutation
    ): Call<MonitaMessageWorkflow>

    @Multipart
    @POST("message/{id}/attachment")
    fun uploadMessageAttachment(
        @Path("id") id: Long,
        @Part attachment: MultipartBody.Part
    ): Call<MonitaMessageAttachment>

    @GET("application/{id}/members")
    fun members(@Path("id") id: Long): Call<List<MonitaChannelMember>>

    @GET("application/{id}/assignable-users")
    fun assignableUsers(@Path("id") id: Long): Call<List<MonitaAdminUser>>

    @POST("application/{id}/members")
    fun upsertMember(
        @Path("id") id: Long,
        @Body body: MonitaChannelMemberMutation
    ): Call<MonitaChannelMember>

    @DELETE("application/{id}/members/{userId}")
    fun deleteMember(
        @Path("id") id: Long,
        @Path("userId") userId: Long
    ): Call<Void>

    @PUT("application/{id}/owner")
    fun transferOwner(
        @Path("id") id: Long,
        @Body body: MonitaOwnerMutation
    ): Call<MonitaOwnerMutation>

    @GET("application/{id}/assignable-groups")
    fun assignableGroups(@Path("id") id: Long): Call<List<MonitaGroup>>

    @GET("application/{id}/groups")
    fun channelGroups(@Path("id") id: Long): Call<List<MonitaChannelGroupAssignment>>

    @POST("application/{id}/groups")
    fun upsertChannelGroup(
        @Path("id") id: Long,
        @Body body: MonitaChannelGroupMutation
    ): Call<MonitaChannelGroupAssignment>

    @DELETE("application/{id}/groups/{groupId}")
    fun deleteChannelGroup(
        @Path("id") id: Long,
        @Path("groupId") groupId: Long
    ): Call<Void>

    @GET("api/mu/v1/capabilities")
    fun capabilities(): Call<MonitaCapabilities>

    @GET("application/{id}/mentionable-users")
    fun mentionableUsers(@Path("id") id: Long): Call<List<MonitaMentionableUser>>

    @POST("application/{id}/typing")
    fun setTyping(
        @Path("id") id: Long,
        @Body body: MonitaTypingRequest
    ): Call<MonitaTypingEvent>

    @PUT("application/{id}/notifications")
    fun setNotifications(
        @Path("id") id: Long,
        @Body body: MonitaToggle
    ): Call<MonitaToggle>

    @GET("message")
    fun archivedMessages(
        @Query("limit") limit: Int = 100,
        @Query("archived") archived: Boolean = true
    ): Call<PagedMessages>

    @GET("application/{id}/message")
    fun archivedChannelMessages(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 100,
        @Query("archived") archived: Boolean = true
    ): Call<PagedMessages>

    @DELETE("message/{id}")
    fun deleteMessage(@Path("id") id: Long): Call<Void>

    @POST("message/{id}/archive")
    fun archiveMessage(@Path("id") id: Long): Call<Void>

    @DELETE("message/{id}/archive")
    fun restoreMessage(@Path("id") id: Long): Call<Void>

    @POST("application/{id}/message/archive")
    fun archiveChannel(@Path("id") id: Long): Call<Void>

    @DELETE("application/{id}/message/archive")
    fun restoreChannel(@Path("id") id: Long): Call<Void>

    @POST("message/archive")
    fun archiveAll(): Call<Void>

    @DELETE("message/archive")
    fun restoreAll(): Call<Void>
}

internal data class MonitaCapabilities(
    val product: String,
    val version: String,
    val apiVersion: Int,
    val features: MonitaFeatureFlags
)

internal data class MonitaFeatureFlags(
    val sharedChannels: Boolean = false,
    val globalChannels: Boolean = false,
    val channelTypes: Boolean = false,
    val channelImages: Boolean = false,
    val chatImages: Boolean = false,
    val notificationImages: Boolean = false,
    val messageControls: Boolean = false,
    val chatChannels: Boolean = false,
    val memberPosting: Boolean = false,
    val senderIdentity: Boolean = false,
    val perUserArchive: Boolean = false,
    val perChannelMute: Boolean = false,
    val membershipManagement: Boolean = false,
    val ownershipTransfer: Boolean = false,
    val userGroups: Boolean = false,
    val auditLog: Boolean = false,
    val typingPresence: Boolean = false,
    val chatNotifications: Boolean = false,
    val mentions: Boolean = false
)

internal data class MonitaToggle(val enabled: Boolean)

internal data class MonitaAssignmentMutation(val userId: Long)

internal data class MonitaStatusMutation(val status: String)

internal data class MonitaMessageWorkflow(
    val messageId: Long,
    val assignedUserId: Long = 0,
    val status: String = "open",
    val resolvedBy: Long = 0,
    val resolvedAt: String? = null,
    val updatedAt: String? = null
)

internal data class MonitaMessageAttachment(
    val id: Long,
    val filename: String,
    val contentType: String = "",
    val size: Long = 0,
    val url: String = ""
)

internal data class MonitaMentionableUser(
    val userId: Long,
    val name: String,
    val displayName: String? = null
)

internal data class MonitaTypingRequest(val typing: Boolean)

internal data class MonitaTypingEvent(
    val type: String = "typing",
    val applicationId: Long,
    val userId: Long,
    val userName: String,
    val typing: Boolean,
    val expiresAt: String
)

internal data class MonitaCurrentUser(
    val id: Long,
    val name: String,
    val displayName: String? = null,
    val admin: Boolean = false,
    val clientId: Long? = null,
    val elevatedUntil: String? = null,
    val createdAt: String? = null
)

internal data class MonitaElevateRequest(val durationSeconds: Int = 900)

internal data class MonitaAdminUser(
    val id: Long,
    val name: String,
    val displayName: String? = null,
    val admin: Boolean = false,
    val createdAt: String? = null
)

internal data class MonitaGroup(
    val id: Long,
    val name: String,
    val description: String? = null,
    val memberCount: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

internal data class MonitaAuditEvent(
    val id: Long,
    val userId: Long = 0,
    val username: String? = null,
    val action: String,
    val target: String,
    val targetId: String? = null,
    val details: String? = null,
    val ipAddress: String? = null,
    val createdAt: String? = null
)

internal data class MonitaChannelMember(
    val userId: Long,
    val name: String,
    val owner: Boolean = false,
    val receiveNotifications: Boolean = true,
    val autoAssigned: Boolean = false,
    val groupAssigned: Boolean = false,
    val role: String = "member"
)

internal data class MonitaUserCreate(
    val name: String,
    val displayName: String = "",
    val admin: Boolean = false,
    val pass: String
)

internal data class MonitaUserUpdate(
    val name: String,
    val displayName: String = "",
    val admin: Boolean = false,
    val pass: String = ""
)

internal data class MonitaGroupMutation(
    val name: String,
    val description: String = ""
)

internal data class MonitaGroupMemberMutation(
    val userId: Long
)

internal data class MonitaGroupMember(
    val userId: Long,
    val name: String,
    val displayName: String? = null,
    val admin: Boolean = false
)

internal data class MonitaChannelMutation(
    val name: String,
    val description: String = "",
    val defaultPriority: Long? = 0,
    val autoAssign: Boolean = false,
    val allowMemberPost: Boolean = false,
    val channelType: String = "notification",
    val retentionDays: Int? = null
)

internal data class MonitaChannelMemberMutation(
    val userId: Long,
    val receiveNotifications: Boolean = true,
    val role: String = "member"
)

internal data class MonitaOwnerMutation(
    val userId: Long
)

internal data class MonitaChannelGroupMutation(
    val groupId: Long,
    val role: String = "member",
    val receiveNotifications: Boolean = true
)

internal data class MonitaChannelGroupAssignment(
    val applicationId: Long? = null,
    val groupId: Long,
    val name: String? = null,
    val role: String = "member",
    val receiveNotifications: Boolean = true,
    val memberCount: Long = 0
)
