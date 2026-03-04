package ru.untriedduck.schooldiary2.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import ru.untriedduck.schooldiary2.LessonDetailsActivity
import ru.untriedduck.schooldiary2.api.Lesson
import ru.untriedduck.schooldiary2.databinding.ItemLessonBinding
import kotlin.jvm.java

class DiaryAdapter(private var lessons: List<Lesson>) : RecyclerView.Adapter<DiaryAdapter.DiaryViewHolder>() {

    class DiaryViewHolder(val binding: ItemLessonBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = ItemLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DiaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        val lesson = lessons[position]
        with(holder.binding) {
            tvLessonNumber.text = lesson.number.toString()
            tvSubjectName.text = lesson.subjectName

            // Формируем строку Время | Кабинет
            val timeStr = if (!lesson.startTime.isNullOrEmpty()) {
                "${lesson.startTime} - ${lesson.endTime}"
            } else ""

            val roomStr = if (!lesson.room.isNullOrEmpty()) {
                " | каб. ${lesson.room}"
            } else ""

            tvLessonInfo.text = "$timeStr$roomStr" // Не забудь добавить этот ID в XML

            val allAssignments = lesson.assignments ?: emptyList()

            // На главном экране ищем ТОЛЬКО домашку (обычно typeId = 3)
            // Или просто ищем первое задание с текстом
            val homework = allAssignments.find { it.typeId == 3 || it.assignmentName?.isNotEmpty() == true }
            tvAssignment.text = homework?.assignmentName ?: "Нет задания"

            // Оценка (ищем любую, если есть на уроке)
            val mark = allAssignments.find { it.mark?.markValue != null }?.mark?.markValue
            if (!mark.isNullOrEmpty()) {
                cardMark.visibility = View.VISIBLE
                tvMark.text = mark

                // Опционально: можно менять цвет в зависимости от оценки
                val color = when(mark) {
                    "5" -> 0xFF4CAF50.toInt() // Зеленый
                    "4" -> 0xFF8BC34A.toInt() // Салатовый
                    "3" -> 0xFFFFC107.toInt() // Желтый
                    "2" -> 0xFFF44336.toInt() // Красный
                    else -> 0xFFE0E0E0.toInt()
                }
                cardMark.setCardBackgroundColor(color)
            } else {
                cardMark.visibility = View.GONE
            }

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
}