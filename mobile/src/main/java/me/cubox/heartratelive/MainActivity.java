package me.cubox.heartratelive;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;


public class MainActivity extends Activity {
    public static MainActivity activity;
    private GoogleApiClient googleApiClient;
    private HeartRateMessageListener heartRateMessageListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activity = this;

        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS
        }, 0);

        Switch toggle = (Switch)findViewById(R.id.switch1);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!googleApiClient.isConnected()) {
                        Log.d("HR", "GoogleApiClient is not connected");
                        return;
                    }
                    heartRateMessageListener = new HeartRateMessageListener();
                    Wearable.MessageApi.addListener(googleApiClient, heartRateMessageListener);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<Node> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await().getNodes();
                            if (nodes.isEmpty()) {
                                emptyNodes();
                                return;
                            }
                            String node = nodes.get(0).getId();
                            Wearable.MessageApi.sendMessage(googleApiClient, node, "/HR", "Start".getBytes());
                        }
                    }).start();
                    editText("On");
                } else {
                    if (!googleApiClient.isConnected()) {
                        return;
                    }
                    Wearable.MessageApi.removeListener(googleApiClient, heartRateMessageListener);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            List<Node> nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await().getNodes();
                            if (nodes.isEmpty()) {
                                emptyNodes();
                                return;
                            }
                            String node = nodes.get(0).getId();
                            Wearable.MessageApi.sendMessage(googleApiClient, node, "/HR", "Stop".getBytes());
                        }
                    }).start();
                    editText("Off");
                }
            }
        });
        googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();

        if (getPreferences(0).getBoolean("enabled", false)) {
            startService(new Intent(this, SmsListener.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void editText(String text) {
        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText(text);
    }

    private void emptyNodes() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(), "No device connected.", Toast.LENGTH_SHORT);
                toast.show();
                Switch toggle = (Switch) findViewById(R.id.switch1);
                toggle.setChecked(false);
            }
        });
    }
}
