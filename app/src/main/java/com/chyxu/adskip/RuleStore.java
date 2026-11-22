package com.chyxu.adskip;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 规则与设置的唯一入口。进程内单例 + SharedPreferences，无数据库、无网络。
 */
public final class RuleStore {
    private static final String PREF = "adskip_prefs";
    private static final String K_MASTER = "master";
    private static final String K_LOW = "low_power";
    private static final String K_RULES = "rules_json";
    private static final String K_TOTAL = "stat_total";
    private static final String K_TODAY = "stat_today";
    private static final String K_DATE = "stat_date";
    private static final String K_INTERVAL = "min_interval";
    private static final String K_DEPTH = "scan_depth";
    private static final String K_LIMIT = "scan_limit";

    private static RuleStore instance;
    private final SharedPreferences prefs;
    private final List<Rule> rules = new ArrayList<>();
    private boolean master;
    private boolean lowPower;
    private int total;
    private int today;
    private int minInterval;
    private int scanDepth;
    private int scanLimit;
    private String statDate;

    private RuleStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        master = prefs.getBoolean(K_MASTER, true);
        lowPower = prefs.getBoolean(K_LOW, false);
        minInterval = prefs.getInt(K_INTERVAL, 250);
        scanDepth = prefs.getInt(K_DEPTH, 10);
        scanLimit = prefs.getInt(K_LIMIT, 150);
        total = prefs.getInt(K_TOTAL, 0);
        statDate = todayString();
        today = statDate.equals(prefs.getString(K_DATE, "")) ? prefs.getInt(K_TODAY, 0) : 0;

        String json = prefs.getString(K_RULES, null);
        if (json != null && !json.isEmpty()) {
            loadRules(json);
        }
        if (rules.isEmpty()) {
            rules.add(Rule.defaultGlobal());
            rules.add(Rule.wechat());
            saveRules();
        }
    }

    public static synchronized RuleStore get(Context context) {
        if (instance == null) instance = new RuleStore(context);
        return instance;
    }

    private static String todayString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void loadRules(String json) {
        rules.clear();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) rules.add(Rule.fromJson(o));
            }
        } catch (JSONException ignored) {
            rules.clear();
        }
    }

    private void saveRules() {
        JSONArray arr = new JSONArray();
        try {
            for (Rule r : rules) arr.put(r.toJson());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(K_RULES, arr.toString()).apply();
    }

    public List<Rule> getRules() {
        return rules;
    }

    public boolean isMasterEnabled() {
        return master;
    }

    public void setMasterEnabled(boolean v) {
        master = v;
        prefs.edit().putBoolean(K_MASTER, v).apply();
    }

    public boolean isLowPower() {
        return lowPower;
    }

    public void setLowPower(boolean v) {
        lowPower = v;
        prefs.edit().putBoolean(K_LOW, v).apply();
    }

    public int getMinInterval() {
        return minInterval;
    }

    public int getScanDepth() {
        return lowPower ? Math.min(scanDepth, 6) : scanDepth;
    }

    public int getScanLimit() {
        return lowPower ? Math.min(scanLimit, 60) : scanLimit;
    }

    public int getTotal() {
        return total;
    }

    public int getToday() {
        checkDay();
        return today;
    }

    private void checkDay() {
        String d = todayString();
        if (!d.equals(statDate)) {
            statDate = d;
            today = 0;
            prefs.edit().putString(K_DATE, d).putInt(K_TODAY, 0).apply();
        }
    }

    public void recordSkip() {
        checkDay();
        total++;
        today++;
        prefs.edit().putInt(K_TOTAL, total).putInt(K_TODAY, today).apply();
    }

    public void addRule(Rule r) {
        rules.add(r);
        saveRules();
    }

    public void updateRule(int index, Rule r) {
        if (index >= 0 && index < rules.size()) {
            rules.set(index, r);
            saveRules();
        }
    }

    public void removeRule(int index) {
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            saveRules();
        }
    }

    public void setRuleEnabled(int index, boolean v) {
        if (index >= 0 && index < rules.size()) {
            rules.get(index).enabled = v;
            saveRules();
        }
    }

    public String exportJson() {
        JSONArray arr = new JSONArray();
        try {
            for (Rule r : rules) arr.put(r.toJson());
        } catch (JSONException ignored) {
        }
        return arr.toString();
    }

    /** 从剪贴板 JSON 导入，成功返回 true，原规则会被替换。 */
    public boolean importJson(String json) {
        List<Rule> parsed = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) parsed.add(Rule.fromJson(o));
            }
        } catch (JSONException e) {
            return false;
        }
        if (parsed.isEmpty()) return false;
        rules.clear();
        rules.addAll(parsed);
        saveRules();
        return true;
    }
}
