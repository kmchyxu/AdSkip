package com.chyxu.adskip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 内存中的环形日志，最多 80 条，不落盘，省电省存储。
 */
public final class AppLog {
    private static final int MAX = 80;
    private static final List<String> LINES = new ArrayList<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private AppLog() {}

    public static synchronized void add(String text) {
        LINES.add(FMT.format(new Date()) + "  " + text);
        while (LINES.size() > MAX) LINES.remove(0);
    }

    public static synchronized String text() {
        if (LINES.isEmpty()) return "暂无记录。开启无障碍服务后，跳过动作会显示在这里。";
        StringBuilder sb = new StringBuilder();
        for (String s : LINES) sb.append(s).append('\n');
        return sb.toString();
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
