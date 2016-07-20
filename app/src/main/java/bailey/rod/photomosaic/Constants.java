package bailey.rod.photomosaic;

import android.graphics.Bitmap;

/**
 * Collection of constants that might need tweeking over time.
 *
 * TODO Might later migrate to an external assets/config.properties file.
 */
public class Constants {

    /**
     * Pixel width of mosaic tiles
     */
    public static final int TILE_WIDTH_PX = 32;

    /**
     * Pixel height of mosaic tiles
     */
    public static final int TILE_HEIGHT_PX = 32;

    /**
     * Simple file name of the scratch file that is the app's private storage
     */
    public static final String SCRATCH_FILE_NAME = "mosaic.jpg";

    /**
     * The beginning of the simple file name that is output by this app to a
     * public directory. e.g. "mosaic_2016_02_04_12_35.jpg"
     */
    public static final String OUTPUT_IMAGE_FILE_PREFIX = "mosaic_";

    /**
     * The middle of the simple file name that is output by this app to a
     * public directory. e.g. "mosaic_2016_02_04_12_35.jpg". This must be a valid
     * date formatting string according to android.text.format.DateFormat.
     *
     * @see android.text.format.DateFormat
     */
    public static final String OUTPUT_IMAGE_FILE_INFIX = "yyyy_MM_dd_hh_mm_ss";

    /**
     * The file extension (including leading '.') of the simple file name that is
     * output by this app to a public directory. If this changes, make sure #OUTPUT_IMAGE_COMPRESS_FORMAT
     * also changes.
     */
    public static final String OUTPUT_IMAGE_FILE_SUFFIX = ".jpg";

    /**
     * Symbolizes the type of output format. If this changes, make sure #OUTPUT_IMAGE_FILE_SUFFIX
     * also changes.
     */
    public static final Bitmap.CompressFormat OUTPUT_IMAGE_COMPRESS_FORMAT = Bitmap.CompressFormat.JPEG;

    /**
     * Quality of output image as a percentage.
     */
    public static final int OUTPUT_IMAGE_QUALITY_PERCENT = 100;

    /**
     * Possible ways of getting the image for a mosaic tile
     */
    public enum MosaicTileImageStrategy {
        CREATE_TILE_IMAGE_FROM_AVERAGE_COLOR, // Fill tile programmatically with average color
        FETCH_TILE_IMAGE_FROM_SERVER; // Fetch from external tile server
    }

    public static final MosaicTileImageStrategy TILE_STRATEGY = MosaicTileImageStrategy
            .CREATE_TILE_IMAGE_FROM_AVERAGE_COLOR;
}
