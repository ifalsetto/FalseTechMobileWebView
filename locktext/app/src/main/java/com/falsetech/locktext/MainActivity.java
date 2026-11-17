package com.falsetech.locktext;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CONTACTS = 100;
    private static final int REQ_PICK_CONTACT = 101;
    private static final int REQ_NOTIFICATIONS = 102;

    private LinearLayout root;
    private TextView status;
    private LinearLayout targetList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(244, 246, 250));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("LockText 1.1 — Jess", 26, true);
        root.addView(title);

        TextView intro = text(
                "Local-only outbound protection for Google Messages. No INTERNET permission, no analytics, no cloud.\n\n" +
                        "Level 1: confirm.  Level 2: biometric/device authentication.  Level 3: hard lock.",
                15, false);
        intro.setPadding(0, dp(8), 0, dp(14));
        root.addView(intro);

        status = text("", 14, true);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, matchWrap());

        root.addView(button("1. Enable Send Protection", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        root.addView(button("2. Enable Notification Guard", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));

        root.addView(button("3. Add Protected Contact", v -> addContact()));
        root.addView(button("4. Add Protected Group Title", v -> addGroupTitle()));
        root.addView(button("Open Google Messages", v -> openMessages()));

        TextView note = text(
                "Notification Guard removes Google Messages' original notification for a protected target and immediately republishes it without a Quick Reply action. Tapping it still opens the original conversation.",
                13, false);
        note.setPadding(0, dp(12), 0, dp(12));
        root.addView(note);

        TextView targetsHeader = text("Protected targets", 20, true);
        targetsHeader.setPadding(0, dp(8), 0, dp(8));
        root.addView(targetsHeader);

        targetList = new LinearLayout(this);
        targetList.setOrientation(LinearLayout.VERTICAL);
        root.addView(targetList, matchWrap());

        setContentView(scroll);
    }

    private void refresh() {
        boolean accessibility = isAccessibilityEnabled();
        boolean listener = isNotificationListenerEnabled();
        boolean notifications = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        String text = "SEND PROTECTION: " + (accessibility ? "ON" : "OFF")
                + "\nNOTIFICATION GUARD: " + (listener ? "ON" : "OFF")
                + "\nLOCKTEXT NOTIFICATIONS: " + (notifications ? "ALLOWED" : "NOT ALLOWED");
        status.setText(text);
        status.setTextColor((accessibility && listener) ? Color.rgb(15, 110, 55) : Color.rgb(165, 35, 45));
        renderTargets();
    }

    private void renderTargets() {
        targetList.removeAllViews();
        List<ProtectedTarget> targets = Store.all(this);
        if (targets.isEmpty()) {
            TextView empty = text("No contacts or group titles protected yet.", 14, false);
            empty.setPadding(0, dp(8), 0, dp(8));
            targetList.addView(empty);
            return;
        }

        for (ProtectedTarget target : targets) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.setBackgroundColor(Color.WHITE);

            TextView label = text(target.displayName(), 16, true);
            card.addView(label);
            TextView level = text(levelDescription(target.level), 13, false);
            level.setPadding(0, dp(3), 0, dp(6));
            card.addView(level);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button change = button("Change level", v -> chooseExistingLevel(target));
            Button remove = button("Remove", v -> confirmRemove(target));
            actions.addView(change, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            actions.addView(remove, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(actions);

            LinearLayout.LayoutParams lp = matchWrap();
            lp.setMargins(0, 0, 0, dp(10));
            targetList.addView(card, lp);
        }
    }

    private void addContact() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, REQ_PICK_CONTACT);
    }

    private void addGroupTitle() {
        final EditText input = new EditText(this);
        input.setHint("Exact group title shown in Google Messages");
        input.setSingleLine(true);
        int pad = dp(20);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(input, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("Protect a group chat")
                .setMessage("Use this when the group has its own title. Exact group-title protection is the reliable fallback when participant names are not exposed in the conversation header.")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (title.length() < 2) {
                        toast("Enter the group title.");
                    } else {
                        chooseNewLevel(title, "", ProtectedTarget.TYPE_GROUP);
                    }
                })
                .show();
    }

    private void chooseExistingLevel(ProtectedTarget target) {
        String[] options = {"Level 1 — Confirm", "Level 2 — Authenticate", "Level 3 — Hard Lock"};
        new AlertDialog.Builder(this)
                .setTitle(target.displayName())
                .setSingleChoiceItems(options, target.level - 1, (dialog, which) -> {
                    Store.setLevel(this, target.key, which + 1);
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseNewLevel(String name, String number, String type) {
        String[] options = {"Level 1 — Confirm", "Level 2 — Authenticate", "Level 3 — Hard Lock"};
        new AlertDialog.Builder(this)
                .setTitle("Protection level")
                .setItems(options, (dialog, which) -> {
                    if (ProtectedTarget.TYPE_GROUP.equals(type)) {
                        Store.addOrUpdateGroup(this, name, which + 1);
                    } else {
                        Store.addOrUpdateContact(this, name, number, which + 1);
                    }
                    Store.revokeGrant(this);
                    refresh();
                })
                .show();
    }

    private void confirmRemove(ProtectedTarget target) {
        new AlertDialog.Builder(this)
                .setTitle("Remove protection?")
                .setMessage(target.displayName())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    Store.remove(this, target.key);
                    refresh();
                })
                .show();
    }

    private void openMessages() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.messaging");
        if (launch == null) {
            toast("Google Messages is not installed.");
            return;
        }
        startActivity(launch);
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                String pkg = info.getResolveInfo().serviceInfo.packageName;
                String name = info.getResolveInfo().serviceInfo.name;
                if (getPackageName().equals(pkg) && LockAccessibilityService.class.getName().equals(name)) return true;
            }
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        return nm.isNotificationListenerAccessGranted(new ComponentName(this, ProtectedNotificationListener.class));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addContact();
            } else {
                toast("Contacts permission is needed only to pick a protected contact.");
            }
        }
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_CONTACT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        String contactId = null;
        String name = "";
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactId = cursor.getString(0);
                name = cursor.getString(1);
            }
        } catch (Exception e) {
            toast("Could not read that contact.");
            return;
        }

        if (contactId == null) return;
        List<String> numbers = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                new String[]{contactId}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String number = cursor.getString(0);
                    if (number != null && !number.trim().isEmpty() && !numbers.contains(number.trim())) {
                        numbers.add(number.trim());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        final String finalName = name == null ? "" : name;
        if (numbers.isEmpty()) {
            chooseNewLevel(finalName, "", ProtectedTarget.TYPE_CONTACT);
        } else if (numbers.size() == 1) {
            chooseNewLevel(finalName, numbers.get(0), ProtectedTarget.TYPE_CONTACT);
        } else {
            String[] items = numbers.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Choose number for " + finalName)
                    .setItems(items, (dialog, which) -> chooseNewLevel(finalName, items[which], ProtectedTarget.TYPE_CONTACT))
                    .show();
        }
    }

    private String levelDescription(int level) {
        if (level == 1) return "Level 1 — deliberate confirmation + 5-second one-send window";
        if (level == 2) return "Level 2 — biometric/device authentication + 5-second one-send window";
        return "Level 3 — hard lock; sending stays blocked until the level is changed";
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(25, 29, 36));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
