package bailey.rod.photomosaic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import bailey.rod.photomosaic.log.ReleaseTree;
import timber.log.Timber;

/**
 * Presents the image to be mosaic'd with a allPurposeButton that takes you through to the next step in the mosaic'ing
 * process, or cancels the current step. When mosaic'ing is in progress, a progress bar appears as well.
 *
 * @see MosaicService
 */
public class MosaicActivity extends AppCompatActivity {

    private static final String WORKING_FILE_NAME = "mosaic.jpg";

    private final IntentFilter intentFilter = new IntentFilter(MosaicService.BROADCAST_ACTION);

    private final MosaicBroadcastReceiver mosaicBroadcastReceiver = new MosaicBroadcastReceiver();

    private Button allPurposeButton;

    private ImageView imageView;

    private OperatingMode mode;

    private ProgressBar progressBar;

    private TextView progressMsg;

    private void adjustUIPerMode() {
        switch (mode) {
            case READY_TO_START:
                progressBar.setVisibility(View.INVISIBLE);
                progressMsg.setVisibility(View.INVISIBLE);
                allPurposeButton.setText("Start Mosaic");
                break;

            case PROCESSING:
                progressBar.setVisibility(View.VISIBLE);
                progressMsg.setVisibility(View.VISIBLE);
                allPurposeButton.setText("Cancel");
                break;

            case READY_TO_SEND_TO_MEDIA_STORE:
                progressBar.setVisibility(View.INVISIBLE);
                progressMsg.setVisibility(View.INVISIBLE);
                allPurposeButton.setText("Send To");
                break;
        }
    }

    private File getWorkingFilePath() {
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getExternalFilesDir(Environment.DIRECTORY_PICTURES); //cw.getFilesDir() for internal;
        File path = new File(directory, WORKING_FILE_NAME);
        return path;
    }

    /**
     * Initiaize the "Timber" logging utility so that debug log statements include a line number
     */
    private void initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree() {
                @Override
                protected String createStackElementTag(StackTraceElement element) {
                    return super.createStackElementTag(element) + ":" + element.getLineNumber();
                }
            });
        } else {
            Timber.plant(new ReleaseTree());
        }
    }

    /**
     * @return Mutable bitmap copy of the 'working file' that contains that mosaic currently under construction
     */
    private Bitmap loadBitmapFromStorage() {
        Bitmap result = null;
        File path = getWorkingFilePath();

        Timber.d("------------------------------------------");
        Timber.d("Loading bitmap from " + path.getAbsolutePath());

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            result = BitmapFactory.decodeStream(new FileInputStream(path), null, options);
        } catch (FileNotFoundException fnfe) {
            Timber.e(fnfe, "Failed to load working file");
        }

        return result;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initLogging();

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

        setContentView(R.layout.activity_mosaic);

        imageView = (ImageView) findViewById(R.id.mosaic_image_view);
        progressBar = (ProgressBar) findViewById(R.id.mosaic_progress_bar);
        progressMsg = (TextView) findViewById(R.id.mosaic_progress_msg);
        allPurposeButton = (Button) findViewById(R.id.mosaic_all_purpose_button);

        mode = OperatingMode.READY_TO_START;
        adjustUIPerMode();

        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {

            Button button = (Button) findViewById(R.id.mosaic_all_purpose_button);
            button.setOnClickListener(new AllPurposeButtonOnClickListener(imageUri));

            try {
                // TODO Load a scaled-down version of the image instead of the full size image
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                //Bitmap bitmap  = loadBitmapFromStorage();
                Timber.i("bitmap=" + bitmap);
                if (bitmap != null) {
                    Timber.i("bitmap.height=%d, bitmap.width=%d", bitmap.getHeight(), bitmap.getWidth());
                    imageView.setImageBitmap(bitmap);
                }
            } catch (IOException iox) {
                Timber.e(iox, "Failed to get raw image to be mosaiced");
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mosaicBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mosaicBroadcastReceiver, intentFilter);
    }

    public enum OperatingMode {
        READY_TO_START, // Raw image displaying with "start" button available
        PROCESSING, // Bailed out of processing, probably taking too long
        READY_TO_SEND_TO_MEDIA_STORE; // Finished mosaic is displaying, now ready to send to Media Store
    }

    /**
     * Listens for messages broadcast by the accompanying MosaicService, which is doing all the heavy lifting of
     * producing the mosaic image. Messages may be progress updates or error notifications.
     *
     * @see MosaicService
     */
    private class MosaicBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Update progress bar from data in the intent
            int percentComplete = intent.getIntExtra(MosaicService.EXTRA_PROGRESS, 0);

            progressBar.setProgress(percentComplete);
            progressMsg.setText(String.format("Percent complete: %d", percentComplete));

            if (percentComplete == 100) {
                Timber.i("***** Percent is 100 ******");
                // TODO: What if I receive 100% notification multiple times?
                // TODO: Probably need to alter MosaicService to send a distinct type of message upon finfish.
                imageView.setImageBitmap(loadBitmapFromStorage());
                imageView.invalidate(); // May not be necessary

                mode = OperatingMode.READY_TO_SEND_TO_MEDIA_STORE;
                adjustUIPerMode();
            }
        }
    }

    /**
     * Listens for a click on the "Start Mosaic" allPurposeButton and sends an Intent
     * off to start that service, which begins the process of creating the mosaic for the
     * image in the media store that is currently displayed.
     */
    private class AllPurposeButtonOnClickListener implements View.OnClickListener {

        private final Uri imageUri;

        /**
         * @param imageUri URI of the image to be mosaic'd. This will be a link to somewhere
         *                 in the Android Media Store.
         */
        public AllPurposeButtonOnClickListener(Uri imageUri) {
            this.imageUri = imageUri;
        }

        @Override
        public void onClick(View view) {
            switch (mode) {
                case READY_TO_START:
                    mode = OperatingMode.PROCESSING;
                    startMosaicService();
                    break;

                case PROCESSING:
                    mode = OperatingMode.READY_TO_START;
                    break;

                case READY_TO_SEND_TO_MEDIA_STORE:
                    // TODO Raise bottom sheet with "send to" options
                    break;
            }

            adjustUIPerMode();
        }

        private void startMosaicService() {
            Timber.i("About to start the MosaicService");

            Intent serviceIntent = new Intent(MosaicActivity.this, MosaicService.class);
            serviceIntent.setData(imageUri);
            MosaicActivity.this.startService(serviceIntent);
        }
    }
}
