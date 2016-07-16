package bailey.rod.photomosaic;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.IOException;

import hugo.weaving.DebugLog;
import timber.log.Timber;

/**
 * Presents the image to be mosaic'd with a slider for adjusting the tile size of the mosaic.
 */
public class SetMosaicTileSizeActivity extends AppCompatActivity {


    @DebugLog
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.i("*****************************************");
        Timber.i("**** Into SetMosaicTileSizeActivity  ****");
        Timber.i("*****************************************");

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        Bundle extras = intent.getExtras();
        Timber.i("extras.contents=" + extras.describeContents());


        for (String key : extras.keySet()) {
            Timber.i("extras key=" + key);
            Object value = extras.get(key);
            if (value != null)
                Timber.i("extras value.class = " + value.getClass());

        }

        Timber.i("intent=%s, action=%s, type=%s", intent, action, type);

        setContentView(R.layout.activity_set_mosaic_tile_size);

        ImageView tileSizeImageView = (ImageView) findViewById(R.id.tile_size_image_view);


        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {

            Button button = (Button) findViewById(R.id.tile_size_button);
            button.setOnClickListener(new SendToServiceOnClickListener(imageUri));

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                Timber.i("bitmap=" + bitmap);
                if (bitmap != null) {
                    Timber.i("bitmap.height=%d, bitmpa.width=%d", bitmap.getHeight(), bitmap.getWidth());
                    tileSizeImageView.setImageBitmap(bitmap);
                }
            } catch (IOException iox) {
                Timber.e(iox, "Failed to get raw image to be mosaiced");
            }
        }
    }


    private class SendToServiceOnClickListener implements View.OnClickListener {

        private final Uri imageUri;

        public SendToServiceOnClickListener(Uri imageUri) {
            this.imageUri = imageUri;
        }

        @Override
        public void onClick(View view) {
            Timber.i("About to trigger the PhotoMosaicService");

            Intent serviceIntent = new Intent(SetMosaicTileSizeActivity.this, PhotoMosaicService.class);
            serviceIntent.setData(imageUri);
            SetMosaicTileSizeActivity.this.startService(serviceIntent);
        }
    }
}
