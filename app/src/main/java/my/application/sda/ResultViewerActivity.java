package my.application.sda;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Bundle;
import android.view.Window;
import android.widget.ImageView;

import androidx.viewpager.widget.ViewPager;

import java.io.FileNotFoundException;
import java.util.List;

import my.application.sda.viewpager.ViewPagerAdapter;

public class ResultViewerActivity extends Activity {

    // Creating object of ViewPager
    ViewPager viewPager;

    // Creating Object of ViewPagerAdapter
    ViewPagerAdapter viewPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_viewer);

        // Initializing the ViewPager Object
        viewPager = (ViewPager)findViewById(R.id.viewPagerMain);

        // Initializing the ViewPagerAdapter
        viewPagerAdapter = new ViewPagerAdapter(ResultViewerActivity.this);

        // Adding the Adapter to the ViewPager
        viewPager.setAdapter(viewPagerAdapter);
    }

}
