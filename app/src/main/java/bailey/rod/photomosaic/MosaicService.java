package bailey.rod.photomosaic;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import static bailey.rod.photomosaic.Constants.TILE_HEIGHT_PX;
import static bailey.rod.photomosaic.Constants.TILE_WIDTH_PX;

/**
 * A service that applies a "Mosaic" effect to a given image file. The image file is specified with a
 * URI to the Android Media Store. The service broadcasts three types of Intents as it proceeds
 * through the mosaicing process - progress, finished and error.
 * <p/>
 * To trigger the service, broadcast an Intent with the sole data of the URI to the image file
 * to be processed. From inside an Activity, do this:
 * <code>
 * <p/>
 * Intent intent = new Intent(this, MosaicService.class);
 * intent.setData(imageUri);
 * startService(intent);
 * </code>
 * Each time such an Intent is received, the following occurs:
 * <li> The image is notionally divided into tiles
 * <li> For each tile 'T':
 * <li> - The average color 'C' of the image's pixels within T is calculated
 * <li> - An external service is contacted that takes C and returns a tile image 'M'
 * <li> - T's area in the mosaic is replaced with image 'Mf'
 */
public class MosaicService extends IntentService {

    private static final String TAG = MosaicService.class.getSimpleName();

    // Defines a custom Intent action
    public static final String BROADCAST_ACTION =
            "bailey.rod.photomosaic.BROADCAST";

    // Defines the key for the status "extra" in an Intent
    public static final String EXTRA_PROGRESS =
            "bailey.rod.photomosaic.EXTRA_PROGRESS";


    public MosaicService() {
        super("Photo Mosaic");
        Log.i(TAG, "MosaicService has been constructed");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "dataString = " + intent.getDataString());

        Uri imageUri = Uri.parse(intent.getDataString());
        bigLoop(imageUri);
    }

    private void bigLoop(Uri imageUri) {
        MosaicScratchFile mosaicScratchFile = new MosaicScratchFile(this);
        mosaicScratchFile.initFromMediaStore(imageUri);
        Bitmap bitmap = mosaicScratchFile.loadMutableBitmapFromScratchFile();

        int pixel;
        int redComponent;
        int blueComponent;
        int greenComponent;
        int numPixelsInTile;

        int tileCountX = (int) Math.ceil((double) bitmap.getWidth() / (double) TILE_WIDTH_PX);
        int tileCountY = (int) Math.ceil((double) bitmap.getHeight() / (double) TILE_HEIGHT_PX);

        int numTilesProcessed = 0;
        int totalTilesToProcess = tileCountX * tileCountY;

        Log.d(TAG, String.format("tileCountX=%d, tileCountY=%d, total tiles=%d", tileCountX, tileCountY,
                                 totalTilesToProcess));

        for (int tileLeftX = 0; tileLeftX < bitmap.getWidth(); tileLeftX += TILE_WIDTH_PX) {
            for (int tileTopY = 0; tileTopY < bitmap.getHeight(); tileTopY += TILE_HEIGHT_PX) {

                redComponent = 0;
                blueComponent = 0;
                greenComponent = 0;

                numPixelsInTile = 0;

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

                int averageRed = redComponent / numPixelsInTile;
                int averageBlue = blueComponent / numPixelsInTile;
                int averageGreen = greenComponent / numPixelsInTile;
                int averageColor = Color.rgb(averageRed, averageGreen, averageBlue);

                // Overdraw the tile with a blank area equal to its average color.
                for (int drawX = tileLeftX; (drawX < (tileLeftX + TILE_WIDTH_PX)) && (drawX < bitmap.getWidth()); drawX++) {
                    for (int drawY = tileTopY; (drawY < (tileTopY + TILE_HEIGHT_PX)) && (drawY < bitmap.getHeight());
                         drawY++) {
                        bitmap.setPixel(drawX, drawY, averageColor);
                    }
                }

                numTilesProcessed++;

                // Contact the server to get a PNG of a tile with the average color

                // Broadcast a new result - another tile has been averaged.
                // Include the tile just returned form the server in the content of the
                // broadcast message.
                int percentProgress = numTilesProcessed * 100 / totalTilesToProcess;
                Log.d(TAG, String.format("Percent complete: %d, Tile [%d, %d] has average color of (R:%d G:%d B:%d)",
                                         percentProgress, tileLeftX, tileTopY,
                                         averageRed,
                                         averageGreen,
                                         averageBlue));

                mosaicScratchFile.saveBitmapToScratchFile(bitmap);
                broadcastProgressUpdate(percentProgress);
            } // for y
        } // for x
    }

    private void broadcastProgressUpdate(int percentProgress) {
        Intent broadcastIntent = new Intent(BROADCAST_ACTION);
        broadcastIntent.putExtra(EXTRA_PROGRESS, percentProgress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }


}
