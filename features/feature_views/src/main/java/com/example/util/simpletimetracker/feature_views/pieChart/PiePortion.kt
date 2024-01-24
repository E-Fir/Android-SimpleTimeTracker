package com.example.util.simpletimetracker.feature_views.pieChart

import android.os.Parcelable
import androidx.annotation.ColorInt
import com.example.util.simpletimetracker.feature_views.viewData.RecordTypeIcon
import kotlinx.parcelize.Parcelize

@Parcelize
data class PiePortion(
    val name: String,
    var value: Long,
    var percent: Double = 0.0,
    var koef: Double = 1.0,
    @ColorInt val colorInt: Int,
    val iconId: RecordTypeIcon? = null,
    val statisticsId: Long,
) : Parcelable
