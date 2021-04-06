package my.application.sda;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.RadioButton;

public class SettingsActivity extends Activity {

    //Radio button
    RadioButton det0;
    RadioButton det2;
    RadioButton det4;

    //Shared preferences
    SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.setting_preferences);

        det0 = (RadioButton) findViewById(R.id.efficientDet0);
        det2 = (RadioButton) findViewById(R.id.efficientDet2);
        det4 = (RadioButton) findViewById(R.id.efficientDet4);

        settings = getSharedPreferences("settings", 0);
        switch (settings.getInt("efficientDet", 0)){
            default: det0.toggle();
            case 0: det0.toggle(); break;
            case 2: det2.toggle(); break;
            case 4: det4.toggle(); break;
        }

        det0.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("efficientDet", 0);
                editor.commit();
            }
        });

        det2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("efficientDet", 2);
                editor.commit();
            }
        });

        det4.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("efficientDet", 4);
                editor.commit();
            }
        });


    }
}


