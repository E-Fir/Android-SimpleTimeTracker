package com.example.util.simpletimetracker.feature_running_records.adapter.runningRecord

import com.example.util.simpletimetracker.core.di.adapter.BaseRecyclerAdapter
import com.example.util.simpletimetracker.core.di.adapter.ViewHolderType

class RunningRecordAdapter(
    onItemClick: ((RunningRecordViewData) -> Unit)
) : BaseRecyclerAdapter() {

    init {
        delegates[ViewHolderType.VIEW] = RunningRecordAdapterDelegate(onItemClick)
    }
}