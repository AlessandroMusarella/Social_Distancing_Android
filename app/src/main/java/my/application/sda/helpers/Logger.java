package my.application.sda.helpers;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    public static final String FILENAME = "log_sda.txt";
    private File logFile;
    private Context context;
    //private BufferedWriter buf;

    public Logger (Context context){
        super();
        this.context = context;
    }

    public synchronized void addRecordToLog(String message){
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
            FileOutputStream fos = context.openFileOutput(FILENAME, Context.MODE_PRIVATE | Context.MODE_APPEND);
            String finalMessage = timeStamp + " [ " + message + " ]\n";
            fos.write(finalMessage.getBytes());
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
