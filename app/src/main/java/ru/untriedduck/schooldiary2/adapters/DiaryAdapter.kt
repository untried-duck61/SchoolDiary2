package ru.untriedduck.schooldiary2.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import ru.untriedduck.schooldiary2.LessonDetailsActivity
import ru.untriedduck.schooldiary2.api.AssignmentAttachmentsResponse
import ru.untriedduck.schooldiary2.api.Lesson
import ru.untriedduck.schooldiary2.databinding.ItemLessonBinding
import kotlin.jvm.java

class DiaryAdapter(private var lessons: List<Lesson>) : RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder>() {

    class DiaryViewHolder(val binding: ItemLessonBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var lessonsWithFiles = mutableSetOf<Long>()

    fun updateAttachmentsInfo(attachments: List<AssignmentAttachmentsResponse>) {
        lessonsWithFiles.clear()
        attachments.forEach { response ->
            if (!response.attachments.isNullOrEmpty()) {
                lessonsWithFiles.add(response.assignmentId)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = ItemLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DiaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        val lesson = lessons[position]
        with(holder.binding) {
            tvLessonNumber.text = lesson.number.toString()
            tvSubjectName.text = lesson.subjectName
            tvLessonInfo.text = "${lesson.startTime} - ${lesson.endTime} | каб. ${lesson.room ?: "-"}"

            val allAssignments = lesson.assignments ?: emptyList()

            // 1. Ищем только Домашнее Задание (typeId == 3)
            val homework = allAssignments.find { it.typeId == 3 }
            tvAssignment.text = homework?.assignmentName ?: "Нет задания"

            // 2. Собираем ВСЕ оценки за урок
            val allMarks = allAssignments.mapNotNull { it.mark?.markValue }
            if (allMarks.isNotEmpty()) {
                cardMark.visibility = View.VISIBLE
                tvMark.text = allMarks.joinToString(", ")
                // Цвет берем по первой оценке
                cardMark.setCardBackgroundColor(getMarkColor(allMarks.first()))
            } else {
                cardMark.visibility = View.GONE
            }

            // 3. Показываем скрепку, если есть хоть один файл в любом задании
            val hasFiles = lesson.assignments?.any { lessonsWithFiles.contains(it.id) } ?: false
            ivHasFile.visibility = if (hasFiles) View.VISIBLE else View.GONE

            // Обработка нажатия для перехода
            root.setOnClickListener {
                val intent = Intent(root.context, LessonDetailsActivity::class.java).apply {
                    // Передаем данные урока (нужно сделать Lesson Serializable или Parcelable)
                    putExtra("LESSON_DATA", Gson().toJson(lesson))
                }
                root.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount() = lessons.size

    fun updateData(newLessons: List<Lesson>) {
        this.lessons = newLessons
        notifyDataSetChanged()
    }

    // Вспомогательная функция для цвета
    private fun getMarkColor(mark: String): Int {
        return when(mark) {
            "5" -> 0xFF4CAF50.toInt()
            "4" -> 0xFF8BC34A.toInt()
            "3" -> 0xFFFFC107.toInt()
            "2" -> 0xFFF44336.toInt()
            else -> 0xFFE0E0E0.toInt()
        }
    }
}