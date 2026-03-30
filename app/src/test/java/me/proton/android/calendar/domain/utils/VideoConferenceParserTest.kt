package me.proton.android.calendar.domain.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class VideoConferenceParserTest {

    @Test
    fun `detects Zoom URL with scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Join at https://zoom.us/j/123456789?pwd=abc"))
    }

    @Test
    fun `detects Zoom URL without scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Join at zoom.us/j/123456789"))
    }

    @Test
    fun `detects Google Meet URL without scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Google Meet: meet.google.com/abc-defg-hij"))
    }

    @Test
    fun `detects Teams URL with scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Weekly sync\nhttps://teams.live.com/meet/123?p=pwd\nSee you"))
    }

    @Test
    fun `detects Teams URL without scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Join: teams.live.com/meet/123?p=pwd"))
    }

    @Test
    fun `detects Slack huddle URL without scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Slack huddle: app.slack.com/huddle/T123/C456"))
    }

    @Test
    fun `detects Proton Meet URL with scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Join at https://meet.proton.me/join/id-abc123#pwd-xyz"))
    }

    @Test
    fun `detects Proton Meet URL without scheme`() {
        assertTrue(VideoConferenceParser.containsVideoConferenceUrl("Join at meet.proton.me/join/id-abc123#pwd-xyz"))
    }

    @Test
    fun `returns false for plain text`() {
        assertFalse(VideoConferenceParser.containsVideoConferenceUrl("Office Conference Room 3B"))
    }

    @Test
    fun `returns false for non-conference URL`() {
        assertFalse(VideoConferenceParser.containsVideoConferenceUrl("See docs: https://example.com/meeting-notes"))
    }

    @Test
    fun `returns false for null`() {
        assertFalse(VideoConferenceParser.containsVideoConferenceUrl(null))
    }

    @Test
    fun `extractProtonMeetUrl returns URL from Meet deeplink description`() {
        val description = "https://meet.proton.me/join/id-1K5WVSNMFW#pwd-P93Elc6xkUZL"
        assertEquals(
            "https://meet.proton.me/join/id-1K5WVSNMFW#pwd-P93Elc6xkUZL",
            VideoConferenceParser.extractProtonMeetUrl(description)
        )
    }

    @Test
    fun `extractProtonMeetUrl returns URL when embedded in text`() {
        val description = "Join at https://meet.proton.me/join/id-abc123#pwd-xyz789 for the meeting"
        assertEquals(
            "https://meet.proton.me/join/id-abc123#pwd-xyz789",
            VideoConferenceParser.extractProtonMeetUrl(description)
        )
    }

    @Test
    fun `extractProtonMeetUrl prepends scheme when missing`() {
        val description = "meet.proton.me/join/id-abc123#pwd-xyz789"
        assertEquals(
            "https://meet.proton.me/join/id-abc123#pwd-xyz789",
            VideoConferenceParser.extractProtonMeetUrl(description)
        )
    }

    @Test
    fun `extractProtonMeetUrl returns null for non-meet URL`() {
        assertNull(VideoConferenceParser.extractProtonMeetUrl("https://example.com/meeting"))
    }

    @Test
    fun `extractProtonMeetUrl returns null for null`() {
        assertNull(VideoConferenceParser.extractProtonMeetUrl(null))
    }

    @Test
    fun `extractProtonMeetUrl returns null for Zoom URL`() {
        assertNull(VideoConferenceParser.extractProtonMeetUrl("https://zoom.us/j/123456789?pwd=abc"))
    }
}
