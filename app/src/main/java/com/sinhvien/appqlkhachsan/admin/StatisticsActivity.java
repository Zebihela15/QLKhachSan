package com.sinhvien.appqlkhachsan.admin;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.sinhvien.appqlkhachsan.R;
import com.sinhvien.appqlkhachsan.migration.InitializeFirestoreData;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {
    private static final String TAG = "StatisticsActivity";
    private FirebaseFirestore db;
    private EditText datePickerStart, datePickerEnd;
    private TextView tvTotalRevenue, tvServiceRevenue, tvTotalBookings, tvCancellationRate, tvBookingSources, tvAvgBookingLeadTime;
    private TextView tvCustomerNewVsReturning, tvMostBookedRoom, tvLeastBookedRoom, tvCheckInOut;
    private WebView chartRevenueByRoomType, chartOccupancyRate;
    private Map<Integer, Double> roomTypePrices = new HashMap<>();
    private Map<Integer, String> roomTypeNames = new HashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isActive = true;
    private Calendar startDate, endDate;
    private final DecimalFormat currencyFormat = new DecimalFormat("#,###,### VNĐ");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        db = FirebaseFirestore.getInstance();

        // Initialize UI components
        datePickerStart = findViewById(R.id.datePickerStart);
        datePickerEnd = findViewById(R.id.datePickerEnd);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvServiceRevenue = findViewById(R.id.tvServiceRevenue);
        tvTotalBookings = findViewById(R.id.tvTotalBookings);
        tvCancellationRate = findViewById(R.id.tvCancellationRate);
        tvBookingSources = findViewById(R.id.tvBookingSources);
        tvAvgBookingLeadTime = findViewById(R.id.tvAvgBookingLeadTime);
        tvCustomerNewVsReturning = findViewById(R.id.tvCustomerNewVsReturning);
        tvMostBookedRoom = findViewById(R.id.tvMostBookedRoom);
        tvLeastBookedRoom = findViewById(R.id.tvLeastBookedRoom);
        tvCheckInOut = findViewById(R.id.tvCheckInOut);
        chartRevenueByRoomType = findViewById(R.id.chartRevenueByRoomType);
        chartOccupancyRate = findViewById(R.id.chartOccupancyRate);

        // Configure WebViews
        chartRevenueByRoomType.getSettings().setJavaScriptEnabled(true);
        chartRevenueByRoomType.getSettings().setDomStorageEnabled(true);
        chartRevenueByRoomType.getSettings().setLoadWithOverviewMode(true);
        chartRevenueByRoomType.getSettings().setUseWideViewPort(true);
        chartRevenueByRoomType.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e(TAG, "WebView error: " + error.getDescription());
                runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải biểu đồ: " + error.getDescription(), Snackbar.LENGTH_LONG).show());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebView page finished loading: " + url);
            }
        });
        chartRevenueByRoomType.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "WebView console: " + consoleMessage.message() + " at line " + consoleMessage.lineNumber());
                return true;
            }
        });

        chartOccupancyRate.getSettings().setJavaScriptEnabled(true);
        chartOccupancyRate.getSettings().setDomStorageEnabled(true);
        chartOccupancyRate.getSettings().setLoadWithOverviewMode(true);
        chartOccupancyRate.getSettings().setUseWideViewPort(true);
        chartOccupancyRate.setWebViewClient(new WebViewClient());
        chartOccupancyRate.setWebChromeClient(new WebChromeClient());

        // Initialize Firestore data
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean dataGenerated = prefs.getBoolean("sampleDataGenerated", false);
        if (!dataGenerated) {
            InitializeFirestoreData initializer = new InitializeFirestoreData();
            initializer.initializeData();
            generateSampleData();
            prefs.edit().putBoolean("sampleDataGenerated", true).apply();
        }

        // Initialize room names and prices
        initializeRoomNames();
        loadRoomTypesFromFirestore();

        // Set default date range (current month)
        startDate = Calendar.getInstance();
        startDate.set(Calendar.DAY_OF_MONTH, 1);
        endDate = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        datePickerStart.setText(sdf.format(startDate.getTime()));
        datePickerEnd.setText(sdf.format(endDate.getTime()));

        // Date picker listeners
        datePickerStart.setOnClickListener(v -> showDatePicker(datePickerStart, true));
        datePickerEnd.setOnClickListener(v -> showDatePicker(datePickerEnd, false));

        // Load statistics
        loadStatistics();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void generateSampleData() {
        Log.d(TAG, "Tạo dữ liệu mẫu Firestore bổ sung...");
        // Customers
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> customer = new HashMap<>();
            customer.put("MaKH", "KH00" + i);
            customer.put("TenKH", "Khách " + i);
            customer.put("BookingCount", i % 2 == 0 ? 2 : 1);
            db.collection("customers").document("KH00" + i).set(customer);
        }

        // Services
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> service = new HashMap<>();
            service.put("Date", "2025-07-2" + i);
            service.put("Cost", 100000.0 * i);
            db.collection("services").add(service);
        }

        // Bookings
        int[] maPhongs = {101, 102, 201, 202, 301, 302};
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> booking = new HashMap<>();
            booking.put("MaPhong", maPhongs[i % 6]);
            booking.put("TGCheckin", "2025-07-2" + (i % 5 + 1));
            booking.put("TGCheckout", "2025-07-2" + (i % 5 + 2));
            booking.put("TrangThaiDD", (i % 4 == 0) ? "Hủy" : i % 3 == 0 ? "Trả phòng" : "Đã nhận phòng");
            booking.put("BookingDate", "2025-07-2" + (i % 5));
            booking.put("Source", "Trực tiếp");
            db.collection("bookings").add(booking);
        }
        Log.d(TAG, "Đã tạo xong dữ liệu mẫu bổ sung");
    }

    private void initializeRoomNames() {
        roomTypeNames.put(1, "Standard");
        roomTypeNames.put(2, "VIP");
        roomTypeNames.put(3, "Deluxe");
    }

    private void showDatePicker(EditText editText, boolean isStart) {
        Calendar calendar = isStart ? startDate : endDate;
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    editText.setText(sdf.format(calendar.getTime()));
                    loadStatistics();
                }, year, month, day);
        datePickerDialog.show();
    }

    private void loadRoomTypesFromFirestore() {
        executorService.execute(() -> {
            db.collection("room_types").get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && isActive) {
                    QuerySnapshot result = task.getResult();
                    if (result != null && !result.isEmpty()) {
                        Log.d(TAG, "Tải room_types thành công, số lượng: " + result.size());
                        for (DocumentSnapshot doc : result.getDocuments()) {
                            int maLoaiPhong = doc.getLong("MaLoaiPhong") != null ? doc.getLong("MaLoaiPhong").intValue() : 1;
                            Double giaPhong = doc.getDouble("GiaPhong");
                            if (giaPhong != null) {
                                roomTypePrices.put(maLoaiPhong, giaPhong);
                                Log.d(TAG, "Room type: " + maLoaiPhong + ", Price: " + giaPhong);
                            }
                        }
                        runOnUiThread(() -> {
                            Snackbar.make(findViewById(android.R.id.content), "Đã tải giá loại phòng", Snackbar.LENGTH_SHORT).show();
                            loadStatistics();
                        });
                    } else {
                        Log.w(TAG, "Không có dữ liệu room_types");
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Không có dữ liệu room_types", Snackbar.LENGTH_LONG).show());
                    }
                } else {
                    Log.e(TAG, "Lỗi tải room_types: ", task.getException());
                    runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải room_types: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"), Snackbar.LENGTH_LONG).show());
                }
            });
        });
    }

    private void loadStatistics() {
        final String start = datePickerStart.getText().toString();
        final String end = datePickerEnd.getText().toString();
        Log.d(TAG, "Tải thống kê từ " + start + " đến " + end);
        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Đang tải thống kê...", Snackbar.LENGTH_SHORT).show());

        executorService.execute(() -> {
            // Total Revenue
            db.collection("bookings")
                    .whereEqualTo("TrangThaiDD", "Đã nhận phòng")
                    .whereGreaterThanOrEqualTo("TGCheckin", start)
                    .whereLessThanOrEqualTo("TGCheckout", end)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        Log.d(TAG, "Tải bookings thành công, số lượng: " + querySnapshot.size());
                        double totalRevenue = calculateRevenue(querySnapshot);
                        runOnUiThread(() -> {
                            tvTotalRevenue.setText(currencyFormat.format(totalRevenue));
                            Snackbar.make(findViewById(android.R.id.content), "Đã tải doanh thu: " + currencyFormat.format(totalRevenue), Snackbar.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi tải total revenue: ", e);
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải doanh thu: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    });

            // Service Revenue
            db.collection("services")
                    .whereGreaterThanOrEqualTo("Date", start)
                    .whereLessThanOrEqualTo("Date", end)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        Log.d(TAG, "Tải services thành công, số lượng: " + querySnapshot.size());
                        double serviceRevenue = 0;
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Double cost = doc.getDouble("Cost");
                            if (cost != null) serviceRevenue += cost;
                        }
                        final double finalServiceRevenue = serviceRevenue;
                        runOnUiThread(() -> {
                            tvServiceRevenue.setText(currencyFormat.format(finalServiceRevenue));
                            Snackbar.make(findViewById(android.R.id.content), "Đã tải doanh thu dịch vụ: " + currencyFormat.format(finalServiceRevenue), Snackbar.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi tải service revenue: ", e);
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải doanh thu dịch vụ: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    });

            // Revenue by Room Type
            Map<String, Double> revenueByRoomType = new HashMap<>();
            revenueByRoomType.put("Standard", 0.0);
            revenueByRoomType.put("VIP", 0.0);
            revenueByRoomType.put("Deluxe", 0.0);

            db.collection("bookings")
                    .whereEqualTo("TrangThaiDD", "Đã nhận phòng")
                    .whereGreaterThanOrEqualTo("TGCheckin", start)
                    .whereLessThanOrEqualTo("TGCheckout", end)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        Log.d(TAG, "Tải bookings cho doanh thu loại phòng, số lượng: " + querySnapshot.size());
                        if (querySnapshot.isEmpty()) {
                            Log.d(TAG, "Không có bookings, hiển thị biểu đồ trống");
                            runOnUiThread(() -> displayRevenueByRoomTypeChart(revenueByRoomType));
                            return;
                        }

                        int[] pendingQueries = {querySnapshot.size()};
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            int maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
                            Log.d(TAG, "Xử lý booking, MaPhong: " + maPhong);
                            db.collection("rooms")
                                    .document(String.valueOf(maPhong))
                                    .get()
                                    .addOnSuccessListener(roomSnapshot -> {
                                        if (roomSnapshot.exists() && isActive) {
                                            int maLoaiPhong = roomSnapshot.getLong("MaLoaiPhong") != null ?
                                                    roomSnapshot.getLong("MaLoaiPhong").intValue() : 1;
                                            String roomType = roomTypeNames.getOrDefault(maLoaiPhong, "Standard");
                                            double revenue = calculateRevenueForSingleBooking(doc);
                                            synchronized (revenueByRoomType) {
                                                revenueByRoomType.compute(roomType, (k, v) -> (v == null ? 0 : v) + revenue);
                                            }
                                            Log.d(TAG, "Cập nhật doanh thu " + roomType + ": " + revenueByRoomType.get(roomType));
                                        } else {
                                            Log.w(TAG, "Không tìm thấy phòng với MaPhong: " + maPhong);
                                        }
                                        synchronized (pendingQueries) {
                                            pendingQueries[0]--;
                                            if (pendingQueries[0] == 0) {
                                                Log.d(TAG, "Hoàn tất truy vấn, doanh thu: " + revenueByRoomType);
                                                runOnUiThread(() -> displayRevenueByRoomTypeChart(new HashMap<>(revenueByRoomType)));
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Lỗi tải room data cho MaPhong " + maPhong + ": ", e);
                                        synchronized (pendingQueries) {
                                            pendingQueries[0]--;
                                            if (pendingQueries[0] == 0) {
                                                Log.d(TAG, "Hoàn tất truy vấn (có lỗi), doanh thu: " + revenueByRoomType);
                                                runOnUiThread(() -> displayRevenueByRoomTypeChart(new HashMap<>(revenueByRoomType)));
                                            }
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi tải bookings cho doanh thu loại phòng: ", e);
                        runOnUiThread(() -> {
                            Snackbar.make(findViewById(android.R.id.content), "Lỗi tải doanh thu loại phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                            displayRevenueByRoomTypeChart(revenueByRoomType);
                        });
                    });

            // Occupancy Rate
            db.collection("rooms").get().addOnSuccessListener(roomSnapshot -> {
                int totalRooms = roomSnapshot.size();
                Log.d(TAG, "Tổng số phòng: " + totalRooms);
                db.collection("bookings")
                        .whereEqualTo("TrangThaiDD", "Đã nhận phòng")
                        .whereGreaterThanOrEqualTo("TGCheckin", start)
                        .whereLessThanOrEqualTo("TGCheckout", end)
                        .get()
                        .addOnSuccessListener(bookingSnapshot -> {
                            int occupiedRooms = bookingSnapshot.size();
                            double occupancyRate = totalRooms > 0 ? (double) occupiedRooms / totalRooms * 100 : 0;
                            Log.d(TAG, "Phòng đã sử dụng: " + occupiedRooms + ", Tỷ lệ sử dụng: " + occupancyRate);
                            if (isActive) {
                                runOnUiThread(() -> {
                                    displayOccupancyRateChart(occupancyRate);
                                    Snackbar.make(findViewById(android.R.id.content), "Đã tải tỷ lệ sử dụng: " + String.format("%.1f%%", occupancyRate), Snackbar.LENGTH_SHORT).show();
                                });
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Lỗi tải occupancy data: ", e);
                            runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải tỷ lệ sử dụng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                        });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Lỗi tải total rooms: ", e);
                runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải tổng số phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
            });

            // Booking Statistics
            db.collection("bookings")
                    .whereGreaterThanOrEqualTo("TGCheckin", start)
                    .whereLessThanOrEqualTo("TGCheckout", end)
                    .get()
                    .addOnSuccessListener(bookingSnapshot -> {
                        Log.d(TAG, "Tải bookings cho thống kê đặt phòng, số lượng: " + bookingSnapshot.size());
                        int totalBookings = bookingSnapshot.size();
                        runOnUiThread(() -> tvTotalBookings.setText(String.valueOf(totalBookings)));

                        // Cancellation Rate
                        db.collection("bookings")
                                .whereEqualTo("TrangThaiDD", "Hủy")
                                .whereGreaterThanOrEqualTo("TGCheckin", start)
                                .whereLessThanOrEqualTo("TGCheckout", end)
                                .get()
                                .addOnSuccessListener(canceledBookings -> {
                                    int canceledCount = canceledBookings.size();
                                    double cancellationRate = totalBookings > 0 ? (double) canceledCount / totalBookings * 100 : 0;
                                    Log.d(TAG, "Đơn hủy: " + canceledCount + ", Tỷ lệ hủy: " + cancellationRate);
                                    runOnUiThread(() -> tvCancellationRate.setText(String.format(Locale.getDefault(), "%.2f%%", cancellationRate)));
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Lỗi tải cancellation rate: ", e));

                        // Booking Sources
                        Map<String, Integer> bookingSources = new HashMap<>();
                        for (DocumentSnapshot doc : bookingSnapshot.getDocuments()) {
                            String source = doc.getString("Source") != null ? doc.getString("Source") : "Trực tiếp";
                            bookingSources.put(source, bookingSources.getOrDefault(source, 0) + 1);
                        }
                        StringBuilder sourcesText = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : bookingSources.entrySet()) {
                            sourcesText.append(entry.getKey()).append(": ").append(entry.getValue());
                        }
                        Log.d(TAG, "Nguồn đặt phòng: " + sourcesText);
                        runOnUiThread(() -> tvBookingSources.setText(sourcesText.length() > 0 ? sourcesText.toString() : "Chưa có dữ liệu"));

                        // Average Booking Lead Time
                        long totalLeadTime = 0;
                        int validBookings = 0;
                        for (DocumentSnapshot doc : bookingSnapshot.getDocuments()) {
                            String bookingDate = doc.getString("BookingDate");
                            String checkIn = doc.getString("TGCheckin");
                            if (bookingDate != null && checkIn != null) {
                                try {
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                    long diff = sdf.parse(checkIn.split(" ")[0]).getTime() - sdf.parse(bookingDate.split(" ")[0]).getTime();
                                    totalLeadTime += diff / (1000 * 60 * 60 * 24);
                                    validBookings++;
                                } catch (Exception e) {
                                    Log.e(TAG, "Lỗi phân tích ngày: " + e.getMessage());
                                }
                            }
                        }
                        double avgLeadTime = validBookings > 0 ? (double) totalLeadTime / validBookings : 0;
                        Log.d(TAG, "Thời gian đặt trước trung bình: " + avgLeadTime + " ngày");
                        runOnUiThread(() -> tvAvgBookingLeadTime.setText(String.format(Locale.getDefault(), "%.1f ngày", avgLeadTime)));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi tải booking statistics: ", e);
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải thống kê đặt phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    });

            // Customer Statistics
            db.collection("customers").get().addOnSuccessListener(customerSnapshot -> {
                Log.d(TAG, "Tải customers thành công, số lượng: " + customerSnapshot.size());
                int newCustomers = 0;
                int returningCustomers = 0;

                for (DocumentSnapshot doc : customerSnapshot.getDocuments()) {
                    Long bookingCount = doc.getLong("BookingCount");
                    if (bookingCount != null && bookingCount > 1) {
                        returningCustomers++;
                    } else {
                        newCustomers++;
                    }
                }

                final int finalNewCustomers = newCustomers;
                final int finalReturningCustomers = returningCustomers;
                Log.d(TAG, "Khách mới: " + newCustomers + ", Khách cũ: " + returningCustomers);

                runOnUiThread(() -> tvCustomerNewVsReturning.setText(
                        String.format(Locale.getDefault(), "%d / %d", finalNewCustomers, finalReturningCustomers)
                ));
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Lỗi tải customer statistics: ", e);
                runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải thống kê khách hàng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
            });

            // Room Usage Statistics
            db.collection("bookings")
                    .whereGreaterThanOrEqualTo("TGCheckin", start)
                    .whereLessThanOrEqualTo("TGCheckout", end)
                    .get()
                    .addOnSuccessListener(bookingSnapshot -> {
                        Log.d(TAG, "Tải bookings cho thống kê sử dụng phòng, số lượng: " + bookingSnapshot.size());
                        Map<Integer, Integer> roomBookings = new HashMap<>();
                        for (DocumentSnapshot doc : bookingSnapshot.getDocuments()) {
                            int maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
                            roomBookings.put(maPhong, roomBookings.getOrDefault(maPhong, 0) + 1);
                        }
                        Log.d(TAG, "Số lần đặt phòng: " + roomBookings);
                        if (!roomBookings.isEmpty()) {
                            Map<Integer, Integer> roomBookingsCopy = new HashMap<>(roomBookings);
                            int mostBookedRoom = roomBookingsCopy.entrySet().stream()
                                    .max(Map.Entry.comparingByValue())
                                    .map(Map.Entry::getKey)
                                    .orElse(0);
                            int leastBookedRoom = roomBookingsCopy.entrySet().stream()
                                    .min(Map.Entry.comparingByValue())
                                    .map(Map.Entry::getKey)
                                    .orElse(0);
                            Log.d(TAG, "Phòng đặt nhiều nhất: " + mostBookedRoom + ", Phòng ít đặt nhất: " + leastBookedRoom);
                            db.collection("rooms").document(String.valueOf(mostBookedRoom)).get()
                                    .addOnSuccessListener(snapshot -> {
                                        if (snapshot.exists() && isActive) {
                                            String roomName = snapshot.getString("TenPhong");
                                            runOnUiThread(() -> tvMostBookedRoom.setText(roomName != null ? roomName : "Chưa có dữ liệu"));
                                        }
                                    });
                            db.collection("rooms").document(String.valueOf(leastBookedRoom)).get()
                                    .addOnSuccessListener(snapshot -> {
                                        if (snapshot.exists() && isActive) {
                                            String roomName = snapshot.getString("TenPhong");
                                            runOnUiThread(() -> tvLeastBookedRoom.setText(roomName != null ? roomName : "Chưa có dữ liệu"));
                                        }
                                    });
                        } else {
                            runOnUiThread(() -> {
                                tvMostBookedRoom.setText("Chưa có dữ liệu");
                                tvLeastBookedRoom.setText("Chưa có dữ liệu");
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi tải room usage statistics: ", e);
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải thống kê sử dụng phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    });

            // Check-in/Check-out
            db.collection("bookings")
                    .whereEqualTo("TrangThaiDD", "Đã nhận phòng")
                    .whereGreaterThanOrEqualTo("TGCheckin", start)
                    .get()
                    .addOnSuccessListener(checkInSnapshot -> {
                        int checkIns = checkInSnapshot.size();
                        Log.d(TAG, "Số check-in: " + checkIns);
                        db.collection("bookings")
                                .whereEqualTo("TrangThaiDD", "Trả phòng")
                                .whereGreaterThanOrEqualTo("TGCheckout", start)
                                .whereLessThanOrEqualTo("TGCheckout", end)
                                .get()
                                .addOnSuccessListener(checkOutSnapshot -> {
                                    int checkOuts = checkOutSnapshot.size();
                                    Log.d(TAG, "Số check-out: " + checkOuts);
                                    runOnUiThread(() -> tvCheckInOut.setText(String.format(Locale.getDefault(), "%d / %d", checkIns, checkOuts)));
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi tải check-out statistics: ", e);
                                    runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải thống kê check-out: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi tải check-in statistics: ", e);
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi tải thống kê check-in: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    });
        });
    }

    private double calculateRevenue(QuerySnapshot querySnapshot) {
        double totalRevenue = 0;
        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
            totalRevenue += calculateRevenueForSingleBooking(doc);
        }
        Log.d(TAG, "Tổng doanh thu tính toán: " + totalRevenue);
        return totalRevenue;
    }

    private double calculateRevenueForSingleBooking(DocumentSnapshot doc) {
        int maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
        String checkIn = doc.getString("TGCheckin");
        String checkOut = doc.getString("TGCheckout");
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar checkInDate = Calendar.getInstance();
            Calendar checkOutDate = Calendar.getInstance();
            checkInDate.setTime(sdf.parse(checkIn.split(" ")[0]));
            checkOutDate.setTime(sdf.parse(checkOut.split(" ")[0]));
            long diffInMillies = checkOutDate.getTimeInMillis() - checkInDate.getTimeInMillis();
            long days = diffInMillies / (1000 * 60 * 60 * 24);
            int maLoaiPhong = getMaLoaiPhong(maPhong);
            double price = roomTypePrices.getOrDefault(maLoaiPhong, 500000.0);
            double revenue = days * price;
            Log.d(TAG, "Doanh thu booking, MaPhong: " + maPhong + ", Số ngày: " + days + ", Giá: " + price + ", Doanh thu: " + revenue);
            return revenue;
        } catch (Exception e) {
            Log.e(TAG, "Lỗi tính doanh thu booking MaPhong " + maPhong + ": " + e.getMessage());
            return 0;
        }
    }

    private int getMaLoaiPhong(int maPhong) {
        try {
            DocumentSnapshot snapshot = db.collection("rooms")
                    .document(String.valueOf(maPhong))
                    .get()
                    .getResult();
            if (snapshot.exists()) {
                Long maLoaiPhongLong = snapshot.getLong("MaLoaiPhong");
                if (maLoaiPhongLong == null) {
                    Log.e(TAG, "MaLoaiPhong is null for MaPhong: " + maPhong);
                    return 1;
                }
                int maLoaiPhong = maLoaiPhongLong.intValue();
                Log.d(TAG, "Lấy MaLoaiPhong cho MaPhong " + maPhong + ": " + maLoaiPhong);
                return maLoaiPhong;
            } else {
                Log.e(TAG, "Không tìm thấy phòng với MaPhong: " + maPhong);
                return 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi lấy MaLoaiPhong cho MaPhong " + maPhong + ": ", e);
            return 1;
        }
    }

    private void displayRevenueByRoomTypeChart(Map<String, Double> revenueByRoomType) {
        Log.d(TAG, "Hiển thị biểu đồ doanh thu theo loại phòng: " + revenueByRoomType);

        if (!isNetworkAvailable()) {
            runOnUiThread(() -> {
                Snackbar.make(findViewById(android.R.id.content), "Không có kết nối mạng, không thể tải biểu đồ", Snackbar.LENGTH_LONG).show();
                chartRevenueByRoomType.loadData("<html><body><p>Không có kết nối mạng</p></body></html>", "text/html", "UTF-8");
            });
            return;
        }

        boolean hasData = revenueByRoomType.values().stream().anyMatch(value -> value > 0);
        if (!hasData) {
            Log.d(TAG, "Không có dữ liệu doanh thu, thử dữ liệu mẫu");
            revenueByRoomType.put("Standard", 5000000.0);
            revenueByRoomType.put("VIP", 3000000.0);
            revenueByRoomType.put("Deluxe", 7000000.0);
        }

        String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>" +
                "<style>" +
                "body { margin: 0; padding: 10px; }" +
                "canvas { width: 100% !important; height: 600px !important; max-height: 700px !important; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<canvas id='chart'></canvas>" +
                "<script>" +
                "const ctx = document.getElementById('chart').getContext('2d');" +
                "new Chart(ctx, {" +
                "type: 'bar'," +
                "data: {" +
                "labels: ['Standard', 'VIP', 'Deluxe']," +
                "datasets: [{" +
                "label: 'Doanh thu'," +
                "data: [" +
                revenueByRoomType.getOrDefault("Standard", 0.0) + "," +
                revenueByRoomType.getOrDefault("VIP", 0.0) + "," +
                revenueByRoomType.getOrDefault("Deluxe", 0.0) +
                "]," +
                "backgroundColor: ['rgba(75, 192, 192, 0.6)', 'rgba(255, 159, 64, 0.6)', 'rgba(153, 102, 255, 0.6)']," +
                "borderColor: ['rgba(75, 192, 192, 1)', 'rgba(255, 159, 64, 1)', 'rgba(153, 102, 255, 1)']," +
                "borderWidth: 1" +
                "}]," +
                "}," +
                "options: {" +
                "responsive: true," +
                "maintainAspectRatio: false," +
                "scales: {" +
                "y: {beginAtZero: true, title: {display: true, text: 'Doanh thu (VNĐ)'}, ticks: {callback: function(value) {return value.toLocaleString('vi-VN', {minimumFractionDigits: 0, maximumFractionDigits: 0}) + ' VNĐ';}}}," +
                "x: {title: {display: true, text: 'Loại phòng'}}" +
                "}," +
                "plugins: {" +
                "legend: {display: true, position: 'top'}," +
                "title: {display: true, text: 'Doanh thu theo loại phòng', font: {size: 18}}" +
                "}" +
                "}" +
                "});" +
                "</script>" +
                "</body>" +
                "</html>";

        runOnUiThread(() -> {
            Log.d(TAG, "Rendering chart with data: Standard=" + revenueByRoomType.getOrDefault("Standard", 0.0) +
                    ", VIP=" + revenueByRoomType.getOrDefault("VIP", 0.0) +
                    ", Deluxe=" + revenueByRoomType.getOrDefault("Deluxe", 0.0));
            chartRevenueByRoomType.setInitialScale(100); // Set initial scale to 100%
            chartRevenueByRoomType.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
            Snackbar.make(findViewById(android.R.id.content), "Đang render biểu đồ doanh thu", Snackbar.LENGTH_SHORT).show();
        });
    }

    private void displayOccupancyRateChart(double occupancyRate) {
        Log.d(TAG, "Hiển thị biểu đồ tỷ lệ sử dụng: " + occupancyRate);

        if (!isNetworkAvailable()) {
            runOnUiThread(() -> {
                Snackbar.make(findViewById(android.R.id.content), "Không có kết nối mạng, không thể tải biểu đồ", Snackbar.LENGTH_LONG).show();
                chartOccupancyRate.loadData("<html><body><p>Không có kết nối mạng</p></body></html>", "text/html", "UTF-8");
            });
            return;
        }

        String htmlContent = "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "<script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>" +
                "<style>" +
                "body { margin: 0; padding: 10px; }" +
                "canvas { width: 100% !important; height: 600px !important; max-height: 700px !important; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<canvas id='chart'></canvas>" +
                "<script>" +
                "const ctx = document.getElementById('chart').getContext('2d');" +
                "new Chart(ctx, {" +
                "type: 'pie'," +
                "data: {" +
                "labels: ['Đã sử dụng', 'Trống']," +
                "datasets: [{" +
                "label: 'Tỷ lệ sử dụng phòng'," +
                "data: [" +
                Math.min(Math.max(occupancyRate, 0), 100) + "," +
                Math.min(Math.max(100 - occupancyRate, 0), 100) +
                "]," +
                "backgroundColor: ['rgba(75, 192, 192, 0.6)', 'rgba(255, 99, 132, 0.6)']," +
                "borderColor: ['rgba(75, 192, 192, 1)', 'rgba(255, 99, 132, 1)']," +
                "borderWidth: 1" +
                "}]," +
                "}," +
                "options: {" +
                "responsive: true," +
                "maintainAspectRatio: false," +
                "plugins: {" +
                "legend: {display: true, position: 'top'}," +
                "title: {display: true, text: 'Tỷ lệ sử dụng phòng', font: {size: 18}}" +
                "}" +
                "}" +
                "});" +
                "</script>" +
                "</body>" +
                "</html>";

        runOnUiThread(() -> {
            chartOccupancyRate.setInitialScale(100); // Set initial scale to 100%
            chartOccupancyRate.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
            Snackbar.make(findViewById(android.R.id.content), "Đang render biểu đồ tỷ lệ sử dụng", Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        loadStatistics();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        executorService.shutdown();
    }
}