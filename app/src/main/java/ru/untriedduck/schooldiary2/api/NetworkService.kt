package ru.untriedduck.schooldiary2.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkService {
    private var sessionManager: SessionManager? = null

    fun init(context: Context) {
        sessionManager = SessionManager(context)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val requestBuilder = request.newBuilder()

            // Стандартные заголовки
            requestBuilder.header("User-Agent", "Mozilla/5.0 ...")

            // Логика: если это НЕ запрос авторизации, добавляем сохраненные ключи
            val path = request.url.encodedPath
            if (!path.contains("auth/getdata") && !path.contains("login") && !path.contains("schools/search")) {
                sessionManager?.getAtKey()?.let { requestBuilder.header("at", it) }
                sessionManager?.getEsrnCookie()?.let { requestBuilder.header("Cookie", it) }
            }

            chain.proceed(requestBuilder.build())
        }
        .addInterceptor(logging)
        .build()

    val api: AsuApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://asurso.ru/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AsuApiService::class.java)
    }

    // Добавляем метод для полной очистки сессии
    fun logout() {
        // Очищаем куки в OkHttp
        // Если ты используешь кастомный CookieJar, нужно очистить его store
        // Но проще всего пересоздать клиент или использовать clear на менеджер
        sessionManager?.saveSession("", "", -1) // Затираем в Prefs
    }

    // Чтобы иметь доступ к кукам извне, если нужно
    fun clearCookies() {
        // Если мы используем стандартный CookieJar в виде анонимного объекта,
        // его сложно достать. Проще всего сделать так:
        client.dispatcher.executorService.shutdown()
        // На самом деле, для АСУ РСО достаточно, чтобы мы НЕ посылали старую куку
        // в момент запроса /auth/getdata и /login.
    }
}