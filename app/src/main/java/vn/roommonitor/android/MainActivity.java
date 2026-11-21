package vn.roommonitor.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final String PREF_NAME = "RoomMonitorConfig";
    private static final String PREF_THEME = "pref_theme";
    private static final String[] TABS = {"Theo dõi", "Trình duyệt", "Cấu hình", "Nhật ký"};
    private static final String[] PROTOCOLS = {"http", "https", "socks5"};

    // Danh sách các giao diện tự chọn (Themes)
    public static final String[] THEMES = {
        "Theo hệ thống (Mặc định)",
        "Cyber Dark (Xanh Đêm)",
        "Clean Light (Sáng Thanh Lịch)",
        "OLED Pitch Black (Đen Tuyệt Đối)",
        "Emerald Forest (Xanh Ngọc)",
        "Royal Violet (Tím Hoàng Gia)"
    };

    // Regex bắt room ID trong JSON body và URL
    private static final Pattern ROOM_IN_JSON = Pattern.compile(
            "(?i)\"(?:roomId|room_id|tableId|table_id|roomCode|room_code)\"\\s*:\\s*\"?(\\d{1,12})\"?");
    private static final Pattern ROOM_IN_URL = Pattern.compile(
            "(?i)(?:[?&](?:roomId|room_id|tableId|table_id|room|table)=(\\d{1,12})(?=[&#]|$))|(?:/(?:room|rooms|table|tables)/(\\d{1,12})(?=[/?#]|$))");

    // JS inject để hook fetch/XHR, đẩy body + headers về app
    private static final String CAPTURE_JS =
            "(function(){if(window.__rmHooked)return;window.__rmHooked=true;" +
            "function cap(u,m,h,b){try{if(window.RMNative&&window.RMNative.capture){" +
            "window.RMNative.capture(String(u||''),String(m||''),JSON.stringify(h||{}),String(b||''));}}catch(e){}}" +
            "var of=window.fetch;if(of){window.fetch=function(i,n){try{" +
            "var u=typeof i==='string'?i:(i&&i.url)||'';" +
            "var m=(n&&n.method)||(i&&i.method)||'GET';" +
            "var h=(n&&n.headers)||(i&&i.headers)||{};" +
            "var b=(n&&n.body)||'';cap(u,m,h,typeof b==='string'?b:'');" +
            "}catch(e){}return of.apply(this,arguments);};}" +
            "var oo=XMLHttpRequest.prototype.open,oh=XMLHttpRequest.prototype.setRequestHeader,os=XMLHttpRequest.prototype.send;" +
            "XMLHttpRequest.prototype.open=function(m,u){this.__rm_m=m;this.__rm_u=u;this.__rm_h={};return oo.apply(this,arguments);};" +
            "XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{this.__rm_h[k]=v;}catch(e){}return oh.apply(this,arguments);};" +
            "XMLHttpRequest.prototype.send=function(b){try{cap(this.__rm_u,this.__rm_m,this.__rm_h,typeof b==='string'?b:'');}catch(e){}return os.apply(this,arguments);};" +
            "})();";

    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private final Browser[] browsers = {new Browser("Web 1", "A"), new Browser("Web 2", "B")};
    private RoomConnection monitor;
    private LinearLayout rootLayout, body, navigation, toolbar;
    private TextView sessionStatus;
    private Button runButton, fetchRooms;
    private EditText roomsInput;
    private int tab = 0, layout = 2, proxyProtocol = 0;
    private int selectedTheme = 0;

    // Bảng màu giao diện (được gán động theo theme người dùng chọn)
    private int base, panel, ink, muted, border, blue, blueTint, violet, violetTint;
    private String tokenA = "", tokenB = "", roomText = "", playerId = "", lobbyId = "";
    private String proxyHost = "", proxyPort = "";
    private boolean proxyBusy;
    private boolean detached;
    private boolean multiProfile, developerBuild;
    private Browser expandedBrowser;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadPreferences();
        applyThemePalette();

        monitor = new RoomConnection(this::updateMonitor);
        developerBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(developerBuild);

        buildShell();
        showTab();
    }

    private void loadPreferences() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        selectedTheme = sp.getInt(PREF_THEME, 0);
        tokenA = sp.getString("tokenA", "");
        tokenB = sp.getString("tokenB", "");
        roomText = sp.getString("roomText", "");
        playerId = sp.getString("playerId", "");
        lobbyId = sp.getString("lobbyId", "");
        layout = sp.getInt("layout", 2);
    }

    private void savePreferences() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        sp.edit()
                .putInt(PREF_THEME, selectedTheme)
                .putString("tokenA", tokenA)
                .putString("tokenB", tokenB)
                .putString("roomText", roomText)
                .putString("playerId", playerId)
                .putString("lobbyId", lobbyId)
                .putInt("layout", layout)
                .apply();
    }

    private void applyThemePalette() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        switch (selectedTheme) {
            case 1: // Cyber Dark
                base = color("#0D131F");
                panel = color("#151E30");
                ink = color("#F1F5F9");
                muted = color("#94A3B8");
                border = color("#2D3748");
                blue = color("#38BDF8");
                blueTint = color("#1E293B");
                violet = color("#C084FC");
                violetTint = color("#2E1065");
                break;
            case 2: // Clean Light
                base = color("#F8FAFC");
                panel = color("#FFFFFF");
                ink = color("#0F172A");
                muted = color("#64748B");
                border = color("#E2E8F0");
                blue = color("#0284C7");
                blueTint = color("#E0F2FE");
                violet = color("#7C3AED");
                violetTint = color("#EDE9FE");
                break;
            case 3: // OLED Black
                base = color("#000000");
                panel = color("#0D0D0D");
                ink = color("#FFFFFF");
                muted = color("#8E8E93");
                border = color("#222222");
                blue = color("#0A84FF");
                blueTint = color("#1C1C1E");
                violet = color("#BF5AF2");
                violetTint = color("#2C153A");
                break;
            case 4: // Emerald Forest
                base = color("#0B1C15");
                panel = color("#112920");
                ink = color("#ECFDF5");
                muted = color("#86EFAC");
                border = color("#1D4536");
                blue = color("#10B981");
                blueTint = color("#064E3B");
                violet = color("#34D399");
                violetTint = color("#065F46");
                break;
            case 5: // Royal Violet
                base = color("#130D24");
                panel = color("#1E1538");
                ink = color("#FAF5FF");
                muted = color("#D8B4FE");
                border = color("#3B2D6B");
                blue = color("#A855F7");
                blueTint = color("#2E1854");
                violet = color("#F472B6");
                violetTint = color("#4C1135");
                break;
            case 0: // Theo hệ thống
            default:
                base = color(dark ? "#101727" : "#F0F4FA");
                panel = color(dark ? "#19243A" : "#FFFFFF");
                ink = color(dark ? "#EDF3FF" : "#122139");
                muted = color(dark ? "#AFBDD2" : "#496077");
                border = color(dark ? "#374760" : "#D6E2EE");
                blue = color(dark ? "#A9CFFF" : "#164DA0");
                blueTint = color(dark ? "#273C5C" : "#E7EFFF");
                violet = color(dark ? "#DBC5FF" : "#643B9B");
                violetTint = color(dark ? "#3C2E55" : "#F0EAFA");
                break;
        }
    }

    private void changeTheme(int newThemeIndex) {
        selectedTheme = newThemeIndex;
        savePreferences();
        applyThemePalette();
        // Cập nhật lại màu sắc trên UI
        if (rootLayout != null) rootLayout.setBackgroundColor(base);
        if (toolbar != null) toolbar.setBackgroundColor(selectedTheme == 3 ? color("#0A0A0A") : color("#111F37"));
        if (navigation != null) navigation.setBackgroundColor(panel);
        showTab();
        message("Đã chuyển sang giao diện: " + THEMES[selectedTheme]);
    }

    @Override protected void onStart() {
        super.onStart();
        if (monitor != null) monitor.setVisible(true);
        for (Browser browser : browsers) if (browser.web != null) browser.web.onResume();
    }

    @Override protected void onStop() {
        if (monitor != null) monitor.setVisible(false);
        for (Browser browser : browsers) if (browser.web != null) browser.web.onPause();
        savePreferences();
        super.onStop();
    }

    @Override protected void onDestroy() {
        detached = true;
        savePreferences();
        if (monitor != null) monitor.destroy();
        for (Browser browser : browsers) browser.close();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (selectedTheme == 0) {
            applyThemePalette();
        }
        showTab();
    }

    private void buildShell() {
        rootLayout = column();
        rootLayout.setBackgroundColor(base);

        toolbar = row();
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(14), dp(8), dp(11), dp(8));
        toolbar.setBackgroundColor(selectedTheme == 3 ? color("#0A0A0A") : color("#111F37"));

        LinearLayout title = column();
        title.addView(text("Room Monitor", 17, Color.WHITE, true));
        sessionStatus = text("Sẵn sàng", 12, color("#C2D1EA"), false);
        title.addView(sessionStatus);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        runButton = button("Bắt đầu", color("#C1DBFF"), color("#173453"), this::toggleMonitor);
        toolbar.addView(runButton);
        rootLayout.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        body = column();
        rootLayout.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = row();
        navigation.setPadding(dp(4), dp(4), dp(4), dp(4));
        navigation.setBackgroundColor(panel);
        rootLayout.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        setContentView(rootLayout);
    }

    private void showTab() {
        boolean expanded = tab == 1 && expandedBrowser != null;
        toolbar.setVisibility(expanded ? View.GONE : View.VISIBLE);
        navigation.setVisibility(expanded ? View.GONE : View.VISIBLE);
        if (expanded) {
            body.removeAllViews();
            buildExpandedBrowser();
            return;
        }
        navigation.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button item = button(TABS[i], i == tab ? blueTint : panel, i == tab ? blue : muted, () -> {
                if (tab != index) {
                    tab = index;
                    showTab();
                }
            });
            item.setTextSize(12);
            item.setPadding(dp(1), 0, dp(1), 0);
            item.setSelected(i == tab);
            navigation.addView(item, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        roomsInput = null;
        fetchRooms = null;
        body.removeAllViews();
        if (tab == 0) renderDashboard(0);
        else if (tab == 1) buildBrowser();
        else if (tab == 2) buildSettings();
        else renderJournal(0);
    }

    private void toggleMonitor() {
        if (monitor.running) {
            monitor.stop();
            return;
        }
        Set<String> rooms = RoomConnection.parseRooms(roomText);
        if (!RoomConnection.validToken(tokenA) || !RoomConnection.validToken(tokenB) || rooms.isEmpty()) {
            message("Cần Token A, Token B và danh sách phòng trong Cấu hình.");
            tab = 2;
            showTab();
            return;
        }
        monitor.start(tokenA, tokenB, rooms, playerId, lobbyId);
    }

    private void updateMonitor() {
        if (detached) return;
        sessionStatus.setText(monitor.status);
        runButton.setText(monitor.running ? "Dừng" : "Bắt đầu");
        runButton.setEnabled(!monitor.loadingRooms || monitor.running);
        if (fetchRooms != null) {
            fetchRooms.setEnabled(!monitor.loadingRooms && !monitor.running);
            fetchRooms.setText(monitor.loadingRooms ? "Đang tải phòng…" : "Tải phòng từ máy chủ");
        }
        if (tab == 0) renderDashboard(currentScroll());
        if (tab == 3) renderJournal(currentScroll());
    }

    private int currentScroll() {
        View child = body.getChildAt(0);
        return child instanceof ScrollView ? child.getScrollY() : 0;
    }

    private void renderDashboard(int position) {
        body.removeAllViews();
        LinearLayout sections = scrollSection(position);
        gap(sections, title("Theo dõi"), 0);
        int total = 0;
        for (int count : monitor.counts) total += count;

        LinearLayout summary = column();
        GradientDrawable hero = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color("#122340"), color("#214D87")});
        hero.setCornerRadius(dp(18));
        summary.setBackground(hero);
        summary.setPadding(dp(15), dp(13), dp(15), dp(13));
        summary.addView(text("Tổng ván đã ghi nhận", 14, color("#CBE1FF"), false));
        gap(summary, text(String.valueOf(total), 34, Color.WHITE, true), 3);
        if (!monitor.running) gap(summary, text("Chưa mở phiên theo dõi", 12, color("#D6E6FD"), false), 2);
        gap(sections, summary, 11);

        gap(sections, text("Số dư tài khoản", 16, ink, true), 13);
        LinearLayout accounts = row();
        addWeighted(accounts, account("A", monitor.balanceA, blue, blueTint), 0);
        addWeighted(accounts, account("B", monitor.balanceB, violet, violetTint), 8);
        gap(sections, accounts, 6);

        LinearLayout results = card();
        results.addView(text("Kết quả các ván", 16, ink, true));
        int[] colors = {color("#5789E9"), color("#9870CB"), color("#319987"), color("#8294AD")};
        LinearLayout bar = row();
        bar.setBackground(shape(base, 8, 0));
        bar.setClipToOutline(true);
        if (total > 0) {
            for (int i = 0; i < 4; i++) {
                if (monitor.counts[i] == 0) continue;
                View block = new View(this);
                block.setBackgroundColor(colors[i]);
                bar.addView(block, new LinearLayout.LayoutParams(0, -1, monitor.counts[i]));
            }
        }
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams barSize = new LinearLayout.LayoutParams(-1, dp(7));
        barSize.topMargin = dp(9);
        barSize.bottomMargin = dp(5);
        results.addView(bar, barSize);
        for (int i = 0; i < 4; i++) {
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text("●  " + RoomConnection.outcomeLabel(i), 14, ink, false);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView result = text(monitor.counts[i] + "   ·   "
                    + String.format(new Locale("vi", "VN"), "%.1f%%", total == 0 ? 0 : 100d * monitor.counts[i] / total),
                    14, muted, false);
            row.addView(result);
            gap(results, row, 4);
        }
        gap(sections, results, 11);
    }

    private LinearLayout account(String name, BigDecimal amount, int accent, int fill) {
        LinearLayout account = card();
        TextView title = text("Tài khoản " + name, 13, accent, true);
        title.setBackground(shape(fill, 7, 0));
        title.setPadding(dp(7), dp(4), dp(7), dp(4));
        account.addView(title, new LinearLayout.LayoutParams(-2, -2));
        TextView balance = text(amount == null ? "—" : money.format(amount), 19, ink, true);
        balance.setMaxLines(2);
        gap(account, balance, 8);
        return account;
    }

    private void buildSettings() {
        LinearLayout sections = scrollSection(0);
        sections.addView(title("Cấu hình"));

        // Mục: Tự chọn giao diện (Theme Selector)
        LinearLayout themeCard = card();
        themeCard.addView(text("Giao diện ứng dụng", 14, ink, true));
        gap(themeCard, text("Tự chọn màu sắc và phong cách hiển thị:", 12, muted, false), 2);
        Button themeBtn = button("Đổi giao diện · " + THEMES[selectedTheme], blueTint, blue, this::showThemeDialog);
        gap(themeCard, themeBtn, 8);
        gap(sections, themeCard, 9);

        // Mục: Tài khoản
        LinearLayout accounts = card();
        accounts.addView(text("Xác thực tài khoản", 14, ink, true));
        addInput(accounts, "Token A", tokenA, true, false, value -> tokenA = value.trim());
        addInput(accounts, "Token B", tokenB, true, false, value -> tokenB = value.trim());
        gap(sections, accounts, 9);

        // Mục: Phòng
        LinearLayout rooms = card();
        rooms.addView(text("Danh sách phòng theo dõi", 14, ink, true));
        roomsInput = addInput(rooms, "Room ID", roomText, false, true, value -> roomText = value);
        roomsInput.setHint("Mỗi ID trên một dòng (Ví dụ: 101, 102)");
        fetchRooms = button(monitor.loadingRooms ? "Đang tải phòng…" : "Tải phòng từ máy chủ",
                blueTint, blue, this::fetchRooms);
        fetchRooms.setEnabled(!monitor.loadingRooms && !monitor.running);
        gap(rooms, fetchRooms, 7);
        addInput(rooms, "WS Player ID (tùy chọn)", playerId, false, false, value -> playerId = value.trim());
        addInput(rooms, "WS Lobby ID (tùy chọn)", lobbyId, false, false, value -> lobbyId = value.trim());
        gap(sections, rooms, 9);
    }

    private void showThemeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Tự chọn giao diện")
                .setSingleChoiceItems(THEMES, selectedTheme, (dialog, which) -> {
                    changeTheme(which);
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void fetchRooms() {
        if (!RoomConnection.validToken(tokenA)) {
            message("Nhập Token A hợp lệ trước khi tải phòng.");
            return;
        }
        monitor.loadRooms(tokenA, ids -> {
            roomText = String.join("\n", ids);
            if (roomsInput != null) roomsInput.setText(roomText);
            message("Đã tải " + ids.size() + " phòng.");
        }, this::message);
    }

    private void renderJournal(int position) {
        body.removeAllViews();
        LinearLayout sections = scrollSection(position);
        sections.addView(title("Nhật ký"));
        LinearLayout entries = card();
        if (monitor.journal.isEmpty()) entries.addView(text("Chưa có sự kiện trong phiên.", 14, muted, false));
        for (String event : monitor.journal) gap(entries, text(event, 13, ink, false), 5);
        gap(sections, entries, 9);
    }

    @Override @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (expandedBrowser != null && tab == 1) {
            closeExpandedBrowser();
            return;
        }
        super.onBackPressed();
    }

    private void closeExpandedBrowser() {
        expandedBrowser = null;
        showTab();
    }

    private void expandBrowser(Browser browser) {
        if (browser.web == null) return;
        expandedBrowser = browser;
        showTab();
    }

    private void buildExpandedBrowser() {
        Browser browser = expandedBrowser;
        if (browser.web == null) {
            closeExpandedBrowser();
            return;
        }
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(5), 0, dp(5), 0);
        bar.setBackgroundColor(panel);
        Button back = button("‹", panel, blue, () -> {
            if (browser.web != null && browser.web.canGoBack()) browser.web.goBack();
        });
        back.setContentDescription("Trang trước");
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(44)));
        bar.addView(text(browser.name, 13, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (developerBuild) bar.addView(button("DevTools", panel, blue, () -> showWebDebug(browser)));
        bar.addView(button("Thu nhỏ", blueTint, blue, this::closeExpandedBrowser));
        body.addView(bar, new LinearLayout.LayoutParams(-1, dp(44)));
        detach(browser.web);
        body.addView(browser.web, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showWebDebug(Browser browser) {
        LinearLayout content = column();
        content.setPadding(dp(14), dp(4), dp(14), dp(4));

        LinearLayout tokenBox = card();
        tokenBox.addView(text("Token (x-authentication-token)", 13, muted, true));
        String token = browser.capturedToken;
        TextView tokenView = text(token.isEmpty() ? "— chưa bắt được —" : token, 13,
                token.isEmpty() ? muted : blue, !token.isEmpty());
        tokenView.setTextIsSelectable(true);
        tokenView.setPadding(0, dp(4), 0, dp(4));
        tokenBox.addView(tokenView);
        if (!token.isEmpty()) {
            String target = browser == browsers[0] ? "A" : "B";
            LinearLayout actions = row();
            addWeighted(actions, button("Gán Token " + target, blueTint, blue, () -> {
                if (browser == browsers[0]) tokenA = browser.capturedToken;
                else tokenB = browser.capturedToken;
                message("Đã gán token cho " + target);
            }), 0);
            addWeighted(actions, button("Sao chép", panel, blue, () -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("token", browser.capturedToken));
                message("Đã sao chép.");
            }), 6);
            tokenBox.addView(actions);
        }
        content.addView(tokenBox);
        gap(content, text("Rooms bắt được (" + browser.capturedRooms.size() + ")", 13, ink, true), 12);

        LinearLayout roomsBox = card();
        if (browser.capturedRooms.isEmpty()) {
            roomsBox.addView(text("— chưa bắt được —", 13, muted, false));
        } else {
            StringBuilder sb = new StringBuilder();
            synchronized (browser.capturedRooms) {
                for (String r : browser.capturedRooms) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(r);
                }
            }
            TextView rv = text(sb.toString(), 13, ink, false);
            rv.setTextIsSelectable(true);
            roomsBox.addView(rv);
            gap(roomsBox, button("Điền vào ô Room ID", blueTint, blue, () -> {
                StringBuilder fill = new StringBuilder();
                synchronized (browser.capturedRooms) {
                    for (String r : browser.capturedRooms) {
                        if (fill.length() > 0) fill.append('\n');
                        fill.append(r);
                    }
                }
                roomText = fill.toString();
                if (roomsInput != null) roomsInput.setText(roomText);
                message("Đã điền " + browser.capturedRooms.size() + " room vào Cấu hình.");
            }), 6);
        }
        content.addView(roomsBox);
        gap(content, text("Lịch sử network (" + browser.networkLog.size() + ")", 13, ink, true), 12);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = column();
        List<NetworkEntry> snapshot;
        synchronized (browser.networkLog) { snapshot = new ArrayList<>(browser.networkLog); }
        Collections.reverse(snapshot);
        if (snapshot.isEmpty()) {
            list.addView(text("Chưa có request nào chứa token hoặc room ID.", 12, muted, false));
        } else {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            for (NetworkEntry e : snapshot) {
                LinearLayout line = card();
                line.setPadding(dp(8), dp(6), dp(8), dp(6));
                line.addView(text(e.method + "  " + (e.host == null ? "" : e.host), 12, ink, true));
                line.addView(text(e.url, 11, muted, false));
                String meta = fmt.format(new java.util.Date(e.time));
                if (e.game != null && !e.game.isEmpty()) meta += "  ·  " + e.game;
                if (e.token != null && !e.token.isEmpty()) meta += "  ·  token " + e.token;
                line.addView(text(meta, 11, blue, false));
                gap(list, line, 4);
            }
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(260)));

        String detail = browser.debugStatus.isEmpty() ? "Chưa có thông tin tải trang." : browser.debugStatus;
        new AlertDialog.Builder(this)
                .setTitle("DevTools · " + browser.name)
                .setView(content)
                .setMessage(detail + "\n\nBật USB debugging → chrome://inspect để debug WebView.")
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void buildBrowser() {
        boolean supported;
        try {
            supported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        } catch (RuntimeException error) {
            body.addView(text("Không tìm thấy Android System WebView.", 15, ink, true));
            return;
        }
        multiProfile = supported;
        LinearLayout section = column();
        section.setPadding(dp(9), dp(7), dp(9), dp(8));
        LinearLayout toolRow = row();
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        toolRow.addView(title("Trình duyệt"), new LinearLayout.LayoutParams(0, -2, 1));
        if (developerBuild) {
            toolRow.addView(button("DevTools", panel, blue,
                    () -> showWebDebug(browsers[layout == 1 ? 1 : 0])));
        }
        section.addView(toolRow, new LinearLayout.LayoutParams(-1, -2));
        Button proxy = button(proxyBusy ? "Đang áp dụng proxy…" : proxyHost.isEmpty()
                ? "Proxy chung · Tắt" : "Proxy chung · " + proxyHost + ":" + proxyPort,
                panel, blue, this::editProxy);
        proxy.setEnabled(!proxyBusy);
        gap(section, proxy, 6);
        LinearLayout choice = row();
        String[] labels = {"Web 1", "Web 2", "Cả hai"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            addWeighted(choice, button(labels[i], layout == i ? blueTint : panel,
                    layout == i ? blue : muted, () -> {
                        layout = index;
                        savePreferences();
                        showTab();
                    }), i == 0 ? 0 : 5);
        }
        gap(section, choice, 6);
        boolean horizontal = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout panes = horizontal ? row() : column();
        if (layout != 1) addPane(panes, browserPane(browsers[0], blue, blueTint), horizontal, 0);
        if (layout != 0) addPane(panes, browserPane(browsers[1], violet, violetTint), horizontal, layout == 2 ? 7 : 0);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, 0, 1);
        size.topMargin = dp(7);
        section.addView(panes, size);
        body.addView(section, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private View browserPane(Browser browser, int accent, int tint) {
        LinearLayout pane = column();
        pane.setBackground(shape(panel, 14, border));
        pane.setClipToOutline(true);
        LinearLayout header = row();
        header.setPadding(dp(8), dp(1), dp(5), dp(1));
        header.setBackgroundColor(tint);
        header.setGravity(Gravity.CENTER_VERTICAL);

        String badge = "";
        if (!browser.capturedToken.isEmpty()) badge += "  ·  Token ✓";
        if (!browser.capturedRooms.isEmpty()) badge += "  ·  " + browser.capturedRooms.size() + " room";
        TextView label = text(browser.name + " · " + browser.account
                + (browser.customAgent.isEmpty() ? "  |  UA mặc định" : "  |  UA tùy chỉnh") + badge,
                12, accent, true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button ua = button("Đổi UA", panel, accent, () -> editUserAgent(browser));
        ua.setEnabled(multiProfile || browser == browsers[0]);
        header.addView(ua);
        Button expand = button("⛶", tint, accent, () -> expandBrowser(browser));
        expand.setContentDescription("Toàn màn hình " + browser.name);
        expand.setEnabled(multiProfile || browser == browsers[0]);
        header.addView(expand, new LinearLayout.LayoutParams(dp(44), dp(44)));
        pane.addView(header);
        if (!multiProfile && browser == browsers[1]) {
            TextView warning = text("Cập nhật Android System WebView để mở Web 2 với cookie riêng.", 13, muted, false);
            warning.setPadding(dp(8), dp(7), dp(8), dp(5));
            pane.addView(warning);
            return pane;
        }
        WebView web;
        try {
            web = browser.get();
        } catch (RuntimeException error) {
            TextView warning = text("Không mở được hồ sơ WebView.", 14, muted, false);
            pane.addView(warning);
            return pane;
        }
        LinearLayout address = row();
        address.setPadding(dp(5), dp(1), dp(5), dp(2));
        EditText input = field("Địa chỉ HTTPS", browser.address, false, false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        observe(input, value -> browser.address = value);
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_GO) return false;
            browser.open();
            return true;
        });
        address.addView(input, new LinearLayout.LayoutParams(0, dp(43), 1));
        Button open = button("Mở", tint, accent, browser::open);
        open.setEnabled(!proxyBusy);
        address.addView(open);
        pane.addView(address);
        detach(web);
        pane.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        return pane;
    }

    private void editUserAgent(Browser browser) {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(4), dp(18), dp(4));
        EditText input = field("Mặc định của WebView", browser.customAgent, false, false);
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("User-Agent · " + browser.name)
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setNeutralButton("Mặc định", (d, which) -> browser.applyAgent(""))
                .setPositiveButton("Áp dụng", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = input.getText().toString().trim();
            if (value.length() > 512 || !value.chars().allMatch(c -> c >= 32 && c <= 126)) {
                input.setError("Tối đa 512 ký tự ASCII có thể in.");
                return;
            }
            browser.applyAgent(value);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void editProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            message("WebView hiện tại chưa hỗ trợ proxy cho ứng dụng.");
            return;
        }
        LinearLayout form = column();
        form.setPadding(dp(18), dp(4), dp(18), dp(4));
        EditText hostField = field("Máy chủ hoặc IP", proxyHost, false, false);
        EditText portField = field("Cổng", proxyPort, false, false);
        portField.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(hostField);
        form.addView(portField);
        new AlertDialog.Builder(this)
                .setTitle("Proxy chung cho Web 1 và Web 2")
                .setSingleChoiceItems(new String[]{"HTTP CONNECT", "HTTPS CONNECT", "SOCKS5"},
                        proxyProtocol, (dialog, which) -> form.setTag(which))
                .setView(form)
                .setNegativeButton("Hủy", null)
                .setNeutralButton("Tắt proxy", (dialog, which) -> updateProxy("", "", 0))
                .setPositiveButton("Áp dụng", (dialog, which) -> {
                    int kind = form.getTag() instanceof Integer ? (Integer) form.getTag() : proxyProtocol;
                    updateProxy(hostField.getText().toString().trim(), portField.getText().toString().trim(), kind);
                }).show();
    }

    private void updateProxy(String host, String port, int protocol) {
        if (!host.isEmpty() || !port.isEmpty()) {
            try {
                if (host.isEmpty() || !host.matches("([A-Za-z0-9.\\-]+|\\[[0-9a-fA-F:]+\\])")
                        || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                message("Nhập địa chỉ hợp lệ và cổng từ 1 đến 65535.");
                return;
            }
        }
        proxyBusy = true;
        for (Browser browser : browsers) browser.close();
        showTab();
        Executor ui = this::runOnUiThread;
        Runnable done = () -> {
            if (detached) return;
            proxyHost = host;
            proxyPort = port;
            proxyProtocol = protocol;
            proxyBusy = false;
            if (tab == 1) showTab();
        };
        try {
            if (host.isEmpty()) ProxyController.getInstance().clearProxyOverride(ui, done);
            else {
                ProxyConfig config = new ProxyConfig.Builder().addProxyRule(PROTOCOLS[protocol]
                        + "://" + host + ":" + port).build();
                ProxyController.getInstance().setProxyOverride(config, ui, done);
            }
        } catch (RuntimeException failure) {
            proxyBusy = false;
            if (tab == 1) showTab();
            message("Không áp dụng được cấu hình proxy.");
        }
    }

    private static final class NetworkEntry {
        final String method, url, token, game, host;
        final long time;
        NetworkEntry(String method, String url, String token, String game, String host) {
            this.method = method; this.url = url; this.token = token;
            this.game = game; this.host = host;
            this.time = System.currentTimeMillis();
        }
    }

    public class JsBridge {
        final Browser browser;
        JsBridge(Browser browser) { this.browser = browser; }
        @JavascriptInterface
        public void capture(String url, String method, String headersJson, String body) {
            recordNetwork(browser, url, method, headersJson, body);
        }
    }

    private void recordNetwork(Browser browser, String url, String method, String headersJson, String body) {
        if (browser == null) return;
        if (url == null) url = "";
        if (method == null) method = "";
        String token = null, game = null;
        if (headersJson != null && !headersJson.isEmpty()) {
            try {
                JSONObject h = new JSONObject(headersJson);
                for (Iterator<String> it = h.keys(); it.hasNext(); ) {
                    String k = it.next();
                    String v = h.optString(k, "");
                    if ("x-authentication-token".equalsIgnoreCase(k)) token = v;
                    else if ("game-identifier".equalsIgnoreCase(k)) game = v;
                }
            } catch (Exception ignored) {}
        }
        if (token != null && !token.isEmpty()) {
            browser.capturedToken = token;
            browser.capturedAt = System.currentTimeMillis();
        }
        Set<String> found = new LinkedHashSet<>();
        scanRooms(url, found);
        if (body != null && !body.isEmpty()) scanRooms(body, found);
        if (!found.isEmpty()) browser.capturedRooms.addAll(found);
        if ((token != null && !token.isEmpty()) || !found.isEmpty()) {
            Uri u = Uri.parse(url);
            browser.networkLog.add(new NetworkEntry(method, url, token, game, u.getHost()));
            while (browser.networkLog.size() > 100) browser.networkLog.remove(0);
        }
    }

    private static void scanRooms(String text, Set<String> out) {
        if (text == null || text.isEmpty()) return;
        Matcher m1 = ROOM_IN_JSON.matcher(text);
        while (m1.find()) {
            String v = m1.group(1);
            if (v != null && !v.isEmpty()) out.add(v);
        }
        Matcher m2 = ROOM_IN_URL.matcher(text);
        while (m2.find()) {
            String v = m2.group(1) != null ? m2.group(1) : m2.group(2);
            if (v != null && !v.isEmpty()) out.add(v);
        }
    }

    private class Browser {
        final String name, account;
        String address = "", customAgent = "", debugStatus = "";
        WebView web;

        final List<NetworkEntry> networkLog = Collections.synchronizedList(new ArrayList<>());
        final Set<String> capturedRooms = Collections.synchronizedSet(new LinkedHashSet<>());
        volatile String capturedToken = "";
        volatile long capturedAt = 0L;

        private void markPage(String status, Uri uri) {
            String host = uri.getHost();
            debugStatus = host == null || host.isEmpty() ? status : status + " · " + host;
        }
        Browser(String name, String account) { this.name = name; this.account = account; }

        WebView get() {
            if (web != null) return web;
            WebView created = new WebView(MainActivity.this);
            try {
                if (multiProfile) WebViewCompat.setProfile(created, "room-monitor-" + account);
                WebSettings settings = created.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                settings.setAllowFileAccess(false);
                settings.setAllowContentAccess(false);
                settings.setJavaScriptCanOpenWindowsAutomatically(false);
                if (!customAgent.isEmpty()) settings.setUserAgentString(customAgent);
                created.setBackgroundColor(panel);

                created.addJavascriptInterface(new JsBridge(this), "RMNative");

                created.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return request.isForMainFrame() && !allowed(request.getUrl());
                    }
                    @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                        markPage("Đang tải trang", Uri.parse(url));
                    }
                    @Override public void onPageFinished(WebView view, String url) {
                        if (debugStatus.startsWith("Đang tải trang")) markPage("Đã tải trang", Uri.parse(url));
                        view.evaluateJavascript(CAPTURE_JS, null);
                    }
                    @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                        if (request.isForMainFrame()) markPage("HTTP " + response.getStatusCode(), request.getUrl());
                    }
                    @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        if (request.isForMainFrame()) {
                            markPage("Không tải được trang", request.getUrl());
                            message(name + " · Không tải được trang.");
                        }
                    }
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        try {
                            Map<String, String> headers = request.getRequestHeaders();
                            String token = null, game = null;
                            if (headers != null) {
                                for (Map.Entry<String, String> e : headers.entrySet()) {
                                    String k = e.getKey();
                                    if (k == null) continue;
                                    if ("x-authentication-token".equalsIgnoreCase(k)) token = e.getValue();
                                    else if ("game-identifier".equalsIgnoreCase(k)) game = e.getValue();
                                }
                            }
                            String url = request.getUrl().toString();
                            Set<String> found = new LinkedHashSet<>();
                            scanRooms(url, found);
                            if (token != null && !token.isEmpty()) {
                                capturedToken = token;
                                capturedAt = System.currentTimeMillis();
                            }
                            if (!found.isEmpty()) capturedRooms.addAll(found);
                            if ((token != null && !token.isEmpty()) || !found.isEmpty()) {
                                networkLog.add(new NetworkEntry(request.getMethod(), url, token, game,
                                        request.getUrl().getHost()));
                                while (networkLog.size() > 100) networkLog.remove(0);
                            }
                        } catch (RuntimeException ignored) {}
                        return null;
                    }
                });
                web = created;
                return web;
            } catch (RuntimeException failure) {
                created.destroy();
                throw failure;
            }
        }

        void open() {
            if (proxyBusy || web == null) return;
            String value = address.trim();
            try {
                URI uri = new URI(value.contains("://") ? value : "https://" + value);
                if (uri.getRawUserInfo() != null || !allowed(Uri.parse(uri.toString()))) throw new IllegalArgumentException();
                web.loadUrl(uri.toString());
            } catch (Exception failure) {
                message("Nhập địa chỉ HTTPS hợp lệ.");
            }
        }

        void applyAgent(String value) {
            customAgent = value;
            if (web != null) {
                web.stopLoading();
                web.getSettings().setUserAgentString(value.isEmpty() ? null : value);
                if (web.getUrl() != null) web.reload();
            }
            if (tab == 1) showTab();
        }

        void close() {
            if (web == null) return;
            detach(web);
            web.stopLoading();
            web.destroy();
            web = null;
        }
    }

    private static boolean allowed(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && !uri.getHost().isEmpty() && uri.getUserInfo() == null;
    }

    private EditText addInput(LinearLayout target, String name, String value,
                              boolean secret, boolean multiple, Consumer<String> onChange) {
        gap(target, text(name, 13, muted, true), 4);
        EditText input = field("", value, secret, multiple);
        input.setContentDescription(name);
        input.setEnabled(!monitor.running);
        observe(input, onChange);
        target.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private EditText field(String hint, String value, boolean secret, boolean multiple) {
        EditText input = new EditText(this);
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setTextSize(14);
        input.setPadding(dp(7), dp(3), dp(7), dp(3));
        input.setBackgroundTintList(ColorStateList.valueOf(border));
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : multiple ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        if (multiple) input.setMinLines(2);
        else input.setSingleLine(true);
        input.setText(value);
        input.setSaveEnabled(false);
        if (secret) input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return input;
    }

    private static void observe(EditText input, Consumer<String> onChange) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                onChange.accept(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });
    }

    private LinearLayout scrollSection(int initialScroll) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout sections = column();
        sections.setPadding(dp(12), dp(10), dp(12), dp(12));
        scroll.addView(sections);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        if (initialScroll != 0) scroll.post(() -> scroll.scrollTo(0, initialScroll));
        return sections;
    }

    private TextView title(String title) { return text(title, 23, ink, true); }
    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(11), dp(9), dp(11), dp(10));
        card.setBackground(shape(panel, 14, border));
        return card;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private TextView text(String label, int size, int tone, boolean strong) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(tone);
        if (strong) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label, int background, int tone, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(tone);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(44));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(shape(background, 9, 0));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private GradientDrawable shape(int fill, int radius, int edge) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (edge != 0) shape.setStroke(dp(1), edge);
        return shape;
    }

    private void gap(LinearLayout container, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        container.addView(view, params);
    }

    private void addWeighted(LinearLayout container, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.leftMargin = dp(margin);
        container.addView(view, params);
    }

    private void addPane(LinearLayout container, View view, boolean horizontal, int margin) {
        LinearLayout.LayoutParams params = horizontal
                ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, 0, 1);
        if (horizontal) params.leftMargin = dp(margin);
        else params.topMargin = dp(margin);
        container.addView(view, params);
    }

    private static void detach(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    private static int color(String value) { return Color.parseColor(value); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void message(String text) {
        if (!detached && !isFinishing()) android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show();
    }
}
