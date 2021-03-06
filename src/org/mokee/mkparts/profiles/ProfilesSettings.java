/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2012 The MoKee Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mokee.mkparts.profiles;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.mokee.mkparts.widget.MKBaseSystemSettingSwitchBar;
import org.mokee.mkparts.PartsActivity;
import org.mokee.mkparts.R;
import org.mokee.mkparts.SettingsPreferenceFragment;

import java.util.UUID;

import mokee.app.Profile;
import mokee.app.ProfileManager;
import mokee.providers.MKSettings;

public class ProfilesSettings extends SettingsPreferenceFragment
        implements MKBaseSystemSettingSwitchBar.SwitchBarChangeCallback,
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "ProfilesSettings";

    public static final String EXTRA_PROFILE = "Profile";
    public static final String EXTRA_NEW_PROFILE = "new_profile_mode";

    private static final int MENU_RESET = Menu.FIRST;
    private static final int MENU_APP_GROUPS = Menu.FIRST + 1;

    private final IntentFilter mFilter;
    private final BroadcastReceiver mReceiver;

    private ProfileManager mProfileManager;
    private MKBaseSystemSettingSwitchBar mProfileEnabler;

    private boolean mEnabled;

    ViewGroup mContainer;

    static Bundle mSavedState;

    public ProfilesSettings() {
        mFilter = new IntentFilter();
        mFilter.addAction(ProfileManager.PROFILES_STATE_CHANGED_ACTION);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ProfileManager.PROFILES_STATE_CHANGED_ACTION.equals(action)) {
                    updateProfilesEnabledState();
                }
            }
        };

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.profiles_settings);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        FrameLayout frameLayout = new FrameLayout(getActivity());
        mContainer = frameLayout;
        frameLayout.addView(view);
        return frameLayout;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Add a footer to avoid a situation where the FAB would cover the last
        // item's options in a non-scrollable listview.
        View footer = LayoutInflater.from(getActivity())
                .inflate(R.layout.empty_list_entry_footer, null, false);
        setFooterView(footer);
        footer.setOnClickListener(null);

        View v = LayoutInflater.from(getActivity())
                .inflate(R.layout.empty_textview, (ViewGroup) view, true);

        TextView emptyTextView = (TextView) v.findViewById(R.id.empty);
        setEmptyView(emptyTextView);

        getFloatingActionButton().setImageResource(R.drawable.ic_menu_add_white);
        getFloatingActionButton().setContentDescription(getString(R.string.profiles_add));
        getFloatingActionButton().setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addProfile();
                    }
                });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mProfileManager = ProfileManager.getInstance(getActivity());
        // After confirming PreferenceScreen is available, we call super.
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mProfileEnabler != null) {
            mProfileEnabler.resume(getActivity());
        }
        getActivity().registerReceiver(mReceiver, mFilter);

        // check if we are enabled
        updateProfilesEnabledState();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mProfileEnabler != null) {
            mProfileEnabler.pause();
        }
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        final PartsActivity activity = (PartsActivity) getActivity();
        mProfileEnabler = new MKBaseSystemSettingSwitchBar(activity, activity.getSwitchBar(),
                MKSettings.System.SYSTEM_PROFILES_ENABLED, true, this);
    }

    @Override
    public void onDestroyView() {
        if (mProfileEnabler != null) {
            mProfileEnabler.teardownSwitchBar();
        }
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, MENU_RESET, 0, R.string.profile_reset_title)
                .setAlphabeticShortcut('r')
                .setEnabled(mEnabled)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, MENU_APP_GROUPS, 0, R.string.profile_appgroups_title)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                resetAll();
                return true;
            case MENU_APP_GROUPS:
                startFragment(this, AppGroupList.class.getName(),
                        R.string.profile_appgroups_title, 0, null);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addProfile() {
        Bundle args = new Bundle();
        args.putBoolean(EXTRA_NEW_PROFILE, true);
        args.putParcelable(EXTRA_PROFILE, new Profile(getString(R.string.new_profile_name)));

        PartsActivity pa = (PartsActivity) getActivity();
        pa.startPreferencePanel(SetupTriggersFragment.class.getCanonicalName(), args,
                0, null, this, 0);
    }

    private void resetAll() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.profile_reset_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.profile_reset_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mProfileManager.resetAll();
                        mProfileManager.setActiveProfile(
                                mProfileManager.getActiveProfile().getUuid());
                        dialog.dismiss();
                        refreshList();

                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateProfilesEnabledState() {
        Activity activity = getActivity();

        mEnabled = MKSettings.System.getInt(activity.getContentResolver(),
                MKSettings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;
        activity.invalidateOptionsMenu();

        getFloatingActionButton().setVisibility(mEnabled ? View.VISIBLE : View.GONE);
        if (!mEnabled) {
            getPreferenceScreen().removeAll(); // empty it
        } else {
            refreshList();
        }

        onSettingsChanged(null);
    }

    @Override
    public void onEnablerChanged(boolean isEnabled) {
        Intent intent = new Intent(ProfileManager.PROFILES_STATE_CHANGED_ACTION);
        intent.putExtra(ProfileManager.EXTRA_PROFILES_STATE,
                isEnabled ?
                        ProfileManager.PROFILES_STATE_ENABLED :
                        ProfileManager.PROFILES_STATE_DISABLED);
        getActivity().sendBroadcast(intent);
    }

    public void refreshList() {
        PreferenceScreen plist = getPreferenceScreen();
        plist.removeAll();

        // Get active profile, if null
        Profile prof = mProfileManager.getActiveProfile();
        String selectedKey = prof != null ? prof.getUuid().toString() : null;

        for (Profile profile : mProfileManager.getProfiles()) {
            Bundle args = new Bundle();
            args.putParcelable(ProfilesSettings.EXTRA_PROFILE, profile);
            args.putBoolean(ProfilesSettings.EXTRA_NEW_PROFILE, false);

            ProfilesPreference ppref = new ProfilesPreference(this, args);
            ppref.setKey(profile.getUuid().toString());
            ppref.setTitle(profile.getName());
            ppref.setPersistent(false);
            ppref.setOnPreferenceChangeListener(this);
            ppref.setSelectable(true);
            ppref.setEnabled(true);

            if (TextUtils.equals(selectedKey, ppref.getKey())) {
                ppref.setChecked(true);
            }

            plist.addPreference(ppref);
        }
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (newValue instanceof String) {
            setSelectedProfile((String) newValue);
            refreshList();
        }
        return true;
    }

    private void setSelectedProfile(String key) {
        try {
            UUID selectedUuid = UUID.fromString(key);
            mProfileManager.setActiveProfile(selectedUuid);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }

    public static final SummaryProvider SUMMARY_PROVIDER = new SummaryProvider() {
        @Override
        public String getSummary(Context context, String key) {
            ProfileManager pm = ProfileManager.getInstance(context);
            if (!pm.isProfilesEnabled()) {
                return context.getString(R.string.profile_settings_summary_off);
            }

            Profile p = pm.getActiveProfile();
            if (p != null) {
                return p.getName();
            }
            return null;
        }
    };
}
