package my.application.sda.viewpager;

//https://www.geeksforgeeks.org/image-slider-in-android-using-viewpager/

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import java.io.FileNotFoundException;
import java.util.Objects;

import my.application.sda.R;

import java.util.Arrays;

public class ViewPagerAdapter extends PagerAdapter {

    // Context object
    Context context;

    // Array of image paths
    String[] imagePaths;

    // Layout Inflater
    LayoutInflater mLayoutInflater;


    // Viewpager Constructor
    public ViewPagerAdapter(Context context) {
        this.context = context;
        this.imagePaths = context.getFilesDir().list();
        Arrays.sort(imagePaths);
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        // return the number of images
        return imagePaths.length;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == ((LinearLayout) object);
    }

    @NonNull
    public Object instantiateItem(@NonNull ViewGroup container, final int position) {
        // inflating the item.xml
        View itemView = mLayoutInflater.inflate(R.layout.image_item, container, false);

        // referencing the image view from the item.xml file
        ImageView imageView = (ImageView) itemView.findViewById(R.id.imageViewMain);

        // setting the image in the imageView
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(context.openFileInput(imagePaths[imagePaths.length - 1 - position]));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        imageView.setImageBitmap(bitmap);

        // adding the View
        Objects.requireNonNull(container).addView(itemView);

        return itemView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {

        container.removeView((LinearLayout) object);
    }
}
