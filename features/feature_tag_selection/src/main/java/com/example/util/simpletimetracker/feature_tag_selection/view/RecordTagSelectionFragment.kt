package com.example.util.simpletimetracker.feature_tag_selection.view

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.viewModels
import com.example.util.simpletimetracker.feature_base_adapter.BaseRecyclerAdapter
import com.example.util.simpletimetracker.feature_base_adapter.category.createCategoryAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.hint.createHintAdapterDelegate
import com.example.util.simpletimetracker.feature_base_adapter.loader.createLoaderAdapterDelegate
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.di.BaseViewModelFactory
import com.example.util.simpletimetracker.core.dialog.OnTagSelectedListener
import com.example.util.simpletimetracker.core.extension.getAllFragments
import com.example.util.simpletimetracker.feature_tag_selection.viewModel.RecordTagSelectionViewModel
import com.example.util.simpletimetracker.navigation.params.RecordTagSelectionParams
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.util.simpletimetracker.feature_tag_selection.databinding.RecordTagSelectionFragmentBinding as Binding

@AndroidEntryPoint
class RecordTagSelectionFragment : BaseFragment<Binding>() {

    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> Binding =
        Binding::inflate

    @Inject
    lateinit var viewModelFactory: BaseViewModelFactory<RecordTagSelectionViewModel>

    private val viewModel: RecordTagSelectionViewModel by viewModels(
        factoryProducer = { viewModelFactory }
    )

    private val adapter: BaseRecyclerAdapter by lazy {
        BaseRecyclerAdapter(
            createLoaderAdapterDelegate(),
            createHintAdapterDelegate(),
            createCategoryAdapterDelegate(viewModel::onCategoryClick)
        )
    }
    private val params: RecordTagSelectionParams by lazy {
        arguments?.getParcelable(ARGS_PARAMS) ?: RecordTagSelectionParams()
    }
    private val listeners: MutableList<OnTagSelectedListener> = mutableListOf()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        when (context) {
            is OnTagSelectedListener -> {
                listeners.add(context)
            }
            is AppCompatActivity -> {
                context.getAllFragments()
                    .filter { it is OnTagSelectedListener }
                    .mapNotNull { it as? OnTagSelectedListener }
                    .let(listeners::addAll)
            }
        }
    }

    override fun initUi(): Unit = with(binding) {
        rvRecordTagSelectionList.apply {
            layoutManager = FlexboxLayoutManager(requireContext()).apply {
                flexDirection = FlexDirection.ROW
                justifyContent = JustifyContent.CENTER
                flexWrap = FlexWrap.WRAP
            }
            adapter = this@RecordTagSelectionFragment.adapter
        }
    }

    override fun initViewModel(): Unit = with(viewModel) {
        extra = params
        viewData.observe(adapter::replace)
        tagSelected.observe(::onTagSelected)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onTagSelected(unused: Unit) {
        listeners.forEach { it.onTagSelected() }
    }

    companion object {
        private const val ARGS_PARAMS = "args_params"

        fun newInstance(data: Any?) = RecordTagSelectionFragment().apply {
            if (data is RecordTagSelectionParams) {
                arguments = Bundle().apply {
                    putParcelable(ARGS_PARAMS, data)
                }
            }
        }
    }
}
