package com.sinhvien.appqlkhachsan.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.sinhvien.appqlkhachsan.R;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboardActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView tvTotalRooms, tvTotalCustomers, tvTodayBookings, tvRevenue, tvPendingBookings;
    private AlertDialog progressDialog;

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

        // Thiết lập các nút chức năng
        setupActionButtons();
    }

    private void setupActionButtons() {
        // Quản lý đơn đặt phòng
        View btnManageBookings = findViewById(R.id.btnManageBookings);
        setupActionItem(btnManageBookings.findViewById(R.id.action_manage_bookings), "Quản lý Đơn đặt phòng", R.drawable.ic_booking);
        btnManageBookings.setOnClickListener(v -> startActivity(new Intent(this, AdminBookingListActivity.class)));

        // Quản lý phòng
        View btnManageRooms = findViewById(R.id.btnManageRooms);
        setupActionItem(btnManageRooms.findViewById(R.id.action_manage_rooms), "Quản lý Phòng", R.drawable.ic_room);
        btnManageRooms.setOnClickListener(v -> startActivity(new Intent(this, RoomManagementActivity.class)));

        // Thống kê
        View btnStatistics = findViewById(R.id.btnStatistics);
        setupActionItem(btnStatistics.findViewById(R.id.action_statistics), "Xem Thống kê", R.drawable.ic_statistics);
        btnStatistics.setOnClickListener(v -> startActivity(new Intent(this, StatisticsActivity.class)));

        // Quản lý tài khoản
        View btnManageAccounts = findViewById(R.id.btnManageAccounts);
        setupActionItem(btnManageAccounts.findViewById(R.id.action_manage_accounts), "Quản lý Tài khoản", R.drawable.ic_account);
        btnManageAccounts.setOnClickListener(v -> startActivity(new Intent(this, AdminAccountManagementActivity.class)));

        // Xóa dữ liệu
        Button btnClearData = findViewById(R.id.btnClearData);
        btnClearData.setOnClickListener(v -> showClearDataConfirmation());
    }

    private void setupActionItem(View actionView, String title, int iconRes) {
        TextView tvTitle = actionView.findViewById(R.id.title);
        ImageView ivIcon = actionView.findViewById(R.id.icon);
        tvTitle.setText(title);
        ivIcon.setImageResource(iconRes);
    }

    private void checkAdminAccess() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Vui lòng đăng nhập lại!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                        Log.d(TAG, "Quyền admin được xác nhận.");
                        loadDashboardData();
                    } else {
                        Toast.makeText(this, "Bạn không có quyền truy cập trang này!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Lỗi kiểm tra quyền", e);
                    finish();
                });
    }

    private void loadDashboardData() {
        // Tổng số phòng
        db.collection("rooms").get().addOnSuccessListener(q -> tvTotalRooms.setText(String.valueOf(q.size())))
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi tải rooms: ", e));

        // Tổng số khách
        db.collection("customers").get().addOnSuccessListener(q -> tvTotalCustomers.setText(String.valueOf(q.size())))
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi tải customers: ", e));

        // Đặt phòng hôm nay
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        db.collection("bookings")
                .whereGreaterThanOrEqualTo("TGDat", today + " 00:00:00")
                .whereLessThanOrEqualTo("TGDat", today + " 23:59:59")
                .get().addOnSuccessListener(q -> tvTodayBookings.setText(String.valueOf(q.size())))
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi tải bookings hôm nay: ", e));

        // Tổng doanh thu
        db.collection("invoices").whereEqualTo("TrangThai", "Hoàn tất").get()
                .addOnSuccessListener(querySnapshot -> {
                    double totalRevenue = 0;
                    for (DocumentSnapshot doc : querySnapshot) {
                        Double tongGia = doc.getDouble("TongGia");
                        if (tongGia != null) {
                            totalRevenue += tongGia;
                        }
                    }
                    tvRevenue.setText(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalRevenue));
                }).addOnFailureListener(e -> Log.e(TAG, "Lỗi tải invoices: ", e));

        // Đơn chờ xử lý
        db.collection("bookings").whereEqualTo("TrangThaiDD", "Đang xử lý").get()
                .addOnSuccessListener(q -> tvPendingBookings.setText(String.valueOf(q.size())))
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi tải pending bookings: ", e));
    }

    private void showClearDataConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận Xóa Dữ Liệu")
                .setMessage("Bạn có chắc chắn muốn xóa toàn bộ bookings, invoices và đặt lại trạng thái tất cả phòng không? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> clearAllData())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showProgressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(R.layout.dialog_progress); // Tạo một layout đơn giản với ProgressBar
        progressDialog = builder.create();
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void clearAllData() {
        showProgressDialog();

        Task<Void> deleteBookingsTask = deleteCollection("bookings");

        deleteBookingsTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            Log.d(TAG, "Xóa xong bookings, đang xóa invoices...");
            return deleteCollection("invoices");
        }).continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            Log.d(TAG, "Xóa xong invoices, đang reset trạng thái phòng...");
            return resetRoomStatuses();
        }).addOnCompleteListener(task -> {
            hideProgressDialog();
            if (isFinishing() || isDestroyed()) return;

            if (task.isSuccessful()) {
                Log.d(TAG, "Hoàn tất tất cả tác vụ xóa và reset.");
                Toast.makeText(this, "Đã xóa thành công toàn bộ dữ liệu giao dịch!", Toast.LENGTH_LONG).show();
                loadDashboardData(); // Tải lại dữ liệu cho dashboard
            } else {
                Log.e(TAG, "Đã xảy ra lỗi trong quá trình xóa dữ liệu.", task.getException());
                Toast.makeText(this, "Lỗi: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private Task<Void> deleteCollection(String collectionPath) {
        return db.collection(collectionPath).get().onSuccessTask(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                return Tasks.forResult(null);
            }
            WriteBatch batch = db.batch();
            for (QueryDocumentSnapshot doc : querySnapshot) {
                batch.delete(doc.getReference());
            }
            Log.d(TAG, "Chuẩn bị xóa " + querySnapshot.size() + " tài liệu từ collection " + collectionPath);
            return batch.commit();
        });
    }

    private Task<Void> resetRoomStatuses() {
        return db.collection("rooms").get().onSuccessTask(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                return Tasks.forResult(null);
            }
            WriteBatch batch = db.batch();
            for (QueryDocumentSnapshot doc : querySnapshot) {
                batch.update(doc.getReference(), "TrangThai", "Trống");
            }
            Log.d(TAG, "Chuẩn bị reset trạng thái cho " + querySnapshot.size() + " phòng.");
            return batch.commit();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tải lại dữ liệu khi Activity được resume
        checkAdminAccess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideProgressDialog(); // Đảm bảo dialog được đóng khi activity bị hủy
    }
}