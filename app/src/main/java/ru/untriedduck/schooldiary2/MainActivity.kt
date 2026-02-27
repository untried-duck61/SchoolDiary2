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
    private val calendar: Calendar = Calendar.getInstance(Locale("ru"))
    private val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
    private val apiSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        NetworkService.init(this)

        if (!checkSession()) {
            return
        }

        setupRecyclerView()
        setupWeekNavigation()

        // Загружаем данные для текущей недели при первом запуске
        loadDiaryForCurrentWeek()
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

    private fun checkSession(): Boolean {
        val session = SessionManager(this)
        if (session.getAtKey() == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return false
        }
        return true
    }

    private fun setupRecyclerView() {
        diaryAdapter = DiaryAdapter(emptyList())
        binding.rvDiary.layoutManager = LinearLayoutManager(this)
        binding.rvDiary.adapter = diaryAdapter
    }

    private fun setupWeekNavigation() {
        binding.btnPreviousWeek.setOnClickListener {
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
            loadDiaryForCurrentWeek()
        }
        binding.btnNextWeek.setOnClickListener {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            loadDiaryForCurrentWeek()
        }
    }

    private fun loadDiaryForCurrentWeek() {
        // Устанавливаем понедельник текущей недели в календаре
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val start = calendar.time

        // Устанавливаем воскресенье
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val end = calendar.time

        // Возвращаем календарь на понедельник для отображения дат
        calendar.add(Calendar.DAY_OF_WEEK, -6)

        // Обновляем UI
        binding.tvCurrentWeek.text = "${sdf.format(start)} - ${sdf.format(end)}"
        binding.progressBar.visibility = View.VISIBLE
        binding.rvDiary.visibility = View.GONE
        binding.tvNoData.visibility = View.GONE

        // Загружаем данные
        val startApi = apiSdf.format(start)
        val endApi = apiSdf.format(end)
        loadDiary(startApi, endApi)
    }

    private fun loadDiary(weekStart: String, weekEnd: String) {
        val session = SessionManager(this)
        val studentId = session.getStudentId()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkService.api.getDiary(studentId, weekStart, weekEnd)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val allLessons = response.body()?.weekDays
                            ?.flatMap { it.lessons ?: emptyList() }
                            ?.sortedBy { it.number }
                            ?: emptyList()

                        if (allLessons.isNotEmpty()) {
                            binding.rvDiary.visibility = View.VISIBLE
                            diaryAdapter.updateData(allLessons)
                        } else {
                            binding.tvNoData.visibility = View.VISIBLE
                        }
                    } else {
                        binding.tvNoData.text = "Ошибка: ${response.code()}"
                        binding.tvNoData.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvNoData.text = "Ошибка сети"
                    binding.tvNoData.visibility = View.VISIBLE
                    e.printStackTrace()
                }
            }
        }
    }
}