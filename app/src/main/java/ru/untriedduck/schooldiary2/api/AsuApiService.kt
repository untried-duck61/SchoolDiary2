package ru.untriedduck.schooldiary2.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AsuApiService {
    // 1. Получение соли и сессии
    @POST("webapi/auth/getdata")
    suspend fun getAuthData(): Response<AuthDataResponse>

    // 2. Поиск школы по названию
    @GET("webapi/schools/search")
    suspend fun searchSchools(
        @Query("name") name: String,
        @Query("funcType") funcType: String = "",
        @Query("withAddress") withAddress: Boolean = true
    ): Response<List<School>>

    // 3. Вход
    @FormUrlEncoded
    @POST("webapi/login")
    suspend fun login(
        @Header("Cookie") sessionCookie: String,
        @Header("Referer") referer: String = "https://asurso.ru/about.html", // Добавь это!
        @FieldMap params: Map<String, String>
    ): Response<LoginResponse>

    // 4. Получение данных ученика (после входа)
    @GET("webapi/student/diary/init")
    suspend fun initDiary(
        @Header("at") at: String
    ): Response<StudentInitResponse>

    @GET("webapi/student/diary")
    suspend fun getDiary(
        @Query("studentId") studentId: Int,
        @Query("weekStart") weekStart: String,
        @Query("weekEnd") weekEnd: String,
        @Query("yearId") yearId: Int,
        @Query("schoolId") schoolId: Int, // Добавлено
        @Query("vers") vers: Long,        // Добавлено (timestamp)
        @Query("withLaAssigns") withLaAssigns: Boolean = true
    ): Response<DiaryResponse>

    @FormUrlEncoded
    @POST("angular/school/main/")
    suspend fun getMainPageHtml(
        @Field("AT") atKey: String
    ): Response<String> // Возвращает сырой HTML

    @GET("webapi/context")
    suspend fun getContext(): Response<ContextResponse>

    @POST("webapi/student/diary/get-attachments")
    suspend fun getAttachments(
        @Query("studentId") studentId: Int,
        @Body body: AttachmentsRequest
    ): Response<List<AssignmentAttachmentsResponse>>

    @GET("webapi/attachments/{id}")
    @Streaming // Для скачивания больших файлов
    suspend fun downloadFile(@Path("id") fileId: Int): Response<ResponseBody>
}