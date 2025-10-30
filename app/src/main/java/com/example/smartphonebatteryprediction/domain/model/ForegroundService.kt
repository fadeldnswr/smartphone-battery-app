package com.example.smartphonebatteryprediction.domain.model


data class ForegroundInfo(
    val packageName: Int?,
    val appLabel: String?,
    val sinceMillis: Long?,
    val screenOn: Boolean,
    val note: String? = null
)