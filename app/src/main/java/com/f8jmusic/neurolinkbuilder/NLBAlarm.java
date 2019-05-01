package com.f8jmusic.neurolinkbuilder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import com.f8jmusic.uroborostlib.UroborosTRuntime;

import org.apache.commons.lang3.StringUtils;

import java.util.Calendar;
import java.util.Date;

public class NLBAlarm extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent alarm_intent) {
        // android.os.Debug.waitForDebugger();
        if (StartupCode.isActivityVisible()) {
            return;
        }

        try {
            if (alarm_intent.getAction().equals(UroborosTRuntime.OPEN_ME_INTENT)) {
                Date NewlyShownNotificaionTime = Calendar.getInstance().getTime();
                String welcomeNotification = String.format("Your %s practice waits for ya :)", MainActivity.currentDictionaryName);
                if (MainActivity.LastTimeShownNotificaion == null) {
                    UroborosTRuntime.ShowToastMessage(String.format("First start of the timer"));

                    if (UroborosTRuntime.DEBUG_EXTENDED_LOGGING_MODE) {
                        UroborosTRuntime.LOG_MESSAGE(this.getClass(), "First boot : " + welcomeNotification);
                    }
                } else {
                    long difference = (new Date(NewlyShownNotificaionTime.getTime() - MainActivity.LastTimeShownNotificaion.getTime()).getTime() / 1000);
                    UroborosTRuntime.ShowToastMessage(String.format("Previous time was %s seconds back", difference));

                    if (UroborosTRuntime.DEBUG_EXTENDED_LOGGING_MODE) {
                        UroborosTRuntime.LOG_MESSAGE(this.getClass(), Long.toString(difference) + " sec : " + welcomeNotification);
                    }
                }

                MainActivity.LastTimeShownNotificaion = NewlyShownNotificaionTime;

                Intent intent = new Intent(context.getApplicationContext(), MainActivity.class); // Here pass your activity where you want to redirect.

                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);

                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                Notification notify;

                notify = new Notification.Builder(context).
                        setContentTitle("Open me").setContentText(
                        !StringUtils.isBlank(MainActivity.currentDictionaryName) ?
                                String.format("Your %s practice waits for ya :)", MainActivity.currentDictionaryName) : "Your practice waits for ya :)").
                        setSmallIcon(R.drawable.ic_fitness).setContentIntent(contentIntent).build();

                notify.flags = Notification.DEFAULT_LIGHTS | Notification.FLAG_AUTO_CANCEL;
                notificationManager.notify(0, notify);

                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(context.getApplicationContext(), notification);
                r.play();
            } else if (alarm_intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                Intent alarmIntent = new Intent(UroborosTRuntime.OPEN_ME_INTENT);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);

                AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                int interval = UroborosTRuntime.NOTIFICATION_PERIOD;
                manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + UroborosTRuntime.NOTIFICATION_PERIOD, interval, pendingIntent);

                UroborosTRuntime.ShowToastMessage("NeuroLinkBuilder alarm set");
            }
        } catch (Exception ex) {
            UroborosTRuntime.LOG_FATAL(this.getClass(), ex);
        }
    }
}