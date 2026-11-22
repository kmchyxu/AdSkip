package com.chyxu.adskip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 核心：事件驱动地查找并点击"跳过"控件。
 *
 * 省电设计：
 *  - 只监听 WINDOW_STATE_CHANGED / WINDOW_CONTENT_CHANGED，不轮询、不常驻通知、不申请网络。
 *  - 系统层 notificationTimeout + 应用层节流，避免高频事件。
 *  - 节点扫描有深度和数量上限，命中即停；匹配失败才走坐标手势。
 *  - 日志只在成功时追加，且仅保存在内存环形缓冲区。
 */
public class SkipAccessibilityService extends AccessibilityService {
    private RuleStore store;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String lastPkg = "";
    private long lastEventMs;
    private long lastSuccessMs;
    private long windowStartMs;
    private int retry;
    private int scanned;
    private Runnable pending;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        store = RuleStore.get(getApplicationContext());
        AppLog.add("跳过服务已连接");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null) store = RuleStore.get(getApplicationContext());
        if (!store.isMasterEnabled()) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        // 省电模式只响应窗口切换，不响应内容刷新
        if (store.isLowPower() && type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();
        CharSequence cls = event.getClassName();
        String activity = cls == null ? "" : cls.toString();

        long now = System.currentTimeMillis();
        if (now - lastEventMs < store.getMinInterval()) return;
        lastEventMs = now;

        Rule rule = pickRule(pkg, activity);
        if (rule == null) return;

        if (!pkg.equals(lastPkg) || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastPkg = pkg;
            windowStartMs = now;
            retry = 0;
        }
        if (now - lastSuccessMs < rule.cooldownMs) return;
        if (now - windowStartMs > rule.windowMs) return;

        if (pending != null) handler.removeCallbacks(pending);
        pending = new Runnable() {
            @Override
            public void run() {
                handle(pkg, rule);
            }
        };
        handler.postDelayed(pending, rule.delayMs);
    }

    /** 精确包名规则优先于通配规则。 */
    private Rule pickRule(String pkg, String activity) {
        Rule exact = null;
        Rule wildcard = null;
        for (Rule r : store.getRules()) {
            if (!r.enabled) continue;
            boolean isWild = "*".equals(r.packageName);
            if (!isWild && !r.packageName.equals(pkg)) continue;
            if (r.activityFilter != null && !r.activityFilter.isEmpty()
                    && !activity.contains(r.activityFilter)) continue;
            if (isWild) {
                if (wildcard == null) wildcard = r;
            } else if (exact == null) {
                exact = r;
            }
        }
        if (exact != null) return exact;
        return wildcard;
    }

    private void handle(String pkg, Rule rule) {
        scanned = 0;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && scan(root, rule, 0)) {
                onSuccess(rule, "节点点击");
                return;
            }
        } catch (Exception ignored) {
        } finally {
            if (root != null) {
                try {
                    root.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        tryFallback(pkg, rule);
    }

    private boolean scan(AccessibilityNodeInfo node, Rule rule, int depth) {
        if (node == null || depth > store.getScanDepth()) return false;
        if (scanned++ > store.getScanLimit()) return false;

        if (matches(node, rule) && clickUp(node)) return true;

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean hit = scan(child, rule, depth + 1);
            child.recycle();
            if (hit) return true;
        }
        return false;
    }

    /** 节点本身不可点击时，向上找最多 4 层可点击祖先。 */
    private boolean clickUp(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cur = node;
        for (int i = 0; i < 4 && cur != null; i++) {
            if (cur.isClickable() && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private boolean matches(AccessibilityNodeInfo node, Rule rule) {
        if (rule.onlyClickable && !node.isClickable()) return false;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        String text = t == null ? "" : t.toString().trim();
        String desc = d == null ? "" : d.toString().trim();
        String id = node.getViewIdResourceName();
        if (id != null && (id.toLowerCase().contains("skip")
                || id.toLowerCase().contains("close_ad")
                || id.toLowerCase().contains("ad_close"))) {
            return true;
        }
        return hit(text, rule) || hit(desc, rule);
    }

    private boolean hit(String s, Rule rule) {
        if (s == null || s.isEmpty()) return false;
        String lower = s.toLowerCase();
        for (String k : rule.keywords) {
            if (k == null || k.isEmpty()) continue;
            if (rule.matchMode == 2) {
                if (Pattern.compile(k, Pattern.CASE_INSENSITIVE).matcher(s).find()) return true;
            } else if (rule.matchMode == 1) {
                if (lower.equals(k.toLowerCase())) return true;
            } else if (lower.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 微信小程序广告可能完全绘制在 Canvas 上，只有坐标手势能点到。 */
    private void tryFallback(String pkg, Rule rule) {
        int maxRetry = store.isLowPower() ? Math.min(rule.maxRetries, 1) : rule.maxRetries;
        if (!rule.fallbackGesture || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (retry >= maxRetry) return;
        retry++;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * Math.max(0f, Math.min(1f, rule.xPercent));
        float y = dm.heightPixels * Math.max(0f, Math.min(1f, rule.yPercent));
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
                .build();

        boolean sent = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                onSuccess(rule, "手势点击");
            }
        }, null);

        if (sent && retry < maxRetry) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    handle(pkg, rule);
                }
            }, rule.delayMs);
        }
    }

    private void onSuccess(Rule rule, String way) {
        lastSuccessMs = System.currentTimeMillis();
        retry = 0;
        store.recordSkip();
        AppLog.add(way + " · " + rule.name);
    }

    @Override
    public void onInterrupt() {
        if (pending != null) handler.removeCallbacks(pending);
    }

    @Override
    public void onDestroy() {
        if (pending != null) handler.removeCallbacks(pending);
        super.onDestroy();
    }
}
