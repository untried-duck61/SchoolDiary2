package ru.untriedduck.schooldiary2

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.untriedduck.schooldiary2.adapters.AssignmentDetailsAdapter
import ru.untriedduck.schooldiary2.api.AttachmentsRequest
import ru.untriedduck.schooldiary2.api.Lesson
import ru.untriedduck.schooldiary2.api.NetworkService
import ru.untriedduck.schooldiary2.api.SessionManager
import ru.untriedduck.schooldiary2.databinding.ActivityLessonDetailsBinding
import kotlin.jvm.java

class LessonDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLessonDetailsBinding
    private lateinit var adapter: AssignmentDetailsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLessonDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lessonJson = intent.getStringExtra("LESSON_DATA")
        val lesson = Gson().fromJson(lessonJson, Lesson::class.java)

        // 1. Ставим заголовки
        binding.tvDetSubject.text = lesson.subjectName
        binding.tvDetInfo.text = "${lesson.startTime} - ${lesson.endTime} | каб. ${lesson.room ?: "-"}"

        // 2. Настраиваем адаптер
        val assignments = lesson.assignments ?: emptyList()
        adapter = AssignmentDetailsAdapter(assignments)
        binding.rvAssignments.layoutManager = LinearLayoutManager(this)
        binding.rvAssignments.adapter = adapter

        // 3. Загружаем файлы для этих заданий
        loadAttachments(assignments.map { it.id })

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadAttachments(ids: List<Long>) {
        if (ids.isEmpty()) return

        val session = SessionManager(this)
        val studentId = session.getStudentId()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkService.api.getAttachments(
                    studentId,
                    AttachmentsRequest(ids)
                )
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        response.body()?.let { adapter.updateAttachments(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}