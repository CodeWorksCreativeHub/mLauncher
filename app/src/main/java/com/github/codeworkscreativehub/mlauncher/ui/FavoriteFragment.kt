package com.github.codeworkscreativehub.mlauncher.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
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

        val view = binding.root
        prefs = Prefs(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backgroundColor = getHexForOpacity(prefs)
        binding.mainLayout.setBackgroundColor(backgroundColor)

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        vibrator = context?.getSystemService(VIBRATOR_SERVICE) as Vibrator

        // Initialize the adapter and pass prefs to it
        val adapter = FavoriteAdapter(mutableListOf(), { from, to ->
            viewModel.updateAppOrder(from, to)
        }, prefs)  // Pass prefs to the adapter

        binding.homeAppsRecyclerview.layoutManager = LinearLayoutManager(requireContext()) // Set LayoutManager
        binding.homeAppsRecyclerview.adapter = adapter

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
                val fromPosition = source.bindingAdapterPosition  // Use bindingAdapterPosition here
                val toPosition = target.bindingAdapterPosition  // Use bindingAdapterPosition here

                // Change the background color when the item is being dragged
                source.itemView.setBackgroundColor(ContextCompat.getColor(source.itemView.context, R.color.hover_effect))

                // Check if the positions are valid
                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    // Update the order of the items in the adapter
                    (recyclerView.adapter as FavoriteAdapter).moveItem(fromPosition, toPosition)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not handling swipe-to-dismiss here
            }

            override fun onSelectedChanged(
                viewHolder: RecyclerView.ViewHolder?,
                actionState: Int
            ) {
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
