package com.microsoft.research.karya.ui.homeScreen

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microsoft.research.karya.BuildConfig
import com.microsoft.research.karya.R
import com.microsoft.research.karya.databinding.FragmentHomeScreenBinding
import com.microsoft.research.karya.ui.base.BaseFragment
import com.microsoft.research.karya.utils.PreferenceKeys
import com.microsoft.research.karya.utils.extensions.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class HomeScreenFragment : BaseFragment(R.layout.fragment_home_screen) {

    private val binding by viewBinding(FragmentHomeScreenBinding::bind)
    private val viewModel by viewModels<HomeScreenViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshXPPoints()
        viewModel.refreshTaskSummary()
        viewModel.refreshPerformanceSummary()
        viewModel.setEarningSummary()
    }

    private fun setupViews() {
        with(binding) {

            viewLifecycleOwner.lifecycleScope.launch {
                val dataStore = requireContext().dataStore
                val profileKey = stringPreferencesKey(PreferenceKeys.CURRENT_USER_ID)
                val userId = withContext(Dispatchers.IO) { dataStore.data.first()[profileKey] }
                userId?.let {
                    appTb.setTitle(it)
                }
            }

            binding.appTb.setKaryaLogoClickListener {
                val singleItems = arrayOf("Divyansh", "Add new profile")
                var checkedItem = 1

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Change user profile")
                    .setNeutralButton("Cancel") { dialog, which ->
                        // Respond to neutral button press
                    }
                    .setPositiveButton("Confirm") { dialog, which ->
                        if (checkedItem == singleItems.size - 1) {
                            // last item means add new user
                            addNewUserProfile()
                        } else {
                            updateUserProfile(singleItems[checkedItem])
                        }
                    }
                    // Single-choice items (initialized with checked item)
                    .setSingleChoiceItems(singleItems, checkedItem) { dialog, which ->
                        // Respond to item chosen
                        checkedItem = which
                    }
                    .show()
            }

            // Move to profile on name click
            nameCv.setOnClickListener {
                val action = HomeScreenFragmentDirections.actionHomeScreenToProfile()
                findNavController().navigate(action)
            }

            // Move to leaderboard on leaderboard click
            moveToLeaderboardCv.setOnClickListener {
                val action = HomeScreenFragmentDirections.actionHomeScreenToLeaderboard()
                findNavController().navigate(action)
            }

            // Move to task dashboard on task click
            taskSummaryCv.setOnClickListener {
                val action = HomeScreenFragmentDirections.actionHomeScreenToDashboard()
                findNavController().navigate(action)
            }

            // Move to payments flow on earning card click
            earningCv.setOnClickListener {
                viewModel.setEarningSummary()
                val workerBalance = viewModel.earningStatus.value.totalEarned
                // Navigate only if worker total earning is greater than 2 rs.
                if (workerBalance > 2.0f) {
                    viewModel.navigatePayment()
                } else {
                    Toast.makeText(requireContext(), "Please earn at least Rs 2", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeUiState() {
        // XP points
        viewModel.points.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { points ->
            binding.pointsTv.text = points.toString()
        }
        // Set name of the user
        viewModel.name.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { name ->
            binding.nameTv.text = name
        }
        // Set phone number of the user
        viewModel.phoneNumber.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { phoneNumber ->
            binding.phoneNumberTv.text = phoneNumber
        }

        // Task summary
        viewModel.taskSummary.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { status ->
            with(binding) {
                val total = with(status) {
                    assignedMicrotasks + completedMicrotasks + submittedMicrotasks + verifiedMicrotasks + skippedMicrotasks + expiredMicrotasks
                }

                if (total > 0) {
                    taskStatsGl.visible()
                    numIncompleteTv.text = status.assignedMicrotasks.toString()
                    numCompletedTv.text = status.completedMicrotasks.toString()
                    numSubmittedTv.text = status.submittedMicrotasks.toString()
                    numVerifiedTv.text = status.verifiedMicrotasks.toString()
                    numSkippedTv.text = status.skippedMicrotasks.toString()
                    numExpiredTv.text = status.expiredMicrotasks.toString()
                } else {
                    taskStatsGl.gone()
                }
            }
        }

        // Performance summary
        viewModel.performanceSummary.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { perf ->
            with(binding) {
                recordingScore.rating = perf.recordingAccuracy
                transcriptionScore.rating = perf.transcriptionAccuracy
                typingScore.rating = perf.typingAccuracy
                imageTaskScore.rating = perf.imageAnnotationAccuracy
            }
        }

        // Earnings summary
        viewModel.earningStatus.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { status ->
            with(binding) {
                earningWeekTv.text = status.weekEarned.toString()
                earningTotalTv.text = status.totalEarned.toString()
                paidTotalTv.text = status.totalPaid.toString()
            }
        }

        // Payments navigation flow
        viewModel.navigationFlow.observe(viewLifecycleOwner.lifecycle, lifecycleScope) { navigation ->
            // Return if payments is not enabled in current config
            if (!BuildConfig.PAYMENTS_ENABLED) {
                return@observe
            }
            val action =
                when (navigation) {
                    HomeScreenNavigation.PAYMENT_REGISTRATION -> HomeScreenFragmentDirections.actionHomeScreenToPaymentRegistration()
                    HomeScreenNavigation.PAYMENT_VERIFICATION -> HomeScreenFragmentDirections.actionHomeScreenToPaymentVerificationFragment()
                    HomeScreenNavigation.PAYMENT_DASHBOARD -> HomeScreenFragmentDirections.actionHomeScreenToPaymentDashboardFragment()
                    HomeScreenNavigation.PAYMENT_FAILURE -> HomeScreenFragmentDirections.actionGlobalPaymentFailureFragment()
                }

            try {
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e("DASHBOARD_NAV_ERROR", e.toString())
            }
        }
    }
}
