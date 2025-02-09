package com.dwagner.filepicker.ui.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.dwagner.filepicker.R
import com.dwagner.filepicker.databinding.FilePickerBinding
import com.dwagner.filepicker.io.AndroidFile
import com.dwagner.filepicker.ui.files.selected.SelectedItemAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel


class FilePickerFragment : Fragment(), SelectionObserver, DataLoadedObserver {

    private var binding: FilePickerBinding? = null
    private val fpViewModel: FilePickerViewModel by viewModel()
    private lateinit var selectedAdapter : SelectedItemAdapter
    private lateinit var fileItemAdapter: FileItemAdapter

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher.
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission())
        { isGranted: Boolean ->
            if (isGranted) {
                // permission granted, set up view
                permissionGranted()
            } else {
                // permission not granted
                Toast.makeText(requireActivity(), R.string.need_permission, Toast.LENGTH_SHORT)
                    .show()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FilePickerBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val readStoragePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        when (readStoragePermission) {
            PackageManager.PERMISSION_GRANTED -> {
                permissionGranted()
            }
            PackageManager.PERMISSION_DENIED -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun permissionGranted() {
        fileItemAdapter = FileItemAdapter(layoutInflater, fpViewModel)
        selectedAdapter = SelectedItemAdapter(layoutInflater, fpViewModel)

        if(fpViewModel.lastLoadedFiles().isEmpty()) {
            // get all files, because no files are loaded from previous runs
            fpViewModel.getFiles(FilterMode.ALL)
        }

        else {
            fileItemAdapter.submitList(fpViewModel.lastLoadedFiles())
        }

        fpViewModel.observeSelectedFiles(this)
        fpViewModel.observeLoadedData(this)
        this.updateSelectedUI()

        // setting up RecyclerViews
        binding?.items?.adapter = fileItemAdapter
        binding?.selectedItems?.adapter = selectedAdapter
        val layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding?.selectedItems?.layoutManager = layoutManager

        binding?.deselectAll?.setOnClickListener { fpViewModel.deselectAll() }
        binding?.acceptChoice?.setOnClickListener {
            // show URIs of all selected files in a dialog
            val fileURIs =
                fpViewModel.getSelectedFiles().map { it.fileURI.toString() }.toTypedArray()
            ShowURIsDialogFragment(fileURIs).show(childFragmentManager, ShowURIsDialogFragment.TAG)
            this.fpViewModel.deselectAll()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.actions_picker, menu)
        when (fpViewModel.lastFilterMode) {
            FilterMode.ALL -> menu.findItem(R.id.all).isChecked = true
            FilterMode.VIDEO -> menu.findItem(R.id.video).isChecked = true
            FilterMode.PHOTO -> menu.findItem(R.id.photo).isChecked = true
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.all -> {
            fpViewModel.getFiles(FilterMode.ALL)
            item.isChecked = true
            true
        }
        R.id.photo -> {
            fpViewModel.getFiles(FilterMode.PHOTO)
            item.isChecked = true
            true
        }
        R.id.video -> {
            fpViewModel.getFiles(FilterMode.VIDEO)
            item.isChecked = true
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        binding = null
        fpViewModel.stopObservingLoadedData(this)
        fpViewModel.stopObservingSelectedFiles(this)
        this.fileItemAdapter.onDestroy()
        this.selectedAdapter.onDestroy()
        super.onDestroyView()
    }

    override fun onDataLoaded() {
        updateSelectedUI()
    }

    override fun onSelection(file: AndroidFile, isSelected: Boolean) {
        updateSelectedUI()
    }

    override fun onDeselectAll(selected: List<AndroidFile>) {
        // ignore list of previously selected files, already handled by adapters
        updateSelectedUI()
    }

    // recycler views get new data from view model
    private fun updateSelectedUI() {
        if (fpViewModel.getSelectedFiles().isEmpty()) {
            // handle updating if no files selected
            binding?.selectedView?.visibility = View.GONE

        } else {
            // handling updating if files are selected
            binding?.selectedView?.visibility = View.VISIBLE
            binding?.selectedText?.text =
                String.format(getString(R.string.selected_text), fpViewModel.getSelectedFiles().size)
        }
    }
}