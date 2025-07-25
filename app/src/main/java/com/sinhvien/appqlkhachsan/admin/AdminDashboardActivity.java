package com.sinhvien.appqlkhachsan.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sinhvien.appqlkhachsan.R;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboardActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView tvTotalRooms, tvTotalCustomers, tvTodayBookings, tvRevenue, tvPendingBookings;
    private boolean isActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ views
        tvTotalRooms = findViewById(R.id.tvTotalRooms);
        tvTotalCustomers = findViewById(R.id.tvTotalCustomers);
        tvTodayBookings = findViewById(R.id.tvTodayBookings);
        tvRevenue = findViewById(R.id.tvRevenue);
        tvPendingBookings = findViewById(R.id.tvPendingBookings);
        Button btnManageBookings = findViewById(R.id.btnManageBookings);
        Button btnClearData = findViewById(R.id.btnClearData);

        // Kiểm tra quyền admin
        checkAdminAccess();
        Button btnStatistics = findViewById(R.id.btnStatistics);
        btnStatistics.setOnClickListener(v -> {
            if (isActive) {
                Log.d(TAG, "Chuyển sang StatisticsActivity");
                try {
                    startActivity(new Intent(this, StatisticsActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khởi động StatisticsActivity: " + e.getMessage());
                    Toast.makeText(this, "Không thể mở thống kê: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        Button btnManageRooms = findViewById(R.id.btnManageRooms);
        btnManageRooms.setOnClickListener(v -> {
            if (isActive) {
                Log.d(TAG, "Chuyển sang RoomManageActivity");
                try {
                    startActivity(new Intent(this, RoomManagementActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khởi động RoomManagementActivity: " + e.getMessage());
                    Toast.makeText(this, "Không thể mở trang quản lý phòng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        // Chuyển sang màn hình quản lý đơn đặt phòng
        btnManageBookings.setOnClickListener(v -> {
            if (isActive) {
                Log.d(TAG, "Chuyển sang AdminBookingListActivity");
                try {
                    startActivity(new Intent(this, AdminBookingListActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi khởi động AdminBookingListActivity: " + e.getMessage());
                    Toast.makeText(this, "Lỗi khởi động màn hình quản lý: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });


        // Xử lý xóa dữ liệu
        btnClearData.setOnClickListener(v -> {
            if (isActive) {
                new AlertDialog.Builder(this)
                        .setTitle("Xác nhận xóa dữ liệu")
                        .setMessage("Bạn có chắc chắn muốn xóa toàn bộ bookings, invoices và đặt lại trạng thái phòng?")
                        .setPositiveButton("Xóa", (dialog, which) -> clearBookingsAndInvoices())
                        .setNegativeButton("Hủy", null)
                        .show();
            }
        });
    }

    private void checkAdminAccess() {
        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "Không tìm thấy người dùng đăng nhập!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isActive) {
                        if (documentSnapshot.exists()) {
                            String role = documentSnapshot.getString("role");
                            if ("admin".equals(role)) {
                                Log.d(TAG, "Quyền admin được xác nhận");
                                loadDashboardData();
                            } else {
                                Toast.makeText(this, "Bạn không có quyền admin! Role: " + (role != null ? role : "null"), Toast.LENGTH_LONG).show();
                                finish();
                            }
                        } else {
                            Toast.makeText(this, "Tài liệu người dùng không tồn tại!", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void loadDashboardData() {
        // Tổng số phòng
        db.collection("rooms").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isActive) {
                        tvTotalRooms.setText("Tổng phòng: " + querySnapshot.size());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải rooms: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải rooms: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Tổng số khách
        db.collection("customers").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isActive) {
                        tvTotalCustomers.setText("Tổng khách: " + querySnapshot.size());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải customers: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải customers: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Đặt phòng hôm nay
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        db.collection("bookings")
                .whereGreaterThanOrEqualTo("TGDat", today + " 00:00:00")
                .whereLessThanOrEqualTo("TGDat", today + " 23:59:59")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isActive) {
                        tvTodayBookings.setText("Đặt hôm nay: " + querySnapshot.size());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải bookings: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải bookings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Doanh thu
        db.collection("invoices")
                .whereEqualTo("TrangThai", "Hoàn tất")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isActive) {
                        double totalRevenue = 0;
                        for (DocumentSnapshot doc : querySnapshot) {
                            Double tongGia = doc.getDouble("TongGia");
                            if (tongGia != null) {
                                totalRevenue += tongGia;
                            }
                        }
                        tvRevenue.setText("Doanh thu: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalRevenue));
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải invoices: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải invoices: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

        // Đơn chờ xử lý
        db.collection("bookings")
                .whereEqualTo("TrangThaiDD", "Đang xử lý")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isActive) {
                        tvPendingBookings.setText("Đơn chờ xử lý: " + querySnapshot.size());
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải pending bookings: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải pending bookings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void clearBookingsAndInvoices() {
        // Xóa tất cả tài liệu trong bookings
        db.collection("bookings").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) return;
                    int totalDocs = querySnapshot.size();
                    int[] deletedCount = {0};
                    if (totalDocs == 0) {
                        Log.d(TAG, "Không có tài liệu nào trong bookings để xóa");
                        clearInvoices();
                        return;
                    }
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        db.collection("bookings").document(doc.getId()).delete()
                                .addOnSuccessListener(aVoid -> {
                                    deletedCount[0]++;
                                    Log.d(TAG, "Đã xóa booking: " + doc.getId());
                                    if (deletedCount[0] == totalDocs) {
                                        Log.d(TAG, "Xóa xong tất cả bookings");
                                        clearInvoices();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi xóa booking " + doc.getId() + ": " + e.getMessage());
                                    if (isActive) {
                                        Toast.makeText(this, "Lỗi xóa booking: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải danh sách bookings: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải danh sách bookings: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void clearInvoices() {
        // Xóa tất cả tài liệu trong invoices
        db.collection("invoices").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) return;
                    int totalDocs = querySnapshot.size();
                    int[] deletedCount = {0};
                    if (totalDocs == 0) {
                        Log.d(TAG, "Không có tài liệu nào trong invoices để xóa");
                        resetRoomStatuses();
                        return;
                    }
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        db.collection("invoices").document(doc.getId()).delete()
                                .addOnSuccessListener(aVoid -> {
                                    deletedCount[0]++;
                                    Log.d(TAG, "Đã xóa invoice: " + doc.getId());
                                    if (deletedCount[0] == totalDocs) {
                                        Log.d(TAG, "Xóa xong tất cả invoices");
                                        resetRoomStatuses();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi xóa invoice " + doc.getId() + ": " + e.getMessage());
                                    if (isActive) {
                                        Toast.makeText(this, "Lỗi xóa invoice: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải danh sách invoices: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải danh sách invoices: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void resetRoomStatuses() {
        // Đặt lại trạng thái tất cả phòng thành "Trống"
        db.collection("rooms").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) return;
                    int totalDocs = querySnapshot.size();
                    int[] updatedCount = {0};
                    if (totalDocs == 0) {
                        Log.d(TAG, "Không có phòng nào để cập nhật trạng thái");
                        if (isActive) {
                            Toast.makeText(this, "Đã xóa tất cả bookings, invoices và đặt lại trạng thái phòng!", Toast.LENGTH_LONG).show();
                            loadDashboardData(); // Cập nhật lại dashboard
                        }
                        return;
                    }
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        db.collection("rooms").document(doc.getId())
                                .update("TrangThai", "Trống")
                                .addOnSuccessListener(aVoid -> {
                                    updatedCount[0]++;
                                    Log.d(TAG, "Đã đặt lại trạng thái phòng: " + doc.getId());
                                    if (updatedCount[0] == totalDocs) {
                                        Log.d(TAG, "Đã đặt lại trạng thái tất cả phòng");
                                        if (isActive) {
                                            Toast.makeText(this, "Đã xóa tất cả bookings, invoices và đặt lại trạng thái phòng!", Toast.LENGTH_LONG).show();
                                            loadDashboardData(); // Cập nhật lại dashboard
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi cập nhật trạng thái phòng " + doc.getId() + ": " + e.getMessage());
                                    if (isActive) {
                                        Toast.makeText(this, "Lỗi cập nhật trạng thái phòng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải danh sách rooms: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải danh sách rooms: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
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
        // Tải lại dữ liệu khi Activity được resume
        checkAdminAccess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }
}