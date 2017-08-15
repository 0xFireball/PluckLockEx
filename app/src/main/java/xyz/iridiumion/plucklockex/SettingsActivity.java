package xyz.iridiumion.plucklockex;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    private CheckBox enabledCheck;
    private CheckBox deviceAdminCheck;
    private EditText thresholdEdit;
    private Spinner lockMethodSpinner;
    private Button lockNowButton;

    public static float MIN_THRESHOLD = 2f;
    public static float DEFAULT_THRESHOLD = 40f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        this.prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        enabledCheck = (CheckBox) findViewById(R.id.enabled);
        deviceAdminCheck = (CheckBox) findViewById(R.id.enable_device_admin);
        thresholdEdit = (EditText) findViewById(R.id.threshold_edit);
        lockMethodSpinner = (Spinner) findViewById(R.id.lock_method);

        enabledCheck.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                Intent accelerometerIntent = new Intent(getBaseContext(),
                        AccelerometerService.class);
                if (checked) {
                    AccelerometerService.dead = false;
                    getBaseContext().startService(accelerometerIntent);
                } else {
                    AccelerometerService.dead = true;
                    getBaseContext().stopService(accelerometerIntent);
                }

                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(PreferenceString.ENABLED, checked).apply();
            }
        });

        enabledCheck.setChecked(this.prefs.getBoolean(PreferenceString.ENABLED, true));    // this triggers the listener, which will start the Service.

        float currentThreshold = prefs.getFloat(PreferenceString.THRESHOLD, DEFAULT_THRESHOLD);
        if (currentThreshold < MIN_THRESHOLD) {
            currentThreshold = MIN_THRESHOLD;
            prefs.edit().putFloat(PreferenceString.THRESHOLD, currentThreshold).apply();
        }
        thresholdEdit.setText("" + currentThreshold);
        thresholdEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable arg0) {
            }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1,
                                          int arg2, int arg3) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
                try {
                    float newVal = Float.valueOf(s.toString());
                    if (newVal < MIN_THRESHOLD) {
                        thresholdEdit.setBackgroundColor(ContextCompat.getColor(SettingsActivity.this, R.color.red));
                        Toast.makeText(getBaseContext(), getResources().getString(R.string.too_low), Toast.LENGTH_SHORT).show();
                    } else {
                        SharedPreferences.Editor editor = prefs.edit();
                        thresholdEdit.setBackgroundColor(ContextCompat.getColor(SettingsActivity.this, android.R.color.background_light));
                        editor.putFloat(PreferenceString.THRESHOLD, newVal).apply();
                    }
                } catch (NumberFormatException e) {
                    thresholdEdit.setBackgroundColor(ContextCompat.getColor(SettingsActivity.this, R.color.red));
                }
            }
        });

        final ComponentName adminComponent = new ComponentName(this,
                AdminReceiver.class);

        if (!prefs.getBoolean(PreferenceString.DISABLED_DEVICE_ADMIN, false)) {    // user has never unchecked it, ever
            // ask nicely if we can enable device admin
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case DialogInterface.BUTTON_POSITIVE:
                            requestDeviceAdmin(adminComponent);
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            //No button clicked
                            break;
                    }
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("PluckLockEx requires Device Administrator to be enabled to lock the device without root. This permission is only used to lock the device when it is snatched, and all the parameters can be changed in settings. Should this permission be enabled now? PLuckLockEx cannot function without it.").setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener).show();
        }

        final DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        deviceAdminCheck.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                if (isChecked) {
                    requestDeviceAdmin(adminComponent);
                    enabledCheck.setEnabled(true);
                } else {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(PreferenceString.DISABLED_DEVICE_ADMIN, true).apply();
                    dpm.removeActiveAdmin(adminComponent);
                    enabledCheck.setChecked(false);
                    enabledCheck.setEnabled(false);
                }
            }
        });

        deviceAdminCheck.setChecked(dpm.isAdminActive(adminComponent));

        int currentLockMethod = prefs.getInt(PreferenceString.LOCK_METHOD, AccelerometerService.LOCK_METHOD_DEVICE_ADMIN);
        lockMethodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String choice = adapterView.getItemAtPosition(i).toString();
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(PreferenceString.LOCK_METHOD, i).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(PreferenceString.LOCK_METHOD, AccelerometerService.LOCK_METHOD_DEVICE_ADMIN).apply();
            }
        });

        lockMethodSpinner.setSelection(currentLockMethod);

        lockNowButton = (Button) findViewById(R.id.lock_now);
        lockNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AccelerometerService.lockDeviceNow(SettingsActivity.this, getBaseContext());
            }
        });
    }

    private void requestDeviceAdmin(ComponentName adminComponent) {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        startActivity(intent);
    }
}
