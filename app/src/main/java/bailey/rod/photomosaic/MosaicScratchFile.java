package bailey.rod.photomosaic;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import static bailey.rod.photomosaic.Constants.*;

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

    // Actual path to the scratch file
    private final File scratchFile;

    // Application context (wrt which the scratch file is stored)
    private final Context context;

    /**
     * Constructs a MosaicScrachFile suitable for use as a working file for creating a mosaic.
     *
     * @param context Application context.
     */
    public MosaicScratchFile(Context context) {
        scratchFile = getWorkingFilePath(context);
        this.context = context;
    }

    /**
     * Adds the current scratch file to the Android Media Store, where it can be seen by other apps.
     * TODO: Is there some way to get back the Uri of the newly added image?
     */
    public void addScratchFileToAndroidMediaStore(File imageFileToAdd, IAddedToMediaStore callback) {
        MediaScannerConnection.MediaScannerConnectionClient client =
                new MosaicMediaScannerConnectionClient(imageFileToAdd, callback);
    }

    /**
     * Copies the scratch file frorm the app's private storage directory to a publicly visible "Pictures"
     * directory. The public copy is the one that should be referenced by the Android Media Store (as the internasl
     * scratch file will be over-written in the next mosaic operation).
     *
     * @return The location of the public copy of the scratch file.
     */
    public File copyScratchFileToPublicDirectory() {
        File externalDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        Log.d(TAG, "externalDir=" + externalDir);

        // Randomly generate a filename using the current date/time/seconds to guarantee uniqueness.
        DateFormat dateFormat = new DateFormat();
        CharSequence fileName = OUTPUT_IMAGE_FILE_PREFIX + dateFormat.format(OUTPUT_IMAGE_FILE_INFIX, new Date()) +
                OUTPUT_IMAGE_FILE_SUFFIX;
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
     * Saves image from the Android Media Store to the internal scratch file.
     *
     * @param imageUri Location of image in Android Media Store.
     */
    public void initFromMediaStore(Uri imageUri) {
        Bitmap bitmap = loadBitmapFromMediaStore(imageUri, context);
        saveBitmapToScratchFile(bitmap);
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
        FileInputStream fileInputStream = null;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            fileInputStream = new FileInputStream(scratchFile);
            result = BitmapFactory.decodeStream(fileInputStream, null, options);
        } catch (IOException iox) {
            Log.e(TAG, "Failed to load bitmap from scratch file", iox);
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to close filoe input stream when reading scratch file");
            }
        }
        return result;
    }

    /**
     * Makes the content of this scratch file equal to the given bitmap.
     *
     * @param bitmap Bitmap that will become the new contents of this scratch file.
     */
    public void saveBitmapToScratchFile(Bitmap bitmap) {
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(scratchFile);
            bitmap.compress(OUTPUT_IMAGE_COMPRESS_FORMAT, OUTPUT_IMAGE_QUALITY_PERCENT, fileOutputStream);
        } catch (IOException iox) {
            Log.e(TAG, "Failed to write bitmap to scratch file", iox);
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to close output stream when saving bitmap to scratch file", e);
            }
        }
    }

    /**
     * Connects to the Android Media Scanner when #addScratchFileToAndroidMediaStore is called and listens
     * for asynchronous completion of the addition. This way the user can be notified when we are *sure* the
     * image has been added.
     */
    private class MosaicMediaScannerConnectionClient implements MediaScannerConnection.MediaScannerConnectionClient {
        private final MediaScannerConnection mediaScannerConnection;

        private final File imageFileToAdd;

        private final IAddedToMediaStore addedCallback;

        /**
         * @param imageFileToAdd Public copy of the scratch file
         * @param addedCallback  Notified asynch when the addition is finished
         */
        public MosaicMediaScannerConnectionClient(File imageFileToAdd, IAddedToMediaStore addedCallback) {
            this.imageFileToAdd = imageFileToAdd;
            this.addedCallback = addedCallback;
            mediaScannerConnection = new MediaScannerConnection(context, this);
            mediaScannerConnection.connect();
        }

        @Override
        public void onMediaScannerConnected() {
            String mimeType = null;
            String filePath = imageFileToAdd.getAbsolutePath();
            Log.d(TAG, "Scanning file at " + filePath);
            mediaScannerConnection.scanFile(filePath, mimeType);
        }

        public void onScanCompleted(String s, Uri uri) {
            mediaScannerConnection.disconnect();
            Log.d(TAG, "Notified of scan completion");
            Log.d(TAG, "URI in Media Store: " + uri);
            Log.d(TAG, "Path of file referenced by Media Store: " + s);

            addedCallback.added(s, uri);
        }
    }
}
