package com.microsoft.research.karya.ui.scenarios.speechTranscription

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.microsoft.research.karya.R
import com.microsoft.research.karya.data.model.karya.enums.AssistantAudio
import com.microsoft.research.karya.databinding.MicrotaskSpeechTranscriptionBinding
import com.microsoft.research.karya.ui.scenarios.common.BaseMTRendererFragment
import com.microsoft.research.karya.ui.scenarios.speechTranscription.SpeechTranscriptionViewModel.ButtonState
import com.microsoft.research.karya.utils.extensions.observe
import com.microsoft.research.karya.utils.extensions.viewBinding
import com.microsoft.research.karya.utils.extensions.viewLifecycleScope
import com.microsoft.research.karya.utils.spotlight.SpotlightBuilderWrapper
import com.microsoft.research.karya.utils.spotlight.TargetData
import com.potyvideo.library.globalInterfaces.AndExoPlayerListener
import com.takusemba.spotlight.shape.Circle
import com.takusemba.spotlight.shape.RoundedRectangle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayList

@AndroidEntryPoint
class SpeechTranscriptionFragment :
    BaseMTRendererFragment(R.layout.microtask_speech_transcription) {
    override val viewModel: SpeechTranscriptionViewModel by viewModels()
    val args: SpeechTranscriptionFragmentArgs by navArgs()

    private val binding by viewBinding(MicrotaskSpeechTranscriptionBinding::bind)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        // TODO: Remove this once we have viewModel Factory
        viewModel.setupViewModel(args.taskId, args.completed, args.total)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()

        /** record instruction */
        val recordInstruction =
            viewModel.task.params.asJsonObject.get("instruction").asString
                ?: getString(R.string.speech_recording_instruction)

        with(binding) {
            instructionTv.text = recordInstruction

            /** Set on click listeners for buttons */

            /** Set on click listeners for buttons */
            playBtn.setOnClickListener { viewModel.handlePlayClick() }
            nextBtnCv.root.setOnClickListener {
                // Check if user has entered the text
                if (transcriptionEt.text.isNullOrEmpty()) {
                    skipTask(true, "", getString(R.string.skip_task_warning))
                    return@setOnClickListener
                } else {
                    val alertDialogBuilder = AlertDialog.Builder(requireContext())
                    alertDialogBuilder.setTitle("Review the transcription.")
                    alertDialogBuilder.setMessage(transcriptionEt.text.toString())
                    alertDialogBuilder.setPositiveButton(R.string.yes) { _, _ ->
                        viewModel.setTranscriptionText(transcriptionEt.text.toString())
                        viewModel.handleNextClick()
                        transcriptionEt.text.clear()
                    }
                    alertDialogBuilder.setNegativeButton(R.string.no) { _, _ -> }
                    val alertDialog = alertDialogBuilder.create()
                    alertDialog.setCancelable(true)
                    alertDialog.setCanceledOnTouchOutside(true)
                    alertDialog.show()
                }
            }

            backBtnCv.root.setOnClickListener { viewModel.handleBackClick() }

            audioPlayer.setAndExoPlayerListener(object : AndExoPlayerListener {
                override fun onExoPlayerError(errorMessage: String?) {
                    viewModel.handleCorruptAudio(errorMessage)
                }
            })
        }
    }

    private fun setupObservers() = with(binding) {
        viewModel.assistWords.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { words ->
            if (words.isEmpty() && assistanceFl.childCount != 0) {
                // Clear the edit text
                transcriptionEt.setText("")
                return@observe
            }
            // Only add if Flow layout is empty
            if (assistanceFl.childCount != 0) {
                return@observe
            }
            for (word in words) {
                val wordButton = Button(requireContext())
                wordButton.text = word
                wordButton.setOnClickListener {
                    transcriptionEt.setText(transcriptionEt.text.toString() + " " + word)
                    transcriptionEt.setSelection(transcriptionEt.length()) // placing cursor at the end of the text
                }
                assistanceFl.addView(wordButton)
            }
        }

        viewModel.recordingFilePath.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { path ->
            if (path.isNotEmpty()) {
                audioPlayer.setSource(path)
                audioPlayer.pausePlayer()
            }
        }

        viewModel.transcriptionText.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { text ->
            transcriptionEt.setText(text)
        }

        viewModel.playbackSecondsTvText.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { text ->
            playbackProgress.secondsTv.text = text
        }

        viewModel.playbackCentiSecondsTvText.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { text ->
            playbackProgress.centiSecondsTv.text = text
        }

        viewModel.playbackProgressPbMax.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { max ->
            playbackProgress.progressPb.max = max
        }

        viewModel.playbackProgress.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { progress ->
            playbackProgress.progressPb.progress = progress
        }

        viewModel.navAndMediaBtnGroup.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { states ->
            flushButtonStates(states.first, states.second, states.third)
        }

        viewModel.showErrorWithDialog.observe(
            viewLifecycleOwner.lifecycle, viewLifecycleScope
        ) { msg ->
            if (msg.isNotEmpty()) {
                showErrorDialog(msg)
            }
        }

        // Trigger Spotlight
        viewModel.playRecordPromptTrigger.observe(
            viewLifecycleOwner.lifecycle,
            viewLifecycleScope
        ) { play ->
            if (play) {
                viewLifecycleScope.launch {
                    // THIS IS A HACK TO WAIT FOR THE VIEWS TO SETUP
                    // SO THAT WE CAN GET ACTUAL HEIGHT AND WIDTH OF
                    // VIEWS FOR THE TARGETS IN SPOTLIGHT. PLEASE FIND
                    // AN ALTERNATIVE TO WAIT FOR THE VIEWS TO SETUP AND
                    // THEN CALL SETUP VIEWS
                    delay(1000)
                    setupSpotLight()
                }
            }
        }
    }

    private fun showErrorDialog(msg: String) {
        val alertDialogBuilder = AlertDialog.Builder(requireContext())
        alertDialogBuilder.setMessage(msg)
        alertDialogBuilder.setNeutralButton("Ok") { _, _ ->
        }
        val alertDialog = alertDialogBuilder.create()
        alertDialog.setCancelable(false)
        alertDialog.setCanceledOnTouchOutside(false)
        alertDialog.show()
    }

    /** Flush the button states */
    private fun flushButtonStates(
        backBtnState: ButtonState,
        playBtnState: ButtonState,
        nextBtnState: ButtonState
    ) = with(binding) {
        playBtn.isClickable = playBtnState != ButtonState.DISABLED
        backBtnCv.root.isClickable = backBtnState != ButtonState.DISABLED
        nextBtnCv.root.isClickable = nextBtnState != ButtonState.DISABLED

        playBtn.setBackgroundResource(
            when (playBtnState) {
                ButtonState.DISABLED -> R.drawable.ic_speaker_disabled
                ButtonState.ENABLED -> R.drawable.ic_speaker_enabled
                ButtonState.ACTIVE -> R.drawable.ic_speaker_active
            }
        )

        nextBtnCv.nextIv.setBackgroundResource(
            when (nextBtnState) {
                ButtonState.DISABLED -> R.drawable.ic_next_disabled
                ButtonState.ENABLED -> R.drawable.ic_next_enabled
                ButtonState.ACTIVE -> R.drawable.ic_next_enabled
            }
        )

        backBtnCv.backIv.setBackgroundResource(
            when (backBtnState) {
                ButtonState.DISABLED -> R.drawable.ic_back_disabled
                ButtonState.ENABLED -> R.drawable.ic_back_enabled
                ButtonState.ACTIVE -> R.drawable.ic_back_enabled
            }
        )
    }

    private fun setupSpotLight() = with(binding) {

        val spotlightPadding = 20

        val targetsDataList = ArrayList<TargetData>()
        targetsDataList.add(
            TargetData(
                audioPlayer,
                RoundedRectangle(audioPlayer.measuredHeight.toFloat() + spotlightPadding, audioPlayer.measuredWidth.toFloat() + spotlightPadding, 5F),
                R.layout.spotlight_target_temp,
                AssistantAudio.SPEECH_TRANSCRIPTION_AUDIO_PLAYER,
            )
        )

        targetsDataList.add(
            TargetData(
                transcriptionEt,
                RoundedRectangle(transcriptionEt.height.toFloat() + spotlightPadding, transcriptionEt.width.toFloat() + spotlightPadding, 5F),
                R.layout.spotlight_target_temp,
                AssistantAudio.SPEECH_TRANSCRIPTION_EDIT_TEXT,
            )
        )

        targetsDataList.add(
            TargetData(
                assistanceFl,
                RoundedRectangle(assistanceFl.height.toFloat() + spotlightPadding, assistanceFl.width.toFloat() + spotlightPadding, 5F),
                R.layout.spotlight_target_temp,
                AssistantAudio.SPEECH_TRANSCRIPTION_ASSISTANCE_LAYOUT,
            )
        )

        targetsDataList.add(
            TargetData(
                nextBtnCv.root,
                Circle(((nextBtnCv.root.height + spotlightPadding) / 2).toFloat()),
                R.layout.spotlight_target_temp,
                AssistantAudio.SPEECH_TRANSCRIPTION_NEXT_BUTTON,
            )
        )

        targetsDataList.add(
            TargetData(
                backBtnCv.root,
                Circle(((backBtnCv.root.height + spotlightPadding) / 2).toFloat()),
                R.layout.spotlight_target_temp,
                AssistantAudio.SPEECH_TRANSCRIPTION_BACK_BUTTON,
            )
        )

        val builderWrapper = SpotlightBuilderWrapper(this@SpeechTranscriptionFragment, targetsDataList)

        builderWrapper.start()
    }
}
