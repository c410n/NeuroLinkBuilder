package com.f8jmusic.neurolinkbuilder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.MediaRouter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.f8jmusic.uroborostlib.UroborosTRuntime;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import lombok.Data;
import lombok.val;

public class MainActivity extends AppCompatActivity {

    public static String currentDictionaryName = null; // Loaded from the comment in the header of the dictionary
    public static Date LastTimeShownNotificaion = null;
    private static String errorMessage = null;
    private static PendingIntent pendingIntent = null;
    private static ContentResolver contentResolver = null;
    private static String initialMATHML_FILEContent = null;
    AlarmManager manager = null;
    private Boolean active_shown = null;
    private Date activity_started = null;
    private DatabaseLoader databaseLoader = null;
    private String MathMLFolder = null;

    //---------------------------------------------------------------------------------

    TextView textViewAnswer = null;
    TextView textViewQuestion = null;
    WebView webViewResponse = null;
    WebView webViewQuestion = null;
    LinearLayout webViewResponseLayout = null;
    LinearLayout webViewQuestionLayout = null;
    Button btnIKnew = null;
    Button btnShowAnswer = null;
    Button btnIDidnt = null;
    Button btnPass = null;
    Button btnFlag = null;
    ProgressBar progressBar = null;
    TextView textViewStatistics = null;

    //---------------------------------------------------------------------------------

