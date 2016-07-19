package bailey.rod.photomosaic;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import static bailey.rod.photomosaic.Constants.TILE_HEIGHT_PX;
import static bailey.rod.photomosaic.Constants.TILE_WIDTH_PX;

/**
 * A service that applies a "Mosaic" effect to a given image file. The image file is specified with a
 * URI to the Android Media Store. The service broadcasts three types of Intents as it proceeds
 * through the mosaicing process - progress, finished and error.
 * <p>
 * To trigger the service, broadcast an Intent with the sole data of the URI to the image file
 * to be processed. From inside an Activity, do this:
 * <code>
 * <p>
 * Intent intent = new Intent(this, MosaicService.class);
 * intent.setData(imageUri);
 * startService(intent);
 * </code>
 * Each time such an Intent is received, the following occurs:
 * <li> The image is notionally divided into tiles
 * <li> For each tile 'T':
 * <li> - The average color 'C' of the image's pixels within T is calculated
 * <li> - An external service is contacted that takes C and returns a mosaic tile image 'M'
 * <li> - T's area in the mosaic is replaced with image 'M'
 */
public class MosaicService extends IntentService {

    // Defines a custom Intent action for Intent broadcast every time the processing advances by another percent
    public static final String MOSAIC_CREATION_PROGRESSED =
            "bailey.rod.photomosaic.MOSAIC_CREATION_PROGRESSED";

    // Custom Intent action broadcase whenever mosaic creation has finished and the completed mosaic
    // image is now in the private scratch file.
    public static final String MOSAIC_CREATION_FINISHED = "bailey.rod.photomosaic.MOSAIC_CREATION_FINISHED";

    // Defines the key for the status "extra" in an Intent
    public static final String EXTRA_PROGRESS =
            "bailey.rod.photomosaic.EXTRA_PROGRESS";

    private static final String TAG = MosaicService.class.getSimpleName();

    // A quick but dodgy way to bring this service to an immediate halt from an associated Activity.
    // TODO Better way would be to use IBinder and bind service to activity in the usual manner
    public static volatile boolean abortRequested;

    public MosaicService() {
        super("Photo Mosaic");
        Log.i(TAG, "MosaicService has been constructed");
    }

    /**
     * @param imageUri URI in the Media Store of the image that is to be mosaic'd.
     */
    private void bigLoop(Uri imageUri) {
        // Initialize the scratch file with the raw image to be mosaic'd. Subsequent modifications are made to this
        // private copy.
        MosaicScratchFile mosaicScratchFile = new MosaicScratchFile(this);
        mosaicScratchFile.initFromMediaStore(imageUri);
        Bitmap bitmap = mosaicScratchFile.loadMutableBitmapFromScratchFile();

        // Lots of declarations up front to avoid overhead of constant re-creation in the tight loop below
        int pixel;
        int redComponent;
        int blueComponent;
        int greenComponent;
        int numPixelsInTile;
        int averageRed;
        int averageBlue;
        int averageGreen;
        int averageColor;

        int tileCountX = (int) Math.ceil((double) bitmap.getWidth() / (double) TILE_WIDTH_PX);
        int tileCountY = (int) Math.ceil((double) bitmap.getHeight() / (double) TILE_HEIGHT_PX);

        int numTilesProcessed = 0;
        int totalTilesToProcess = tileCountX * tileCountY;

        Log.d(TAG, String.format("tileCountX=%d, tileCountY=%d, total tiles=%d", tileCountX, tileCountY,
                                 totalTilesToProcess));

        // Process mosaic tiles in row-major order i.e. same as western reading order.
        for (int tileTopY = 0; (tileTopY < bitmap.getHeight()) && !abortRequested; tileTopY += TILE_HEIGHT_PX) {
            for (int tileLeftX = 0; (tileLeftX < bitmap.getWidth()) && !abortRequested; tileLeftX += TILE_WIDTH_PX) {

                // Find the average colour for the pixels in the tile by finding the average red, green and blue
                // component values of each pixel individually.
                redComponent = 0;
                blueComponent = 0;
                greenComponent = 0;

                numPixelsInTile = 0;

                // TODO: Investigate if bitmap.getpixels() is better than bitmap.getPixel() in a loop.
                for (int pixelX = tileLeftX; (pixelX < (tileLeftX + TILE_WIDTH_PX)) && (pixelX < bitmap.getWidth());
                     pixelX++) {
                    for (int pixelY = tileTopY; (pixelY < (tileTopY + TILE_HEIGHT_PX)) && (pixelY < bitmap.getHeight());
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

                averageRed = redComponent / numPixelsInTile;
                averageBlue = blueComponent / numPixelsInTile;
                averageGreen = greenComponent / numPixelsInTile;
                averageColor = Color.rgb(averageRed, averageGreen, averageBlue);

                // Fill the tile with its average color.
                for (int drawX = tileLeftX; (drawX < (tileLeftX + TILE_WIDTH_PX)) && (drawX < bitmap.getWidth()); drawX++) {
                    for (int drawY = tileTopY; (drawY < (tileTopY + TILE_HEIGHT_PX)) && (drawY < bitmap.getHeight());
                         drawY++) {
                        bitmap.setPixel(drawX, drawY, averageColor);
                    }
                }

                numTilesProcessed++;

                // TODO Contact the server to get a PNG of a tile with the average color

                // Broadcast a new result - another tile has been averaged.
                // Include the tile just returned form the server in the content of the
                // broadcast message.
                int percentProgress = numTilesProcessed * 100 / totalTilesToProcess;
                Log.d(TAG, String.format("Percent complete: %d, Tile [%d, %d] has average color of (R:%d G:%d B:%d)",
                                         percentProgress, tileLeftX, tileTopY,
                                         averageRed,
                                         averageGreen,
                                         averageBlue));

                // TODO: Maybe just save the tile we just changed, rather than the entire bitmap, most of which
                // hasn't changed.
                mosaicScratchFile.saveBitmapToScratchFile(bitmap);
                broadcastProgressUpdate(percentProgress);


                // TODO At end of row, broadcast MOSAIC_ROW_PROCESSED intent
            } // for x
        } // for y
    }

    /**
     * Broadcasts to MosaicActivity that this service has finished the mosaic creation process
     */
    private void broadcastMosaicCreationFinished() {
        Intent broadcastIntent = new Intent(MOSAIC_CREATION_FINISHED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }

    /**
     * Broadcasts to MosaicActivity that this service has made further progress in creating the mosaic, by
     * processing another tile.
     *
     * @param percentProgress The percentage progress in [0,100]
     */
    private void broadcastProgressUpdate(int percentProgress) {
        Intent broadcastIntent = new Intent(MOSAIC_CREATION_PROGRESSED);
        broadcastIntent.putExtra(EXTRA_PROGRESS, percentProgress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "intent = " + intent);
        Log.i(TAG, "dataString = " + intent.getDataString());

        abortRequested = false;

        Uri imageUri = Uri.parse(intent.getDataString());
        bigLoop(imageUri);

        if (!abortRequested) {
            broadcastMosaicCreationFinished();
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.i(TAG, "MosaicService is being started");
    }
}
