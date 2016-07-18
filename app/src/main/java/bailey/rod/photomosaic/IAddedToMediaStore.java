package bailey.rod.photomosaic;

import android.net.Uri;

/**
 * Created by rodbailey on 18/07/2016.
 */
public interface IAddedToMediaStore {

    public void added(String pathToUnderlyingImageFile, Uri mediaStoreUri);
}
