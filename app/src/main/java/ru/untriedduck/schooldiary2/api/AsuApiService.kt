package ru.untriedduck.schooldiary2.api

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

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
        @Query("yearId") yearId: Int
    ): Response<DiaryResponse>
}