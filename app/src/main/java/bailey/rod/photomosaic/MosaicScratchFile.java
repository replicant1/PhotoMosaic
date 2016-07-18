package bailey.rod.photomosaic;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Date;

/**
 * A local copy of the mosaic under construction. When the app starts, this file is initialized to be a
 * copy of the raw, un-processed image that is to be mosaic'd. As the mosaic'ing process continues, this scratch
 * file is continually updated so that, tile by tile, it is eventually transformed into the finished mosaic.
 * It is then copied back out to the Android Media Store and deleted.</p>
 * This scratch file is stored in a location that only the parent application can see. It is only after the mosaic'ing
 * is finished and a copy is exported, that the result of the world can see the result.
 */
public class MosaicScratchFile {

    // Logging tag
    private static final String TAG = MosaicScratchFile.class.getSimpleName();

    // Simple file name of the scratch file
    private static final String SCRATCH_FILE_NAME = "mosaic.jpg";

    // Actual path to the scratch file
    private final File scratchFile;

    // Application context (wrt which the scratch file is stored)
    private final Context context;

    /**
     * Constructs a MosaicScrachFile suitable for use as a working file for creating a mosaic.
     *
     * @param context  Application context.
     */
    public MosaicScratchFile(Context context) {
        scratchFile = getWorkingFilePath(context);
        this.context = context;
    }

    public void initFromMediaStore(Uri imageUri) {
        Bitmap bitmap = loadBitmapFromMediaStore(imageUri, context);
        saveBitmapToScratchFile(bitmap);
    }

    /**
     * Finds where this scratch file should be store on the file system.
     *
     * @param context Application context
     * @return The path to this scratch file
     */
    private File getWorkingFilePath(Context context) {
        ContextWrapper cw = new ContextWrapper(context);
        File directory = cw.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(directory, SCRATCH_FILE_NAME);
    }

    /**
     * Loads an image from the Anddroid media store into an immutable Bitmap
     *
     * @param imageUri Location of image to get from Media Store eg.
     *                 "content://media/external/images/media/9819"
     * @param context  Application context
     * @return Immutable bitmap containing the given image, or null if it couldn't be loaded.
     */
    private Bitmap loadBitmapFromMediaStore(Uri imageUri, Context context) {
        Bitmap result = null;
        try {
            result = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
        } catch (IOException iox) {
            Log.e(TAG, "failed to load raw image from media store", iox);
        }
        return result;
    }

    /**
     * Retrieves the contents of this scratch file in mutable Bitmap form.
     *
     * @return Mutable bitmap copy of the 'working file' that contains that mosaic currently under construction
     */
    public Bitmap loadMutableBitmapFromScratchFile() {
        Bitmap result = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            result = BitmapFactory.decodeStream(new FileInputStream(scratchFile), null, options);
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Failed to load bitmap from scratch file", fnfe);
        }
        return result;
    }

    /**
     * Adds the current scratch file to the Android Media Store, where it can be seen by other apps.
     * TODO: Is there some way to get back the Uri of the newly added image?
     */
    public void addScratchFileToAndroidMediaStore(File imageFileToAdd, IAddedToMediaStore callback) {
        MediaScannerConnection.MediaScannerConnectionClient client =
                new MosaicMediaScannerConnectionClient(imageFileToAdd, callback);
    }

    public File copyScratchFileToPublicDirectory() {
        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        Log.d(TAG, "externalDir=" + externalDir);

        // Randomly generate a filename using the current date/time/seconds to guarantee uniqueness.
        DateFormat dateFormat = new DateFormat();
        CharSequence fileName = "mosaic_" + dateFormat.format("yyyy_MM_dd_hh_mm_ss", new Date()) + ".jpg";
        File externalFile = new File(externalDir, (String) fileName);

        Log.d(TAG, "scratchFile=" + scratchFile.getAbsolutePath());
        Log.d(TAG, "externalFile=" + externalFile.getAbsolutePath());

        try {
            FileUtils.copyFile(scratchFile, externalFile);
            Log.i(TAG, String.format("Copied scratch file to %s OK", scratchFile.getAbsolutePath()));
        } catch (IOException iox) {
            Log.e(TAG, "Failed to copy scratch file to public", iox);
        }

        return externalFile;
    }

    /**
     * Makes the content of this scratch file equal to the given bitmap.
     *
     * @param bitmap Bitmap that will become the new contents of this scratch file.
     */
    public void saveBitmapToScratchFile(Bitmap bitmap) {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(scratchFile);
            // "100" = "100 percent" quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Failed to write bitmap to scratch file", fnfe);
        } finally {
            try {
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close output stream when saving bitmap to scratch file", e);
            }
        }
    }

    private class MosaicMediaScannerConnectionClient implements MediaScannerConnection.MediaScannerConnectionClient {
        private final MediaScannerConnection msc;
        private final File imageFileToAdd;
        private final IAddedToMediaStore addedCallback;

        public MosaicMediaScannerConnectionClient(File imageFileToAdd, IAddedToMediaStore addedCallback) {
            this.imageFileToAdd = imageFileToAdd;
            this.addedCallback = addedCallback;
            msc = new MediaScannerConnection(context, this);
            msc.connect();
        }

        @Override
        public void onMediaScannerConnected() {
            String mimeType = null;
            String filePath = imageFileToAdd.getAbsolutePath();
            Log.d(TAG, "Scanning file at " + filePath);
            msc.scanFile(filePath, mimeType);
        }

        public void onScanCompleted(String s, Uri uri) {
            msc.disconnect();
            Log.d(TAG, "Notified of scan completion");
            Log.d(TAG, "URI in Media Store: " + uri);
            Log.d(TAG, "Path of file referenced by Media Store: " + s);

            addedCallback.added(s, uri);



        }
    }
}
