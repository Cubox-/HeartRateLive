package me.cubox.heartratelive;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SmsListener extends BroadcastReceiver {
    private static class QueryReply implements MessageApi.MessageListener {
        final String sender;
        final ArrayList<Short> totalHr;
        final ArrayList<Byte> totalAccuracy;
        final GoogleApiClient googleApiClient;
        Calendar end;
        private QueryReply(String _sender, Context context) {
            sender = _sender;
            totalHr = new ArrayList<>();
            totalAccuracy = new ArrayList<>();
            googleApiClient = new GoogleApiClient.Builder(context).addApi(Wearable.API).build();
            ConnectionResult connectionResult = googleApiClient.blockingConnect(10, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                sendText(sender, "Unable to connect to Google Api");
                Log.d("HR", "Unable to connect to Google Api");
                return;
            }

            NodeApi.GetConnectedNodesResult result = Wearable.NodeApi.getConnectedNodes(googleApiClient).await(10, TimeUnit.SECONDS);
            if (!result.getStatus().isSuccess() || result.getNodes().isEmpty()) {
                sendText(sender, "Watch not connected to phone");
                Log.d("HR", "Watch not connected to phone");
                return;
            }


            String node = result.getNodes().get(0).getId();
            Wearable.MessageApi.addListener(googleApiClient, this);
            Wearable.MessageApi.sendMessage(googleApiClient, node, "/HR", "Start".getBytes());
            end = Calendar.getInstance();
            end.add(Calendar.MINUTE, 1);
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            ByteBuffer buffer = ByteBuffer.wrap(messageEvent.getData());
            if (messageEvent.getData()[0] == 'E') {
                Log.d("HR", new String(messageEvent.getData()));
                Wearable.MessageApi.removeListener(googleApiClient, this);
                return;
            } else if (Calendar.getInstance().after(end)) {
                Log.d("HR", "Unable to find a stable heart rate");
                Wearable.MessageApi.sendMessage(googleApiClient, messageEvent.getSourceNodeId(), "/HR", "Stop".getBytes());
                Wearable.MessageApi.removeListener(googleApiClient, this);
                sendText(sender, "Unable to find a stable heart rate. Try later.");
                return;
            }

            totalHr.add(buffer.getShort());
            totalAccuracy.add(buffer.get());

            if (totalHr.size() < 5) {
                return;
            }

            int accIs3 = 0;
            int accIs2Plus = 0;

            for (int i = totalHr.size() - 1; i > 5; i--) {
                if (totalAccuracy.get(i) == 3) {
                    accIs3++;
                    accIs2Plus++;
                } else if (totalAccuracy.get(i) == 2) {
                    accIs3 = 0;
                    accIs2Plus++;
                } else {
                    accIs3 = 0;
                    accIs2Plus = 0;
                }

                if (accIs3 == 3 || accIs2Plus == 5) {
                    int elements = accIs3 == 3 ? 3 : 5;
                    short min = Short.MAX_VALUE;
                    short max = Short.MIN_VALUE;
                    int total = 0;
                    for (short elem : totalHr.subList(i, i+elements)) {
                        if (elem < min) {
                            min = elem;
                        } else if (elem > max) {
                            max = elem;
                        }
                        total += elem;
                    }
                    if ((max - min) <= 15) { // Won!
                        sendText(sender, "Live heart rate is: " + String.valueOf(total / elements) + " BPM.");
                        Wearable.MessageApi.sendMessage(googleApiClient, messageEvent.getSourceNodeId(), "/HR", "Stop".getBytes());
                        Wearable.MessageApi.removeListener(googleApiClient, this);
                    }
                }
            }
        }

        private void sendText(String recipient, String body) {
            SmsManager.getDefault().sendTextMessage(recipient, null, body, null, null);
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                SharedPreferences sharedPreferences = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);

                String messageBody = smsMessage.getMessageBody();
                String trigger = sharedPreferences.getString("keyword", "aaa");

                if (!messageBody.equals(trigger)) {
                    continue;
                }

                final String sender = smsMessage.getOriginatingAddress();
                Set<String> contacts = sharedPreferences.getStringSet("contacts", null);
                if (contacts == null) {
                    Log.d("HR", "Contacts are empty");
                    return;
                }

                boolean allowed = false;

                for (String number : contacts) {
                    if (PhoneNumberUtils.compare(number, sender)) {
                        allowed = true;
                    }
                }
                if (!allowed) {
                    Log.d("HR", "Not allowed!");
                    continue;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        new QueryReply(sender, context);
                    }
                }).start();
            }
        }
    }
}
