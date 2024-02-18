package com.example.util.simpletimetracker.feature_widget.statistics

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.util.simpletimetracker.core.interactor.StatisticsChartViewDataInteractor
import com.example.util.simpletimetracker.core.interactor.StatisticsMediator
import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.core.utils.PendingIntents
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_KEY
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_STATISTICS
import com.example.util.simpletimetracker.domain.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.model.ChartFilterType
import com.example.util.simpletimetracker.domain.model.RangeLength
import com.example.util.simpletimetracker.domain.model.RecordType
import com.example.util.simpletimetracker.domain.model.Statistics
import com.example.util.simpletimetracker.domain.repo.CategoryRepo
import com.example.util.simpletimetracker.feature_views.IconView
import com.example.util.simpletimetracker.feature_views.extension.dpToPx
import com.example.util.simpletimetracker.feature_views.extension.getBitmapFromView
import com.example.util.simpletimetracker.feature_views.extension.measureExactly
import com.example.util.simpletimetracker.feature_views.extension.pxToDp
import com.example.util.simpletimetracker.feature_views.viewData.RecordTypeIcon
import com.example.util.simpletimetracker.feature_widget.R
import com.example.util.simpletimetracker.feature_widget.statistics.customView.WidgetStatisticsChartView
import com.example.util.simpletimetracker.navigation.Router
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToLong

@AndroidEntryPoint
class WidgetStatisticsChartProvider : AppWidgetProvider() {

    @Inject
    lateinit var statisticsChartViewDataInteractor: StatisticsChartViewDataInteractor

    @Inject
    lateinit var statisticsMediator: StatisticsMediator

    @Inject
    lateinit var router: Router

    @Inject
    lateinit var prefsInteractor: PrefsInteractor

    @Inject
    lateinit var recordTypeInteractor: RecordTypeInteractor

    @Inject
    lateinit var resourceRepo: ResourceRepo

    @Inject
    lateinit var categoryRepo: CategoryRepo;

