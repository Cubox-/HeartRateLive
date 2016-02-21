package me.cubox.heartratelive;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends Activity {
    private static final int REQUEST_CODE = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    public static class MainSettingsFragment extends PreferenceFragment {
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode != REQUEST_CODE || resultCode == RESULT_CANCELED) {
                return;
            }

            Cursor cursor = getActivity().getContentResolver().query(data.getData(), null, null, null, null);
            if (cursor == null) {
                Log.wtf("HR", "Could not find contact. This is not possible.");
                return;
            }
            cursor.moveToFirst();

            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME));
            String phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();

            MultiSelectListPreference contacts = (MultiSelectListPreference)getPreferenceManager().findPreference("contacts");
            String[] entries = (String[])contacts.getEntries();
            String[] newEntries = Arrays.copyOf(entries, entries.length + 1);
            newEntries[newEntries.length-1] = String.format("%s (%s)", name, PhoneNumberUtils.formatNumber(phoneNumber, Locale.getDefault().getCountry()));
            contacts.setEntries(newEntries);

            String[] entryValues = (String[])contacts.getEntryValues();
            String[] newEntryValues = Arrays.copyOf(entryValues, entryValues.length + 1);
            newEntryValues[newEntryValues.length-1] = PhoneNumberUtils.formatNumber(phoneNumber, Locale.getDefault().getCountry());
            contacts.setEntryValues(newEntryValues);

            Set<String> contactValues = contacts.getValues();
            contactValues.add(newEntryValues[newEntryValues.length - 1]);
            contacts.getEditor().putStringSet("contacts", contactValues).apply();

            Preference addcontact = findPreference("addcontact");
            SharedPreferences.Editor addcontectPrefs = addcontact.getEditor();
            addcontectPrefs.putStringSet("entries", new HashSet<>(Arrays.asList(newEntries)));
            addcontectPrefs.putStringSet("entriesValues", new HashSet<>(Arrays.asList(newEntryValues)));
            addcontectPrefs.apply();
        }

        private String[] toStringArray(Object[] objects) {
            return Arrays.copyOf(objects, objects.length, String[].class);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.main_pref);

            MultiSelectListPreference contacts = (MultiSelectListPreference)findPreference("contacts");
            SwitchPreference enabled = (SwitchPreference)findPreference("enabled");
            Preference resetcontacts = findPreference("resetcontacts");

            Preference addcontact = findPreference("addcontact");
            addcontact.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @SuppressWarnings("ConstantConditions")
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                    intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                    startActivityForResult(intent, REQUEST_CODE);
                    return true;
                }
            });

            contacts.setEntries(toStringArray(addcontact.getSharedPreferences().getStringSet("entries", new HashSet<String>()).toArray()));
            contacts.setEntryValues(toStringArray(addcontact.getSharedPreferences().getStringSet("entriesValues", new HashSet<String>()).toArray()));

            resetcontacts.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    MultiSelectListPreference contacts = (MultiSelectListPreference) findPreference("contacts");
                    contacts.setValues(new HashSet<String>());
                    contacts.setEntries(new String[]{});
                    contacts.setEntryValues(new String[]{});
                    return true;
                }
            });

            enabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (preference.isEnabled()) {
                        getActivity().startService(new Intent(getActivity(), SmsListener.class));
                    } else {
                        getActivity().stopService(new Intent(getActivity(), SmsListener.class));
                    }
                    return true;
                }
            });
        }

    }
}
