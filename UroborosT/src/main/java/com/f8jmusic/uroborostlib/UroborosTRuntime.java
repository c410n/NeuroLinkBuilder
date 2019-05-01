package com.f8jmusic.uroborostlib;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Calendar;

import de.mindpipe.android.logging.log4j.LogConfigurator;
import lombok.val;

public class UroborosTRuntime {

    public static final int FILE_CHOOSER = 666;
    public static final int PERMISSION_ALL = 999;
    public static final String MATH_ML_INDICE = "<label style=”display:none”></label>";
    public static final String SHOWN_STRING = ".SHOWN";
    public static final String PREV_DATABASE = "PREV_DATABASE";
    public static final String NLB_DATABASE_INDICE = "# THIS IS AN NLB DATABASE FILE";
    public static final String OPEN_ME_INTENT = "MainActivity.OPEN_ME";
    public static final int NOTIFICATION_PERIOD = 30 /*min*/ * 60 /*sec*/ * 1000 /*millisec*/;
    // DEBUG MODE MAY BE ENABLED
    public static final boolean DEBUG_EXTENDED_LOGGING_MODE = true;

    public static final String[] PERMISSIONS =
            {
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED
            };

    private static final LogConfigurator logConfigurator = new LogConfigurator();
    public static String LOG_PATH = null;
    private static AppCompatActivity activity = null;
    private static Context appContext;

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    static {
        System.loadLibrary("native-lib");
    }

    public static AppCompatActivity getMainActivityReference() {
        return activity;
    }

    public static void setMainActivityReference(AppCompatActivity appCompatActivity) {
        activity = appCompatActivity;
    }

    public static <T> String getDebugRepresentationOfAnObject(T object) {
        if (object == null)
            return "null";

        StringBuilder result = new StringBuilder();
        String newLine = System.getProperty("line.separator");

        result.append(object.getClass().getName());
        result.append(": ");
        result.append(object.toString());
        result.append(newLine);
        result.append("{");
        result.append(newLine);

        //determine fields declared in this class only (no fields of superclass)
        Field[] fields = object.getClass().getDeclaredFields();

        //print field names paired with their values
        for (Field field : fields) {
            result.append("  ");
            try {
                result.append(field.getName());
                result.append(": ");
                //requires access to private field:
                result.append(field.get(object));
            } catch (IllegalAccessException ex) {
                System.out.println(ex);
            }
            result.append(newLine);
        }
        result.append("}");

        return result.toString();
    }

    public static String getFileNameFromURI(Uri uri) {

        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        Cursor cursor = getMainActivityReference().getContentResolver().
                query(uri, null, null, null, null, null);

        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                //int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                return displayName;
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return null;
    }

    public static void InitLogger() {
        logConfigurator.setFileName(LOG_PATH + File.separator +
                Calendar.getInstance().getTime() +
                "nlb.txt");
        logConfigurator.setRootLevel(Level.ALL);
        logConfigurator.setLevel("org.apache", Level.ALL);
        logConfigurator.setUseFileAppender(true);
        logConfigurator.setFilePattern("%d %-5p [%c{2}]-[%L] %m%n");
        logConfigurator.setMaxFileSize(1024 * 1024 * 5);
        logConfigurator.setImmediateFlush(true);
        logConfigurator.configure();
    }

    private static Logger Log(Class clazz) {
        return Logger.getLogger(clazz);
    }

    public static void SetupLogsFolder() {
        LOG_PATH = combinePaths(
                false, Environment.getExternalStorageDirectory().getAbsolutePath(), "NLB/logs");
        new File(LOG_PATH).mkdirs();
    }

    public static void RedirectLogcat() {
        File logFile = new File(LOG_PATH, "logcat." + System.currentTimeMillis() + ".txt");

        try {
            Process process = Runtime.getRuntime().exec("logcat -c");
            process = Runtime.getRuntime().exec("logcat -f " + logFile);
        } catch (IOException e) {
            // The only exception we are not logging as the MainActivity may not be initialized at this time
            e.printStackTrace();
        }
    }

