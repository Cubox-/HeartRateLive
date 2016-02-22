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

import java.util.Set;
import java.util.concurrent.SynchronousQueue;

public class SmsListener extends BroadcastReceiver {
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
                        SynchronousQueue<String> result = new SynchronousQueue<>();
                        new QueryReply(result, context);
                        try {
                            SmsManager.getDefault().sendTextMessage(sender, null, result.take(), null, null);
                        } catch (InterruptedException e) {
                            Log.wtf("HR", e.toString());
                            SmsManager.getDefault().sendTextMessage(sender, null, "Thread died, phone must be on fire", null, null);
                        }
                    }
                }).start();
            }
        }
    }
}
