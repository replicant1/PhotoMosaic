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

    private TextView helpTextView;

    /**
     * Adjusts the visibility of UI components and the label on the all-purpose button
     * to suit the current operating mode.
     */
    private void adjustUIPerMode() {
        switch (mode) {
            // Only the help text is visible
            case NO_IMAGE_TO_MOSAIC:
                imageView.setVisibility(View.GONE);
                helpTextView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                progressMsg.setVisibility(View.GONE);
                allPurposeButton.setVisibility(View.GONE);
                break;

            // Only the image and the "Start" button are visible
            case READY_TO_START_MOSAIC_PROCESSING:
                helpTextView.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                progressMsg.setVisibility(View.GONE);
                allPurposeButton.setVisibility(View.VISIBLE);
                allPurposeButton.setText(R.string.button_label_start);
                break;

            // Visible: The image, progress bar, progress bar message and
            // "Cancel" button.
            case MOSAIC_PROCESSING_IN_PROGRESS:
                helpTextView.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);
                progressMsg.setVisibility(View.VISIBLE);
                allPurposeButton.setVisibility(View.VISIBLE);
                allPurposeButton.setText(R.string.button_label_cancel);
                break;

            // Only the image view and "Send" button are visible.
            case MOSIAC_PROCESSING_COMPLETED:
                helpTextView.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                progressMsg.setVisibility(View.INVISIBLE);
                allPurposeButton.setVisibility(View.VISIBLE);
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
        helpTextView = (TextView) findViewById(R.id.mosaic_help_text_view);

        imageUri = (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);

        if (imageUri == null) {
            switchToNoImageToMosaicMode();
        } else {
            allPurposeButton.setOnClickListener(new AllPurposeButtonOnClickListener(imageUri));
            switchToReadyToStartMode();
        }
    }

    /**
     * Resets the UI ready for the user to start the mosaic process. The image shown is the one whose URI was
     * passed into the Intent that started this activity (if any).
     */
    private void switchToReadyToStartMode() {
        mode = OperatingMode.READY_TO_START_MOSAIC_PROCESSING;
        adjustUIPerMode();

        if (imageUri != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            } catch (IOException iox) {
                Log.e(TAG, "Failed to get raw image to be mosaiced", iox);
            }
        }
    }

    private void switchToNoImageToMosaicMode() {
        mode = OperatingMode.NO_IMAGE_TO_MOSAIC;
        adjustUIPerMode();
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
        NO_IMAGE_TO_MOSAIC, // When app is started from Android app launcher without Intent specifying an image
        READY_TO_START_MOSAIC_PROCESSING, // Raw image displaying with "start" button available
        MOSAIC_PROCESSING_IN_PROGRESS, // Creation of the mosaic image in the scratch file is underway
        MOSIAC_PROCESSING_COMPLETED; // Finished mosaic is displaying, now ready to send to Media Store
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

            mode = OperatingMode.MOSIAC_PROCESSING_COMPLETED;
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
                // Button is labelled "Start" and user has just clicked it. Kick off mosaic processing.
                case READY_TO_START_MOSAIC_PROCESSING:
                    mode = OperatingMode.MOSAIC_PROCESSING_IN_PROGRESS;
                    startMosaicService();
                    break;

                // Button is labelled "Cancel" and user has just clicked it. Cancel the mosaic service
                // and rever to "Ready to Start" mode.
                case MOSAIC_PROCESSING_IN_PROGRESS:
                    MosaicService.abortRequested = true;

                    // Resets the scratch file to the original contents from the Media Store
                    switchToReadyToStartMode();
                    break;

                // Button is labelled "Send To", and mosaic processing has finished. User clicks button to elect
                // to "Send to" some other. Scratch file contains completed mosaic.
                case MOSIAC_PROCESSING_COMPLETED:
                    MosaicScratchFile scratchFile = new MosaicScratchFile(MosaicActivity.this);
                    File publicCopyOfScratchFile = scratchFile.copyScratchFileToPublicDirectory();
                    scratchFile.addScratchFileToAndroidMediaStore(publicCopyOfScratchFile, new IAddedToMediaStore() {

                        @Override
                        public void added(final String pathToUnderlyingImageFile, final Uri mediaStoreUri) {
                            // TODO: Perhaps raise a Toast to reinforce what just happenned
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
            Log.i(TAG, "Into startMosaicService with imageUri=" + imageUri);
            Intent serviceIntent = new Intent(MosaicActivity.this, MosaicService.class);
            serviceIntent.setData(imageUri);
            MosaicActivity.this.startService(serviceIntent);
        }
    }
}
