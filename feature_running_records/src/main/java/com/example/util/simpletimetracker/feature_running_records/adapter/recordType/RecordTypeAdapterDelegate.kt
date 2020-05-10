package com.example.util.simpletimetracker.feature_running_records.adapter.recordType

import android.view.ViewGroup
import com.example.util.simpletimetracker.core.adapter.BaseRecyclerAdapterDelegate
import com.example.util.simpletimetracker.core.adapter.BaseRecyclerViewHolder
import com.example.util.simpletimetracker.core.adapter.ViewHolderType
import com.example.util.simpletimetracker.core.extension.ivRecordTypeItemIcon
import com.example.util.simpletimetracker.core.extension.layoutRecordTypeItem
import com.example.util.simpletimetracker.core.extension.tvRecordTypeItemName
import com.example.util.simpletimetracker.feature_running_records.R

class RecordTypeAdapterDelegate(
    private val onItemClick: ((RecordTypeViewData) -> Unit),
    private val onItemLongClick: ((RecordTypeViewData) -> Unit)
) : BaseRecyclerAdapterDelegate() {

    override fun onCreateViewHolder(parent: ViewGroup): BaseRecyclerViewHolder =
        RunningRecordsViewHolder(parent)

    inner class RunningRecordsViewHolder(parent: ViewGroup) :
        BaseRecyclerViewHolder(parent, R.layout.record_type_item_layout) {

        override fun bind(item: ViewHolderType) = with(itemView) {
            item as RecordTypeViewData

            layoutRecordTypeItem.setCardBackgroundColor(item.color)
            ivRecordTypeItemIcon.setBackgroundResource(item.iconId)
            tvRecordTypeItemName.text = item.name

            layoutRecordTypeItem.setOnClickListener {
                onItemClick.invoke(item)
            }
            layoutRecordTypeItem.setOnLongClickListener {
                onItemLongClick.invoke(item)
                true
            }
        }
    }
}