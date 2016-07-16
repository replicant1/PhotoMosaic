package bailey.rod.photomosaic;

import android.Manifest;
import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import hugo.weaving.DebugLog;
import timber.log.Timber;

import static timber.log.Timber.e;
import static timber.log.Timber.i;

/**
 * A service that applies a "Mosaic" effect to a given image file. The image file is modified "in place" in an
 * asynchronous manner. The service broadcasts updates as it proceeds
 * <p/>
 * To trigger the service, broadcast an Intent with the following data:
 * <ul>
 * <li> EXTERNAL_PATH_URI - Path to the raw image file to be mosaic'd. It should be somewhere where this service
 * will have permission to access it, such as on external storage.
 * <li> TILE_SIZE_PX - Integer number of pixels, being the width and the height of the mosaic tiles
 * </ul>
 * Each time such an Intent is received, the following occurs:
 * <li> The image is notionally divided into tiles of size TILE_SIZE_PX x TILE_SIZE_PX.
 * <li> For each tile:
 * <li> The average color of the image's pixels within the given tile is calculated
 * <li> An external service is contacted that takes the calculated average and returns a tile image 'I'
 * <li> The tile's area in the phone is filled with image 'I'
 */
public class PhotoMosaicService extends IntentService {

    @DebugLog
    public PhotoMosaicService() {
        super("Photo Mosaic");
        i("PhotoMosaicService has been constructed");
    }

    @DebugLog
    @Override
    protected void onHandleIntent(Intent intent) {
        i("dataString = " + intent.getDataString());
        i("data directory = " + Environment.getDataDirectory()); // "/data"
        i("external storage directory = " + Environment.getExternalStorageDirectory()); // "/storage/emulated/0"
        i("DIRECTORY_PICTURES = " + Environment.DIRECTORY_PICTURES); // "Pictures"
        i("absolute public directory for DIRECTORY_PICTURES = " + Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getAbsolutePath()); // "/storage/emulated/0/Pictures/grumpy_cat.jpg"

        String[] columnsToReturn = {MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.TITLE};
        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columnsToReturn, null, null, null);
        i("cursor=" + cursor);

        int columnIndexTitle = cursor.getColumnIndex(MediaStore.Images.ImageColumns.TITLE);
        int columnIndexId = cursor.getColumnIndex((MediaStore.Images.ImageColumns._ID));

        i("Number of images found=" + cursor.getCount());

        if (cursor.moveToFirst()) {
            int imageNumber = 0;
            do {

                String imageTitle = cursor.getString(columnIndexTitle);
                long imageId = cursor.getLong(columnIndexId);

                i("Image[%d] title: %s ID: %d", imageNumber, imageTitle, imageId);

                imageNumber++;
            } while (cursor.moveToNext());
        }

        cursor.close();
    }
}