    public static String combinePaths(boolean removeStartingDash, String... values) {
        String path = "";

        for (String item : values) {
            item = "/" + item;
            path += item;
        }

        return removeStartingDash ? path.replace("//", "/").substring(1) : path.replace("//", "/");
    }

    // Creates a folder if required
    public static String getApplicationStorageFolder(String applicationName, String specificFolder, String filename) {
        String resulting_folder = combinePaths(false, Environment.getExternalStorageDirectory().getAbsolutePath(), applicationName, specificFolder);
        new File(resulting_folder).mkdirs();

        return combinePaths(false, resulting_folder, filename);
    }

    public static String copyAssets(AppCompatActivity appCompatActivity, String asset_folder, String target_folder) {
        AssetManager assetManager = appCompatActivity.getAssets();
        String resulting_folder = "";
        String[] files = null;
        try {
            files = assetManager.list(asset_folder);
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        if (files != null) for (String filename : files) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(combinePaths(true, asset_folder, filename));
                resulting_folder = combinePaths(false, Environment.getExternalStorageDirectory().getAbsolutePath(), target_folder);
                new File(resulting_folder).mkdirs();
                File outFile = new File(combinePaths(false, Environment.getExternalStorageDirectory().getAbsolutePath(), target_folder, filename));
                out = new FileOutputStream(outFile);
                copyFile(in, out);
            } catch (IOException e) {
                Log.e("tag", "Failed to copy asset file: " + filename, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return resulting_folder;
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public static void copyFile(Uri in_uri, String out_name) throws IOException {
        InputStream in = getMainActivityReference().getContentResolver().openInputStream(in_uri);
        OutputStream out = new FileOutputStream(out_name, false);

        copyFile(in, out);

        out.close();
        in.close();
    }

    public static void copyFile(String in_name, String out_name) throws IOException {
        InputStream in = new FileInputStream(in_name);
        OutputStream out = new FileOutputStream(out_name, false);

        copyFile(in, out);

        out.close();
        in.close();
    }

    public static String combinePathsAsURI(boolean b, String... values) {
        return Uri.fromFile(new File(combinePaths(b, values))).toString();
    }

    public static void saveSetting(Context context, String settingKey, String settingValue) {
        SharedPreferences settings;
        SharedPreferences.Editor editor;
        settings = context.getSharedPreferences("NEURO_LINK_BUILDER.ini", Context.MODE_PRIVATE);
        editor = settings.edit();

        editor.putString(settingKey, settingValue);
        editor.commit();
    }

    public static String getSetting(Context context, String settingKey) {
        SharedPreferences settings;
        SharedPreferences.Editor editor;

        settings = context.getSharedPreferences("NEURO_LINK_BUILDER.ini", Context.MODE_PRIVATE);
        val values = settings.getAll();

        if (values.containsKey(settingKey)) {
            return (String) values.get(settingKey);
        }

        return null;
    }

    public static String getFileSetting(Context context, String settingKey) {
        String filePath = getSetting(context, settingKey);

        if (StringUtils.isNotEmpty(filePath)) {
            File file = new File(filePath);
            if (file.exists())
                return filePath;
        }

        return null;
    }

    private static boolean hasPermissions(Context context, final String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hasBasicPermissions(Context context) {
        return hasPermissions(context, PERMISSIONS);
    }

    public static void RequestBasicPermissions() {
        Context context = getMainActivityReference().getApplicationContext();
        if (!hasPermissions(context, PERMISSIONS)) {
            ActivityCompat.requestPermissions(getMainActivityReference(), PERMISSIONS, PERMISSION_ALL);
        }
    }

    public static boolean CheckIfEveryEqual(final int blueprint, final int[] result) {
        for (val param : result)
            if (blueprint != param)
                return false;

        return true;
    }

    public static void PerformLogsSetup() {
        UroborosTRuntime.SetupLogsFolder();

        if (UroborosTRuntime.DEBUG_EXTENDED_LOGGING_MODE)
            RedirectLogcat();

        InitLogger();
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    public static void forceTheNonkill(@NonNull Context pkg, @NonNull Class<?> cls) {
        ComponentName receiver = new ComponentName(pkg, cls);
        PackageManager pm = pkg.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    // Works only from the main activity process
    public static void userMessage(String title, String message, final DialogInterface.OnClickListener listener) {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(getMainActivityReference());
        dlgAlert.setMessage(message);
        dlgAlert.setTitle(title);
        dlgAlert.setPositiveButton("OK", listener);
        dlgAlert.setCancelable(false);

        dlgAlert.create().show();
    }

    public static String userInputRequestLastResult = null;
    private static int userInputRequestShown = 0;
    private static AlertDialog alertDialog = null;

    public static void setApplicationContext(Context appContext_) {
        appContext = appContext_;
    }

    public static Context getApplicationContext() {
        return appContext;
    }

    public interface userInputRequestCallback {
        void callback();
    }
    public static void userInputRequest(String customTitle, String currentContent, final userInputRequestCallback callback){
        if (userInputRequestShown++ != 0)
            return;
        LayoutInflater li = LayoutInflater.from(getMainActivityReference());
        View promptsView = li.inflate(R.layout.user_input_dialog, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                getMainActivityReference());

        alertDialogBuilder.setView(promptsView);

        final EditText editTextDialogUserInput = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserInput);
        editTextDialogUserInput.setText(currentContent);

        final TextView editTextDialogUserInputTitle = (TextView) promptsView
                .findViewById(R.id.editTextDialogUserInputTitle);
        editTextDialogUserInputTitle.setText(customTitle);

        alertDialogBuilder.setCancelable(false);

        alertDialogBuilder
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                // get user input and set it to result
                                // edit text
                                userInputRequestLastResult = editTextDialogUserInput.getText().toString().trim();
                                userInputRequestShown = 0;
                                callback.callback();

                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                userInputRequestLastResult = null;
                                userInputRequestShown = 0;
                                dialog.cancel();
                            }
                        });

        alertDialog = alertDialogBuilder.create();

        alertDialog.setOnShowListener( new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface arg0) {
               alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.DKGRAY);
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.DKGRAY);
            }
        });

        alertDialog.show();
    }

    // Works only from the main activity process
    public static void userQuestion(String title, String message, final DialogInterface.OnClickListener yes_listener, final DialogInterface.OnClickListener no_listener) {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(getMainActivityReference());
        dlgAlert.setMessage(message);
        dlgAlert.setTitle(title);
        dlgAlert.setPositiveButton("Yes", yes_listener);
        dlgAlert.setNegativeButton("No", no_listener);
        dlgAlert.setCancelable(false);

        dlgAlert.create().show();
    }

    public static void ShowToastMessage(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }

    public static synchronized void LOG_FATAL(Class aClass, Exception ex) {
        try {
            UroborosTRuntime.ShowToastMessage(String.format("A fatal error has occurred: %s", ex.getMessage()));
        } catch (Exception internal_ex) {
            Log.e("UroborosTRuntime", String.format("A toast message failed to show: %s", ex.getMessage()));
        }
        UroborosTRuntime.Log(aClass).fatal(ex);
        UroborosTRuntime.Log(aClass).fatal(ex.getMessage());
        UroborosTRuntime.Log(aClass).fatal(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(ex));
    }

    public static synchronized void LOG_REGULAR(Class aClass, Exception ex) {
        if (DEBUG_EXTENDED_LOGGING_MODE) {
            UroborosTRuntime.ShowToastMessage(String.format("A regular error has occurred: %s", ex.getMessage()));
            UroborosTRuntime.Log(aClass).info(ex);
            UroborosTRuntime.Log(aClass).fatal(ex.getMessage());
            UroborosTRuntime.Log(aClass).info(ex.getStackTrace().toString());
        }
    }

    public static synchronized void LOG_MESSAGE(Class aClass, String message) {
        if (DEBUG_EXTENDED_LOGGING_MODE) {
            UroborosTRuntime.Log(aClass).info(message);
        }
    }

    private native String stringFromJNI();

    // Universal exception
    public static class UroborosTLibUroborosTRuntimeException extends Exception {
        public UroborosTLibUroborosTRuntimeException(String info) {
            super(info);
        }
    }
}
