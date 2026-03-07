package ru.untriedduck.schooldiary2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.untriedduck.schooldiary2.adapters.DiaryAdapter
import ru.untriedduck.schooldiary2.api.AttachmentsRequest
import ru.untriedduck.schooldiary2.api.DiaryResponse
import ru.untriedduck.schooldiary2.api.NetworkService
import ru.untriedduck.schooldiary2.api.SessionManager
import ru.untriedduck.schooldiary2.databinding.ActivityMainBinding
import ru.untriedduck.schooldiary2.utils.md5
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var diaryAdapter: DiaryAdapter

    private val calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+4"), Locale("ru"))
    private val displaySdf = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
    private val apiSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        NetworkService.init(this)
        if (!checkSession()) return

        setupRecyclerView()
        setupNavigation()
        refreshData()

        binding.swipeRefresh.setOnRefreshListener {
            loadDiaryForDate(calendar.time)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        diaryAdapter = DiaryAdapter(emptyList())
        binding.rvDiary.layoutManager = LinearLayoutManager(this)
        binding.rvDiary.adapter = diaryAdapter
    }

    private fun setupNavigation() {
        binding.btnPreviousWeek.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            refreshData()
        }
        binding.btnNextWeek.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            refreshData()
        }
    }

    private fun refreshData() {
        val dateText = displaySdf.format(calendar.time)
        binding.tvCurrentWeek.text = dateText.replaceFirstChar { it.uppercase() }
        loadDiaryForDate(calendar.time)
    }

    private fun loadDiaryForDate(date: Date) {
        val weekCalendar = Calendar.getInstance(Locale("ru"))
        weekCalendar.time = date
        weekCalendar.firstDayOfWeek = Calendar.MONDAY
        weekCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val startApi = apiSdf.format(weekCalendar.time)
        weekCalendar.add(Calendar.DAY_OF_WEEK, 6)
        val endApi = apiSdf.format(weekCalendar.time)

        val session = SessionManager(this)
        val studentId = session.getStudentId()

        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoData.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var yearId = session.getYearId()
                val schoolId = session.getSchoolId()

                // Делаем первый запрос
                var response = NetworkService.api.getDiary(
                    studentId = studentId,
                    weekStart = startApi,
                    weekEnd = endApi,
                    yearId = yearId,
                    schoolId = schoolId,
                    vers = System.currentTimeMillis()
                )

                // Если 401 - пробуем перелогиниться
                if (response.code() == 401) {
                    Log.d("ASU_DEBUG", "Сессия истекла, выполняем Silent Login...")
                    if (performSilentLogin()) {
                        // Повторяем запрос с новыми данными
                        response = NetworkService.api.getDiary(
                            studentId = session.getStudentId(),
                            weekStart = startApi,
                            weekEnd = endApi,
                            yearId = session.getYearId(),
                            schoolId = session.getSchoolId(),
                            vers = System.currentTimeMillis()
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val targetDateString = apiSdf.format(date)
                        val dayData = response.body()?.weekDays?.find {
                            it.date.startsWith(targetDateString)
                        }

                        val lessons = dayData?.lessons ?: emptyList()
                        if (lessons.isNotEmpty()) {
                            binding.rvDiary.visibility = View.VISIBLE
                            binding.tvNoData.visibility = View.GONE
                            diaryAdapter.updateData(lessons)
                        } else {
                            binding.rvDiary.visibility = View.GONE
                            binding.tvNoData.visibility = View.VISIBLE
                            binding.tvNoData.text = "На этот день уроков нет"
                        }
                        binding.swipeRefresh.isRefreshing = false
                        val allAssignIds = dayData?.lessons?.flatMap { lesson ->
                            lesson.assignments?.map { it.id } ?: emptyList()
                        } ?: emptyList()

// Запускаем фоновую проверку вложений для всего дня
                        if (allAssignIds.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val attachResp = NetworkService.api.getAttachments(studentId,
                                    AttachmentsRequest(allAssignIds)
                                )
                                if (attachResp.isSuccessful) {
                                    val attachments = attachResp.body() ?: emptyList()
                                    // Передаем данные в адаптер
                                    withContext(Dispatchers.Main) {
                                        diaryAdapter.updateAttachmentsInfo(attachments)
                                    }
                                }
                            }
                        }
                    } else if (response.code() == 401) {
                        redirectToLogin()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvNoData.text = "Ошибка: ${e.message}"
                    binding.tvNoData.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun performSilentLogin(): Boolean {
        val session = SessionManager(this)
        val login = session.getUserLogin() ?: return false
        val password = session.getUserPass() ?: return false
        val schoolId = session.getSchoolId()

        return try {
            // 1. Получаем соль
            val authResp = NetworkService.api.getAuthData()
            if (!authResp.isSuccessful) return false

            val authData = authResp.body()!!
            val nSessionId = authResp.headers().values("Set-Cookie")
                .find { it.contains("NSSESSIONID") }?.split(";")?.first() ?: ""

            // 2. Хешируем
            val pwHash = (authData.salt + password.md5()).md5()
            val loginParams = mapOf(
                "LoginType" to "1", "cid" to "2", "sid" to "1", "pid" to "-232",
                "cn" to "232", "sft" to "2", "scid" to schoolId.toString(),
                "UN" to login, "PW" to pwHash.substring(0, password.length),
                "lt" to authData.lt, "pw2" to pwHash, "ver" to authData.ver
            )

            // 3. Логин
            val loginResp = NetworkService.api.login(nSessionId, params = loginParams)
            if (!loginResp.isSuccessful) return false

            val atKey = loginResp.body()?.at
            val esrnSec = loginResp.headers().values("Set-Cookie")
                .filter { it.contains("ESRNSec=") && !it.contains("ESRNSec=;") }
                .map { it.split(";").first() }.lastOrNull()

            if (atKey != null && esrnSec != null) {
                session.saveSession(atKey, esrnSec, session.getStudentId())
                NetworkService.init(this) // Переинициализируем перехватчик кук

                // Обновляем yearId через context
                val ctxResp = NetworkService.api.getContext()
                if (ctxResp.isSuccessful) {
                    ctxResp.body()?.schoolYearId?.let { session.saveYearId(it) }
                }
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSession(): Boolean {
        val session = SessionManager(this)
        if (session.getAtKey() == null) {
            redirectToLogin()
            return false
        }
        return true
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}