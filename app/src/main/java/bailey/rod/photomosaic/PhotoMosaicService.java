package bailey.rod.photomosaic;

import android.Manifest;
import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import hugo.weaving.DebugLog;
import timber.log.Timber;

import static timber.log.Timber.e;
import static timber.log.Timber.i;

/**
 * A service that applies a "Mosaic" effect to a given image file. The image file is modified "in place" in an
 * asynchronous manner. The service broadcasts updates as it proceeds
 * <p>
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
        Uri imageUri = Uri.parse(intent.getDataString());

        /*
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
        */

        try {
            // TODO: Maybe do something more efficient based on loading some attributes of image?
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            int TILE_WIDTH_PX = 32;
            int TILE_HEIGHT_PX = 32;

            int pixel;
            int redComponent;
            int blueComponent;
            int greenComponent;
            int alphaComponent;
            int pixelCount;

            for (int x = 0; x < bitmap.getWidth(); x += 32) {
                for (int y = 0; y < bitmap.getHeight(); y += 32) {

                    Timber.d("Processing tile [%d, %d]", x, y);

                    redComponent = 0;
                    blueComponent = 0;
                    greenComponent = 0;
                    alphaComponent = 0;

                    pixelCount = 0;

                    for (int x1 = x; (x1 < (x + 31)) && (x1 < bitmap.getWidth()); x1++) {
                        for (int y1 = y; (y1 < (y + 31)) && (y1 < bitmap.getHeight()); y1++) {
                            // Find the average color of the tile whose top left corner
                            // is at  [x,y]
                            pixel = bitmap.getPixel(x, y);
                            pixelCount++;

                            redComponent += Color.red(pixel);
                            blueComponent += Color.blue(pixel);
                            greenComponent += Color.green(pixel);
                            alphaComponent += Color.alpha(pixel);
                        }
                    }


                    int averageRed = redComponent / pixelCount;
                    int averageBlue = blueComponent / pixelCount;
                    int averageGreen = greenComponent / pixelCount;
                    int averageAlpha = alphaComponent / pixelCount;

                    // Broadcast a new result - another tile has been averaged.
                    Timber.d("Tile [%d, %d] has average color of (R:%d G:%d B:%d A:%d)", x, y, averageRed, averageGreen,
                             averageBlue, averageAlpha);
                } // for y
            } // for x


        } catch (IOException iox) {
            Timber.i(iox, "Failed to get bitmap for processing");
        }
    }

}
