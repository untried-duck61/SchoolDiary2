package ru.untriedduck.schooldiary2.api

data class DiaryResponse(
    val weekDays: List<WeekDay>
)

data class WeekDay(
    val date: String, // Формат "2024-05-20T00:00:00"
    val lessons: List<Lesson>?
)

data class Lesson(
    val number: Int,
    val subjectName: String,
    val room: String?,
    val assignments: List<Assignment>?
)

data class Assignment(
    val mark: Mark?,
    val typeName: String?, // Например, "Ответ на уроке"
    val assignmentName: String? // Описание задания (ДЗ)
)

data class Mark(
    val mark: String? // Сама оценка: "5", "4", "3"
)