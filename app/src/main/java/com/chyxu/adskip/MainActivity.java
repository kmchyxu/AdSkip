package com.chyxu.adskip;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_EDIT = 100;

    private RuleStore store;
    private TextView tvStatus;
    private TextView tvStats;
    private Switch swMaster;
    private Switch swLow;
    private LinearLayout ruleContainer;
    private Button btnOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = RuleStore.get(this);

        tvStatus = findViewById(R.id.tvStatus);
        tvStats = findViewById(R.id.tvStats);
        swMaster = findViewById(R.id.swMaster);
        swLow = findViewById(R.id.swLow);
        ruleContainer = findViewById(R.id.ruleContainer);
        btnOpen = findViewById(R.id.btnOpenSettings);

        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        swMaster.setChecked(store.isMasterEnabled());
        swMaster.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                store.setMasterEnabled(checked);
            }
        });
        swLow.setChecked(store.isLowPower());
        swLow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                store.setLowPower(checked);
            }
        });

        findViewById(R.id.btnAddRule).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editRule(-1);
            }
        });
        findViewById(R.id.btnExport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setPrimaryClip(ClipData.newPlainText("adskip_rules", store.exportJson()));
                Toast.makeText(MainActivity.this, "规则 JSON 已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnImport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                ClipData cd = cm.getPrimaryClip();
                if (cd == null || cd.getItemCount() == 0 || cd.getItemAt(0).getText() == null) {
                    Toast.makeText(MainActivity.this, "剪贴板中没有规则 JSON", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (store.importJson(cd.getItemAt(0).getText().toString())) {
                    refreshed();
                    Toast.makeText(MainActivity.this, "规则已导入", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "JSON 格式不正确", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.btnLog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, LogActivity.class));
            }
        });

        refreshed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshed();
    }

    private void refreshed() {
        boolean on = isServiceEnabled();
        tvStatus.setText(on ? "无障碍服务：已开启" : "无障碍服务：未开启（点下方按钮开启）");
        btnOpen.setText(on ? "打开无障碍设置" : "去开启无障碍服务");
        tvStats.setText("累计跳过 " + store.getTotal() + " 次　·　今日 " + store.getToday() + " 次");
        renderRules();
    }

    private boolean isServiceEnabled() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName cn = new ComponentName(this, SkipAccessibilityService.class);
        return s.contains(cn.flattenToShortString()) || s.contains(cn.flattenToString());
    }

    private void renderRules() {
        ruleContainer.removeAllViews();
        List<Rule> rules = store.getRules();
        for (int i = 0; i < rules.size(); i++) {
            final int idx = i;
            final Rule r = rules.get(i);
            View v = LayoutInflater.from(this).inflate(R.layout.item_rule, ruleContainer, false);
            TextView name = v.findViewById(R.id.tvRuleName);
            TextView desc = v.findViewById(R.id.tvRuleDesc);
            Switch sw = v.findViewById(R.id.swRule);
            Button edit = v.findViewById(R.id.btnEditRule);
            Button del = v.findViewById(R.id.btnDelRule);

            name.setText((i + 1) + ". " + r.name);
            desc.setText(r.summary());
            sw.setChecked(r.enabled);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    store.setRuleEnabled(idx, checked);
                }
            });
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v1) {
                    editRule(idx);
                }
            });
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v1) {
                    store.removeRule(idx);
                    refreshed();
                    Toast.makeText(MainActivity.this, "规则已删除", Toast.LENGTH_SHORT).show();
                }
            });
            ruleContainer.addView(v);
        }
    }

    private void editRule(int index) {
        Intent i = new Intent(this, RuleEditActivity.class);
        i.putExtra("index", index);
        startActivityForResult(i, REQ_EDIT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT && resultCode == RESULT_OK) refreshed();
    }
}
