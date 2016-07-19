package bailey.rod.photomosaic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;


/**
 * Presents the image to be mosaic'd with a allPurposeButton that takes you through to the next step in the mosaic'ing
 * process, or cancels the current step. When mosaic'ing is in progress, a progress bar appears as well.
 *
 * @see MosaicService
 */
public class MosaicActivity extends AppCompatActivity {

    private static final String TAG = MosaicActivity.class.getSimpleName();

    private final IntentFilter mosaicProgressedIntentFilter =
            new IntentFilter(MosaicService.MOSAIC_CREATION_PROGRESSED);

    private final BroadcastReceiver mosaicProgressReceiver = new MosaicProgressBroadcastReceiver();

    private final IntentFilter mosaicFinishedIntentFilter =
            new IntentFilter(MosaicService.MOSAIC_CREATION_FINISHED);

    private final BroadcastReceiver mosaicFinishedReceiver = new MosaicFinishedBroadcastReceiver();

    private Button allPurposeButton;

    private Uri imageUri;

    private ImageView imageView;

    private OperatingMode mode;

    private ProgressBar progressBar;

    private TextView progressMsg;

    private void adjustUIPerMode() {
        switch (mode) {
            case READY_TO_START:
                progressBar.setVisibility(View.INVISIBLE);
                progressMsg.setVisibility(View.INVISIBLE);
                allPurposeButton.setText(R.string.button_label_start);
                break;

            case PROCESSING:
                progressBar.setVisibility(View.VISIBLE);
                progressMsg.setVisibility(View.VISIBLE);
                allPurposeButton.setText(R.string.button_label_cancel);
                break;

            case READY_TO_SEND_TO_MEDIA_STORE:
                progressBar.setVisibility(View.INVISIBLE);
                progressMsg.setVisibility(View.INVISIBLE);
                allPurposeButton.setText(R.string.button_label_send_to);
                break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_mosaic);

        imageView = (ImageView) findViewById(R.id.mosaic_image_view);
        progressBar = (ProgressBar) findViewById(R.id.mosaic_progress_bar);
        progressMsg = (TextView) findViewById(R.id.mosaic_progress_msg);
        allPurposeButton = (Button) findViewById(R.id.mosaic_all_purpose_button);

        mode = OperatingMode.READY_TO_START;
        adjustUIPerMode();

        imageUri = (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {

            Button button = (Button) findViewById(R.id.mosaic_all_purpose_button);
            button.setOnClickListener(new AllPurposeButtonOnClickListener(imageUri));

            try {
                // TODO Load a scaled-down version of the image instead of the full size image, to save memory
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            } catch (IOException iox) {
                Log.e(TAG, "Failed to get raw image to be mosaiced", iox);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mosaicProgressReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mosaicFinishedReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(mosaicProgressReceiver, mosaicProgressedIntentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(mosaicFinishedReceiver, mosaicFinishedIntentFilter);
    }

    /**
     * The mode in which this Activity is currently running. Like a state machine. Varies according to where we are
     * up to in the process of creating the mosaic image.
     */
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
    private class MosaicProgressBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Update progress bar from data in the intent
            int percentComplete = intent.getIntExtra(MosaicService.EXTRA_PROGRESS, 0);

            progressBar.setProgress(percentComplete);
            String progressBarMsgFormat = getResources().getString(R.string.progress_bar_percent_msg);
            progressMsg.setText(String.format(progressBarMsgFormat, percentComplete));


        }
    }

    /**
     * Listens for a "finished" intent from the MosaicService, which indicates the scratch file now
     * contains the completed mosaic.
     */
    private class MosaicFinishedBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            MosaicScratchFile mosaicScratchFile = new MosaicScratchFile(MosaicActivity.this);
            imageView.setImageBitmap(mosaicScratchFile.loadMutableBitmapFromScratchFile());

            mode = OperatingMode.READY_TO_SEND_TO_MEDIA_STORE;
            adjustUIPerMode();
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
                    MosaicScratchFile scratchFile = new MosaicScratchFile(MosaicActivity.this);
                    File publicCopyOfScratchFile = scratchFile.copyScratchFileToPublicDirectory();
                    scratchFile.addScratchFileToAndroidMediaStore(publicCopyOfScratchFile, new IAddedToMediaStore() {

                        @Override
                        public void added(final String pathToUnderlyingImageFile, final Uri mediaStoreUri) {
                            Handler h = new Handler(MosaicActivity.this.getMainLooper());
                            Runnable runnable = new Runnable() {

                                @Override
                                public void run() {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setDataAndType(mediaStoreUri, "image/*");
                                    startActivity(intent);
                                }
                            };

                            h.post(runnable);
                        }
                    });

                    break;
            }

            adjustUIPerMode();
        }

        private void startMosaicService() {
            Intent serviceIntent = new Intent(MosaicActivity.this, MosaicService.class);
            serviceIntent.setData(imageUri);
            MosaicActivity.this.startService(serviceIntent);
        }
    }
}
