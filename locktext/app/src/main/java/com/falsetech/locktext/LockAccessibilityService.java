package com.falsetech.locktext;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

public class LockAccessibilityService extends AccessibilityService {
    private static final String MESSAGES_PACKAGE = "com.google.android.apps.messaging";

    private Button overlay;
    private WindowManager windowManager;
    private String activeKey = "";
    private Rect activeBounds = new Rect();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        inspect();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence packageName = event.getPackageName();
        if (packageName == null || !MESSAGES_PACKAGE.contentEquals(packageName)) {
            removeOverlay();
            return;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            String grantKey = Store.activeGrantKey(this);
            if (!grantKey.isEmpty() && looksLikeSend(event.getSource())) {
                Store.consumeGrant(this);
            }
        }
        inspect();
    }

    @Override
    public void onInterrupt() {
        removeOverlay();
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    private void inspect() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            removeOverlay();
            return;
        }

        Rect send = findSendBounds(root);
        if (send == null || send.width() < dp(20) || send.height() < dp(20)) {
            removeOverlay();
            return;
        }

        List<String> header = TargetMatcher.headerCandidates(
                root,
                send,
                getResources().getDisplayMetrics().densityDpi);
        TargetMatcher.Match match = TargetMatcher.bestMatch(Store.all(this), header);
        if (match == null) {
            removeOverlay();
            return;
        }

        if (Store.granted(this, match.target.key)) {
            removeOverlay();
            return;
        }

        showOverlay(send, match.target);
    }

    private Rect findSendBounds(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        collectSendCandidates(root, candidates);
        if (candidates.isEmpty()) return null;

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        AccessibilityNodeInfo best = null;
        long bestScore = Long.MIN_VALUE;
        for (AccessibilityNodeInfo node : candidates) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.isEmpty() || r.centerY() < screenHeight * 0.45f) continue;
            long score = ((long) r.centerY() * 100000L) + r.centerX();
            if (score > bestScore) {
                best = node;
                bestScore = score;
            }
        }
        if (best == null) return null;
        Rect out = new Rect();
        best.getBoundsInScreen(out);
        return out;
    }

    private void collectSendCandidates(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        if (node.isClickable() && looksLikeSend(node)) out.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectSendCandidates(child, out);
        }
    }

    private boolean looksLikeSend(AccessibilityNodeInfo node) {
        if (node == null) return false;
        String label = ((node.getContentDescription() == null ? "" : node.getContentDescription().toString())
                + " " + (node.getText() == null ? "" : node.getText().toString())).toLowerCase();
        String viewId = node.getViewIdResourceName();
        if (viewId == null) viewId = "";
        viewId = viewId.toLowerCase();
        return label.matches(".*\\bsend\\b.*") || viewId.contains("send");
    }

    private void showOverlay(Rect bounds, ProtectedTarget target) {
        if (windowManager == null) windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        Rect normalized = new Rect(bounds);
        int min = dp(48);
        if (normalized.width() < min) normalized.right = normalized.left + min;
        if (normalized.height() < min) normalized.bottom = normalized.top + min;

        if (overlay != null && activeKey.equals(target.key)) {
            if (!activeBounds.equals(normalized)) {
                activeBounds.set(normalized);
                WindowManager.LayoutParams p = paramsFor(normalized);
                try {
                    windowManager.updateViewLayout(overlay, p);
                } catch (Exception ignored) {
                    removeOverlay();
                }
            }
            return;
        }

        removeOverlay();
        activeKey = target.key;
        activeBounds.set(normalized);

        Button button = new Button(this);
        button.setText("LOCK");
        button.setTextSize(10);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription("LockText protected Send for " + target.displayName());

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 176, 0, 32));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.WHITE);
        button.setBackground(background);

        button.setOnClickListener(v -> {
            ProtectedTarget fresh = Store.byKey(this, target.key);
            if (fresh == null) {
                removeOverlay();
                return;
            }
            Intent intent = new Intent(this, GuardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("key", fresh.key)
                    .putExtra("name", fresh.displayName())
                    .putExtra("level", fresh.level);
            startActivity(intent);
        });

        try {
            windowManager.addView(button, paramsFor(normalized));
            overlay = button;
        } catch (Exception ignored) {
            overlay = null;
            activeKey = "";
            activeBounds.setEmpty();
        }
    }

    private WindowManager.LayoutParams paramsFor(Rect r) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                r.width(),
                r.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = r.left;
        p.y = r.top;
        return p;
    }

    private void removeOverlay() {
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
        activeKey = "";
        activeBounds.setEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
