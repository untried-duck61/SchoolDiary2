package ru.untriedduck.schooldiary2.utils

import ru.untriedduck.schooldiary2.api.AuthDataResponse
import java.security.MessageDigest

fun String.md5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

// Логика формирования параметров
fun prepareLoginParams(
    authData: AuthDataResponse,
    pass: String,
    schoolId: Int,
    username: String
): Map<String, String> {
    val md5Pass = pass.md5()
    val fullHash = (authData.salt + md5Pass).md5()

    return mapOf(
        "LoginType" to "1",
        "cid" to "2",
        "sid" to "1",
        "pid" to "-232",
        "cn" to "232",
        "sft" to "2",
        "scid" to schoolId.toString(),
        "UN" to username,
        "PW" to fullHash.substring(0, pass.length), // Первые N символов по длине пароля
        "lt" to authData.lt,
        "pw2" to fullHash,
        "ver" to authData.ver
    )
}