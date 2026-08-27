package com.falsetech.locktext;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

public class ProtectedNotificationListener extends NotificationListenerService {
    private static final String MESSAGES_PACKAGE = "com.google.android.apps.messaging";
    private static final String CHANNEL_ID = "locktext_protected_messages";

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) {
            for (StatusBarNotification sbn : active) guard(sbn);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        guard(sbn);
    }

    private void guard(StatusBarNotification sbn) {
        if (sbn == null || !MESSAGES_PACKAGE.equals(sbn.getPackageName())) return;
        Notification original = sbn.getNotification();
        if (original == null) return;

        List<String> identityCandidates = identityCandidates(original);
        TargetMatcher.Match match = TargetMatcher.bestMatch(Store.all(this), identityCandidates);
        if (match == null) return;

        cancelNotification(sbn.getKey());
        postProtectedReplacement(original, match.target);
    }

    private List<String> identityCandidates(Notification notification) {
        ArrayList<String> result = new ArrayList<>();
        Bundle extras = notification.extras;
        if (extras == null) return result;

        add(result, extras.getCharSequence(Notification.EXTRA_TITLE));
        add(result, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(result, extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));

        ArrayList<Person> people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST);
        if (people != null) {
            for (Person person : people) {
                if (person == null) continue;
                add(result, person.getName());
                if (person.getUri() != null) add(result, person.getUri());
            }
        }

        Parcelable[] rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (rawMessages != null) {
            try {
                List<Notification.MessagingStyle.Message> messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages);
                for (Notification.MessagingStyle.Message message : messages) {
                    Person sender = message.getSenderPerson();
                    if (sender != null) {
                        add(result, sender.getName());
                        if (sender.getUri() != null) add(result, sender.getUri());
                    } else {
                        add(result, message.getSender());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private void postProtectedReplacement(Notification original, ProtectedTarget target) {
        ensureChannel();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        CharSequence originalTitle = original.extras == null ? null : original.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence originalText = original.extras == null ? null : original.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (originalText == null && original.extras != null) originalText = original.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = originalTitle == null || originalTitle.length() == 0
                ? target.displayName()
                : originalTitle.toString();
        String text = originalText == null || originalText.length() == 0
                ? "Protected message — open Google Messages to reply."
                : originalText.toString();

        PendingIntent content = original.contentIntent;
        if (content == null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(MESSAGES_PACKAGE);
            if (launch != null) {
                content = PendingIntent.getActivity(
                        this,
                        Math.abs(target.key.hashCode()),
                        launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false);
        if (content != null) builder.setContentIntent(content);

        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String tag = "locktext:" + target.key;
        int id = 4000 + Math.abs(target.key.hashCode() % 100000);
        nm.notify(tag, id, builder.build());
    }

    private void ensureChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Protected Messages",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Google Messages notifications republished by LockText without Quick Reply controls.");
        nm.createNotificationChannel(channel);
    }

    private void add(List<String> out, CharSequence value) {
        if (value == null) return;
        String s = value.toString().trim();
        if (s.length() >= 2 && s.length() <= 180 && !out.contains(s)) out.add(s);
    }
}
