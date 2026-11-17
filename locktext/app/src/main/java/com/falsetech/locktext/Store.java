package com.falsetech.locktext;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Store {
    private static final String PREF = "locktext_v11";
    private static final String TARGETS = "targets";
    private static final String ACTIVE_GRANT_KEY = "active_grant_key";
    private static final String ACTIVE_GRANT_UNTIL = "active_grant_until";
    public static final long GRANT_MS = 5000L;

    private Store() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized List<ProtectedTarget> all(Context context) {
        String raw = prefs(context).getString(TARGETS, "[]");
        List<ProtectedTarget> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                ProtectedTarget target = ProtectedTarget.fromJson(array.getJSONObject(i));
                if (!target.key.isEmpty() && (!target.name.isEmpty() || !target.number.isEmpty())) {
                    result.add(target);
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(result, Comparator.comparing(t -> t.displayName().toLowerCase(Locale.ROOT)));
        return result;
    }

    public static synchronized void save(Context context, List<ProtectedTarget> targets) {
        JSONArray array = new JSONArray();
        for (ProtectedTarget target : targets) {
            try {
                array.put(target.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit().putString(TARGETS, array.toString()).apply();
    }

    public static synchronized ProtectedTarget addOrUpdateContact(Context context, String name, String number, int level) {
        List<ProtectedTarget> list = new ArrayList<>(all(context));
        String normalizedNumber = digits(number);
        String normalizedName = normalizeWords(name);
        for (int i = 0; i < list.size(); i++) {
            ProtectedTarget existing = list.get(i);
            if (!ProtectedTarget.TYPE_CONTACT.equals(existing.type)) continue;
            boolean sameNumber = !normalizedNumber.isEmpty() && normalizedNumber.equals(digits(existing.number));
            boolean sameNameOnly = normalizedNumber.isEmpty()
                    && !normalizedName.isEmpty()
                    && normalizedName.equals(normalizeWords(existing.name));
            if (sameNumber || sameNameOnly) {
                ProtectedTarget updated = new ProtectedTarget(existing.key, name, number, level, ProtectedTarget.TYPE_CONTACT);
                list.set(i, updated);
                save(context, list);
                return updated;
            }
        }
        ProtectedTarget created = new ProtectedTarget(UUID.randomUUID().toString(), name, number, level, ProtectedTarget.TYPE_CONTACT);
        list.add(created);
        save(context, list);
        return created;
    }

    public static synchronized ProtectedTarget addOrUpdateGroup(Context context, String title, int level) {
        List<ProtectedTarget> list = new ArrayList<>(all(context));
        String normalized = normalizeWords(title);
        for (int i = 0; i < list.size(); i++) {
            ProtectedTarget existing = list.get(i);
            if (ProtectedTarget.TYPE_GROUP.equals(existing.type)
                    && normalized.equals(normalizeWords(existing.name))) {
                ProtectedTarget updated = new ProtectedTarget(existing.key, title, "", level, ProtectedTarget.TYPE_GROUP);
                list.set(i, updated);
                save(context, list);
                return updated;
            }
        }
        ProtectedTarget created = new ProtectedTarget(UUID.randomUUID().toString(), title, "", level, ProtectedTarget.TYPE_GROUP);
        list.add(created);
        save(context, list);
        return created;
    }

    public static synchronized void setLevel(Context context, String key, int level) {
        List<ProtectedTarget> list = new ArrayList<>(all(context));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key.equals(key)) {
                list.set(i, list.get(i).withLevel(level));
                break;
            }
        }
        save(context, list);
        revokeGrant(context);
    }

    public static synchronized void remove(Context context, String key) {
        List<ProtectedTarget> list = new ArrayList<>(all(context));
        list.removeIf(target -> target.key.equals(key));
        save(context, list);
        String active = activeGrantKey(context);
        if (key.equals(active)) revokeGrant(context);
    }

    public static synchronized ProtectedTarget byKey(Context context, String key) {
        for (ProtectedTarget target : all(context)) {
            if (target.key.equals(key)) return target;
        }
        return null;
    }

    public static synchronized void grant(Context context, String key) {
        long until = System.currentTimeMillis() + GRANT_MS;
        prefs(context).edit()
                .putString(ACTIVE_GRANT_KEY, key)
                .putLong(ACTIVE_GRANT_UNTIL, until)
                .apply();
    }

    public static synchronized boolean granted(Context context, String key) {
        cleanupGrant(context);
        return key != null && key.equals(prefs(context).getString(ACTIVE_GRANT_KEY, ""));
    }

    public static synchronized String activeGrantKey(Context context) {
        cleanupGrant(context);
        return prefs(context).getString(ACTIVE_GRANT_KEY, "");
    }

    public static synchronized void consumeGrant(Context context) {
        revokeGrant(context);
    }

    public static synchronized void revokeGrant(Context context) {
        prefs(context).edit()
                .remove(ACTIVE_GRANT_KEY)
                .remove(ACTIVE_GRANT_UNTIL)
                .apply();
    }

    private static void cleanupGrant(Context context) {
        long until = prefs(context).getLong(ACTIVE_GRANT_UNTIL, 0L);
        if (until <= System.currentTimeMillis()) revokeGrant(context);
    }

    public static String normalizeWords(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static String compact(String value) {
        return normalizeWords(value).replace(" ", "");
    }

    public static String digits(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9]", "");
    }
}
