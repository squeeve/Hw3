package com.squeeve.hw3

import com.google.firebase.Timestamp
import com.google.firebase.database.ServerValue
import java.util.Date

data class User(
    var displayName: String = "",
    var email: String = "",
    var phone: String = "",
    var timestamp: Any = ServerValue.TIMESTAMP
) {
    fun timestampDate(): Date {
        return when (timestamp) {
            is Long -> Date(timestamp as Long)
            is Timestamp -> (timestamp as Timestamp).toDate()
            else -> Date()
        }
    }
}
