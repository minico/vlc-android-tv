/*
 * *************************************************************************
 *  PreferencesFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.resources.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.resources.VLCInstance
import org.videolan.tools.LocaleUtils
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_HUD_TIMEOUT
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.UiTools

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class PreferencesFragment : BasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun getXml() = R.xml.preferences

    override fun getTitleId() = R.string.preferences

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        prepareLocaleList()
    }

    override fun onStop() {
        super.onStop()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "clear_history" -> {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.clear_playback_history)
                        .setMessage(R.string.validation)
                        .setIcon(R.drawable.ic_warning)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                Medialibrary.getInstance().clearHistory()
                                Settings.getInstance(requireActivity()).edit().remove(KEY_AUDIO_LAST_PLAYLIST).remove(KEY_MEDIA_LAST_PLAYLIST).apply()
                            }
                        }
                        .setNegativeButton(R.string.cancel, null).show()
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            "set_locale" -> {
                (activity as PreferencesActivity).setRestart()
                UiTools.restartDialog(requireActivity())
            }
            "hardware_acceleration", "subtitle_text_encoding" -> {
                VLCInstance.restart()
                (activity as? PreferencesActivity)?.restartMediaPlayer()
            }
            VIDEO_HUD_TIMEOUT -> {
                Settings.videoHudDelay = sharedPreferences.getString(VIDEO_HUD_TIMEOUT, "2")?.toInt() ?: 2
            }
        }
    }

    private fun prepareLocaleList() {
        val localePair = LocaleUtils.getLocalesUsedInProject(requireActivity(), BuildConfig.TRANSLATION_ARRAY, getString(R.string.device_default))
        val lp = findPreference<ListPreference>("set_locale")
        lp?.entries = localePair.localeEntries
        lp?.entryValues = localePair.localeEntryValues
    }
}
