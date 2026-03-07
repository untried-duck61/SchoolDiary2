package ru.untriedduck.schooldiary2.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.untriedduck.schooldiary2.R
import ru.untriedduck.schooldiary2.api.Assignment
import ru.untriedduck.schooldiary2.api.AssignmentAttachmentsResponse
import ru.untriedduck.schooldiary2.api.AttachmentFile
import ru.untriedduck.schooldiary2.databinding.ItemAssignmentDetailBinding
import kotlin.collections.forEach

class AssignmentDetailsAdapter(private val assignments: List<Assignment>) :
    RecyclerView.Adapter<AssignmentDetailsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemAssignmentDetailBinding) : RecyclerView.ViewHolder(binding.root)

    // Храним мапу: ID задания -> Список файлов
    private var attachmentsMap = mutableMapOf<Long, List<AttachmentFile>>()

    fun updateAttachments(newAttachments: List<AssignmentAttachmentsResponse>) {
        newAttachments.forEach {
            attachmentsMap[it.assignmentId] = it.attachments ?: emptyList()
        }
        notifyDataSetChanged() // Это заставит список перерисоваться и показать файлы
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssignmentDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = assignments[position]
        val context = holder.itemView.context // Получаем контекст
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

            filesContainer.removeAllViews()
            val files = attachmentsMap[item.id] ?: emptyList()

            if (files.isEmpty()) {
                filesContainer.visibility = View.GONE
            } else {
                filesContainer.visibility = View.VISIBLE
                files.forEach { file ->
                    val fileView = LayoutInflater.from(context).inflate(
                        R.layout.view_file_chip, filesContainer, false
                    )
                    val btnFile = fileView.findViewById<TextView>(R.id.btnFileName)
                    btnFile.text = file.name
                    fileView.setOnClickListener {
                        downloadFile(context, file.id, file.name)
                    }
                    filesContainer.addView(fileView)
                }
            }
        }
    }

    override fun getItemCount() = assignments.size

    private fun downloadFile(context: android.content.Context, fileId: Int, name: String) {
        // Логика скачивания:
        // 1. Формируем URL: https://asurso.ru/webapi/attachments/{fileId}
        // 2. Открываем в браузере или качаем через DownloadManager
        val url = "https://asurso.ru/webapi/attachments/$fileId"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        context.startActivity(intent)
    }
}