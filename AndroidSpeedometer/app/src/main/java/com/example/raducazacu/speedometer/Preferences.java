package com.example.raducazacu.speedometer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Preferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG 		= "Preferences";
    private static final String DEF_VALUE 	= "Not Set";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        CharSequence value = DEF_VALUE;
        Preference pref = findPreference(key);
        updatePrefSummary(key, pref);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Setup the initial values
        setAllSummaries();

        // Set up a listener whenever a key changes
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setAllSummaries() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, ?> map = sharedPreferences.getAll();
        Set<String> keySet = map.keySet();
        Iterator<String> it = keySet.iterator();
        while (it.hasNext()) {
            String key = it.next();
            Preference pref = findPreference(key);
            if (pref != null) {
                updatePrefSummary(key, pref);
            }
        }
    }

    private void updatePrefSummary(String key, Preference p){
        CharSequence value 		= DEF_VALUE;
        CharSequence summary	= getSummaryForKey(key);

        if (p instanceof ListPreference) {
            ListPreference listPref = (ListPreference) p;
            value = listPref.getEntry();
        } else if (p instanceof CheckBoxPreference) {
            value = null;
        } else if (p instanceof EditTextPreference) {
            EditTextPreference editTextPref = (EditTextPreference) p;
            value = editTextPref.getText();
        }

        // Do not show value if it is NULL
        if (value == null) {
            p.setSummary(summary);
        } else {
            p.setSummary(summary + ": " + value);
        }
    }

    private String getSummaryForKey(String key) {
        String summary = null;

        if (getString(R.string.pref_wheel_size).equals(key)) {
            summary = getString(R.string.pref_wheel_size);

        }
//        else if (   getString(R.string.pref_display_orientation_key).equals(key)) {
//            summary = getString(R.string.pref_display_orientation_summary);
//
//        } else if (   getString(R.string.pref_keyboard_type_key).equals(key)) {
//            summary = getString(R.string.pref_keyboard_type_summary);
//
//        } else if (   getString(R.string.pref_show_keyboard_key).equals(key)) {
//            summary = getString(R.string.pref_show_keyboard_summary);
//
//        } else if    (getString(R.string.pref_local_echo_key).equals(key)) {
//            summary = getString(R.string.pref_local_echo_summary);
//
//        } else if (   getString(R.string.pref_clear_action_key).equals(key)) {
//            summary = getString(R.string.pref_clear_action_summary);
//
//        } else if (   getString(R.string.pref_display_colors_key).equals(key)) {
//            summary = getString(R.string.pref_display_colors_summary);
//
//        } else if (   getString(R.string.pref_font_size_key).equals(key)) {
//            summary = getString(R.string.pref_font_size_summary);
//
//        } else if (   getString(R.string.pref_font_typeface_key).equals(key)) {
//            summary = getString(R.string.pref_font_typeface_summary);
//
//        }

        return summary;
    }
}

