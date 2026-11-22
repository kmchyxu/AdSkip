package com.chyxu.adskip;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 一条跳过规则。零依赖，仅用 Android 内置的 org.json 序列化。
 */
public final class Rule {
    /** 显示名称 */
    public String name = "新规则";
    /** 目标包名，"*" 表示所有应用 */
    public String packageName = "*";
    /** 活动名关键字过滤，为空表示不过滤 */
    public String activityFilter = "";
    /** 匹配关键字 */
    public List<String> keywords = new ArrayList<>(Arrays.asList("跳过", "skip"));
    /** 0=包含 1=完全相等 2=正则 */
    public int matchMode = 0;
    public boolean enabled = true;
    /** 仅点击可点击节点（默认关闭，兼容 WebView 中的伪按钮） */
    public boolean onlyClickable = false;
    /** 找不到节点时是否用坐标手势兜底（微信小程序广告常需要） */
    public boolean fallbackGesture = false;
    public int delayMs = 300;
    public int maxRetries = 0;
    public int cooldownMs = 1500;
    public int windowMs = 5000;
    /** 兜底点击位置，屏幕宽高百分比 0~1 */
    public float xPercent = 0.9f;
    public float yPercent = 0.08f;

    public static Rule defaultGlobal() {
        Rule r = new Rule();
        r.name = "通用开屏广告";
        r.packageName = "*";
        r.keywords = new ArrayList<>(Arrays.asList("跳过", "跳过广告", "立即跳过", "关闭广告", "skip", "skip ad"));
        r.matchMode = 0;
        r.onlyClickable = false;
        r.fallbackGesture = false;
        r.delayMs = 300;
        r.maxRetries = 0;
        r.cooldownMs = 1500;
        r.windowMs = 5000;
        return r;
    }

    public static Rule wechat() {
        Rule r = new Rule();
        r.name = "微信 / 小程序广告";
        r.packageName = "com.tencent.mm";
        r.keywords = new ArrayList<>(Arrays.asList("跳过", "跳过广告", "关闭广告", "skip"));
        r.matchMode = 0;
        r.onlyClickable = false;
        r.fallbackGesture = true;
        r.delayMs = 650;
        r.maxRetries = 3;
        r.cooldownMs = 2500;
        r.windowMs = 6000;
        r.xPercent = 0.88f;
        r.yPercent = 0.10f;
        return r;
    }

    public static Rule blank() {
        return new Rule();
    }

    public Rule copy() throws JSONException {
        return Rule.fromJson(toJson());
    }

    public String keywordsText() {
        StringBuilder sb = new StringBuilder();
        for (String k : keywords) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k);
        }
        return sb.toString();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(packageName);
        if (activityFilter != null && !activityFilter.isEmpty()) sb.append(" · ").append(activityFilter);
        sb.append(" · ").append(keywordsText());
        sb.append(" · 延时").append(delayMs).append("ms");
        if (fallbackGesture) sb.append(" · 坐标兜底");
        return sb.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("packageName", packageName);
        o.put("activityFilter", activityFilter);
        JSONArray arr = new JSONArray();
        for (String k : keywords) arr.put(k);
        o.put("keywords", arr);
        o.put("matchMode", matchMode);
        o.put("enabled", enabled);
        o.put("onlyClickable", onlyClickable);
        o.put("fallbackGesture", fallbackGesture);
        o.put("delayMs", delayMs);
        o.put("maxRetries", maxRetries);
        o.put("cooldownMs", cooldownMs);
        o.put("windowMs", windowMs);
        o.put("xPercent", xPercent);
        o.put("yPercent", yPercent);
        return o;
    }

    public static Rule fromJson(JSONObject o) throws JSONException {
        Rule r = new Rule();
        r.name = o.optString("name", r.name);
        r.packageName = o.optString("packageName", r.packageName);
        r.activityFilter = o.optString("activityFilter", "");
        JSONArray arr = o.optJSONArray("keywords");
        if (arr != null) {
            r.keywords.clear();
            for (int i = 0; i < arr.length(); i++) {
                String k = arr.optString(i, "").trim();
                if (!k.isEmpty()) r.keywords.add(k);
            }
        }
        r.matchMode = clamp(o.optInt("matchMode", 0), 0, 2);
        r.enabled = o.optBoolean("enabled", true);
        r.onlyClickable = o.optBoolean("onlyClickable", false);
        r.fallbackGesture = o.optBoolean("fallbackGesture", false);
        r.delayMs = clamp(o.optInt("delayMs", 300), 0, 10000);
        r.maxRetries = clamp(o.optInt("maxRetries", 0), 0, 10);
        r.cooldownMs = clamp(o.optInt("cooldownMs", 1500), 0, 60000);
        r.windowMs = clamp(o.optInt("windowMs", 5000), 200, 60000);
        r.xPercent = (float) clamp(o.optDouble("xPercent", 0.9), 0.0, 1.0);
        r.yPercent = (float) clamp(o.optDouble("yPercent", 0.08), 0.0, 1.0);
        return r;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
