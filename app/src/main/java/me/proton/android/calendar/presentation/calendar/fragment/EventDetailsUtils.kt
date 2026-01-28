package me.proton.android.calendar.presentation.calendar.fragment

import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
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
            binding.imageButtonAction.isVisible = true

            val showPassword = meetType != MeetIntegrationType.ProtonMeet
            if (showPassword && event.meetConferencePassword != null) {
                val meetConferencePassword = event.meetConferencePassword!!
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
            } else {
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

            val expandedContent = binding.conferenceMoreDetailsContentLayout
            if (expandedContent.isVisible) {
                rotateArrowUpward(binding.textConferenceMoreDetailsButton)
                binding.textConferenceMoreDetails.setText(R.string.generic_meeting_less_details)
            } else {
                rotateArrowDownward(binding.textConferenceMoreDetailsButton)
                binding.textConferenceMoreDetails.setText(R.string.generic_meeting_more_details)
            }

            var conferenceDetailsHeight: Int? = null

            binding.conferenceMoreDetailsTitleLayout.setOnClickListener {
                if (expandedContent.isVisible) {
                    collapse(expandedContent)
                    rotateArrowDownward(binding.textConferenceMoreDetailsButton)
                    binding.textConferenceMoreDetails.setText(R.string.generic_meeting_more_details)
                } else {
                    if (conferenceDetailsHeight == null) { // first time expansion, or section was re-created
                        expandedContent.clearAnimation()
                        expandedContent.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        expandedContent.requestLayout()
                        expandedContent.isVisible = true
                        expandedContent.post {
                            conferenceDetailsHeight = expandedContent.measuredHeight
                        }
                    } else {
                        expand(expandedContent, height = conferenceDetailsHeight)
                    }
                    rotateArrowUpward(binding.textConferenceMoreDetailsButton)
                    binding.textConferenceMoreDetails.setText(R.string.generic_meeting_less_details)
                }
            }
            binding.root.isVisible = true
        }
    }
}