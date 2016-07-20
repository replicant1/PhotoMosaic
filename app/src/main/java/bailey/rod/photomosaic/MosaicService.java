package bailey.rod.photomosaic;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static bailey.rod.photomosaic.Constants.*;

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
 * <li> For each request 'T':
 * <li> - The average color 'C' of the image's pixels within T is calculated
 * <li> - An external service is contacted that takes C and returns a mosaic request image 'M'
 * <li> - T's area in the mosaic is replaced with image 'M'
 */
public class MosaicService extends IntentService {

    // Defines a custom Intent action for Intent broadcast every time the processing advances by another percent
    public static final String MOSAIC_CREATION_PROGRESSED =
            "bailey.rod.photomosaic.MOSAIC_CREATION_PROGRESSED";

    // Custom Intent action broadcast whenever mosaic creation has finished and the completed mosaic
    // image is now in the private scratch file.
    public static final String MOSAIC_CREATION_FINISHED = "bailey.rod.photomosaic.MOSAIC_CREATION_FINISHED";

    // Custom Intent broadcast whenever the mosaic'ing process finishes the last request in a given row.
    // The process proceeds one row at a time, from top to bottom of the image.
    public static final String MOSAIC_CREATION_ROW_FINISHED = "bailey.rod.photomosaic.MOSAIC_CREATION_ROW_FINISHED";

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
        int numXPixelsInTile;
        int numYPixelsInTile;
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
            ExecutorService rowExecutorService = Executors.newFixedThreadPool(10);
            List<MosaicTileCreator> creatorsForThisRow = new LinkedList<MosaicTileCreator>();

            for (int tileLeftX = 0; (tileLeftX < bitmap.getWidth()) && !abortRequested; tileLeftX += TILE_WIDTH_PX) {

                // Find the average colour for the pixels in the request by finding the average red, green and blue
                // component values of each pixel individually.
                redComponent = 0;
                blueComponent = 0;
                greenComponent = 0;

                numXPixelsInTile = 0;
                numYPixelsInTile = 0;

                // TODO: Investigate if bitmap.getpixels() is better than bitmap.getPixel() in a loop.
                for (int pixelX = tileLeftX; (pixelX < (tileLeftX + TILE_WIDTH_PX)) && (pixelX < bitmap.getWidth());
                     pixelX++) {
                    numXPixelsInTile++;
                    numYPixelsInTile = 0;

                    for (int pixelY = tileTopY; (pixelY < (tileTopY + TILE_HEIGHT_PX)) && (pixelY < bitmap.getHeight());
                         pixelY++) {
                        numYPixelsInTile++;

                        // Find the average color of the request whose top left corner
                        // is at  [x,y]
                        pixel = bitmap.getPixel(pixelX, pixelY);

                        redComponent += Color.red(pixel);
                        blueComponent += Color.blue(pixel);
                        greenComponent += Color.green(pixel);
                    }
                }

                averageRed = redComponent / (numXPixelsInTile * numYPixelsInTile);
                averageBlue = blueComponent / (numXPixelsInTile * numYPixelsInTile);
                averageGreen = greenComponent / (numXPixelsInTile * numYPixelsInTile);
                averageColor = Color.rgb(averageRed, averageGreen, averageBlue);

                MosaicTileCreatorRequest request = new MosaicTileCreatorRequest();
                request.topLeftX = tileLeftX;
                request.topLeftY = tileTopY;
                request.averageColor = averageColor;
                request.tileWidth = numXPixelsInTile;
                request.tileHeight = numYPixelsInTile;

                creatorsForThisRow.add(new MosaicTileCreator(request));


                // Include the request just returned form the server in the content of the
                // broadcast message.
                int percentProgress = numTilesProcessed * 100 / totalTilesToProcess;
                Log.d(TAG, String.format("Percent complete: %d, Tile [%d, %d] has average color of (R:%d G:%d B:%d)",
                                         percentProgress, tileLeftX, tileTopY,
                                         averageRed,
                                         averageGreen,
                                         averageBlue));

            } // for x

            // Before moving onto the next row, update the bitmap with all of the
            // mosaic tiles for the current row.

