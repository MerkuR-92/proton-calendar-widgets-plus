package me.proton.android.calendar.presentation.calendar.fragment

import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.collapse
import me.proton.android.calendar.common.utils.AndroidUtils.expand
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.rotateArrowDownward
import me.proton.android.calendar.common.utils.AndroidUtils.rotateArrowUpward
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.databinding.ItemFormConferenceSectionBinding
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.MeetIntegrationType
import me.proton.android.calendar.domain.usecase.LinkifyAndParseHTMLUseCase
import me.proton.core.util.kotlin.nullIfBlank

object EventDetailsUtils {

    fun setupConferenceDetailsSection(fragment: Fragment,
                                      meetType: MeetIntegrationType?,
                                      event: Event,
                                      binding: ItemFormConferenceSectionBinding,
                                      linkifyAndParseHTMLUseCase: LinkifyAndParseHTMLUseCase,
    ) {
        var isConferenceDetailsVisible = false
        var conferenceDetailsHeight: Int? = null
        val meetUrl = event.meetUrl?.nullIfBlank() ?: return
        with(fragment) {
            binding.joinMeetingButton.text = when (meetType) {
                MeetIntegrationType.ProtonMeet, null -> getString(R.string.join_proton_meet_action)
                MeetIntegrationType.Zoom -> getString(R.string.join_zoom_meeting_action)
            }

            binding.joinMeetingButton.setOnSingleClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(meetUrl))
                startActivity(intent)
            }

            binding.imageButtonAction.setImageResource(R.drawable.ic_proton_squares)
            binding.imageButtonAction.visibleOrInvisible(true)

            event.meetConferenceId?.let { meetConferenceId ->
                val spannableConferenceId: Spannable = SpannableString(
                    getString(R.string.generic_meeting_id, meetConferenceId)
                )
                spannableConferenceId.setSpan(
                    ForegroundColorSpan(requireContext().getColorFromAttr(R.attr.proton_text_weak)),
                    spannableConferenceId.indexOf(meetConferenceId),
                    spannableConferenceId.indexOf(meetConferenceId).plus(meetConferenceId.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.textConferenceId.text = spannableConferenceId
                binding.textConferenceId.visibleOrGone(true)
            } ?: run {
                binding.textConferenceId.isVisible = false
            }

            event.meetConferencePassword?.let { meetConferencePassword ->
                val spannableConferencePassword: Spannable = SpannableString(
                    getString(R.string.generic_meeting_password, meetConferencePassword)
                )
                spannableConferencePassword.setSpan(
                    ForegroundColorSpan(requireContext().getColorFromAttr(R.attr.proton_text_weak)),
                    spannableConferencePassword.indexOf(meetConferencePassword),
                    spannableConferencePassword.indexOf(meetConferencePassword)
                        .plus(meetConferencePassword.length),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.textConferencePassword.text = spannableConferencePassword
                binding.textConferencePassword.visibleOrGone(true)
            } ?: run {
                binding.textConferencePassword.isVisible = false
            }

            if (event.meetMeetingHost != null) {
                event.meetMeetingHost?.let { meetingHost ->
                    binding.textConferenceMeetingHostValue.text =
                        linkifyAndParseHTMLUseCase.execute(meetingHost)
                    binding.textConferenceMeetingHostValue.movementMethod =
                        LinkMovementMethod.getInstance()
                    binding.textConferenceMeetingHost.visibleOrGone(true)
                    binding.textConferenceMeetingHostValue.visibleOrGone(true)
                }
            } else {
                binding.textConferenceMeetingHost.isVisible = false
                binding.textConferenceMeetingHostValue.isVisible = false
            }

            binding.textConferenceMeetingLinkValue.text = linkifyAndParseHTMLUseCase.execute(meetUrl)
            binding.textConferenceMeetingLinkValue.movementMethod = LinkMovementMethod.getInstance()

            binding.textConferenceJoiningInstructions.visibleOrGone(false) // TODO Handle joining instructions once implemented
            binding.textConferenceJoiningInstructions.movementMethod = LinkMovementMethod.getInstance()

            binding.conferenceMoreDetailsTitleLayout.setOnClickListener {
                if (isConferenceDetailsVisible) {
                    // Save expanded view height once so we can animate it
                    val height = collapse(binding.conferenceMoreDetailsContentLayout).first
                    if (conferenceDetailsHeight == null) conferenceDetailsHeight = height
                    rotateArrowDownward(binding.textConferenceMoreDetailsButton)
                    isConferenceDetailsVisible = false
                } else {
                    conferenceDetailsHeight?.let {
                        if (it > 0) expand(binding.conferenceMoreDetailsContentLayout, height = it)
                        else binding.conferenceMoreDetailsContentLayout.visibleOrGone(true)
                    } ?: run {
                        binding.conferenceMoreDetailsContentLayout.visibleOrGone(true)
                    }
                    rotateArrowUpward(binding.textConferenceMoreDetailsButton)
                    isConferenceDetailsVisible = true
                }
            }
            binding.root.visibleOrGone(true)
        }
    }
}