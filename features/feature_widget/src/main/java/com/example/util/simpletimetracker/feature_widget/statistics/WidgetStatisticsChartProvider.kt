package com.example.util.simpletimetracker.feature_widget.statistics

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.RemoteViews
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
import com.example.util.simpletimetracker.domain.model.RecordType
import com.example.util.simpletimetracker.domain.repo.CategoryRepo
import com.example.util.simpletimetracker.feature_views.IconView
import com.example.util.simpletimetracker.feature_views.extension.dpToPx
import com.example.util.simpletimetracker.feature_views.extension.getBitmapFromView
import com.example.util.simpletimetracker.feature_views.extension.measureExactly
import com.example.util.simpletimetracker.feature_views.extension.pxToDp
import com.example.util.simpletimetracker.feature_views.pieChart.PiePortion
import com.example.util.simpletimetracker.feature_views.viewData.RecordTypeIcon
import com.example.util.simpletimetracker.feature_widget.R
import com.example.util.simpletimetracker.feature_widget.statistics.customView.WidgetStatisticsChartView
import com.example.util.simpletimetracker.navigation.Router
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        val filterType = widgetData.chartFilterType
        val filteredIds = when (filterType) {
            ChartFilterType.ACTIVITY -> widgetData.filteredTypes
            ChartFilterType.CATEGORY -> widgetData.filteredCategories
            ChartFilterType.RECORD_TAG -> widgetData.filteredTags
        }.toList()
        val rangeLength = widgetData.rangeLength

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
            el.koef = try {
                (el.name?.substringAfterLast(" ")?.toFloat() ?: 1.0).toFloat()
            } catch (e: Exception) {
                1.toFloat()
            }
            el.value = (el.value.toFloat() * el.koef).toLong()
        }

        var totalTracked = ""
        var sum = 0F
        var cnt = 0
        var minIndex = -1
        var minValue = 10000000000000
        for (i in chart.indices) {
            val el = chart.elementAt(i)
            sum += el.value
            cnt++
            if (el.value < minValue) {
                minValue = el.value
                minIndex = i
            }
        }

        var el: PiePortion
        var percent: Float
        var timeStr: String
        if (cnt > 1) {
            el = chart.elementAt(minIndex)
            var expectedPercent = 100 / cnt.toFloat()
            percent = el.value / sum * 100
            var percentDiff = expectedPercent - percent
            percentDiff = Math.round(percentDiff * 1000F) / 1000F
            timeStr = timeMapper.formatInterval(
                interval = (sum * percentDiff / 100 / el.koef).toLong() * cnt,
                forceSeconds = showSeconds,
                useProportionalMinutes = useProportionalMinutes,
            )
            totalTracked += "Need ${el.name}:\n$percentDiff%\n$timeStr\n\n"
        }

        for (i in chart.indices) {
            el = chart.elementAt(i)
            percent = el.value / sum * 100
            percent = Math.round(percent * 1000F) / 1000F
            timeStr = timeMapper.formatInterval(
                interval = (el.value.toFloat() / el.koef).toLong(),
                forceSeconds = showSeconds,
                useProportionalMinutes = useProportionalMinutes,
            )
            if (el.koef != 1F) {
                timeStr += " (" + timeMapper.formatInterval(
                    interval = el.value,
                    forceSeconds = showSeconds,
                    useProportionalMinutes = useProportionalMinutes,
                ) + ")"
            }

            totalTracked += "${el.name}:\n$timeStr"
            if (cnt > 1) {
                totalTracked += "\n$percent%"
            }
            totalTracked += "\n\n"
        }

        if (totalTracked.length >= 2) {
            totalTracked = totalTracked.substring(0, totalTracked.length - 2)
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
