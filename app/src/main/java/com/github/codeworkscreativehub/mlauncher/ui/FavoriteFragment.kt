package com.github.codeworkscreativehub.mlauncher.ui

// ...existing imports...
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.codeworkscreativehub.common.getLocalizedString
import com.github.codeworkscreativehub.common.showShortToast
import com.github.codeworkscreativehub.mlauncher.MainViewModel
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.AppCategory
import com.github.codeworkscreativehub.mlauncher.data.AppListItem
import com.github.codeworkscreativehub.mlauncher.data.Constants.AppDrawerFlag
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.databinding.FragmentFavoriteBinding
import com.github.codeworkscreativehub.mlauncher.helper.emptyString
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
        val adapter = FavoriteAdapter(
            mutableListOf(),
            viewModel::updateAppOrder,
            prefs,
            onItemClick = { position ->
                // Open the AppDrawerFragment to set a home app for this position
                val args = Bundle().apply {
                    putString("flag", AppDrawerFlag.SetHomeApp.toString())
                    putInt("n", position)
                }
                findNavController().navigate(R.id.appListFragment, args)
            }
        )

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
        // Reload the saved order in case the user selected a new app from the app drawer
        viewModel.loadAppOrder()
    }

    private fun initObservers() {
        binding.pageName.apply {
            text = getLocalizedString(R.string.favorite_apps)
            textSize = prefs.appSize * 1.1f
            setTextColor(prefs.appColor)
        }

        // Setup add/remove buttons
        setupAddRemoveButtons()

        with(viewModel) {
            homeAppsNum.observe(viewLifecycleOwner) { newAppsNum ->
                updateRecyclerView(newAppsNum)
            }
        }
    }

    private fun setupAddRemoveButtons() {
        binding.addAppButton.apply {
            setOnClickListener {
                addHomeAppSlot()
            }
        }

        binding.removeAppButton.apply {
            setOnClickListener {
                removeHomeAppSlot()
            }
        }
    }

    private fun addHomeAppSlot() {
        val currentNum = prefs.homeAppsNum
        val maxSlots = 12  // Set a reasonable maximum

        if (currentNum >= maxSlots) {
            requireContext().showShortToast(getLocalizedString(R.string.max_apps_reached))
            return
        }

        val newNum = currentNum + 1
        prefs.homeAppsNum = newNum
        viewModel.homeAppsNum.postValue(newNum)
        // Refresh order so UI shows the new empty slot immediately
        viewModel.loadAppOrder()

        // Open app drawer to select an app for the newly added favorite
        val args = Bundle().apply {
            putString("flag", AppDrawerFlag.SetHomeApp.toString())
            putInt("n", newNum - 1) // index of new favorite
            putString("profileType", "SYSTEM")
        }
        try {
            findNavController().navigate(R.id.appListFragment, args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeHomeAppSlot() {
        val currentNum = prefs.homeAppsNum
        val minSlots = 1  // Minimum one slot

        if (currentNum <= minSlots) {
            requireContext().showShortToast(getLocalizedString(R.string.min_apps_reached))
            return
        }

        // Build a list of favorite descriptions (show alias or app label, or "Empty favorite")
        val favoriteLabels = (0 until currentNum).map { index ->
            val app = prefs.getHomeAppModel(index)
            if (app.activityPackage.isNotEmpty() && app.activityClass.isNotEmpty()) {
                prefs.getAppAlias(app.activityPackage).takeIf { it.isNotBlank() }
                    ?: app.activityLabel
            } else {
                getLocalizedString(R.string.empty_favorite)
            }
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_app_to_remove)
            .setItems(favoriteLabels) { _, which ->
                // Confirm removal of the selected favorite
                val favoriteIndex = which
                AlertDialog.Builder(requireContext())
                    .setMessage(getLocalizedString(R.string.confirm_remove_favorite))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        removeFavoriteAtPosition(favoriteIndex)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun removeFavoriteAtPosition(position: Int) {
        val currentNum = prefs.homeAppsNum
        if (position < 0 || position >= currentNum) return

        // Shift all subsequent favorites left by one
        for (i in position until currentNum - 1) {
            val next = prefs.getHomeAppModel(i + 1)
            prefs.setHomeAppModel(i, next)
        }

        // Clear the last favorite now duplicated
        val userManager = requireContext().getSystemService(Context.USER_SERVICE) as UserManager
        val clearApp = AppListItem(
            activityLabel = "Clear",
            activityPackage = emptyString(),
            activityClass = emptyString(),
            user = userManager.userProfiles[0],
            profileType = "SYSTEM",
            customTag = emptyString(),
            category = AppCategory.REGULAR
        )
        prefs.setHomeAppModel(currentNum - 1, clearApp)

        // Decrease favorites count and refresh
        val newNum = currentNum - 1
        prefs.homeAppsNum = newNum
        viewModel.homeAppsNum.postValue(newNum)
        viewModel.loadAppOrder()
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
