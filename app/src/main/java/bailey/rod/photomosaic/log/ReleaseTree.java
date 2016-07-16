package bailey.rod.photomosaic.log;

import timber.log.Timber;

public class ReleaseTree extends Timber.Tree {
    @Override
    protected boolean isLoggable(int priority) {
        return ((priority == android.util.Log.WARN) || (priority == android.util.Log.ERROR) ||
                (priority == android.util.Log.INFO));
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {

    }
}
