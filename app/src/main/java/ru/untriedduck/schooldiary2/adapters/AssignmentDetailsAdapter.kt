package ru.untriedduck.schooldiary2.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ru.untriedduck.schooldiary2.api.Assignment
import ru.untriedduck.schooldiary2.databinding.ItemAssignmentDetailBinding

class AssignmentDetailsAdapter(private val assignments: List<Assignment>) :
    RecyclerView.Adapter<AssignmentDetailsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAssignmentDetailBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssignmentDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = assignments[position]
        with(holder.binding) {
            // Маппинг типов работ АСУ РСО
            tvAssignmentType.text = when (item.typeId) {
                1 -> "Практическая работа"
                3 -> "Домашнее задание"
                4 -> "Контрольная работа"
                5 -> "Практическая работа"
                10 -> "Ответ на уроке"
                11 -> "Сочинение"
                13 -> "Зачёт"
                32 -> "Предметный/тематический диктант"
                37 -> "Творческое задание"
                else -> "Задание"
            }

            tvAssignmentDescription.text = item.assignmentName ?: "Нет описания"

            val mark = item.mark?.markValue
            if (!mark.isNullOrEmpty()) {
                tvAssignmentMark.visibility = View.VISIBLE
                tvAssignmentMark.text = mark

                // Цвет в зависимости от оценки
                tvAssignmentMark.setTextColor(when(mark) {
                    "5" -> 0xFF4CAF50.toInt()
                    "4" -> 0xFF8BC34A.toInt()
                    "3" -> 0xFFFFC107.toInt()
                    "2" -> 0xFFF44336.toInt()
                    else -> 0xFF000000.toInt()
                })
            } else {
                tvAssignmentMark.visibility = View.GONE
            }

            tvWeight.text = item.weight?.let { "Вес задания: $it" } ?: ""
        }
    }

    override fun getItemCount() = assignments.size
}