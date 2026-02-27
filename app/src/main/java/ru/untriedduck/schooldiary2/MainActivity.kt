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
import ru.untriedduck.schooldiary2.api.DiaryResponse
import ru.untriedduck.schooldiary2.api.NetworkService
import ru.untriedduck.schooldiary2.api.SessionManager
import ru.untriedduck.schooldiary2.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var diaryAdapter: DiaryAdapter

    // Календарь хранит текущий ВЫБРАННЫЙ день
    private val calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+4"), Locale("ru"))
    private val displaySdf = SimpleDateFormat("EEEE, d MMMM", Locale("ru")) // Пятница, 21 февраля
    private val apiSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var currentWeekData: DiaryResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        NetworkService.init(this)

        if (!checkSession()) return

        setupRecyclerView()
        setupNavigation()

        // Загружаем данные для текущего дня при первом запуске
        refreshData()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Функция для получения даты в формате АСУ РСО
    fun Calendar.toAsuDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(this.time)
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
        // Форматируем дату для заголовка
        val dateText = displaySdf.format(calendar.time)
        binding.tvCurrentWeek.text = dateText.replaceFirstChar { it.uppercase() }

        // Проверяем, есть ли уже данные за эту неделю в памяти, чтобы не спамить запросами
        // (Для упрощения пока будем качать каждый раз, но за нужную неделю)
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
                val yearId = 612
                val response = NetworkService.api.getDiary(studentId, startApi, endApi, yearId)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val diaryResponse = response.body()
                        val targetDateString = apiSdf.format(date)

                        Log.d("ASU_DEBUG", "Ищем уроки на дату: $targetDateString")
                        Log.d("ASU_DEBUG", "Всего дней в ответе: ${diaryResponse?.weekDays?.size}")

                        // Более надежный поиск дня (игнорируем время после T)
                        val dayData = diaryResponse?.weekDays?.find {
                            it.date.split("T")[0] == targetDateString
                        }

                        val lessons = dayData?.lessons ?: emptyList()

                        Log.d("ASU_DEBUG", "Найдено уроков: ${lessons.size}")

                        if (lessons.isNotEmpty()) {
                            binding.rvDiary.visibility = View.VISIBLE
                            binding.tvNoData.visibility = View.GONE
                            diaryAdapter.updateData(lessons)
                        } else {
                            binding.rvDiary.visibility = View.GONE
                            binding.tvNoData.visibility = View.VISIBLE
                            binding.tvNoData.text = "На этот день ($targetDateString) уроков нет"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e.message?.contains("401") == true) {
                        // Если сессия сдохла - выкидываем на логин
                        val intent = Intent(this@MainActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        binding.progressBar.visibility = View.GONE
                        binding.tvNoData.text = "Ошибка сети"
                        binding.tvNoData.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun checkSession(): Boolean {
        val session = SessionManager(this)
        if (session.getAtKey() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return false
        }
        return true
    }
}