package ru.untriedduck.schooldiary2.api

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(at: String, esrnSec: String, studentId: Int) {
        prefs.edit().apply {
            putString("at_key", at)
            putString("esrn_cookie", esrnSec)
            putInt("student_id", studentId)
            apply()
        }
    }

    fun getAtKey(): String? = prefs.getString("at_key", null)
    fun getEsrnCookie(): String? = prefs.getString("esrn_cookie", null)
    fun getStudentId(): Int = prefs.getInt("student_id", -1)

    fun saveUserCredentials(login: String, pass: String, schoolId: Int) {
        prefs.edit().apply {
            putString("user_login", login)
            putString("user_pass", pass)
            putInt("school_id", schoolId)
            apply()
        }
    }

    fun getUserLogin(): String? = prefs.getString("user_login", null)
    fun getUserPass(): String? = prefs.getString("user_pass", null)
    fun getSchoolId(): Int = prefs.getInt("school_id", -1)

    // В SessionManager.kt
    fun saveYearId(yearId: Int) {
        prefs.edit().putInt("year_id", yearId).apply()
    }

    fun getYearId(): Int = prefs.getInt("year_id", -1)

}