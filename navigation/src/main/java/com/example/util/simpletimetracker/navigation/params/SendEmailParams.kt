package com.example.util.simpletimetracker.navigation.params

data class SendEmailParams(
    val email: String? = "",
    val subject: String? = "",
    val body: String? = "",
    val chooserTitle: String? = null
)