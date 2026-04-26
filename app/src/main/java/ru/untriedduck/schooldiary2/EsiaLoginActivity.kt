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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEsiaLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    private fun setupWebView() {
        val webView = binding.webViewEsia
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // Важно для Госуслуг

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.webViewProgress.visibility = View.GONE

                // Проверяем куки при каждой загрузке страницы
                checkCookies(url ?: "")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Позволяем WebView самому обрабатывать редиректы
            }
        }

        // URL для начала входа через ЕСИА (для Самары)
        // Внимание: иногда нужно передать дополнительные параметры региона
        val esiaUrl = "https://asurso.ru/webapi/auth/esia/login"
        webView.loadUrl(esiaUrl)
    }

    private fun checkCookies(url: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url) ?: ""

        // Ищем нашу главную куку
        if (cookies.contains("ESRNSec")) {
            // Если кука появилась, значит вход в систему произошел
            // Теперь нам нужно вытащить 'at' токен.
            // В ESIA-потоке он обычно отдается сервером после успешного редиректа.
            extractSessionAndFinish(cookies)
        }
    }

    private fun extractSessionAndFinish(cookieString: String) {
        val esrnSec = cookieString.split("; ")
            .find { it.startsWith("ESRNSec=") } ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Сначала инициализируем NetworkService с новой кукой
                val session = SessionManager(this@EsiaLoginActivity)
                // 'at' пока не знаем, ставим временный, чтобы сделать запрос за контекстом
                session.saveSession("temp", esrnSec, -1)
                NetworkService.init(this@EsiaLoginActivity)

                // 2. Запрашиваем контекст, чтобы получить настоящий 'at' и 'studentId'
                val contextResp = NetworkService.api.getContext()
                if (contextResp.isSuccessful) {
                    val context = contextResp.body()
                    val realAt = context?.at
                    val userId = context?.userId ?: -1

                    if (realAt != null) {
                        // 3. Теперь у нас есть ВСЁ. Сохраняем финально.
                        session.saveSession(realAt, esrnSec, userId)

                        // Получаем yearId для коллекции
                        val yearId = context.schoolYearId
                        session.saveYearId(yearId)

                        withContext(Dispatchers.Main) {
                            startActivity(Intent(this@EsiaLoginActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}