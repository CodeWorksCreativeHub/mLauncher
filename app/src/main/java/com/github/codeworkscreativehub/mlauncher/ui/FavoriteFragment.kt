package com.github.codeworkscreativehub.mlauncher.ui

import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.FragmentFavoriteBinding
import com.github.codeworkscreativehub.mlauncher.helper.getHexForOpacity
import com.github.codeworkscreativehub.mlauncher.ui.adapter.FavoriteAdapter

class FavoriteFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var vibrator: Vibrator

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        prefs = Prefs(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backgroundColor = getHexForOpacity(prefs)
        binding.mainLayout.setBackgroundColor(backgroundColor)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        deviceManager = requireContext().getSystemService(DevicePolicyManager::class.java)
            ?: throw IllegalStateException("DevicePolicyManager unavailable")
        vibrator = requireContext().getSystemService(Vibrator::class.java)
            ?: throw IllegalStateException("Vibrator unavailable")

        // Initialize the adapter and pass prefs to it
        val adapter = FavoriteAdapter(mutableListOf(), viewModel::updateAppOrder, prefs)

        binding.homeAppsRecyclerview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        // Initialize the ItemTouchHelper to enable drag-and-drop
        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = source.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                // Change the background color when the item is being dragged
                source.itemView.setBackgroundColor(
                    ContextCompat.getColor(source.itemView.context, R.color.hover_effect)
                )

                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    val favAdapter = recyclerView.adapter as? FavoriteAdapter ?: return false
                    favAdapter.moveItem(fromPosition, toPosition)
                    return true
                }

                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not handling swipe-to-dismiss here
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                viewHolder?.itemView?.setBackgroundColor(
                    ContextCompat.getColor(viewHolder.itemView.context, R.color.hover_effect)
                )
            }


            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                // Reset the background color after dragging is finished
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.setBackgroundColor(
                    ContextCompat.getColor(
                        viewHolder.itemView.context,
                        R.color.transparent
                    )
                )  // Set the background to transparent
            }
        }


        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.homeAppsRecyclerview)

        // Load the saved order when the fragment starts
        viewModel.loadAppOrder()  // Load the app order

        // Observe LiveData and update RecyclerView when order changes
        viewModel.homeAppsOrder.observe(viewLifecycleOwner) { order ->
            if (order.isNotEmpty()) {
                adapter.updateList(order)  // Update the adapter with the new order
            }
        }

        initObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        val backgroundColor = getHexForOpacity(prefs)
        binding.mainLayout.setBackgroundColor(backgroundColor)
    }

    private fun initObservers() {
        binding.pageName.apply {
            text = getLocalizedString(R.string.favorite_apps)
            textSize = prefs.appSize * 1.1f
            setTextColor(prefs.appColor)
        }

        with(viewModel) {
            homeAppsNum.observe(viewLifecycleOwner) { newAppsNum ->
                updateRecyclerView(newAppsNum)
            }
        }
    }

    private fun updateRecyclerView(newAppsNum: Int) {
        val currentList = viewModel.homeAppsOrder.value ?: emptyList()

        // If the number of apps has changed, update the RecyclerView's list
        if (currentList.size != newAppsNum) {
            val newList = (0 until newAppsNum).map { index ->
                prefs.getHomeAppModel(index) // Retrieve app info from Prefs
            }
            viewModel.homeAppsOrder.postValue(newList) // Update LiveData to trigger RecyclerView refresh
        }
    }
}
