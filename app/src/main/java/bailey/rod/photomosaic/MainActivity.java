package bailey.rod.photomosaic;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new FABOnClickListener());

    }

    private class FABOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            // Start up the MosaicService
            Intent serviceIntent = new Intent(MainActivity.this, MosaicService.class);
            serviceIntent.setData(Uri.EMPTY);
            MainActivity.this.startService(serviceIntent);
        }
    }
}
