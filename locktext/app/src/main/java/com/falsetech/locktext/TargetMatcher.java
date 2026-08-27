package com.falsetech.locktext;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TargetMatcher {
    private TargetMatcher() {}

    public static final class Match {
        public final ProtectedTarget target;
        public final int score;
        public final String evidence;

        Match(ProtectedTarget target, int score, String evidence) {
            this.target = target;
            this.score = score;
            this.evidence = evidence;
        }
    }

    public static Match bestMatch(List<ProtectedTarget> targets, List<String> candidates) {
        Match best = null;
        for (ProtectedTarget target : targets) {
            for (String candidate : candidates) {
                int score = score(target, candidate);
                if (score <= 0) continue;
                if (best == null || score > best.score) {
                    best = new Match(target, score, candidate);
                }
            }
        }
        return best;
    }

    public static List<String> headerCandidates(AccessibilityNodeInfo root, Rect sendBounds, int densityDpi) {
        Set<String> out = new LinkedHashSet<>();
        if (root == null || sendBounds == null) return new ArrayList<>();

        float density = Math.max(1f, densityDpi / 160f);
        int cap = Math.round(280f * density);
        int floor = Math.round(120f * density);
        int proportional = Math.max(floor, sendBounds.top / 3);
        int headerBottom = Math.min(cap, proportional);
        collectHeader(root, headerBottom, out);
        return new ArrayList<>(out);
    }

    private static void collectHeader(AccessibilityNodeInfo node, int headerBottom, Set<String> out) {
        if (node == null) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!r.isEmpty() && r.top >= 0 && r.bottom <= headerBottom) {
            add(out, node.getText());
            add(out, node.getContentDescription());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectHeader(child, headerBottom, out);
        }
    }

    private static void add(Set<String> out, CharSequence value) {
        if (value == null) return;
        String s = value.toString().trim();
        if (s.length() < 2 || s.length() > 160) return;
        out.add(s);
    }

    private static int score(ProtectedTarget target, String candidate) {
        if (target == null || TextUtils.isEmpty(candidate)) return 0;
        String candidateWords = Store.normalizeWords(candidate);
        String candidateCompact = Store.compact(candidate);
        if (candidateWords.isEmpty()) return 0;

        if (ProtectedTarget.TYPE_GROUP.equals(target.type)) {
            String group = Store.normalizeWords(target.name);
            if (group.length() < 2) return 0;
            if (candidateWords.equals(group)) return 1200;
            if (containsWholePhrase(candidateWords, group) && candidateWords.length() <= group.length() + 36) return 1050;
            return 0;
        }

        String number = Store.digits(target.number);
        String candidateDigits = Store.digits(candidate);
        if (number.length() >= 7 && candidateDigits.length() >= 7) {
            String last = number.substring(Math.max(0, number.length() - 10));
            String candLast = candidateDigits.substring(Math.max(0, candidateDigits.length() - 10));
            if (last.equals(candLast)) return 1300;
            String last7 = number.substring(number.length() - 7);
            if (candidateDigits.endsWith(last7)) return 1225;
        }

        String name = Store.normalizeWords(target.name);
        String nameCompact = Store.compact(target.name);
        if (nameCompact.length() < 3) return 0;
        if (candidateWords.equals(name)) return 1150;
        if (candidateCompact.equals(nameCompact)) return 1140;
        if (containsWholePhrase(candidateWords, name) && candidateWords.length() <= name.length() + 48) return 1000;
        return 0;
    }

    private static boolean containsWholePhrase(String haystack, String needle) {
        if (needle.isEmpty()) return false;
        String h = " " + haystack + " ";
        String n = " " + needle + " ";
        return h.contains(n);
    }
}
