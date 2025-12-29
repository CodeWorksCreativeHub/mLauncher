package com.github.codeworkscreativehub.mlauncher.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import com.github.codeworkscreativehub.common.AppLogger
import com.github.codeworkscreativehub.mlauncher.R
import com.github.codeworkscreativehub.mlauncher.data.Prefs
import com.github.codeworkscreativehub.mlauncher.helper.receivers.LocationResult
import com.github.codeworkscreativehub.mlauncher.helper.receivers.WeatherReceiver
import com.github.codeworkscreativehub.mlauncher.helper.utils.AppReloader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocationSearchFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private val weatherReceiver: WeatherReceiver by lazy { WeatherReceiver(requireContext()) }
    private lateinit var adapter: ArrayAdapter<String>
    private var locations: List<LocationResult> = emptyList()

    private var searchJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<EditText>(R.id.searchInput).requestFocus()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        prefs = Prefs(requireContext())
        val view = inflater.inflate(R.layout.fragment_location_search, container, false)

        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val listView = view.findViewById<ListView>(R.id.resultsList)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1)
        listView.adapter = adapter

        // Live update search as user types
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(600) // debounce
                    if (query.isNotEmpty()) {
                        searchLocations(query)
                    } else {
                        locations = emptyList()
                        adapter.clear()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Save location on click
        listView.setOnItemClickListener { _, _, position, _ ->
            if (locations.isNotEmpty() && position in locations.indices) {
                val location = locations[position]
                prefs.saveLocation(location)
                AppReloader.restartApp(requireContext())
            } else {
                AppLogger.w("LocationSearch", "Item clicked but locations list is empty")
            }
        }

        return view
    }

    private fun searchLocations(query: String) {
        lifecycleScope.launch {
            // 🔹 FIX: update the class property, not a local var
            locations = weatherReceiver.searchLocation(query)

            adapter.clear()
            adapter.addAll(locations.map { it.displayName })
        }
    }
}
