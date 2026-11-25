package com.hozoborolo.musemulti;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String MUSE_URL = "https://muse.ai/";
    private static final String PREFS_NAME = "MuseMultiAdvancedPrefs";
    private static final String KEY_ACCOUNTS = "accounts_csv";

    private LinearLayout tabBar;
    private FrameLayout container;
    private WebView webView;

    private final List<String> accounts = new ArrayList<>();
    private String activeAccount;
    private String pendingRestoreAccount = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tabBar = findViewById(R.id.tab_bar);
        container = findViewById(R.id.webview_container);

        loadAccountList();
        activeAccount = accounts.get(0);

        initWebView();
        renderTabs();
        switchToAccount(activeAccount, false);
    }

    private void loadAccountList() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_ACCOUNTS, "Acc 1,Acc 2,Acc 3");
        accounts.clear();
        for (String s : saved.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty() && !accounts.contains(trimmed)) {
                accounts.add(trimmed);
            }
        }
        if (accounts.isEmpty()) {
            accounts.add("Acc 1");
        }
    }

    private void saveAccountList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accounts.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(accounts.get(i));
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCOUNTS, sb.toString())
                .apply();
    }

    private void initWebView() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (pendingRestoreAccount != null) {
                    String target = pendingRestoreAccount;
                    pendingRestoreAccount = null;
                    restoreLocalStorage(target);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        container.addView(webView);
    }

    private void renderTabs() {
        tabBar.removeAllViews();

        for (final String acc : accounts) {
            Button btn = new Button(this);
            btn.setText(acc);
            btn.setAllCaps(false);
            boolean selected = acc.equals(activeAccount);
            btn.setBackgroundColor(selected ? Color.WHITE : Color.parseColor("#125AC2"));
            btn.setTextColor(selected ? Color.BLACK : Color.WHITE);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!acc.equals(activeAccount)) {
                        switchToAccount(acc, true);
                    }
                }
            });

            btn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAccountOptionsDialog(acc);
                    return true;
                }
            });

            tabBar.addView(btn);
        }

        Button addBtn = new Button(this);
        addBtn.setText("+");
        addBtn.setBackgroundColor(Color.parseColor("#0D2A52"));
        addBtn.setTextColor(Color.WHITE);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveFullSession(activeAccount, new Runnable() {
                    @Override
                    public void run() {
                        String nextName = "Acc " + (accounts.size() + 1);
                        while (accounts.contains(nextName)) {
                            nextName = nextName + "_new";
                        }
                        accounts.add(nextName);
                        saveAccountList();
                        activeAccount = nextName;
                        renderTabs();
                        applyCookiesAndLoad(nextName);
                    }
                });
            }
        });
        tabBar.addView(addBtn);
    }

    private void showAccountOptionsDialog(final String targetAccount) {
        String[] options = {"Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(targetAccount)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showRenameDialog(targetAccount);
                        } else if (which == 1) {
                            deleteAccount(targetAccount);
                        }
                    }
                })
                .show();
    }

    private void showRenameDialog(final String oldName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Account");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(oldName);
        builder.setView(input);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim().replace(",", "");
                if (!newName.isEmpty() && !accounts.contains(newName)) {
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String cookieData = prefs.getString("cookie_" + oldName, "");
                    String lsData = prefs.getString("ls_" + oldName, "{}");

                    int idx = accounts.indexOf(oldName);
                    if (idx >= 0) {
                        accounts.set(idx, newName);
                    }
                    prefs.edit()
                            .putString("cookie_" + newName, cookieData)
                            .putString("ls_" + newName, lsData)
                            .remove("cookie_" + oldName)
                            .remove("ls_" + oldName)
                            .apply();

                    if (oldName.equals(activeAccount)) {
                        activeAccount = newName;
                    }
                    saveAccountList();
                    renderTabs();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteAccount(String nameToDelete) {
        if (accounts.size() <= 1) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove("cookie_" + nameToDelete)
                .remove("ls_" + nameToDelete)
                .apply();

        accounts.remove(nameToDelete);
        saveAccountList();

        if (nameToDelete.equals(activeAccount)) {
            activeAccount = accounts.get(0);
            renderTabs();
            applyCookiesAndLoad(activeAccount);
        } else {
            renderTabs();
        }
    }

    private void switchToAccount(final String targetAccount, boolean saveOld) {
        if (saveOld && activeAccount != null) {
            saveFullSession(activeAccount, new Runnable() {
                @Override
                public void run() {
                    activeAccount = targetAccount;
                    renderTabs();
                    applyCookiesAndLoad(targetAccount);
                }
            });
        } else {
            activeAccount = targetAccount;
            renderTabs();
            applyCookiesAndLoad(targetAccount);
        }
    }

    private void saveFullSession(final String accountName, final Runnable onComplete) {
        CookieManager cm = CookieManager.getInstance();
        cm.flush();
        final String cookies = cm.getCookie(MUSE_URL) != null ? cm.getCookie(MUSE_URL) : "";

        String jsDump = "(function(){ var o={}; for(var i=0;i<localStorage.length;i++){ var k=localStorage.key(i); o[k]=localStorage.getItem(k); } return JSON.stringify(o); })();";
        webView.evaluateJavascript(jsDump, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                String parsedJson = "{}";
                try {
                    if (value != null && !value.equals("null")) {
                        String unquoted = new JSONObject("{\"v\":" + value + "}").getString("v");
                        if (unquoted != null && !unquoted.isEmpty()) {
                            parsedJson = unquoted;
                        }
                    }
                } catch (Exception ignored) {
                }

                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString("cookie_" + accountName, cookies)
                        .putString("ls_" + accountName, parsedJson)
                        .apply();

                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private void restoreLocalStorage(String accountName) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lsJson = prefs.getString("ls_" + accountName, "{}");
        if (lsJson == null || lsJson.isEmpty()) {
            lsJson = "{}";
        }

        StringBuilder js = new StringBuilder();
        js.append("(function(){ try { localStorage.clear(); var data = ").append(lsJson).append("; ");
        js.append("for (var k in data) { if (data.hasOwnProperty(k)) { localStorage.setItem(k, data[k]); } } ");
        js.append("} catch(e) {} })();");

        webView.evaluateJavascript(js.toString(), null);
    }

    private void applyCookiesAndLoad(final String accountName) {
        pendingRestoreAccount = accountName;
        final CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String savedCookies = prefs.getString("cookie_" + accountName, "");
                if (savedCookies != null && !savedCookies.isEmpty()) {
                    for (String part : savedCookies.split(";")) {
                        String cookie = part.trim();
                        if (!cookie.isEmpty()) {
                            cm.setCookie(MUSE_URL, cookie);
                        }
                    }
                }
                cm.flush();
                webView.loadUrl(MUSE_URL);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeAccount != null) {
            saveFullSession(activeAccount, null);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
