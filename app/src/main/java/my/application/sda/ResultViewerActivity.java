package my.application.sda;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;

public class ResultViewerActivity extends Activity {

    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.image_viewer);
        imageView = findViewById(R.id.imageView);

        //here context can be anything like getActivity() for fragment, this or MainActivity.this
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(imageView.getContext().openFileInput("myImage"));
            imageView.setImageBitmap(bitmap);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
