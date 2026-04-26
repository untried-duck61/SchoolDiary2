package ru.untriedduck.schooldiary2

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.values
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.untriedduck.schooldiary2.api.NetworkService
import ru.untriedduck.schooldiary2.api.SessionManager
import ru.untriedduck.schooldiary2.databinding.ActivityEsiaLoginBinding
import android.util.Log
import android.webkit.*
import com.google.gson.Gson

class EsiaLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEsiaLoginBinding
    private var interceptedToken: String? = null // Хранилище для токена
    private var isLoginTriggered = false // Флаг, чтобы не зайти дважды

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEsiaLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupWebView() {
        val webView = binding.webViewEsia

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        }

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onTokenIntercepted(jsonResponse: String) {
                // ШАГ 1: Просто сохраняем токен
                val token = Gson().fromJson(jsonResponse, Map::class.java)["token"] as? String
                if (!token.isNullOrEmpty()) {
                    interceptedToken = token
                    Log.d("ASU_ESIA", "Токен сохранен в память, ждем перехода на АСУ РСО...")
                }
            }
        }, "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectInterceptor(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectInterceptor(view)
                binding.webViewProgress.visibility = View.GONE

                Log.d("ASU_ESIA", "Загружена страница: $url")

                // ШАГ 2: Проверяем, перекинули ли нас обратно на АСУ РСО
                // Обычно редирект идет на https://asurso.ru/app/school/main/
                if (url != null && url.contains("asurso.ru/app/") && interceptedToken != null && !isLoginTriggered) {
                    isLoginTriggered = true
                    Log.d("ASU_ESIA", "Мы на странице АСУ РСО. Запускаем обмен токена...")

                    // Прячем WebView, чтобы пользователь не видел веб-интерфейс
                    webView.visibility = View.INVISIBLE
                    binding.webViewProgress.visibility = View.VISIBLE

                    performAsuLogin(interceptedToken!!)
                }
            }
        }

        val startUrl = "https://asurso.ru/webapi/sso/esia/login?esia_permissions=1&esia_role=1"
        webView.loadUrl(startUrl)
    }

    private fun injectInterceptor(view: WebView?) {
        val script = """
            (function() {
                // Перехват XMLHttpRequest
                var oldOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function() {
                    this.addEventListener('load', function() {
                        if (this.responseURL.includes('sfd.gosuslugi.ru/session')) {
                            AndroidBridge.onTokenIntercepted(this.responseText);
                        }
                    });
                    oldOpen.apply(this, arguments);
                };
                
                // Перехват Fetch
                var oldFetch = window.fetch;
                window.fetch = function() {
                    return oldFetch.apply(this, arguments).then(function(response) {
                        if (response.url.includes('sfd.gosuslugi.ru/session')) {
                            response.clone().text().then(function(data) {
                                AndroidBridge.onTokenIntercepted(data);
                            });
                        }
                        return response;
                    });
                };
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }


    private fun performAsuLogin(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = SessionManager(this@EsiaLoginActivity)

                // Берем актуальный NSSESSIONID прямо из кук WebView
                val cookies = CookieManager.getInstance().getCookie("https://asurso.ru/")
                val nSessionId = cookies?.split("; ")
                    ?.find { it.startsWith("NSSESSIONID=") }?.split(";")?.first() ?: ""

                val params = mapOf(
                    "LoginType" to "9",
                    "grant_type" to "refresh_token",
                    "refresh_token" to token
                )

                Log.d("ASU_ESIA", "Отправка LoginType 9 с токеном...")
                val response = NetworkService.api.loginByEsia(nSessionId, params)

                if (response.isSuccessful) {
                    val body = response.body()
                    val atKey = body?.at
                    val esrnSec = response.headers().values("Set-Cookie")
                        .find { it.contains("ESRNSec=") && !it.contains("ESRNSec=;") }
                        ?.split(";")?.first()

                    if (atKey != null && esrnSec != null) {
                        // Сохраняем метод входа для Silent Login
                        session.setLoginMethod("ESIA")
                        session.saveEsiaToken(token)
                        session.saveSession(atKey, esrnSec, -1)

                        NetworkService.init(this@EsiaLoginActivity)

                        // Получаем данные ученика через контекст
                        val ctx = NetworkService.api.getContext()
                        if (ctx.isSuccessful) {
                            session.saveSession(atKey, esrnSec, ctx.body()?.userId ?: -1)
                            session.saveYearId(ctx.body()?.schoolYearId ?: -1)
                        }

                        withContext(Dispatchers.Main) {
                            Log.d("ASU_ESIA", "Успешный переход в приложение!")
                            startActivity(Intent(this@EsiaLoginActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                } else {
                    Log.e("ASU_ESIA", "Ошибка обмена токена: ${response.code()}")
                    isLoginTriggered = false // Разрешаем повторную попытку при ошибке
                }
            } catch (e: Exception) {
                Log.e("ASU_ESIA", "Ошибка: ${e.message}")
                isLoginTriggered = false
            }
        }
    }
}