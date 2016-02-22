package me.cubox.heartratelive;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import java.util.concurrent.SynchronousQueue;

public class IntentListener extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);

        SynchronousQueue<String> result = new SynchronousQueue<>();
        new QueryReply(result, this);
        Intent intent = new Intent(getPackageName() + ".RESULT_ACTION", Uri.parse("content://result_uri"));
        setResult(RESULT_OK, intent);

        try {
            intent.putExtra("text", result.take());
        } catch (InterruptedException e) {
            Log.wtf("HR", e.toString());
            intent.putExtra("text", "Phone on fire");
        }
        finish();
    }
}
