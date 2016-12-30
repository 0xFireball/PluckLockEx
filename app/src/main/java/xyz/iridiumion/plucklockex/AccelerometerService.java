package xyz.iridiumion.plucklockex;

import android.app.KeyguardManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

public class AccelerometerService extends Service {
    public static final int LOCK_METHOD_DEVICE_ADMIN = 0;
    public static final int LOCK_METHOD_ROOT = 1;

    public static boolean dead = false;

    private SensorManager sensorManager;
    private Sensor sensor;
    private SensorEventListener activeListener;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(new PresenceReceiver(), intentFilter);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        activeListener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (AccelerometerService.dead)
                    return;

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                float threshold;

                try {
                    threshold = prefs.getFloat("threshold_pref_key", 1);
                    if (threshold < SettingsActivity.MIN_THRESHOLD) {    // only possible pre-update.
                        threshold = SettingsActivity.DEFAULT_THRESHOLD;
                        prefs.edit().putFloat("threshold_pref_key", threshold).apply();
                    }
                } catch (ClassCastException e) {
                    // The user has a non-float in the settings! Probably because they're migrating from an old version of the app.
                    String thresholdStr = prefs.getString("threshold_pref_key", "1");
                    threshold = Float.valueOf(thresholdStr);
                    prefs.edit().putFloat("threshold_pref_key", threshold).apply();
                }
                double x = Math.abs(event.values[0]);
                double y = Math.abs(event.values[1]);
                double z = Math.abs(event.values[2]);
                double sum = x + y + z;
                Log.i("PluckLockEx", "" + sum);
                if (sum > threshold) {
                    // time to lock
                    int lockMethod = prefs.getInt(PreferenceString.LOCK_METHOD, LOCK_METHOD_DEVICE_ADMIN);
                    switch (lockMethod) {
                        case LOCK_METHOD_DEVICE_ADMIN:
                            KeyguardManager keyguardManager = (KeyguardManager) getBaseContext().getSystemService(Context.KEYGUARD_SERVICE);
                            if (!keyguardManager.inKeyguardRestrictedInputMode()) {
                                DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
                                if (dpm.isAdminActive(new ComponentName(getBaseContext(), AdminReceiver.class)))
                                    dpm.lockNow();
                            }
                            break;
                        case LOCK_METHOD_ROOT:
                            try {
                                Runtime.getRuntime().exec(new String[]{"su", "-c", "input keyevent 26"}).waitFor();
                            } catch (IOException | InterruptedException e) {
                                Toast.makeText(AccelerometerService.this, "PluckLockEx Root access denied", Toast.LENGTH_SHORT).show();
                            }
                            break;
                    }
                }
            }
        };

        sensorManager.registerListener(activeListener, sensor, SensorManager.SENSOR_DELAY_UI);

        return START_STICKY;
    }

    public void killSensor() {
        // THIS DOES NOT WORK.
        sensorManager.unregisterListener(activeListener);

        // workaround that may or may not actually end up improving battery life
        AccelerometerService.dead = true;
    }

}