    @Inject
    lateinit var timeMapper: TimeMapper

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?,
    ) {
        appWidgetIds?.forEach { widgetId ->
            updateAppWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        GlobalScope.launch(Dispatchers.Main) {
            appWidgetIds?.forEach { prefsInteractor.removeStatisticsWidget(it) }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateAppWidget(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
    ) {
        if (context == null || appWidgetManager == null) return

        GlobalScope.launch(Dispatchers.Main) {
            val view = prepareView(context, appWidgetId)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            measureView(context, options, view)
            val bitmap = view.getBitmapFromView()
            val refreshButtonBitmap = prepareRefreshButtonView(context).getBitmapFromView()

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setImageViewBitmap(R.id.ivWidgetBackground, bitmap)
            views.setOnClickPendingIntent(R.id.btnWidget, getPendingSelfIntent(context))

            views.setImageViewBitmap(R.id.ivRefresh, refreshButtonBitmap)
            views.setOnClickPendingIntent(R.id.btnRefresh, getRefreshIntent(context, appWidgetId))
            views.setViewVisibility(R.id.ivRefresh, View.VISIBLE)
            views.setViewVisibility(R.id.btnRefresh, View.VISIBLE)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun parseOptionsString(input: String): Map<String, Any> {
        val opts = mutableMapOf<String, Any>()

        val keyValuePairs = input.split(Regex("[\\s\\n\\r]+")).filter { it.isNotBlank() }

        for (pair in keyValuePairs) {
            val parts = pair.split("=")
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                var value: Double
                if (parts[1].matches(Regex("(\\d+h)?(\\d+m)?")) && parts[1].matches(Regex(".*?[hm]"))) {
                    val (hours, minutes) = parts[1].split("h", "m")
                    value = 0.0
                    if (hours.isNotEmpty()) {
                        if ('h' !in parts[1]) {
                            value += hours.toDouble() / 60.0
                        } else {
                            value += hours.toDouble()
                        }
                    }
                    if (minutes.isNotEmpty()) {
                        value += minutes.toDouble() / 60.0
                    }
                } else {
                    value = parts[1].toDouble()
                }

                opts[parts[0]] = value
            } else {
                opts[parts[0]] = 0.0
            }
        }

        return opts
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun prepareView(
        context: Context,
        appWidgetId: Int,
    ): View {
        val isDarkTheme = prefsInteractor.getDarkMode()
        val useProportionalMinutes = prefsInteractor.getUseProportionalMinutes()
        val showSeconds = prefsInteractor.getShowSeconds()
        val firstDayOfWeek = prefsInteractor.getFirstDayOfWeek()
        val startOfDayShift = prefsInteractor.getStartOfDayShift()
        val widgetData = prefsInteractor.getStatisticsWidget(appWidgetId)
        val backgroundTransparency = prefsInteractor.getWidgetBackgroundTransparencyPercent()
        val types = recordTypeInteractor.getAll().associateBy(RecordType::id)

        fun formatHours(hours: Double): String {
            return timeMapper.formatInterval(
                interval = (hours * 1000.0 * 60.0 * 60.0).toLong(),
                forceSeconds = showSeconds,
                useProportionalMinutes = useProportionalMinutes,
            )
        }

        val filterType = widgetData.chartFilterType
        val filteredIds = when (filterType) {
            ChartFilterType.ACTIVITY -> widgetData.filteredTypes
            ChartFilterType.CATEGORY -> widgetData.filteredCategories
            ChartFilterType.RECORD_TAG -> widgetData.filteredTags
        }.toList()
        val rangeLength = widgetData.rangeLength

        val opts = parseOptionsString(widgetData.options)

        val dataHolders = statisticsMediator.getDataHolders(
            filterType = filterType,
            types = types,
        )

        val range = timeMapper.getRangeStartAndEnd(
            rangeLength = rangeLength,
            shift = 0,
            firstDayOfWeek = firstDayOfWeek,
            startOfDayShift = startOfDayShift,
        )
        val statistics = statisticsMediator.getStatistics(
            filterType = filterType,
            filteredIds = filteredIds,
            range = range,
        )

        var chart = statisticsChartViewDataInteractor.getChart(
            filterType = filterType,
            filteredIds = filteredIds,
            statistics = statistics,
            dataHolders = dataHolders,
            types = types,
            isDarkTheme = isDarkTheme,
        )

        for (i in chart.indices) {
            val el = chart.elementAt(i)
            val koefOptionName = el.name + "_K"
            if (opts.containsKey(koefOptionName)) {
                el.koef = opts[koefOptionName] as Double
            } else {
                el.koef = 1.0
            }
            el.value = (el.value.toDouble() * el.koef).toLong()
        }

        var totalTracked = ""
        var sum = 0.0
        var cnt = 0
        var minIndex = -1
        var minValue = 10000000000000
        var maxIndex = -1
        var maxValue = -10000000000000
        for (i in chart.indices) {
            val el = chart.elementAt(i)
            sum += el.value
            cnt++
            if (el.value < minValue) {
                minValue = el.value
                minIndex = i
            }
            if (el.value > maxValue) {
                maxValue = el.value
                maxIndex = i
            }
        }

        val STYLE_DEFAULT = 0
        val STYLE_SHORT = 1
        val style = if (opts.containsKey("style")) (opts["style"] as Double).toInt() else STYLE_DEFAULT

        val isSmooth = opts.containsKey("smooth") && opts["smooth"] as Double > 0.0
        var statisticsToday: List<Statistics> = listOf()
        var sumToday = 0.0
        var cntToday = 0
        var minIndexToday = -1
        var minValueToday = 10000000000000
        var maxIndexToday = -1
        var maxValueToday = -10000000000000
        if (isSmooth) {
            val rangeToday = timeMapper.getRangeStartAndEnd(
                rangeLength = RangeLength.Day,
                shift = 0,
                firstDayOfWeek = firstDayOfWeek,
                startOfDayShift = startOfDayShift,
            )
            statisticsToday = statisticsMediator.getStatistics(
                filterType = filterType,
                filteredIds = filteredIds,
                range = rangeToday,
            )
            for ((i, item) in statisticsToday.withIndex()) {
                if (chart.find { it.statisticsId == item.id } != null) {
                    sumToday += item.data.duration
                    cntToday++
                    if (item.data.duration < minValueToday) {
                        minValueToday = item.data.duration
                        minIndexToday = i
                    }
                    if (item.data.duration > maxValueToday) {
                        maxValueToday = item.data.duration
                        maxIndexToday = i
                    }
                }
            }
        }

        var percent: Double
        var timeStr: String
        if (cnt > 1) {
            val elMin = chart.elementAt(minIndex)
            if (isSmooth) {
                val smoothKoef = opts["smooth"] as Double
                val elTodayMin =
                    chart.find { el -> statisticsToday.find { it.id == el.statisticsId } == null }
                if (elTodayMin != null) {
                    totalTracked += if (style == STYLE_SHORT) {
                        elTodayMin.name
                    } else {
                        "Need ${elTodayMin.name}\n\n"
                    }
                } else {
                    var minDurStatId = -1L
                    var minDur = 100000000000000
                    statisticsToday.forEach { item ->
                        if (chart.find { it.statisticsId == item.id } != null) {
                            var dur = if (item.id != elMin.statisticsId) {
                                (item.data.duration.toDouble() * smoothKoef).roundToLong()
                            } else {
                                item.data.duration
                            }
                            if (minDur > dur) {
                                minDur = dur
                                minDurStatId = item.id
                            }
                        }
                    }
                    val minDurEl = chart.find { it.statisticsId == minDurStatId }
                    totalTracked += if (style == STYLE_SHORT) {
                        elMin.name
                    } else {
                        "(${elMin.name}) Need ${minDurEl?.name?.uppercase(Locale.ROOT)}\n\n"
                    }
                }
            } else {
                var expectedPercent = 100 / cnt.toFloat()
                percent = elMin.value / sum * 100
                var percentDiff = expectedPercent - percent
                percentDiff = Math.round(percentDiff * 1000.0) / 1000.0
                timeStr = timeMapper.formatInterval(
                    interval = (sum * percentDiff / 100 / elMin.koef).toLong() * cnt,
                    forceSeconds = showSeconds,
                    useProportionalMinutes = useProportionalMinutes,
                )
                totalTracked += if (style == STYLE_SHORT) {
                    elMin.name
                } else {
                    "Need ${elMin.name}:\n$percentDiff%\n$timeStr\n\n"
                }
            }
        }

        if (style != STYLE_SHORT) {
            for (i in chart.indices) {
                val el = chart.elementAt(i)
                val statItemToday =
                    statisticsToday.filter { it.id == el.statisticsId }.firstOrNull()
                percent = el.value / sum * 100
                percent = Math.round(percent * 1000.0) / 1000.0
                timeStr = timeMapper.formatInterval(
                    interval = (el.value.toFloat() / el.koef).toLong(),
                    forceSeconds = showSeconds,
                    useProportionalMinutes = useProportionalMinutes,
                )
                if (el.koef != 1.0) {
                    timeStr += " (" + timeMapper.formatInterval(
                        interval = el.value,
                        forceSeconds = showSeconds,
                        useProportionalMinutes = useProportionalMinutes,
                    ) + ")"
                }
                if (isSmooth) {
                    if (statItemToday != null) {
                        timeStr += " / " + timeMapper.formatInterval(
                            interval = statItemToday.data.duration,
                            forceSeconds = showSeconds,
                            useProportionalMinutes = useProportionalMinutes,
                        )
                    } else {
                        timeStr += " / 0м"
                    }
                }

                totalTracked += "${el.name}:\n$timeStr"
                if (cnt > 1) {
                    totalTracked += "\n$percent%"
                    if (isSmooth) {
                        if (sumToday > 0) {
                            val dur = statItemToday?.data?.duration ?: 0L
                            percent = dur / sumToday * 100
                            percent = (percent * 1000.0).roundToLong() / 1000.0
                        } else {
                            percent = 0.0
                        }
                        totalTracked += " / $percent%"
                    }
                }
                totalTracked += "\n\n"
            }

            if (totalTracked.length >= 2) {
                totalTracked = totalTracked.substring(0, totalTracked.length - 2)
            }

            if (widgetData.options.isNotEmpty()) {
                totalTracked = widgetData.options + "\n\n" + totalTracked

                if (chart.size == 1 && opts.containsKey("goal")) {
                    val goal = opts["goal"] as Double
                    if (opts.containsKey("magnet")) {
                        val magnetHour = opts["magnet"] as Double
                        val activeSessionLen = if (opts.containsKey("session_len")) {
                            opts["session_len"] as Double
                        } else {
                            0.75
                        }

                        val el = chart.elementAt(0)

                        val activeHoursSpent = el.value.toDouble() / 1000.0 / 60.0 / 60.0
                        if (activeHoursSpent < goal) {
                            val waitForHours = magnetHour - goal + activeHoursSpent
                            val currentTime = LocalTime.now()
                            val currentHours = currentTime.hour + currentTime.minute / 60.0

                            if (currentHours < waitForHours) {
                                totalTracked += "\n\nWait for: " + formatHours(waitForHours)
                                val activeHoursLeft = goal - activeHoursSpent
                                val activeSessionsLeft = activeHoursLeft / activeSessionLen
                                val hoursToMagnet = magnetHour - currentHours
                                val relaxSessionLen: Double
                                if (activeSessionsLeft <= 1.0) {
                                    relaxSessionLen = hoursToMagnet - activeHoursLeft
                                } else {
                                    val relaxHoursLeft = waitForHours - currentHours
                                    relaxSessionLen = relaxHoursLeft / ceil(activeSessionsLeft)
                                }
                                totalTracked += "\nRelax: " + formatHours(relaxSessionLen) + " / " + formatHours(
                                    activeSessionLen,
                                )
                            }
                        }
                    }
                }
            }
        }

        return WidgetStatisticsChartView(ContextThemeWrapper(context, R.style.AppTheme)).apply {
            setSegments(
                data = chart,
                total = totalTracked,
                backgroundAlpha = 1f - backgroundTransparency / 100f,
            )
        }
    }

    private fun prepareRefreshButtonView(
        context: Context,
    ): View {
        val size = resourceRepo
            .getDimenInDp(R.dimen.widget_statistics_refresh_button_size)
            .dpToPx()

        return IconView(ContextThemeWrapper(context, R.style.AppTheme)).apply {
            itemIcon = RecordTypeIcon.Image(R.drawable.refresh)
            itemIconColor = resourceRepo.getColor(R.color.white)
            measureExactly(size)
        }
    }

    private fun measureView(
        context: Context,
        options: Bundle,
        view: View,
    ) {
        val defaultWidth =
            context.resources.getDimensionPixelSize(R.dimen.record_type_card_width).pxToDp()
        val defaultHeight =
            context.resources.getDimensionPixelSize(R.dimen.record_type_card_height).pxToDp()

        var width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultWidth)
            .dpToPx().takeUnless { it == 0 } ?: defaultWidth
        var height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defaultHeight)
            .dpToPx().takeUnless { it == 0 } ?: defaultHeight
        val inflater = LayoutInflater.from(context)

        val entireView: View = inflater.inflate(R.layout.widget_layout, null)
        entireView.measureExactly(width = width, height = height)

        val imageView = entireView.findViewById<View>(R.id.ivWidgetBackground)
        width = imageView.measuredWidth
        height = imageView.measuredHeight
        view.measureExactly(width = width, height = height)
    }

    private fun getPendingSelfIntent(
        context: Context,
    ): PendingIntent {
        val intent = router.getMainStartIntent().apply {
            putExtra(SHORTCUT_NAVIGATION_KEY, SHORTCUT_NAVIGATION_STATISTICS)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntents.getFlags())
    }

    private fun getRefreshIntent(
        context: Context,
        widgetId: Int,
    ): PendingIntent {
        val intent = Intent(context, javaClass)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, javaClass))
            ?: intArrayOf()
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        return PendingIntent.getBroadcast(context, widgetId, intent, PendingIntents.getFlags())
    }
}
