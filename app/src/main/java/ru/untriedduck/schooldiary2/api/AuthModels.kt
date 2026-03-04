package ru.untriedduck.schooldiary2.api

data class AuthDataResponse(
    val lt: String,
    val ver: String,
    val salt: String
)

// Модель школы для поиска
data class School(
    val id: Int,
    val name: String,
    val address: String?
)

// Ответ от /webapi/login
data class LoginResponse(
    val at: String,
    val accessToken: String?,
    val refreshToken: String?,
    val errorMessage: String?
)

// Для инициализации дневника (studentId)
data class StudentInitResponse(
    val students: List<StudentInfo>
)

data class StudentInfo(
    val studentId: Int
)

data class ContextResponse(
    val schoolYearId: Int,
    val schoolId: Int,
    val userId: Int
)