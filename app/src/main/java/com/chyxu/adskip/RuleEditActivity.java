package com.chyxu.adskip;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

public class RuleEditActivity extends Activity {
    private int index = -1;
    private Rule rule;
    private RuleStore store;

    private EditText etName, etPackage, etActivity, etKeywords;
    private EditText etDelay, etRetry, etCooldown, etWindow, etX, etY;
    private Spinner spMode;
    private Switch swEnabled, swOnly, swFallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rule_edit);
        setTitle("编辑规则");
        store = RuleStore.get(this);
        index = getIntent().getIntExtra("index", -1);
        if (index >= 0 && index < store.getRules().size()) {
            try {
                rule = store.getRules().get(index).copy();
            } catch (Exception e) {
                rule = Rule.blank();
            }
        } else {
            rule = Rule.blank();
            index = -1;
        }

        etName = findViewById(R.id.etName);
        etPackage = findViewById(R.id.etPackage);
        etActivity = findViewById(R.id.etActivity);
        etKeywords = findViewById(R.id.etKeywords);
        etDelay = findViewById(R.id.etDelay);
        etRetry = findViewById(R.id.etRetry);
        etCooldown = findViewById(R.id.etCooldown);
        etWindow = findViewById(R.id.etWindow);
        etX = findViewById(R.id.etX);
        etY = findViewById(R.id.etY);
        spMode = findViewById(R.id.spMode);
        swEnabled = findViewById(R.id.swEnabled);
        swOnly = findViewById(R.id.swOnly);
        swFallback = findViewById(R.id.swFallback);

        etName.setText(rule.name);
        etPackage.setText(rule.packageName);
        etActivity.setText(rule.activityFilter);
        etKeywords.setText(rule.keywordsText());
        etDelay.setText(String.valueOf(rule.delayMs));
        etRetry.setText(String.valueOf(rule.maxRetries));
        etCooldown.setText(String.valueOf(rule.cooldownMs));
        etWindow.setText(String.valueOf(rule.windowMs));
        etX.setText(String.valueOf(Math.round(rule.xPercent * 100)));
        etY.setText(String.valueOf(Math.round(rule.yPercent * 100)));
        spMode.setSelection(rule.matchMode);
        swEnabled.setChecked(rule.enabled);
        swOnly.setChecked(rule.onlyClickable);
        swFallback.setChecked(rule.fallbackGesture);

        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private String text(EditText et) {
        return et.getText().toString().trim();
    }

    private int intOf(EditText et, int def) {
        try {
            return Integer.parseInt(text(et));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void save() {
        String name = text(etName);
        rule.name = name.isEmpty() ? "未命名规则" : name;
        String pkg = text(etPackage);
        rule.packageName = pkg.isEmpty() ? "*" : pkg;
        rule.activityFilter = text(etActivity);

        rule.keywords.clear();
        for (String k : text(etKeywords).split("[,，\n]")) {
            k = k.trim();
            if (!k.isEmpty()) rule.keywords.add(k);
        }
        if (rule.keywords.isEmpty()) {
            Toast.makeText(this, "至少填写一个匹配关键字", Toast.LENGTH_SHORT).show();
            return;
        }

        rule.matchMode = Math.max(0, Math.min(2, spMode.getSelectedItemPosition()));
        rule.enabled = swEnabled.isChecked();
        rule.onlyClickable = swOnly.isChecked();
        rule.fallbackGesture = swFallback.isChecked();
        rule.delayMs = Math.max(0, Math.min(10000, intOf(etDelay, 300)));
        rule.maxRetries = Math.max(0, Math.min(10, intOf(etRetry, 0)));
        rule.cooldownMs = Math.max(0, Math.min(60000, intOf(etCooldown, 1500)));
        rule.windowMs = Math.max(200, Math.min(60000, intOf(etWindow, 5000)));
        rule.xPercent = Math.max(0f, Math.min(1f, intOf(etX, 88) / 100f));
        rule.yPercent = Math.max(0f, Math.min(1f, intOf(etY, 10) / 100f));

        if (index < 0) store.addRule(rule);
        else store.updateRule(index, rule);

        setResult(RESULT_OK);
        finish();
    }
}
