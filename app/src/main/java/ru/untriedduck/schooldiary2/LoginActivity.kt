package ru.untriedduck.schooldiary2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.untriedduck.schooldiary2.api.NetworkService
import ru.untriedduck.schooldiary2.api.School
import ru.untriedduck.schooldiary2.api.SessionManager
import ru.untriedduck.schooldiary2.databinding.ActivityLoginBinding
import ru.untriedduck.schooldiary2.utils.md5

class LoginActivity : AppCompatActivity() {
    private lateinit var binding : ActivityLoginBinding
    private var searchJob: Job? = null
    private var schoolsList = listOf<School>()
    private var selectedSchoolId: Int? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupSchoolSearch()

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }
    }

    private fun setupSchoolSearch() {
        binding.edtSearchSchool.threshold = 1
        binding.edtSearchSchool.addTextChangedListener { text ->
            searchJob?.cancel()
            val query = text.toString()
            if (query.length >= 3) {
                searchJob = lifecycleScope.launch {
                    delay(600) // Debounce
                    fetchSchools(query)
                }
            }
        }

        binding.edtSearchSchool.setOnItemClickListener { _, _, position, _ ->
            val selected = schoolsList[position]
            selectedSchoolId = selected.id
            binding.edtSearchSchool.setText(selected.name, false)
        }
    }

    private suspend fun fetchSchools(query: String) {
        try {
            val response = NetworkService.api.searchSchools(query)
            if (response.isSuccessful) {
                schoolsList = response.body() ?: emptyList()
                val names = schoolsList.map { it.name }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
                binding.edtSearchSchool.setAdapter(adapter)
                binding.edtSearchSchool.showDropDown()
                if (schoolsList.isEmpty()) {
                    binding.edtSearchSchool.dismissDropDown()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun attemptLogin() {
        val session = SessionManager(this)

        val username = binding.edtUsername.text.toString()
        val password = binding.edtPassword.text.toString()
        val schoolId = selectedSchoolId

        if (username.isEmpty() || password.isEmpty() || schoolId == null) {
            Toast.makeText(this, "Заполните все поля и выберите школу", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ШАГ 1: Получаем "соль" и NSSESSIONID
                val authDataResponse = NetworkService.api.getAuthData()
                if (!authDataResponse.isSuccessful) throw Exception("Сервер АСУ не отвечает (getdata)")

                val authData = authDataResponse.body()!!
                // Вытаскиваем куку NSSESSIONID из заголовков
                val nSessionId = authDataResponse.headers().values("Set-Cookie")
                    .find { it.contains("NSSESSIONID") }?.split(";")?.first() ?: ""

                // ШАГ 2: Хеширование пароля (md5(salt + md5(password)))
                val md5Password = password.md5()
                val pw2 = (authData.salt + md5Password).md5()
                val pwShort = pw2.substring(0, password.length)

                // ШАГ 3: Запрос на вход
                val loginParams = mapOf(
                    "LoginType" to "1",
                    "cid" to "2",
                    "sid" to "1",
                    "pid" to "-232",
                    "cn" to "232",
                    "sft" to "2",
                    "scid" to schoolId.toString(),
                    "UN" to username,
                    "PW" to pwShort,
                    "lt" to authData.lt,
                    "pw2" to pw2,
                    "ver" to authData.ver
                )

                val loginResponse = NetworkService.api.login(nSessionId, params = loginParams)

                if (loginResponse.isSuccessful) {
                    val loginBody = loginResponse.body()
                    val atKey = loginBody?.at

                    // 1. Берем куки ПРЯМО из заголовков текущего ответа
                    val allSetCookies = loginResponse.headers().values("Set-Cookie")

                    // Ищем строку, которая содержит ESRNSec и НЕ пустая
                    val esrnSecValue = allSetCookies
                        .filter { it.contains("ESRNSec=") && !it.contains("ESRNSec=;") }
                        .map { it.split(";").first() } // Отрезаем expires, path и т.д.
                        .lastOrNull()

                    Log.d("ASU_DEBUG", "Куки из заголовков: $allSetCookies")
                    Log.d("ASU_DEBUG", "Выбранная кука: $esrnSecValue")

                    if (atKey != null && esrnSecValue != null) {
                        // ШАГ 4: Инициализация дневника
                        delay(500)

                        val htmlResponse = NetworkService.api.getMainPageHtml(atKey)
                        if (htmlResponse.isSuccessful) {
                            val html = htmlResponse.body() ?: ""

                            // Ищем строку appContext.yearId = "XXXX"
                            val pattern = """appContext\.yearId\s*=\s*"(\d+)"""".toRegex()
                            val match = pattern.find(html)
                            val yearId = match?.groupValues?.get(1)?.toInt() ?: -1

                            if (yearId != -1) {
                                session.saveYearId(yearId)
                                Log.d("ASU_DEBUG", "Успешно спарсили yearId: $yearId")
                            } else {
                                Log.e("ASU_DEBUG", "Не удалось найти yearId в HTML. Проверь регулярку.")
                            }
                        }

                        // ВАЖНО: Нам нужно, чтобы NetworkService УЖЕ знал эту куку перед вызовом initDiary
                        val session = SessionManager(this@LoginActivity)
                        // Временно сохраняем (studentId пока заглушка -1)
                        session.saveUserCredentials(username, password, schoolId) // Добавь эту строку
                        session.saveSession(atKey, esrnSecValue, -1)

                        // Инициализируем NetworkService, чтобы Interceptor подхватил новую куку
                        NetworkService.init(this@LoginActivity)

                        val contextResponse = NetworkService.api.getContext()
                        if (contextResponse.isSuccessful) {
                            val yearId = contextResponse.body()?.schoolYearId ?: -1
                            if (yearId != -1) {
                                session.saveYearId(yearId)
                                Log.d("ASU_DEBUG", "Нашли настоящий schoolYearId: $yearId")
                            }
                        } else {
                            Log.e("ASU_DEBUG", "Не удалось получить контекст: ${contextResponse.code()}")
                        }

                        val initResponse = NetworkService.api.initDiary(atKey)

                        if (initResponse.isSuccessful) {
                            val studentId = initResponse.body()?.students?.firstOrNull()?.studentId

                            if (studentId != null) {
                                // Сохраняем всё финально
                                session.saveSession(atKey, esrnSecValue, studentId)

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@LoginActivity, "Вход успешен!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            } else {
                                throw Exception("Список учеников пуст")
                            }
                        }
                    } else {
                        throw Exception("Сервер не прислал ключ сессии (ESRNSec)")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.INVISIBLE
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }
}