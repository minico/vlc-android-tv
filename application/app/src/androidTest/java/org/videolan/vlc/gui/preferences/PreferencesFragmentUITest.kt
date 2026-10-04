package org.videolan.vlc.gui.preferences

import androidx.test.espresso.intent.rule.IntentsTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.videolan.vlc.R
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.SCREEN_ORIENTATION

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesFragmentUITest: BasePreferenceUITest() {
    @get:Rule
    val intentsTestRule = IntentsTestRule(PreferencesActivity::class.java)

    lateinit var activity: PreferencesActivity

    override fun beforeTest() {
        activity = intentsTestRule.activity
    }

    @Test
    fun checkPipModeSetting() {
        val key = KEY_VIDEO_APP_SWITCH

        checkModeChanged(key, "0", "0", MAP_PIP_MODE)
        checkModeChanged(key, "1", "0", MAP_PIP_MODE)
        checkModeChanged(key, "2", "0", MAP_PIP_MODE)
    }

    @Test
    fun checkScreenOrientationSetting() {
        val key = SCREEN_ORIENTATION

        checkModeChanged(key, "99", "99", MAP_ORIENTATION)
        checkModeChanged(key, "100", "99", MAP_ORIENTATION)
        checkModeChanged(key, "101", "99", MAP_ORIENTATION)
        checkModeChanged(key, "102", "99", MAP_ORIENTATION)
    }

    companion object {
        val MAP_PIP_MODE = mapOf("0" to R.string.stop, "1" to R.string.play_as_audio_background, "2" to R.string.play_pip_title)
        val MAP_ORIENTATION = mapOf("99" to R.string.screen_orientation_sensor, "101" to R.string.screen_orientation_landscape, "102" to R.string.screen_orientation_portrait)
    }
}
