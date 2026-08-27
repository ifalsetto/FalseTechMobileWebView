package com.falsetech.locktext;

import org.json.JSONException;
import org.json.JSONObject;

public final class ProtectedTarget {
    public static final String TYPE_CONTACT = "contact";
    public static final String TYPE_GROUP = "group";

    public final String key;
    public final String name;
    public final String number;
    public final int level;
    public final String type;

    public ProtectedTarget(String key, String name, String number, int level, String type) {
        this.key = key == null ? "" : key;
        this.name = name == null ? "" : name.trim();
        this.number = number == null ? "" : number.trim();
        this.level = Math.max(1, Math.min(3, level));
        this.type = TYPE_GROUP.equals(type) ? TYPE_GROUP : TYPE_CONTACT;
    }

    public ProtectedTarget withLevel(int newLevel) {
        return new ProtectedTarget(key, name, number, newLevel, type);
    }

    public String displayName() {
        if (TYPE_GROUP.equals(type)) return name + " (group title)";
        if (number.isEmpty()) return name;
        return name.isEmpty() ? number : name + "  •  " + number;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("key", key)
                .put("name", name)
                .put("number", number)
                .put("level", level)
                .put("type", type);
    }

    public static ProtectedTarget fromJson(JSONObject object) throws JSONException {
        return new ProtectedTarget(
                object.optString("key", ""),
                object.optString("name", ""),
                object.optString("number", ""),
                object.optInt("level", 1),
                object.optString("type", TYPE_CONTACT)
        );
    }
}
