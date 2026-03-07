package ru.untriedduck.schooldiary2.api

import com.google.gson.annotations.SerializedName

data class DiaryResponse(
    val weekStart: String,
    val weekEnd: String,
    val weekDays: List<WeekDay>,
    val termName: String?,
    val className: String?
)

data class WeekDay(
    val date: String,
    val lessons: List<Lesson>?
)

data class Lesson(
    val number: Int,
    val subjectName: String,
    val startTime: String?,
    val endTime: String?,
    val room: String?,
    val assignments: List<Assignment>?,
    val classmeetingId: Long
)

data class Mark(
    val id: Int,
    @SerializedName("mark") val markValue: String?, // В JSON может быть числом или строкой, Gson обычно справляется
    val studentId: Int,
    val assignmentId: Long
)

data class Assignment(
    val id: Long,
    val typeId: Int,
    val assignmentName: String?,
    val weight: Int?,
    val mark: Mark?,
    // Добавляем список вложений
    val attachments: List<AttachmentInfo>?
)

data class AttachmentInfo(
    val id: Int,
    val fileName: String
)

data class AttachmentsRequest(
    val assignId: List<Long>
)

// Элемент ответа
data class AssignmentAttachmentsResponse(
    val assignmentId: Long,
    val attachments: List<AttachmentFile>?,
    val answerFiles: List<AttachmentFile>?
)

data class AttachmentFile(
    val id: Int,
    val name: String,
    val originalFileName: String?
)