package com.example.util.simpletimetracker.feature_settings.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.BaseViewModel
import com.example.util.simpletimetracker.core.base.SingleLiveEvent
import com.example.util.simpletimetracker.core.extension.lazySuspend
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.model.NavigationTab
import com.example.util.simpletimetracker.domain.extension.flip
import com.example.util.simpletimetracker.domain.extension.orFalse
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_settings.adapter.SettingsBlock
import com.example.util.simpletimetracker.feature_settings.mapper.SettingsMapper
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsAdditionalViewModelDelegate
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsDisplayViewModelDelegate
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsMainViewModelDelegate
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsNotificationsViewModelDelegate
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsParent
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsRatingViewModelDelegate
import com.example.util.simpletimetracker.feature_settings.viewModel.delegate.SettingsTranslatorsViewModelDelegate
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.screen.DateTimeDialogParams
import com.example.util.simpletimetracker.navigation.params.screen.DateTimeDialogType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val mainDelegate: SettingsMainViewModelDelegate,
    val displayDelegate: SettingsDisplayViewModelDelegate,
    val additionalDelegate: SettingsAdditionalViewModelDelegate,
    val translatorsDelegate: SettingsTranslatorsViewModelDelegate,
    private val router: Router,
    private val settingsMapper: SettingsMapper,
    private val ratingDelegate: SettingsRatingViewModelDelegate,
    private val notificationsDelegate: SettingsNotificationsViewModelDelegate,
) : BaseViewModel(), SettingsParent {

    val content: LiveData<List<ViewHolderType>> by lazySuspend { loadContent() }
    val settingsDisplayVisibility: LiveData<Boolean> = MutableLiveData(false)
    val settingsAdditionalVisibility: LiveData<Boolean> = MutableLiveData(false)
    val settingsBackupVisibility: LiveData<Boolean> = MutableLiveData(false)
    val settingsExportImportVisibility: LiveData<Boolean> = MutableLiveData(false)
    val resetScreen: SingleLiveEvent<Unit> = SingleLiveEvent()

    init {
        mainDelegate.init(this)
        notificationsDelegate.init(this)
        displayDelegate.init(this)
        additionalDelegate.init(this)
    }

    override fun onCleared() {
        mainDelegate.clear()
        notificationsDelegate.clear()
        displayDelegate.clear()
        additionalDelegate.clear()
        ratingDelegate.clear()
        translatorsDelegate.clear()
        super.onCleared()
    }

    override suspend fun onUseMilitaryTimeClicked() {
        additionalDelegate.onUseMilitaryTimeClicked()
    }

    fun onVisible() {
        additionalDelegate.onVisible()
        // Update can come from quick settings widget.
        // Update can come from system settings.
        // Need to update card order because it changes on card order dialog.
        viewModelScope.launch { updateContent() }
    }

    // TODO merge all click functions into one, and move to delegates.
    fun onCollapseClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.NotificationsCollapse ->
                notificationsDelegate.onCollapseClick()
            SettingsBlock.DisplayCollapse ->
                displayDelegate.onCollapseClick()
            else -> {
                // Do nothing
            }
        }
    }

    fun onSelectorClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.NotificationsInactivity ->
                notificationsDelegate.onInactivityReminderClicked()
            SettingsBlock.NotificationsActivity ->
                notificationsDelegate.onActivityReminderClicked()
            SettingsBlock.DisplayUntrackedIgnoreShort ->
                displayDelegate.onIgnoreShortUntrackedClicked()
            else -> {
                // Do nothing
            }
        }
    }

    fun onRangeStartClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.NotificationsInactivityDoNotDisturb ->
                notificationsDelegate.onInactivityReminderDoNotDisturbStartClicked()
            SettingsBlock.NotificationsActivityDoNotDisturb ->
                notificationsDelegate.onActivityReminderDoNotDisturbStartClicked()
            SettingsBlock.DisplayUntrackedRange ->
                displayDelegate.onUntrackedRangeStartClicked()
            else -> {
                // Do nothing
            }
        }
    }

    fun onRangeEndClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.NotificationsInactivityDoNotDisturb ->
                notificationsDelegate.onInactivityReminderDoNotDisturbEndClicked()
            SettingsBlock.NotificationsActivityDoNotDisturb ->
                notificationsDelegate.onActivityReminderDoNotDisturbEndClicked()
            SettingsBlock.DisplayUntrackedRange ->
                displayDelegate.onUntrackedRangeEndClicked()
            else -> {
                // Do nothing
            }
        }
    }

    fun onTextClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.Categories -> mainDelegate.onEditCategoriesClick()
            SettingsBlock.Archive -> mainDelegate.onArchiveClick()
            SettingsBlock.DataEdit -> mainDelegate.onDataEditClick()
            SettingsBlock.RateUs -> ratingDelegate.onRateClick()
            SettingsBlock.Feedback -> ratingDelegate.onFeedbackClick()
            SettingsBlock.DisplayCardSize -> displayDelegate.onChangeCardSizeClick()
            else -> {
                // Do nothing
            }
        }
    }

    fun onButtonClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.DisplaySortActivities ->
                displayDelegate.onCardOrderManualClick()
            else -> {
                // Do nothing
            }
        }
    }

    fun onCheckboxClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.AllowMultitasking ->
                mainDelegate.onAllowMultitaskingClicked()
            SettingsBlock.NotificationsShow ->
                notificationsDelegate.onShowNotificationsClicked()
            SettingsBlock.NotificationsShowControls ->
                notificationsDelegate.onShowNotificationsControlsClicked()
            SettingsBlock.NotificationsInactivityRecurrent ->
                notificationsDelegate.onInactivityReminderRecurrentClicked()
            SettingsBlock.NotificationsActivityRecurrent ->
                notificationsDelegate.onActivityReminderRecurrentClicked()
            SettingsBlock.DisplayUntrackedInRecords ->
                displayDelegate.onShowUntrackedInRecordsClicked()
            SettingsBlock.DisplayUntrackedInStatistics ->
                displayDelegate.onShowUntrackedInStatisticsClicked()
            SettingsBlock.DisplayUntrackedRange ->
                displayDelegate.onUntrackedRangeClicked()
            SettingsBlock.DisplayCalendarView ->
                displayDelegate.onShowRecordsCalendarClicked()
            SettingsBlock.DisplayReverseOrder ->
                displayDelegate.onReverseOrderInCalendarClicked()
            SettingsBlock.DisplayShowActivityFilters ->
                displayDelegate.onShowActivityFiltersClicked()
            SettingsBlock.DisplayGoalsOnSeparateTabs ->
                displayDelegate.onShowGoalsSeparatelyClicked()
            SettingsBlock.DisplayKeepScreenOn ->
                displayDelegate.onKeepScreenOnClicked()
            SettingsBlock.DisplayMilitaryFormat ->
                displayDelegate.onUseMilitaryTimeClicked()
            SettingsBlock.DisplayMonthDayFormat ->
                displayDelegate.onUseMonthDayTimeClicked()
            SettingsBlock.DisplayProportionalFormat ->
                displayDelegate.onUseProportionalMinutesClicked()
            SettingsBlock.DisplayShowSeconds ->
                displayDelegate.onShowSecondsClicked()
            else -> {
                // Do nothing
            }
        }
    }

    fun onSpinnerPositionSelected(block: SettingsBlock, position: Int) {
        when (block) {
            SettingsBlock.DarkMode ->
                mainDelegate.onDarkModeSelected(position)
            SettingsBlock.Language ->
                mainDelegate.onLanguageSelected(position)
            SettingsBlock.DisplayDaysInCalendar ->
                displayDelegate.onDaysInCalendarSelected(position)
            SettingsBlock.DisplayWidgetBackground ->
                displayDelegate.onWidgetTransparencySelected(position)
            SettingsBlock.DisplaySortActivities ->
                displayDelegate.onRecordTypeOrderSelected(position)
            else -> {
                // Do nothing
            }
        }
    }

    fun onSettingsDisplayClick() {
        val newValue = settingsDisplayVisibility.value?.flip().orFalse()
        settingsDisplayVisibility.set(newValue)
    }

    fun onSettingsAdditionalClick() {
        val newValue = settingsAdditionalVisibility.value?.flip().orFalse()
        settingsAdditionalVisibility.set(newValue)
    }

    fun onSettingsBackupClick() {
        val newValue = settingsBackupVisibility.value?.flip().orFalse()
        settingsBackupVisibility.set(newValue)
    }

    fun onSettingsExportImportClick() {
        val newValue = settingsExportImportVisibility.value?.flip().orFalse()
        settingsExportImportVisibility.set(newValue)
    }

    fun onDurationSet(tag: String?, duration: Long) {
        when (tag) {
            INACTIVITY_DURATION_DIALOG_TAG,
            ACTIVITY_DURATION_DIALOG_TAG,
            -> notificationsDelegate.onDurationSet(tag, duration)
            IGNORE_SHORT_RECORDS_DIALOG_TAG,
            -> additionalDelegate.onDurationSet(tag, duration)
            IGNORE_SHORT_UNTRACKED_DIALOG_TAG,
            -> displayDelegate.onDurationSet(tag, duration)
        }
    }

    fun onDurationDisabled(tag: String?) {
        when (tag) {
            INACTIVITY_DURATION_DIALOG_TAG,
            ACTIVITY_DURATION_DIALOG_TAG,
            -> notificationsDelegate.onDurationDisabled(tag)
            IGNORE_SHORT_RECORDS_DIALOG_TAG,
            -> additionalDelegate.onDurationDisabled(tag)
            IGNORE_SHORT_UNTRACKED_DIALOG_TAG,
            -> displayDelegate.onDurationDisabled(tag)
        }
    }

    fun onDateTimeSet(timestamp: Long, tag: String?) = viewModelScope.launch {
        when (tag) {
            START_OF_DAY_DIALOG_TAG,
            -> additionalDelegate.onDateTimeSet(timestamp, tag)
            INACTIVITY_REMINDER_DND_START_DIALOG_TAG,
            INACTIVITY_REMINDER_DND_END_DIALOG_TAG,
            ACTIVITY_REMINDER_DND_START_DIALOG_TAG,
            ACTIVITY_REMINDER_DND_END_DIALOG_TAG,
            -> notificationsDelegate.onDateTimeSet(timestamp, tag)
            UNTRACKED_RANGE_START_DIALOG_TAG,
            UNTRACKED_RANGE_END_DIALOG_TAG,
            -> displayDelegate.onDateTimeSet(timestamp, tag)
        }
    }

    fun onTabReselected(tab: NavigationTab?) {
        if (tab is NavigationTab.Settings) {
            resetScreen.set(Unit)
        }
    }

    override fun openDateTimeDialog(
        tag: String,
        timestamp: Long,
        useMilitaryTime: Boolean,
    ) {
        DateTimeDialogParams(
            tag = tag,
            type = DateTimeDialogType.TIME,
            timestamp = timestamp.let(settingsMapper::startOfDayShiftToTimeStamp),
            useMilitaryTime = useMilitaryTime,
        ).let(router::navigate)
    }

    override suspend fun updateContent() {
        content.set(loadContent())
    }

    private suspend fun loadContent(): List<ViewHolderType> {
        val result = mutableListOf<ViewHolderType>()
        result += mainDelegate.getViewData()
        result += ratingDelegate.getViewData()
        result += notificationsDelegate.getViewData()
        result += displayDelegate.getViewData()
        return result
    }

    companion object {
        const val INACTIVITY_DURATION_DIALOG_TAG = "inactivity_duration_dialog_tag"
        const val INACTIVITY_REMINDER_DND_START_DIALOG_TAG = "inactivity_reminder_dnd_start_dialog_tag"
        const val INACTIVITY_REMINDER_DND_END_DIALOG_TAG = "inactivity_reminder_dnd_end_dialog_tag"
        const val ACTIVITY_DURATION_DIALOG_TAG = "activity_duration_dialog_tag"
        const val ACTIVITY_REMINDER_DND_START_DIALOG_TAG = "activity_reminder_dnd_start_dialog_tag"
        const val ACTIVITY_REMINDER_DND_END_DIALOG_TAG = "activity_reminder_dnd_end_dialog_tag"
        const val IGNORE_SHORT_RECORDS_DIALOG_TAG = "ignore_short_records_dialog_tag"
        const val IGNORE_SHORT_UNTRACKED_DIALOG_TAG = "ignore_short_untracked_dialog_tag"
        const val UNTRACKED_RANGE_START_DIALOG_TAG = "untracked_range_start_dialog_tag"
        const val UNTRACKED_RANGE_END_DIALOG_TAG = "untracked_range_end_dialog_tag"
        const val START_OF_DAY_DIALOG_TAG = "start_of_day_dialog_tag"
    }
}
