package no.neste.coach;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Self-rescheduling daily reminders, restored after device reboot. */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String PREFS = "neste_reminders";
    private static final String CHANNEL = "gentle_prompts";
    private static final String ACTION = "no.neste.coach.REMIND";
    private static final String[] KINDS = {"morning", "home", "evening"};
    private static final int[] IDS = {111, 112, 113};

    static boolean isKnownKind(String kind) {
        if (kind == null) return false;
        for (String item : KINDS) if (item.equals(kind)) return true;
        return false;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            for (String kind : KINDS) reschedule(context, kind);
            return;
        }
        if (!ACTION.equals(action)) return;
        String kind = intent.getStringExtra("kind");
        if (!isKnownKind(kind)) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(kind + "_on", false)) return;
        showNotification(context, kind);
        reschedule(context, kind);
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL, "Små, daglige påminnelser", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Korte, personlige dytt fra NESTE. Kan slås av når som helst.");
                manager.createNotificationChannel(channel);
            }
        }
    }

    static void setDailyAdvice(Context context, String day, String title, String body) {
        // Prevent a lock-screen notification from inadvertently exposing long notes.
        if(day==null||!day.matches("\\d{4}-\\d{2}-\\d{2}"))return;
        if(title==null||body==null)return;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("advice_title_"+day, title.substring(0,Math.min(95,title.length())))
            .putString("advice_body_"+day, body.substring(0,Math.min(180,body.length()))).apply();
    }

    static void setReminder(Context context, String kind, int hour, int minute, boolean enabled) {
        if (!isKnownKind(kind) || hour < 0 || hour > 23 || minute < 0 || minute > 59) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(kind + "_hour", hour).putInt(kind + "_minute", minute)
            .putBoolean(kind + "_on", enabled).apply();
        reschedule(context, kind);
    }

    private static PendingIntent makeIntent(Context context, String kind) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION).putExtra("kind", kind);
        int id = kind.equals("morning") ? IDS[0] : kind.equals("home") ? IDS[1] : IDS[2];
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void reschedule(Context context, String kind) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        PendingIntent pi = makeIntent(context, kind);
        alarm.cancel(pi);
        if (!prefs.getBoolean(kind + "_on", false)) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, prefs.getInt(kind + "_hour", kind.equals("morning") ? 8 : 17));
        next.set(Calendar.MINUTE, prefs.getInt(kind + "_minute", 0));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DATE, 1);
        // Inexact by design; no special exact-alarm permission required.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
    }

    private static void showNotification(Context context, String kind) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannel(context);
        String title;
        String body;
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        if (kind.equals("morning")) {
            String date = new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(Calendar.getInstance().getTime());
            SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String planned=p.getString("advice_title_"+date, null);
            title = "NESTE · Dagens første steg";
            body = planned != null ? planned :
                (day == Calendar.SATURDAY || day == Calendar.SUNDAY)
                ? "Se etter én liten ting du kan ta initiativ til hjemme i dag. Åpne NESTE for dagens råd."
                : "Velg én oppgave du ellers ville utsatt. Begynn med det første lille steget.";
        } else if (kind.equals("home")) {
            title = "NESTE · Et lite initiativ";
            body = "Kan du gjøre én ting som gjør ettermiddagen lettere hjemme? Legg gjerne fra deg telefonen.";
        } else {
            title = "NESTE · Hva hjalp i dag?";
            body = "Et lite tilbakeblikk er nok. Du trenger ikke ha gjort alt.";
        }
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 40, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
            .setSmallIcon(no.neste.coach.R.drawable.ic_stat_n)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build();
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null) manager.notify(kind.equals("morning")?IDS[0]:kind.equals("home")?IDS[1]:IDS[2], notification);
    }
}
