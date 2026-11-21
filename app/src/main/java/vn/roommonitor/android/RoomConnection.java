package vn.roommonitor.android;

import android.os.Handler;
import android.os.Looper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Quản lý kết nối dữ liệu phòng, số dư tài khoản và thống kê ván.
 */
public class RoomConnection {
    public interface Listener {
        void onUpdate();
    }

    public boolean running = false;
    public boolean loadingRooms = false;
    public String status = "Sẵn sàng";
    public BigDecimal balanceA = BigDecimal.ZERO;
    public BigDecimal balanceB = BigDecimal.ZERO;
    public final int[] counts = new int[]{0, 0, 0, 0}; // 0: Player, 1: Banker, 2: Tie, 3: Khác
    public final List<String> journal = Collections.synchronizedList(new ArrayList<>());

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isVisible = true;

    public RoomConnection(Listener listener) {
        this.listener = listener;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
    }

    public static boolean validToken(String token) {
        return token != null && token.trim().length() >= 8;
    }

    public static Set<String> parseRooms(String text) {
        Set<String> set = new LinkedHashSet<>();
        if (text == null) return set;
        String[] lines = text.split("[\r\n,;]+");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    public static String outcomeLabel(int index) {
        switch (index) {
            case 0: return "Cửa A (Con / Player)";
            case 1: return "Cửa B (Cái / Banker)";
            case 2: return "Hòa (Tie)";
            default: return "Khác / Bỏ qua";
        }
    }

    public void start(String tokenA, String tokenB, Set<String> rooms, String playerId, String lobbyId) {
        running = true;
        status = "Đang theo dõi " + rooms.size() + " phòng";
        log("Bắt đầu phiên theo dõi. Số phòng: " + rooms.size());
        notifyUpdate();

        // Giả lập cập nhật trạng thái kết nối
        mainHandler.postDelayed(() -> {
            if (!running) return;
            status = "Đã kết nối máy chủ";
            log("Kết nối luồng dữ liệu WebSocket thành công.");
            notifyUpdate();
        }, 1200);
    }

    public void stop() {
        running = false;
        status = "Đã dừng";
        log("Đã dừng phiên theo dõi.");
        notifyUpdate();
    }

    public void loadRooms(String tokenA, Consumer<List<String>> onSuccess, Consumer<String> onError) {
        loadingRooms = true;
        notifyUpdate();

        // Thực hiện tải danh sách phòng ngầm
        mainHandler.postDelayed(() -> {
            loadingRooms = false;
            notifyUpdate();
            // Mẫu danh sách phòng ví dụ
            List<String> mockRooms = Arrays.asList("101", "102", "103", "105", "201", "202");
            onSuccess.accept(mockRooms);
        }, 1500);
    }

    public void log(String message) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        String timeStr = sdf.format(new java.util.Date());
        journal.add("[" + timeStr + "] " + message);
        if (journal.size() > 200) {
            journal.remove(0);
        }
    }

    public void destroy() {
        running = false;
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void notifyUpdate() {
        if (listener != null) {
            mainHandler.post(listener::onUpdate);
        }
    }
}
