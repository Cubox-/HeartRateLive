package me.cubox.heartratelive;

import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;

import java.nio.ByteBuffer;

class HeartRateMessageListener implements MessageApi.MessageListener {
    public void onMessageReceived(MessageEvent messageEvent) {
        ByteBuffer buffer = ByteBuffer.wrap(messageEvent.getData());
        if (messageEvent.getData()[0] == 'E') {
            MainActivity.activity.editText(new String(messageEvent.getData()));
            return;
        }

        String result = String.valueOf(buffer.getShort()) + " BPM";
        String accuracy = new String[]{"No contact", "Invalid", "Low", "Medium", "High"}[buffer.get() + 1];
        result += " Accuracy: " + accuracy;
        MainActivity.activity.editText(result);
    }
}
