package com.sinhvien.appqlkhachsan.admin;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sinhvien.appqlkhachsan.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class StatisticsActivity extends AppCompatActivity {

    private static final String TAG = "StatisticsActivity";

    private FirebaseFirestore db;
    private TextView tvTotalRevenue, tvTotalBookings, tvCancelledBookings, tvTotalAccounts;
    private TextView tvMostBookedRoom, tvLeastBookedRoom;
    private BarChart barChartRoomRevenue;
    private PieChart pieChartRoomStatus;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        db = FirebaseFirestore.getInstance();

        // Ánh xạ Views
        toolbar = findViewById(R.id.toolbar);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvTotalBookings = findViewById(R.id.tvTotalBookings);
        tvCancelledBookings = findViewById(R.id.tvCancelledBookings);
        // THAY ĐỔI: Sử dụng TextView mới cho tổng tài khoản
        tvTotalAccounts = findViewById(R.id.tvTotalReceptionists); // ID trong XML vẫn giữ nguyên để tránh lỗi
        barChartRoomRevenue = findViewById(R.id.barChartRoomRevenue);
        pieChartRoomStatus = findViewById(R.id.pieChartRoomStatus);
        tvMostBookedRoom = findViewById(R.id.tvMostBookedRoom);
        tvLeastBookedRoom = findViewById(R.id.tvLeastBookedRoom);

        // Cài đặt Toolbar
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Tải dữ liệu
        loadAllStatistics();
    }

    private void loadAllStatistics() {
        loadTotalRevenue();
        loadBookingStats();
        // THAY ĐỔI: Gọi hàm đếm tất cả tài khoản
        loadTotalUserAccounts();
        loadRoomRevenueStats();
        loadRoomUsageStats();
        loadMostAndLeastBookedRooms();
    }

    private void loadTotalRevenue() {
        db.collection("invoices")
                .whereEqualTo("TrangThai", "Hoàn thành")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    double totalRevenue = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double revenue = doc.getDouble("TongGia");
                        if (revenue != null) {
                            totalRevenue += revenue;
                        }
                    }
                    NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                    tvTotalRevenue.setText(currencyFormat.format(totalRevenue));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading total revenue", e));
    }

    private void loadBookingStats() {
        db.collection("bookings")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long totalBookings = queryDocumentSnapshots.size();
                    long cancelledBookings = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        if ("Đã hủy".equals(doc.getString("TrangThaiDD"))) {
                            cancelledBookings++;
                        }
                    }
                    tvTotalBookings.setText(String.valueOf(totalBookings));
                    tvCancelledBookings.setText(String.valueOf(cancelledBookings));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading booking stats", e));
    }

    // THAY ĐỔI: Hàm này giờ sẽ đếm tất cả người dùng
    private void loadTotalUserAccounts() {
        // Lấy dữ liệu từ file bạn cung cấp
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long totalAccounts = queryDocumentSnapshots.size();
                    tvTotalAccounts.setText(String.valueOf(totalAccounts));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading user accounts count", e));
    }

    // FIX: Sửa lỗi chỉ hiển thị phòng có doanh thu
    private void loadRoomRevenueStats() {
        // Map để lưu doanh thu theo TÊN LOẠI PHÒNG (Standard, VIP, Deluxe)
        Map<String, Double> roomTypeRevenueMap = new HashMap<>();
        roomTypeRevenueMap.put("Standard", 0.0);
        roomTypeRevenueMap.put("VIP", 0.0);
        roomTypeRevenueMap.put("Deluxe", 0.0);

        // Map để tra cứu MaLoaiPhong từ MaPhong
        Map<Integer, Integer> roomIdToTypeIdMap = new HashMap<>();
        // Map để tra cứu Tên loại phòng từ MaLoaiPhong
        Map<Integer, String> typeIdToNameMap = new HashMap<>();
        typeIdToNameMap.put(1, "Standard");
        typeIdToNameMap.put(2, "VIP");
        typeIdToNameMap.put(3, "Deluxe");

        // Bước 1: Lấy thông tin tất cả các phòng để xây dựng map tra cứu
        db.collection("rooms").get().addOnSuccessListener(roomSnapshots -> {
            for (QueryDocumentSnapshot roomDoc : roomSnapshots) {
                // Lấy MaPhong và MaLoaiPhong từ mỗi phòng
                if (roomDoc.getLong("MaPhong") != null && roomDoc.getLong("MaLoaiPhong") != null) {
                    int roomId = roomDoc.getLong("MaPhong").intValue();
                    int typeId = roomDoc.getLong("MaLoaiPhong").intValue();
                    roomIdToTypeIdMap.put(roomId, typeId);
                }
            }


            db.collection("invoices")
                    .whereEqualTo("TrangThai", "Hoàn thành")
                    .get()
                    .addOnSuccessListener(invoiceSnapshots -> {
                        for (QueryDocumentSnapshot invoiceDoc : invoiceSnapshots) {
                            Integer roomId = invoiceDoc.getLong("MaPhong") != null ? invoiceDoc.getLong("MaPhong").intValue() : null;
                            Double revenue = invoiceDoc.getDouble("TongGia");

                            if (roomId != null && revenue != null) {

                                Integer typeId = roomIdToTypeIdMap.get(roomId);
                                if (typeId != null) {
                                    // Từ MaLoaiPhong, tìm ra tên loại phòng (Standard, VIP, Deluxe)
                                    String typeName = typeIdToNameMap.get(typeId);
                                    if (typeName != null) {

                                        roomTypeRevenueMap.put(typeName, roomTypeRevenueMap.get(typeName) + revenue);
                                    }
                                }
                            }
                        }

                        setupBarChart(roomTypeRevenueMap);
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error loading invoices for revenue", e));
        }).addOnFailureListener(e -> Log.e(TAG, "Error loading rooms to map types", e));
    }

    private void setupBarChart(Map<String, Double> roomRevenueMap) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>(roomRevenueMap.keySet());
        int index = 0;
        for (String roomName : labels) {
            entries.add(new BarEntry(index, roomRevenueMap.get(roomName).floatValue()));
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu (VNĐ)");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);

        BarData barData = new BarData(dataSet);
        barChartRoomRevenue.setData(barData);
        barChartRoomRevenue.getDescription().setEnabled(false);
        barChartRoomRevenue.setFitBars(true);

        XAxis xAxis = barChartRoomRevenue.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setLabelRotationAngle(-45);

        barChartRoomRevenue.animateY(1000);
        barChartRoomRevenue.invalidate();
    }


    private void loadRoomUsageStats() {
        db.collection("rooms")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Map<String, Integer> statusCount = new HashMap<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String status = doc.getString("TrangThai");
                        if (status != null) {
                            statusCount.put(status, statusCount.getOrDefault(status, 0) + 1);
                        }
                    }
                    setupPieChart(statusCount);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading room usage stats", e));
    }

    private void setupPieChart(Map<String, Integer> statusCount) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : statusCount.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Tỉ lệ sử dụng phòng");
        dataSet.setColors(ColorTemplate.JOYFUL_COLORS);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(14f);

        PieData pieData = new PieData(dataSet);
        pieChartRoomStatus.setData(pieData);
        pieChartRoomStatus.getDescription().setEnabled(false);
        pieChartRoomStatus.setCenterText("Trạng thái phòng");
        pieChartRoomStatus.animateY(1000);
        pieChartRoomStatus.invalidate();
    }

    // FIX: Sửa lỗi "ít nhất" hiện null
    private void loadMostAndLeastBookedRooms() {
        Map<Integer, Integer> roomBookingCount = new HashMap<>();
        Map<Integer, String> roomNames = new HashMap<>();

        db.collection("rooms").get().addOnSuccessListener(roomSnapshots -> {
            if (roomSnapshots.isEmpty()) {
                tvMostBookedRoom.setText("Nhiều nhất: Chưa có");
                tvLeastBookedRoom.setText("Ít nhất: Chưa có");
                return;
            }

            // Bước 1: Khởi tạo tất cả các phòng với 0 lượt đặt và lấy tên
            for (QueryDocumentSnapshot doc : roomSnapshots) {
                int roomId = Objects.requireNonNull(doc.getLong("MaPhong")).intValue();
                String roomName = doc.getString("TenPhong");
                roomBookingCount.put(roomId, 0);
                roomNames.put(roomId, roomName);
            }

            // Bước 2: Đếm lượt đặt từ collection "bookings"
            db.collection("bookings").get().addOnSuccessListener(bookingSnapshots -> {
                for (QueryDocumentSnapshot doc : bookingSnapshots) {
                    Integer roomId = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : null;
                    if (roomId != null && roomBookingCount.containsKey(roomId)) {
                        roomBookingCount.put(roomId, roomBookingCount.get(roomId) + 1);
                    }
                }

                // Bước 3: Tìm phòng nhiều nhất và ít nhất từ map đã đầy đủ
                if (roomBookingCount.isEmpty()) {
                    tvMostBookedRoom.setText("Nhiều nhất: N/A");
                    tvLeastBookedRoom.setText("Ít nhất: N/A");
                    return;
                }

                Map.Entry<Integer, Integer> mostBooked = Collections.max(roomBookingCount.entrySet(), Map.Entry.comparingByValue());
                Map.Entry<Integer, Integer> leastBooked = Collections.min(roomBookingCount.entrySet(), Map.Entry.comparingByValue());

                String mostBookedRoomName = roomNames.get(mostBooked.getKey());
                String leastBookedRoomName = roomNames.get(leastBooked.getKey());

                tvMostBookedRoom.setText("Nhiều nhất: " + mostBookedRoomName + " (" + mostBooked.getValue() + " lần)");
                tvLeastBookedRoom.setText("Ít nhất: " + leastBookedRoomName + " (" + leastBooked.getValue() + " lần)");

            }).addOnFailureListener(e -> Log.e(TAG, "Error loading bookings for stats", e));

        }).addOnFailureListener(e -> Log.e(TAG, "Error loading rooms for stats", e));
    }
}