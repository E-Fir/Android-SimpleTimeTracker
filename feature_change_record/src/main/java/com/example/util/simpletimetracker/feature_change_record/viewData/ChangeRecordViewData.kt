package com.example.util.simpletimetracker.feature_change_record.viewData

import androidx.annotation.ColorInt
import com.example.util.simpletimetracker.core.viewData.RecordTypeIcon

data class ChangeRecordViewData(
    val name: String,
    val timeStarted: String,
    val timeFinished: String,
    val dateTimeStarted: String,
    val dateTimeFinished: String,
    val duration: String,
    val iconId: RecordTypeIcon,
    @ColorInt val color: Int,
    val comment: String
)