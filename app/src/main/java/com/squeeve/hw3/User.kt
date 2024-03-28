package com.squeeve.hw3

import com.google.firebase.database.ServerValue

data class User(
    var displayName: String,
    var email: String,
    var phone: String,
    var timestamp: Map<String, String> = ServerValue.TIMESTAMP
)