            try {
                List<Future<MosaicTileCreatorResult>> futures = rowExecutorService.invokeAll(creatorsForThisRow);

                // Should do this from the individual creators so that progress arrives between rows
                numTilesProcessed += futures.size();

                for (Future<MosaicTileCreatorResult> future : futures) {
                    // Get the calculated mosaic pixels out of the result
                    MosaicTileCreatorResult result = future.get();

                    int bitmapWidth = result.bitmap.getWidth();
                    int bitmapHeight = result.bitmap.getHeight();
                    int[] pixels = new int[bitmapHeight * bitmapWidth];

                    result.bitmap.getPixels(pixels, // data out
                                            0, // offset
                                            bitmapWidth, // stride
                                            0, // x
                                            0, // y
                                            bitmapWidth, // width
                                            bitmapHeight);// height

                    // Copy the mosaic pixels into the scratch file
                    bitmap.setPixels(pixels, // data in
                                     0, // offset
                                     bitmapWidth, // stride
                                     result.topLeftX, // x
                                     result.topLeftY, // y
                                     bitmapWidth, // width
                                     bitmapHeight); // height
                }

                // TODO: Maybe just save the row we just changed, rather than the entire bitmap, most of which
                // TODO: hasn't changed.
                mosaicScratchFile.saveBitmapToScratchFile(bitmap);

                Log.d(TAG, String.format("numTilesProcessed=%d, totalTilesToProcess=%d", numTilesProcessed,
                                         totalTilesToProcess));
                int progressPercent = 100 * numTilesProcessed / totalTilesToProcess;
                broadcastProgressUpdate(progressPercent);

                // Let external parties know that another row has been finished
                broadcastMosaicCreationRowFinished();

            } catch (InterruptedException iex) {
                Log.e(TAG, "Error invoking all creators for row", iex);
            } catch (ExecutionException eex) {
                Log.e(TAG, "Error invoking all creators for row", eex);
            }
        } // for y
    }

    /**
     * Broadcasts to MosaicActivity that this service has finished the mosaic creation process
     */
    private void broadcastMosaicCreationFinished() {
        Log.d(TAG, "Broadcasting MOSAIC FINISHED");
        Intent broadcastIntent = new Intent(MOSAIC_CREATION_FINISHED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }

    /**
     * Broadcasts to MosaicActivity this service has finished mosaic process for another row of tiles in the image.
     * Some clients will use this to perform screen refreshes of one row at a time.
     */
    private void broadcastMosaicCreationRowFinished() {
        Log.d(TAG, "Broadcasting ROW FINISHED");
        Intent broadcastIntent = new Intent(MOSAIC_CREATION_ROW_FINISHED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }

    /**
     * Broadcasts to MosaicActivity that this service has made further progress in creating the mosaic, by
     * processing another request.
     *
     * @param percentProgress The percentage progress in [0,100]
     */
    private void broadcastProgressUpdate(int percentProgress) {
        Log.d(TAG, "Broadcasting PROGRESS percent = " + percentProgress);
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

    /**
     * Parameters of a request to create/fetch a particular mosaic tile at some future time.
     */
    public class MosaicTileCreatorRequest {
        // The average color of the pixels within the tile's area of the raw image
        public int averageColor;

        // Pixel height of the tile to be created
        public int tileHeight;

        // Pixel width of the tile to be created
        public int tileWidth;

        // X coord of the top left of the tile in the raw image (and mosaic image)
        public int topLeftX;

        // Y coord of the top left of the tile in the raw image (and mosaic image)
        public int topLeftY;

    }

    /**
     * Encapsulates the results of processing a MosaicTileCreatorRequest
     */
    public class MosaicTileCreatorResult {
        // The requested tile, in bitmpa form
        public Bitmap bitmap;

        // Taken from the "topLeftX" attribute of the corresponding MosaicTileCreatorRequest
        public int topLeftX;

        // Taken from the "topLeftY" attribute of the corresponding MosaicTileCreatorRequest
        public int topLeftY;
    }

    /**
     * Executable task that when called, serves the given MosaicTileCreatorRequest by producing
     * a MosaicTIleCreatorResult which contains the mosaic tile image.
     */
    public class MosaicTileCreator implements Callable<MosaicTileCreatorResult> {
        private final MosaicTileCreatorRequest request;

        public MosaicTileCreator(MosaicTileCreatorRequest tile) {
            this.request = tile;
        }

        @Override
        public MosaicTileCreatorResult call() throws Exception {
            Log.d(TAG, String.format("Creating bitmap with top left [%d, %d] and size %d x %d",
                                     request.topLeftX,
                                     request.topLeftY,
                                     request.tileWidth,
                                     request.tileHeight));
            Bitmap bitmap = Bitmap.createBitmap(request.tileWidth, request.tileHeight, Bitmap.Config.RGB_565);
            bitmap.eraseColor(request.averageColor);

            MosaicTileCreatorResult result = new MosaicTileCreatorResult();
            result.topLeftX = request.topLeftX;
            result.topLeftY = request.topLeftY;
            result.bitmap = bitmap;

            return result;
        }

    }
}
