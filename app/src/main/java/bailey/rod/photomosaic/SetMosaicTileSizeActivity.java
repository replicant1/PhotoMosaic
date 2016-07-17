package bailey.rod.photomosaic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;

import timber.log.Timber;

/**
 * Presents the image to be mosaic'd with a slider for adjusting the tile size of the mosaic.
 */
public class SetMosaicTileSizeActivity extends AppCompatActivity {

    private final IntentFilter intentFilter = new IntentFilter(PhotoMosaicService.BROADCAST_ACTION);

    private final MosaicBroadcastReceiver mosaicBroadcastReceiver = new MosaicBroadcastReceiver();

    private TextView mosaicProgressMsg;

    private ProgressBar progressBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        Bundle extras = intent.getExtras();
        Timber.i("extras.contents=" + extras.describeContents());


        for (String key : extras.keySet()) {
            Timber.i("extras key=" + key);
            Object value = extras.get(key);
            if (value != null)
                Timber.i("extras value.class = " + value.getClass());

        }

        Timber.i("intent=%s, action=%s, type=%s", intent, action, type);

        setContentView(R.layout.activity_set_mosaic_tile_size);

        ImageView tileSizeImageView = (ImageView) findViewById(R.id.tile_size_image_view);
        progressBar = (ProgressBar) findViewById(R.id.mosaic_progress_bar);
        mosaicProgressMsg = (TextView) findViewById(R.id.mosaic_progress_msg);

        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {

            Button button = (Button) findViewById(R.id.tile_size_button);
            button.setOnClickListener(new SendToServiceOnClickListener(imageUri));

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                Timber.i("bitmap=" + bitmap);
                if (bitmap != null) {
                    Timber.i("bitmap.height=%d, bitmpa.width=%d", bitmap.getHeight(), bitmap.getWidth());
                    tileSizeImageView.setImageBitmap(bitmap);
                }
            } catch (IOException iox) {
                Timber.e(iox, "Failed to get raw image to be mosaiced");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Timber.i("unregisetering receiver for PhotoMosaicService broadcasts");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mosaicBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.i("Registering receiver for PhotoMossaicService broadcasts");
        LocalBroadcastManager.getInstance(this).registerReceiver(mosaicBroadcastReceiver, intentFilter);
    }

    private class MosaicBroadcastReceiver extends BroadcastReceiver {

        private MosaicBroadcastReceiver() {

        }


        @Override
        public void onReceive(Context context, Intent intent) {
            // Update progress bar from data in the intent
            int percentComplete = intent.getIntExtra(PhotoMosaicService.EXTRA_PROGRESS, 0);

            Timber.d("Into onReceive with percentComplete=%d", percentComplete);

            progressBar.setProgress(percentComplete);
            mosaicProgressMsg.setText(String.format("Percent complete: %d", percentComplete));

        }
    }

    /**
     * Listens for a click on the "Send to PhotoMosaicService' button and sends an Intent
     * off to start that service, which begins the process of creating the mosaic for the
     * image in the media store that is currently displayed.
     */
    private class SendToServiceOnClickListener implements View.OnClickListener {

        private final Uri imageUri;

        public SendToServiceOnClickListener(Uri imageUri) {
            this.imageUri = imageUri;
        }

        @Override
        public void onClick(View view) {
            Timber.i("About to start the PhotoMosaicService");

            Intent serviceIntent = new Intent(SetMosaicTileSizeActivity.this, PhotoMosaicService.class);
            serviceIntent.setData(imageUri);
            SetMosaicTileSizeActivity.this.startService(serviceIntent);
        }
    }
}
