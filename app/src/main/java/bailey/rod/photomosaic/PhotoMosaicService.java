package bailey.rod.photomosaic;

import android.app.IntentService;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

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

    // Defines a custom Intent action
    public static final String BROADCAST_ACTION =
            "bailey.rod.photomosaic.BROADCAST";

    // Defines the key for the status "extra" in an Intent
    public static final String EXTRA_PROGRESS =
            "bailey.rod.photomosaic.EXTRA_PROGRESS";

    @DebugLog
    public PhotoMosaicService() {
        super("Photo Mosaic");
        i("PhotoMosaicService has been constructed");
    }

    private File getWorkingFilePath() {
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        File directory = cw.getExternalFilesDir(Environment.DIRECTORY_PICTURES); //cw.getFilesDir();
        File path = new File(directory, "mosaic.jpg");
        return path;
    }

    /**
     * @return Mutable bitmap copy of the 'working file' that contains that mosaic currently under construction
     */
    private Bitmap loadBitmapFromInternalStorage() {
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
            Timber.d("As created from Media.getBitmap, the bitmap.ismutable=" + bitmap.isMutable());

            saveBitmapToStorage(bitmap);

            bitmap = loadBitmapFromInternalStorage();

            Timber.d("As loaded from internal storeage, bitmap.isMutable=" + bitmap.isMutable());

            int TILE_WIDTH_PX = 32;
            int TILE_HEIGHT_PX = 32;

            int pixel;
            int redComponent;
            int blueComponent;
            int greenComponent;
            int numPixelsInTile;

            int tileCountX = (int) Math.ceil((double) bitmap.getWidth() / 32D);
            int tileCountY = (int) Math.ceil((double) bitmap.getHeight() / 32D);

            int numTilesProcessed = 0;
            int totalTilesToProcess = tileCountX * tileCountY;

            Timber.d("tileCountX=%d, tileCountY=%d, total tiles=%d", tileCountX, tileCountY, totalTilesToProcess);

            for (int tileLeftX = 0; tileLeftX < bitmap.getWidth(); tileLeftX += 32) {
                for (int tileTopY = 0; tileTopY < bitmap.getHeight(); tileTopY += 32) {

                    redComponent = 0;
                    blueComponent = 0;
                    greenComponent = 0;

                    numPixelsInTile = 0;

                    for (int pixelX = tileLeftX; (pixelX < (tileLeftX + 32)) && (pixelX < bitmap.getWidth());
                         pixelX++) {
                        for (int pixelY = tileTopY; (pixelY < (tileTopY + 32)) && (pixelY < bitmap.getHeight());
                             pixelY++) {

                            // Find the average color of the tile whose top left corner
                            // is at  [x,y]
                            pixel = bitmap.getPixel(pixelX, pixelY);
                            numPixelsInTile++;

                            redComponent += Color.red(pixel);
                            blueComponent += Color.blue(pixel);
                            greenComponent += Color.green(pixel);
                        }
                    }

                    int averageRed = redComponent / numPixelsInTile;
                    int averageBlue = blueComponent / numPixelsInTile;
                    int averageGreen = greenComponent / numPixelsInTile;
                    int averageColor = Color.rgb(averageRed, averageGreen, averageBlue);

                    // Overdraw the tile with a blank area equal to its average color.

                    for (int drawX = tileLeftX; (drawX < (tileLeftX + 32)) && (drawX < bitmap.getWidth()); drawX++) {
                        for (int drawY = tileTopY; (drawY < (tileTopY + 32)) && (drawY < bitmap.getHeight()); drawY++) {
                            bitmap.setPixel(drawX, drawY, averageColor);
                        }
                    }

                    numTilesProcessed++;

                    // Contact the server to get a PNG of a tile with the average color

                    // Broadcast a new result - another tile has been averaged.
                    // Include the tile just returned form the server in the content of the
                    // broadcast message.
                    int percentProgress = numTilesProcessed * 100 / totalTilesToProcess;
                    Timber.d("Percent complete: %d, Tile [%d, %d] has average color of (R:%d G:%d B:%d)",
                             percentProgress, tileLeftX, tileTopY,
                             averageRed,
                             averageGreen,
                             averageBlue);


                    Intent broadcastIntent = new Intent(BROADCAST_ACTION);
                    broadcastIntent.putExtra(EXTRA_PROGRESS, percentProgress);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
                } // for y
            } // for x


            // Now write the modified bitmap (with red tile at top left) as a new image
            // back to the Media Store using the MediaScanner.
            //mediaScan();
            saveBitmapToStorage(bitmap);


        } catch (IOException iox) {
            Timber.i(iox, "Failed to get bitmap for processing");
        }
    }

    private void mediaScan() {
        MediaScannerConnection.MediaScannerConnectionClient mediaScannerClient = new MediaScannerConnection.MediaScannerConnectionClient() {
            private MediaScannerConnection msc = null;

            {
                msc = new MediaScannerConnection(PhotoMosaicService.this, this);
                msc.connect();
            }

            @Override
            public void onMediaScannerConnected() {
                String mimeType = null;
                String filePath = getWorkingFilePath().getAbsolutePath();

                Timber.d("Scanning file at " + filePath);
                msc.scanFile(filePath, mimeType);
            }

            @Override
            public void onScanCompleted(String s, Uri uri) {
                msc.disconnect();
                Timber.d("Mosaic file added from: " + uri);
            }
        };
    }

    private void saveBitmapToStorage(Bitmap bitmap) {

        File path = getWorkingFilePath();

        Timber.d("------------------------------------------");
        Timber.d("Saving bitmap to " + path.getAbsolutePath());

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(path);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        } catch (FileNotFoundException fnfe) {
            Timber.e(fnfe, "Failed to write bitmap");
        } finally {
            try {
                outputStream.close();
            } catch (Exception e) {
                // Empty
            }
        }
    }

}
