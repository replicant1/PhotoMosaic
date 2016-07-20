package bailey.rod.photomosaic;

import android.graphics.Color;

/**
 * Miscellaneous utility methods
 */
public abstract class Utils {

    /**
     * @param packedColor RGBA packed together. A is ignored.
     * @return 6 character hex string representing the RGB components of the given packedColor
     */
    public static String packagedColorIntToRGBHexString(int packedColor) {
        int redComponentInt = Color.red(packedColor);
        int greenComponentInt = Color.green(packedColor);
        int blueComponentInt = Color.blue(packedColor);

        String redComponentStr = padHexToTwoChars(Integer.toHexString(redComponentInt));
        String blueComponentStr = padHexToTwoChars(Integer.toHexString(blueComponentInt));
        String greenComponentStr = padHexToTwoChars(Integer.toHexString(greenComponentInt));

        return redComponentStr + greenComponentStr + blueComponentStr;
    }

    private static String padHexToTwoChars(String hexString) {
        return hexString.length() == 1 ? "0" + hexString : hexString;
    }
}
