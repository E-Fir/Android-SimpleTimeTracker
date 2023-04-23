package com.example.util.simpletimetracker.feature_records_all.interactor

import com.example.util.simpletimetracker.core.interactor.RecordFilterInteractor
import com.example.util.simpletimetracker.core.mapper.DateDividerViewDataMapper
import com.example.util.simpletimetracker.core.mapper.RecordViewDataMapper
import com.example.util.simpletimetracker.domain.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.interactor.RecordTagInteractor
import com.example.util.simpletimetracker.domain.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.model.Range
import com.example.util.simpletimetracker.domain.model.RecordsFilter
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_records_all.model.RecordsAllSortOrder
import com.example.util.simpletimetracker.navigation.params.screen.TypesFilterParams
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordsAllViewDataInteractor @Inject constructor(
    private val recordTypeInteractor: RecordTypeInteractor,
    private val recordTagInteractor: RecordTagInteractor,
    private val prefsInteractor: PrefsInteractor,
    private val recordFilterInteractor: RecordFilterInteractor,
    private val recordViewDataMapper: RecordViewDataMapper,
    private val dateDividerViewDataMapper: DateDividerViewDataMapper,
) {

    suspend fun getViewData(
        filter: TypesFilterParams,
        sortOrder: RecordsAllSortOrder,
        rangeStart: Long,
        rangeEnd: Long,
        commentSearch: String,
    ): List<ViewHolderType> {
        val isDarkTheme = prefsInteractor.getDarkMode()
        val useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat()
        val useProportionalMinutes = prefsInteractor.getUseProportionalMinutes()
        val showSeconds = prefsInteractor.getShowSeconds()
        val recordTypes = recordTypeInteractor.getAll().associateBy { it.id }
        val recordTags = recordTagInteractor.getAll()

        val records = recordFilterInteractor.mapFilter(filter)
            .let { mainFilters ->
                var finalFilters = mainFilters

                if (finalFilters.isEmpty()) {
                    return@let finalFilters
                }
                if (rangeStart != 0L && rangeEnd != 0L) {
                    finalFilters = finalFilters + RecordsFilter.Date(Range(rangeStart, rangeEnd))
                }
                if (commentSearch.isNotEmpty()) {
                    finalFilters = finalFilters + RecordsFilter.Comment(commentSearch)
                }

                finalFilters
            }.let {
                recordFilterInteractor.getByFilter(it)
            }

        return withContext(Dispatchers.Default) {
            records
                .mapNotNull { record ->
                    Triple(
                        record.timeStarted,
                        record.timeEnded - record.timeStarted,
                        recordViewDataMapper.map(
                            record = record,
                            recordType = recordTypes[record.typeId] ?: return@mapNotNull null,
                            recordTags = recordTags.filter { it.id in record.tagIds },
                            timeStarted = record.timeStarted,
                            timeEnded = record.timeEnded,
                            isDarkTheme = isDarkTheme,
                            useMilitaryTime = useMilitaryTime,
                            useProportionalMinutes = useProportionalMinutes,
                            showSeconds = showSeconds,
                        )
                    )
                }
                .sortedByDescending { (timeStarted, duration, _) ->
                    when (sortOrder) {
                        RecordsAllSortOrder.TIME_STARTED -> timeStarted
                        RecordsAllSortOrder.DURATION -> duration
                    }
                }
                .map { (timeStarted, _, record) -> timeStarted to record }
                .let { viewData ->
                    if (sortOrder == RecordsAllSortOrder.TIME_STARTED) {
                        dateDividerViewDataMapper.addDateViewData(viewData)
                    } else {
                        viewData.map { it.second }
                    }
                }
                .ifEmpty { listOf(recordViewDataMapper.mapToEmpty()) }
        }
    }
}