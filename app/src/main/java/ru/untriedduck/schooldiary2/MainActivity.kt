package ru.untriedduck.schooldiary2

import android.content.Intent
import android.os.Bundle
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
        setContentView(R.layout.activity_main)

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
}