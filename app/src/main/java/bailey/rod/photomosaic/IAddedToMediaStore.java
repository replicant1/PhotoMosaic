package bailey.rod.photomosaic;

import android.net.Uri;

import java.io.File;

/**
 * Implemented by any party wanting notification that a public copy of the mosaic image file
 * has been added to the Android Media Store.
 *
 * @see MosaicScratchFile#addScratchFileToAndroidMediaStore(File, IAddedToMediaStore)
 */
public interface IAddedToMediaStore {

    /**
     * @param pathToUnderlyingImageFile Public copy of app-private scratch file
     * @param mediaStoreUri             Uri in Media Store where the new entry for the above file has just been added
     */
    public void added(String pathToUnderlyingImageFile, Uri mediaStoreUri);
}
