package me.cubox.heartratelive;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class WatchHeartRateListener extends WearableListenerService {
    public static GoogleApiClient googleApiClient;
    public static String node;
    private static HeartRateSensorListener listener = null;
    private static Timer timeout = null;
    private static int active = 0;

    public void onMessageReceived(MessageEvent messageEvent) {
        if (!messageEvent.getPath().equals("/HR")) {
            Log.d("HR", "Not a HR message");
            return;
        }

        googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API)
                .build();
        googleApiClient.blockingConnect();
        node = messageEvent.getSourceNodeId();
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BODY_SENSORS)
                == PackageManager.PERMISSION_DENIED) {
            Wearable.MessageApi.sendMessage(googleApiClient, node, "HR", "E: Permission denied".getBytes());
            Log.d("HR", "Permission denied");
            return;
        }


        SensorManager sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        if (new String(messageEvent.getData()).equals("Start")) {
            if (listener != null) {
                timeout.cancel();
                timeout = new Timer();
                timeout.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.d("HR", "Stopped after delay");
                        SensorManager sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
                        sensorManager.unregisterListener(listener);
                        active = 0;
                        listener = null;
                    }
                }, TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES));
            }
            Log.d("HR", "Started");
            listener = new HeartRateSensorListener();
            Boolean done = sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE), SensorManager.SENSOR_DELAY_NORMAL);
            if (!done) {
                Wearable.MessageApi.sendMessage(googleApiClient, node, "HR", "E: Error setting up listener".getBytes());
                Log.d("HR", "Error setting up listener");
                return;
            }
            active++;

            timeout = new Timer();
            timeout.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d("HR", "Stopped after delay");
                    SensorManager sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
                    sensorManager.unregisterListener(listener);
                    active = 0;
                    listener = null;
                }
            }, TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES));
        } else {
            active--;
            if (active <= 0) {
                active = 0;
                Log.d("HR", "Stopped");
                sensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
                sensorManager.unregisterListener(listener);
                listener = null;
            }
        }
    }
}
