package com.microsoft.research.karya.ui.scenarios.imageData

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.microsoft.research.karya.R
import com.microsoft.research.karya.databinding.MicrotaskImageDataBinding
import com.microsoft.research.karya.ui.scenarios.common.BaseMTRendererFragment
import com.microsoft.research.karya.utils.ImageUtils.bitmapFromFile
import com.microsoft.research.karya.utils.extensions.*
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraOptions
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.gesture.Gesture
import com.otaliastudios.cameraview.gesture.GestureAction
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.math.max

@AndroidEntryPoint
class ImageDataFragment : BaseMTRendererFragment(R.layout.microtask_image_data) {
    override val viewModel: ImageDataViewModel by viewModels()
    private val args: ImageDataFragmentArgs by navArgs()

    private val binding by viewBinding(MicrotaskImageDataBinding::bind)

    private var localImageState: MutableList<Boolean> = mutableListOf()
    private var currentImageIndex: Int = 0
    private lateinit var imageListAdapter: ImageListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        viewModel.setupViewModel(args.taskId, args.completed, args.total)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.cameraCv) {
            pictureMetering = false
            mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS)
        }

        setupObservers()
        setupListeners()
    }

    override fun requiredPermissions(): Array<String> {
        return arrayOf(android.Manifest.permission.CAMERA)
    }

    private fun setupObservers() {
        viewModel.newImageCount.observe(viewLifecycleOwner.lifecycle, viewLifecycleScope) { pair ->
            val count = pair.second
            localImageState.clear()
            for (i in 0..count - 1) {
                val outputFilePath = viewModel.outputFilePath(i)
                localImageState.add(File(outputFilePath).exists())
            }

            val instruction = getString(R.string.image_data_collection_instruction).replace("#", (count - 1).toString())
            binding.instructionTv.text = instruction

            // Reinitialize state
            resetAdapter()

            updateNavigationState()
        }
    }

    private fun setupListeners() = with(binding) {
        // Camera event listeners
        cameraCv.addCameraListener(object : CameraListener() {
            // When camera is opened, make capture view visible
            override fun onCameraOpened(options: CameraOptions) {
                super.onCameraOpened(options)
                switchToCaptureView()
            }

            // Enable start capture button, after camera is closed
            override fun onCameraClosed() {
                super.onCameraClosed()
                startCaptureBtn.root.enable()
                recaptureBtn.enable()
            }

            // When picture is taken, move it to the correct file
            override fun onPictureTaken(result: PictureResult) {
                super.onPictureTaken(result)
                val imagePath = viewModel.outputFilePath(currentImageIndex)
                result.toFile(File(imagePath)) { file ->
                    if (file != null) {
                        localImageState[currentImageIndex] = true
                        updateAdapter(currentImageIndex)

                        // Show a toast to indicate that the picuture is taken
                        if (currentImageIndex < localImageState.lastIndex && !localImageState[currentImageIndex + 1]) {
                            val toastText: String = if (currentImageIndex == 0) {
                                "Picture taken. Move to a random page and take a picture."
                            } else {
                                "Picture taken. Take picture of the next page."
                            }
                            Toast.makeText(requireContext(), toastText, Toast.LENGTH_LONG).show()
                        }

                        moveToNextImage()
                        takePictureBtn.enable()
                    }
                }
            }
        })

        // When the capture button is clicked
        startCaptureBtn.root.setOnClickListener {
            val index = localImageState.indexOf(false)
            currentImageIndex = max(index, 0)
            startCaptureBtn.root.disable()
            recaptureBtn.disable()
            cameraCv.open()
        }

        // When take picture button is clicked, take picture
        takePictureBtn.setOnClickListener {
            takePictureBtn.disable()
            cameraCv.takePicture()
        }

        // When the back to grid button is clicked
        backToGridViewBtn.setOnClickListener {
            switchToGridView()
        }

        nextBtnCv.root.setOnClickListener {
            viewModel.completeDataCollection(localImageState)
        }

        closeFullImageViewBtn.setOnClickListener {
            switchToGridView()
        }

        recaptureBtn.setOnClickListener {
            recaptureBtn.disable()
            startCaptureBtn.root.disable()
            cameraCv.open()
        }

        nextImageCv.setOnClickListener {
            if (currentImageIndex < localImageState.lastIndex && localImageState[currentImageIndex + 1]) {
                currentImageIndex++
                updateFullImageView()
            } else {
                switchToGridView()
            }
        }

        previousImageCv.setOnClickListener {
            if (currentImageIndex > 0 && localImageState[currentImageIndex - 1]) {
                currentImageIndex--
                updateFullImageView()
            } else {
                switchToGridView()
            }
        }

        backBtn.root.setOnClickListener { }
    }

    private fun updateNavigationState() = with(binding) {
        val complete = localImageState.all { it }
        if (complete) {
            nextBtnCv.root.isClickable = true
            nextBtnCv.nextIv.setBackgroundResource(R.drawable.ic_next_enabled)
        } else {
            nextBtnCv.root.isClickable = false
            nextBtnCv.nextIv.setBackgroundResource(R.drawable.ic_next_disabled)
        }
    }

    private fun switchToGridView() = with(binding) {
        updateNavigationState()
        imageDataCaptureView.invisible()
        fullImageDisplayView.invisible()
        imageDataGridView.visible()
        cameraCv.close()
    }

    private fun switchToCaptureView() = with(binding) {
        imageDataGridView.invisible()
        fullImageDisplayView.invisible()
        imageDataCaptureView.visible()
        val label = if (currentImageIndex == 0) "Front Cover" else "Picture $currentImageIndex"
        imageLabelTv.text = label
    }

    private fun switchToFullImageDisplayView() = with(binding) {
        imageDataCaptureView.invisible()
        imageDataGridView.invisible()
        fullImageDisplayView.visible()
        updateFullImageView()
    }

    private fun moveToNextImage() {
        currentImageIndex++
        while (currentImageIndex < localImageState.size && localImageState[currentImageIndex]) {
            currentImageIndex++
        }
        if (currentImageIndex >= localImageState.size) {
            currentImageIndex = 0
            switchToGridView()
        } else {
            val label = if (currentImageIndex == 0) "Front Cover" else "Picture $currentImageIndex"
            binding.imageLabelTv.text = label
        }
    }

    private fun resetAdapter() {
        val pathList: MutableList<String> = localImageState.mapIndexed { index, present ->
            when (present) {
                false -> ""
                true -> viewModel.outputFilePath(index)
            }
        }.toMutableList()

        imageListAdapter = ImageListAdapter(requireContext(), pathList) { index ->
            handleGridImageClick(index)
        }
        binding.imagesGv.adapter = imageListAdapter
    }

    private fun updateAdapter(index: Int) {
        val path = when (localImageState[index]) {
            false -> ""
            true -> viewModel.outputFilePath(index)
        }
        imageListAdapter.updateItem(index, path)
    }

    private fun handleGridImageClick(index: Int) = with(binding) {
        currentImageIndex = index
        if (localImageState[index]) {
            switchToFullImageDisplayView()
        } else {
            recaptureBtn.disable()
            startCaptureBtn.root.disable()
            cameraCv.open()
        }
    }

    private fun updateFullImageView() = with(binding) {
        // Text label
        val label = if (currentImageIndex == 0) "Front Cover" else "Picture $currentImageIndex"
        fullImageLabelTv.text = label

        // Image path
        val path = viewModel.outputFilePath(currentImageIndex)
        fullImage.setImageBitmap(bitmapFromFile(path))
    }
}
