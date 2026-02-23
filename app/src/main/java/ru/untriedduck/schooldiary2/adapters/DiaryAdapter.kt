package ru.untriedduck.schooldiary2.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.untriedduck.schooldiary2.api.Lesson
import ru.untriedduck.schooldiary2.databinding.ItemLessonBinding

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

            // Берем первое задание и его описание (ДЗ)
            val assignment = lesson.assignments?.firstOrNull()
            tvAssignment.text = assignment?.assignmentName ?: "Нет задания"

            // Проверяем оценку
            val mark = assignment?.mark?.mark
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
        }
    }

    override fun getItemCount() = lessons.size

    fun updateData(newLessons: List<Lesson>) {
        this.lessons = newLessons
        notifyDataSetChanged()
    }
}