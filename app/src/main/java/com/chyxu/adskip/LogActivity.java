package com.chyxu.adskip;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class LogActivity extends Activity {
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        setTitle("跳过记录");
        tvLog = findViewById(R.id.tvLog);
        tvLog.setText(AppLog.text());
        findViewById(R.id.btnClearLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppLog.clear();
                tvLog.setText(AppLog.text());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvLog.setText(AppLog.text());
    }
}
