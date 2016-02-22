package me.cubox.heartratelive;

import android.content.Context;
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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

class QueryReply implements MessageApi.MessageListener {
    private final ArrayList<Short> totalHr;
    private final ArrayList<Byte> totalAccuracy;
    private final GoogleApiClient googleApiClient;
    private final Timer timer;
    private Calendar end;
    final private SynchronousQueue<String> reply;

    QueryReply(SynchronousQueue<String> _reply, Context context) {
        totalHr = new ArrayList<>();
        totalAccuracy = new ArrayList<>();
        timer = new Timer();
        reply = _reply;

        googleApiClient = new GoogleApiClient.Builder(context).addApi(Wearable.API).build();
        ConnectionResult connectionResult = googleApiClient.blockingConnect(10, TimeUnit.SECONDS);
        if (!connectionResult.isSuccess()) {
            sendResult("Unable to connect to Google Api");
            Log.d("HR", "Unable to connect to Google Api");
            return;
        }

        NodeApi.GetConnectedNodesResult result = Wearable.NodeApi.getConnectedNodes(googleApiClient).await(10, TimeUnit.SECONDS);
        if (!result.getStatus().isSuccess() || result.getNodes().isEmpty()) {
            sendResult("Unable to connect to Google Api");
            Log.d("HR", "Watch not connected to phone");
            return;
        }


        final String node = result.getNodes().get(0).getId();
        Wearable.MessageApi.addListener(googleApiClient, this);
        Wearable.MessageApi.sendMessage(googleApiClient, node, "/HR", "Start".getBytes());
        end = Calendar.getInstance();
        end.add(Calendar.MINUTE, 1);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                onMessageReceived(new MessageEvent() {
                    @Override
                    public int getRequestId() {
                        return 0;
                    }

                    @Override
                    public String getPath() {
                        return "/HR";
                    }

                    @Override
                    public byte[] getData() {
                        return "E: Timeout".getBytes();
                    }

                    @Override
                    public String getSourceNodeId() {
                        return node;
                    }
                });
            }
        }, TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES));
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        ByteBuffer buffer = ByteBuffer.wrap(messageEvent.getData());
        if (messageEvent.getData()[0] == 'E') {
            timer.cancel();
            Log.d("HR", new String(messageEvent.getData()));
            if (new String(messageEvent.getData()).compareTo("E: Timeout") == 0) {
                sendResult("Heart rate query failed. Watch not replying. Try again.");
            } else {
                sendResult("Error: " + new String(messageEvent.getData()));
        }
            Wearable.MessageApi.removeListener(googleApiClient, this);
            return;
        } else if (Calendar.getInstance().after(end)) {
            timer.cancel();
            Log.d("HR", "Unable to find a stable heart rate");
            Wearable.MessageApi.sendMessage(googleApiClient, messageEvent.getSourceNodeId(), "/HR", "Stop".getBytes());
            Wearable.MessageApi.removeListener(googleApiClient, this);
            sendResult("Unable to find a stable heart rate. Try again.");
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
                for (short elem : totalHr.subList(i, i + elements)) {
                    if (elem < min) {
                        min = elem;
                    } else if (elem > max) {
                        max = elem;
                    }
                    total += elem;
                }
                if ((max - min) <= 15) { // Won!
                    timer.cancel();
                    sendResult("Live heart rate is: " + String.valueOf(total / elements) + " BPM.");
                    Wearable.MessageApi.sendMessage(googleApiClient, messageEvent.getSourceNodeId(), "/HR", "Stop".getBytes());
                    Wearable.MessageApi.removeListener(googleApiClient, this);
                    return;
                }
            }
        }
    }

    private void sendResult(String text) {
        try {
            reply.put(text);
        } catch (InterruptedException ignored){

        }
    }
}
