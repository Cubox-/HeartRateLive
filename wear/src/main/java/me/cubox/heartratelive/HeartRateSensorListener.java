package me.cubox.heartratelive;


import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;

class HeartRateSensorListener implements SensorEventListener {
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        byte[] message = ByteBuffer.allocate(4).putShort(((short)event.values[0])).put(((byte)event.accuracy)).array();
        Wearable.MessageApi.sendMessage(WatchHeartRateListener.googleApiClient, WatchHeartRateListener.node, "/HR", message);

    }
}