    // A LITTLE BIT OF MVVM HERE
    private void SetupBindings() {
        textViewAnswer = (TextView) findViewById(R.id.textViewAnswer);
        textViewQuestion = (TextView) findViewById(R.id.textViewQuestion);
        webViewResponse = (WebView) findViewById(R.id.webViewResponse);
        webViewQuestion = (WebView) findViewById(R.id.webViewQuestion);
        webViewResponseLayout = (LinearLayout) findViewById(R.id.webViewResponseLayout);
        webViewQuestionLayout = (LinearLayout) findViewById(R.id.webViewQuestionLayout);
        btnIKnew = (Button) findViewById(R.id.btnIKnew);
        btnShowAnswer = (Button) findViewById(R.id.btnShowAnswer);
        btnIDidnt = (Button) findViewById(R.id.btnIDidnt);
        btnPass = (Button) findViewById(R.id.btnPass);
        btnFlag = (Button) findViewById(R.id.btnFlag);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        textViewStatistics = (TextView) findViewById(R.id.textViewStatistics);
        manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    public MainActivity() {
        UroborosTRuntime.setMainActivityReference(this);
    }

    @Override
    public void onBackPressed() {
        // Back pressed event are to be processed here
        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, String[] permissions, int[] result) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, result);
            if (!(requestCode == UroborosTRuntime.PERMISSION_ALL &&
                    UroborosTRuntime.CheckIfEveryEqual(PackageManager.PERMISSION_GRANTED, result))) {
                UroborosTRuntime.userMessage(
                        "Permissions were not granted at request",
                        Arrays.toString(permissions).
                                replace("[", "").replace("]", "").
                                replace("android.permission.", "").trim(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                System.exit(0);
                            }
                        });
                UroborosTRuntime.LOG_FATAL(this.getClass(), new UroborosTRuntime.UroborosTLibUroborosTRuntimeException("Can't aquire permissions"));
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    // Called when you reload the window too
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.activity_main);

            contentResolver = getApplicationContext().getContentResolver();

            if (errorMessage != null && errorMessage.isEmpty())
                errorMessage = null;

            LastTimeShownNotificaion = Calendar.getInstance().getTime();

            UroborosTRuntime.RequestBasicPermissions();

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            int versionCode = BuildConfig.VERSION_CODE;
            String versionName = BuildConfig.VERSION_NAME;

            val text = String.format("%s aka %s",
                    UroborosTRuntime.getApplicationName(this.getApplicationContext()), versionName);

            SetupBindings();

            UroborosTRuntime.ShowToastMessage(text);

            activity_started = Calendar.getInstance().getTime();

            if (!new File(UroborosTRuntime.combinePaths(true, Environment.getExternalStorageDirectory().getAbsolutePath(), "NLB/dics")).exists())
                new File(UroborosTRuntime.combinePaths(true, Environment.getExternalStorageDirectory().getAbsolutePath(), "NLB/dics")).mkdir();

            startAlarm();
            preLoadWebViewsForMathML();

            if (databaseLoader != null && databaseLoader.isLoaded()) {
                restartAndActivateUI(errorMessage);
            } else {
                if (StringUtils.isNotEmpty(UroborosTRuntime.getFileSetting(this, "PREV_DATABASE"))) {
                    LoadAndDisplay(UroborosTRuntime.getFileSetting(this, "PREV_DATABASE"));
                } else {
                    restartAndDisableUI(errorMessage);
                }
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void onFocusingQuestionCurrentViewClick(View v) {
        try {
            if (v.getId() == R.id.webViewQuestion || v.getId() == R.id.textViewQuestion) {
                if (!DatabaseLoader.isLoaded())
                    return;

                if (btnIKnew.isShown() || btnShowAnswer.isShown()) {
                    UroborosTRuntime.userInputRequest("Please enter your title",
                            DatabaseLoader.getAnswer().term,
                            new UroborosTRuntime.userInputRequestCallback() {
                                @Override
                                public void callback() {
                                    if (DatabaseLoader.ifKeyAlreadyExists(UroborosTRuntime.userInputRequestLastResult)) {
                                        UroborosTRuntime.userQuestion("Term already exists",
                                                "Do you wish to overwrite the existing term?",
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        setCurrentTermTitle(UroborosTRuntime.userInputRequestLastResult);
                                                    }
                                                },
                                                null);
                                    } else {
                                        setCurrentTermTitle(UroborosTRuntime.userInputRequestLastResult);
                                    }
                                }
                            });
                }
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void setCurrentTermTitle(String newTitle) {
        DatabaseLoader.setCurrentTermTitle(newTitle);

        if (DatabaseLoader.getAnswer().isMathML()) {
            InjectTextView2WebViewSwitch(DatabaseLoader.getAnswer(), false);
        } else {
            textViewQuestion.setText(newTitle);
        }
    }

    public void onFocusingAnswerCurrentViewClick(View v) {
        try {
            if (v.getId() == R.id.webViewResponse || v.getId() == R.id.textViewAnswer) {
                if (!DatabaseLoader.isLoaded())
                    return;

                if (btnIKnew.isShown()) {
                    UroborosTRuntime.userInputRequest("Please enter your definition",
                            DatabaseLoader.getAnswer().definition,
                            new UroborosTRuntime.userInputRequestCallback() {
                                @Override
                                public void callback() {
                                    DatabaseLoader.setCurrentTermMeaning(UroborosTRuntime.userInputRequestLastResult);

                                    if (DatabaseLoader.getAnswer().isMathML()) {
                                        InjectTextView2WebViewSwitch(DatabaseLoader.getAnswer(), true);
                                    } else {
                                        textViewAnswer.setText(UroborosTRuntime.userInputRequestLastResult);
                                    }
                                }
                            });
                }
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void onBtnFlagClicked(View v) {
        try {
            if (v.getId() == R.id.btnFlag) {
                ProcessPositiveOrFlagAnswers(Response.FLAG);
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void onBtnShowAnswerClicked(View v) {
        try {
            if (v.getId() == R.id.btnShowAnswer) {
                btnIKnew.setVisibility(View.VISIBLE);
                btnIDidnt.setVisibility(View.VISIBLE);
                btnShowAnswer.setVisibility(View.GONE);

                btnPass.setEnabled(false);

                InjectTextView2WebViewSwitch(databaseLoader.getAnswer(), true);
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void onBtnIKnewClicked(View v) {
        try {
            if (v.getId() == R.id.btnIKnew) {
                ProcessPositiveOrFlagAnswers(Response.YES);
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    private void ProcessPositiveOrFlagAnswers(final Response yes) {
        BackToTextViews();

        btnIKnew.setVisibility(View.GONE);
        btnIDidnt.setVisibility(View.GONE);
        btnShowAnswer.setVisibility(View.VISIBLE);

        btnPass.setEnabled(true);

        try {
            databaseLoader.setResponse(yes);
        } catch (Exception ex) {
            errorMessage = ex.getClass().getName() + " " + ex.getMessage();
            UroborosTRuntime.LOG_REGULAR(this.getClass(), ex);
        }

        if (databaseLoader.isLoaded()) {

            InjectTextView2WebViewSwitch(databaseLoader.getNextTerm(), false);

            if (textViewQuestion.getText().equals("Internal error occured")) {
                textViewAnswer.setText("An abnormal workflow was produced by the application, the database is empty and the getNextTerm() was called");
            } else {
                textViewAnswer.setText("Your guess?");
            }
        } else {
            textViewQuestion.setText("You seem to learn everything from this database, please load another one or re-process this one");
            textViewAnswer.setText("You seem to learn everything from this database, please load another one or re-process this one");

            btnShowAnswer.setEnabled(false);
            btnPass.setEnabled(false);
            btnFlag.setEnabled(false);
        }

        textViewStatistics.setText(errorMessage != null ? errorMessage : databaseLoader.getActualStats());
        errorMessage = null;
    }

    public void onBtnIDidntClicked(View v) {
        try {
            if (v.getId() == R.id.btnIDidnt) {
                ProcessNegativeAnswers(Response.NO);
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    private void ProcessNegativeAnswers(Response no) {
        BackToTextViews();

        btnIKnew.setVisibility(View.GONE);
        btnIDidnt.setVisibility(View.GONE);
        btnShowAnswer.setVisibility(View.VISIBLE);

        btnPass.setEnabled(true);

        try {
            databaseLoader.setResponse(no);
        } catch (Exception ex) {
            errorMessage = ex.getClass().getName() + " " + ex.getMessage();
            UroborosTRuntime.LOG_REGULAR(this.getClass(), ex);
        }

        InjectTextView2WebViewSwitch(databaseLoader.getNextTerm(), false);

        textViewStatistics.setText(errorMessage != null ? errorMessage : databaseLoader.getActualStats());
        errorMessage = null;
    }

    public void onBtnPassClicked(View v) {
        try {
            if (v.getId() == R.id.btnPass) {
                ProcessNegativeAnswers(Response.PASS);
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void onBtnLoadDatabaseClicked(View v) {
        try {
            if (v.getId() == R.id.btnLoadDatabase) {
                Intent getContentIntent = com.ipaulpro.afilechooser.utils.FileUtils.createGetContentIntent();

                Intent intent = Intent.createChooser(getContentIntent, "Select a database file");
                startActivityForResult(intent, UroborosTRuntime.FILE_CHOOSER);
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    public void LoadAndDisplay(String path) {
        errorMessage = null;

        try {
            databaseLoader = DatabaseLoader.GetDatabaseLoader(path);

            UroborosTRuntime.saveSetting(this, UroborosTRuntime.PREV_DATABASE, DatabaseLoader.getFilename());
        } catch (Exception ex) {
            UroborosTRuntime.saveSetting(this, UroborosTRuntime.PREV_DATABASE, null);
            errorMessage = ex.getMessage();
            UroborosTRuntime.LOG_REGULAR(this.getClass(), ex);

            restartAndDisableUI(null);
            return;
        }

        if (errorMessage == null || errorMessage.isEmpty())
            restartAndActivateUI(errorMessage);
        else {
            restartAndDisableUI(errorMessage);
        }
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();

            StartupCode.activityResumed();

            val text = String.format("Running since %s", activity_started);
            UroborosTRuntime.ShowToastMessage(text);

            active_shown = true;
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    @Override
    protected void onPause() {
        try {
            super.onPause();

            StartupCode.activityPaused();

            active_shown = false;
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    // The retrieval of the file path value happens in this function
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            switch (requestCode) {
                case UroborosTRuntime.FILE_CHOOSER:
                    if (resultCode == RESULT_OK) {
                        Uri uri = data.getData();

                        try {
                            String path = com.ipaulpro.afilechooser.utils.FileUtils.getPath(this, uri); // Doesn't work with Huawei - Android 8.0
                            LoadAndDisplay(path);
                        } catch (Exception ex) {
                            LoadAndDisplay(uri.toString());
                        }
                    }
                    break;
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }

    // We presume here that database was loaded and checked before
    private void restartAndActivateUI(final String statisticsMessage) {
        BackToTextViews();

        btnIKnew.setVisibility(View.GONE);
        btnIDidnt.setVisibility(View.GONE);
        btnShowAnswer.setVisibility(View.VISIBLE);

        btnShowAnswer.setEnabled(true);
        btnPass.setEnabled(true);
        btnFlag.setEnabled(true);

        InjectTextView2WebViewSwitch(databaseLoader.getNextTerm(), false);

        if (statisticsMessage != null && !statisticsMessage.isEmpty())
            textViewStatistics.setText(statisticsMessage);
        else
            textViewStatistics.setText(databaseLoader.getActualStats());
    }

    private void InjectTextView2WebViewSwitch(DatabaseLoader.TermData termData, boolean isAnswer) {
        textViewAnswer.setText("Your guess?");

        try {
            if (isAnswer) {
                if (termData.isMathML()) {
                    alterWebViewAndContainingLayoutVisibility(webViewResponse, webViewResponseLayout, View.VISIBLE);

                    textViewAnswer.setVisibility(View.GONE);

                    ShowMathML(termData.definition, webViewResponse);
                } else {
                    alterWebViewAndContainingLayoutVisibility(webViewResponse, webViewResponseLayout, View.GONE);

                    textViewAnswer.setVisibility(View.VISIBLE);

                    textViewAnswer.setText(termData.definition);
                }

            } else {
                if (termData.isMathML()) {
                    alterWebViewAndContainingLayoutVisibility(webViewQuestion, webViewQuestionLayout, View.VISIBLE);

                    textViewQuestion.setVisibility(View.GONE);

                    ShowMathML(termData.term, webViewQuestion);
                } else {
                    alterWebViewAndContainingLayoutVisibility(webViewQuestion, webViewQuestionLayout, View.GONE);

                    textViewQuestion.setVisibility(View.VISIBLE);

                    textViewQuestion.setText(termData.term);
                }
            }
        } catch (Exception ex) {
            BackToTextViews();

            textViewQuestion.setText(ex.getClass().toString());
            textViewAnswer.setText(String.format("%s %s", ex.getClass().getName(), ex.getMessage()));

            UroborosTRuntime.LOG_REGULAR(this.getClass(), ex);
        }
    }

    private void alterWebViewAndContainingLayoutVisibility(WebView webView, LinearLayout webViewLayout, int VisibilitySetting) {
        webView.setVisibility(VisibilitySetting);
        webViewLayout.setVisibility(VisibilitySetting);
    }

    private void BackToTextViews() {
        alterWebViewAndContainingLayoutVisibility(webViewQuestion, webViewQuestionLayout, View.GONE);
        alterWebViewAndContainingLayoutVisibility(webViewResponse, webViewResponseLayout, View.GONE);

        textViewQuestion.setVisibility(View.VISIBLE);
        textViewAnswer.setVisibility(View.VISIBLE);
    }

    private void restartAndDisableUI(final String statisticsMessage) {
        BackToTextViews();

        btnIKnew.setVisibility(View.GONE);
        btnIDidnt.setVisibility(View.GONE);
        btnShowAnswer.setVisibility(View.VISIBLE);

        btnShowAnswer.setEnabled(false);
        btnPass.setEnabled(false);
        btnFlag.setEnabled(false);

        if (statisticsMessage != null && !statisticsMessage.isEmpty())
            textViewQuestion.setText(statisticsMessage);
        else
            textViewQuestion.setText("Please press on Load Database to load a set of terms");

        if (statisticsMessage != null && !statisticsMessage.isEmpty())
            textViewAnswer.setText(statisticsMessage);
        else
            textViewAnswer.setText("Please press on Load Database to load a set of terms");

        if (statisticsMessage != null && !statisticsMessage.isEmpty())
            textViewStatistics.setText(statisticsMessage);
        else
            textViewStatistics.setText("Please press on Load Database to load a set of terms");
    }

    private void ShowMathML(final String data, WebView webView) throws IOException {
        progressBar.setVisibility(View.VISIBLE);
        setupMathMLPage(UroborosTRuntime.MATH_ML_INDICE, data);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    progressBar.setVisibility(View.GONE);
                } catch (Exception ex) {
                    UroborosTRuntime.userMessage(
                            "An error has occurred",
                            errorMessage = ex.getClass().getName() + " " + ex.getMessage(),
                            null);
                    UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
                }
            }
        });

        webView.loadUrl(UroborosTRuntime.combinePathsAsURI(false, MathMLFolder, "math.html"));

        System.gc();
    }

    private void setupMathMLPage(final String indice, final String data) throws IOException {
        String math_ml_doc;

        BufferedReader br = new BufferedReader(new FileReader(MathMLFolder + "/math.html"));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            if (initialMATHML_FILEContent == null) // We need that to be set only once
                initialMATHML_FILEContent = sb.toString();

            math_ml_doc = initialMATHML_FILEContent.replace(indice, data);
        } finally {
            br.close();
        }

        try (PrintWriter out = new PrintWriter(MathMLFolder + "/math.html")) {
            out.println(math_ml_doc);
        }
    }

    private void preLoadWebViewsForMathML() {
        webViewQuestion.getSettings().setDisplayZoomControls(false);
        webViewResponse.getSettings().setDisplayZoomControls(false);

        webViewQuestion.getSettings().setJavaScriptEnabled(true);
        webViewResponse.getSettings().setJavaScriptEnabled(true);

        webViewQuestion.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webViewResponse.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

        webViewQuestion.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webViewResponse.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        MathMLFolder = UroborosTRuntime.copyAssets(this, "mathweb", "NLB/mathweb");

        webViewQuestion.loadUrl(UroborosTRuntime.combinePathsAsURI(false, MathMLFolder, "math.html"));
        webViewResponse.loadUrl(UroborosTRuntime.combinePathsAsURI(false, MathMLFolder, "math.html"));
    }

    private void startAlarm() {
        /* Retrieve a PendingIntent that will perform a broadcast */
        Intent alarmIntent = new Intent(UroborosTRuntime.OPEN_ME_INTENT);
        pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, alarmIntent, 0);

        UroborosTRuntime.forceTheNonkill(this, NLBAlarm.class);

        int interval = UroborosTRuntime.NOTIFICATION_PERIOD;

        manager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, pendingIntent);

        if ((PendingIntent.getBroadcast(this, 0,
                new Intent(UroborosTRuntime.OPEN_ME_INTENT),
                PendingIntent.FLAG_NO_CREATE) != null)) {
            UroborosTRuntime.LOG_MESSAGE(this.getClass(), "Alarm was successfully created");
        }
    }
}
