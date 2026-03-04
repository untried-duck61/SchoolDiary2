package ru.untriedduck.schooldiary2

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import ru.untriedduck.schooldiary2.adapters.AssignmentDetailsAdapter
import ru.untriedduck.schooldiary2.api.Lesson
import ru.untriedduck.schooldiary2.databinding.ActivityLessonDetailsBinding
import kotlin.jvm.java

class LessonDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityLessonDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lessonJson = intent.getStringExtra("LESSON_DATA")
        val lesson = Gson().fromJson(lessonJson, Lesson::class.java)

        // Формируем строку Время | Кабинет
        val timeStr = if (!lesson.startTime.isNullOrEmpty()) {
            "${lesson.startTime} - ${lesson.endTime}"
        } else ""

        val roomStr = if (!lesson.room.isNullOrEmpty()) {
            " | каб. ${lesson.room}"
        } else ""
        binding.tvDetSubject.text = lesson.subjectName
        binding.tvDetInfo.text = "$timeStr$roomStr"

        // Настраиваем адаптер для заданий
        val adapter = AssignmentDetailsAdapter(lesson.assignments ?: emptyList())
        binding.rvAssignments.layoutManager = LinearLayoutManager(this)
        binding.rvAssignments.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}