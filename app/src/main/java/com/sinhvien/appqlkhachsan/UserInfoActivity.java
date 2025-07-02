package com.sinhvien.appqlkhachsan;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class UserInfoActivity extends AppCompatActivity {

    private static final String TAG = "UserInfoActivity";
    private TextView tvUserName, tvUserEmail, tvUserId;
    private LinearLayout btnViewPersonalInfo, btnViewBookingHistory;
    private Button btnLogout;
    private FirebaseAuth mAuth;
    private DatabaseHelper dbHelper;
    private boolean isActive = true; // Theo dõi trạng thái activity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_info);

        // Ánh xạ các view theo layout mới
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvUserId = findViewById(R.id.tvUserId);
        btnViewPersonalInfo = findViewById(R.id.btnViewPersonalInfo);
        btnViewBookingHistory = findViewById(R.id.btnViewBookingHistory);
        btnLogout = findViewById(R.id.btnLogout);

        // Khởi tạo Firebase và Database
        mAuth = FirebaseAuth.getInstance();
        dbHelper = new DatabaseHelper(this);

        // Load dữ liệu người dùng
        loadUserData();

        // Cấu hình BottomNavigationView
        BottomNavigationView bottomNavigationView = findViewById(R.id.navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
                if (!isActive) return false;
                int itemId = item.getItemId();
                if (itemId == R.id.nav_profile) {
                    return true; // Đã ở đây
                } else if (itemId == R.id.nav_home) {
                    startActivity(new Intent(UserInfoActivity.this, MainActivity.class));
                    finish(); // Kết thúc activity hiện tại để tránh chồng lấp
                    return true;
                }
                return false;
            });
            // Đặt item "nav_profile" là item được chọn mặc định
            bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        }

        // Sự kiện xem thông tin cá nhân
        btnViewPersonalInfo.setOnClickListener(v -> {
            if (!isActive) return;
            Log.d(TAG, "Click on btnViewPersonalInfo");
            try {
                Intent intent = new Intent(UserInfoActivity.this, Edit_Info_Activity.class);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to Edit_Info_Activity: " + e.getMessage());
                Toast.makeText(this, "Lỗi chuyển sang chỉnh sửa thông tin", Toast.LENGTH_SHORT).show();
            }
        });

        // Sự kiện xem lịch sử đặt phòng
        btnViewBookingHistory.setOnClickListener(v -> {
            if (!isActive) return;
            Intent intent = new Intent(UserInfoActivity.this, BookingHistoryActivity.class);
            startActivity(intent);
        });

        // Sự kiện đăng xuất
        btnLogout.setOnClickListener(v -> {
            if (!isActive) return;
            mAuth.signOut();
            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();
            Intent intent = new Intent(UserInfoActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
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
        loadUserData(); // Tải lại dữ liệu khi quay lại activity
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }

    private void loadUserData() {
        if (!isActive) return;
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
            String email = prefs.getString("USER_EMAIL", user.getEmail() != null ? user.getEmail() : "Chưa có email");
            tvUserEmail.setText(email);
            tvUserId.setText(uid);

            // Lấy họ tên từ SQLite
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT TenKH FROM KhachHang WHERE MaKH = ?", new String[]{uid});
                if (cursor.moveToFirst()) {
                    tvUserName.setText(cursor.getString(0));
                } else {
                    tvUserName.setText("Chưa cập nhật thông tin");
                    // Tạo bản ghi mới nếu chưa có
                    ContentValues values = new ContentValues();
                    values.put("MaKH", uid);
                    values.put("TenKH", "Chưa cập nhật");
                    values.put("Email", email);
                    db.insert("KhachHang", null, values);
                }
            } catch (Exception e) {
                tvUserName.setText("Lỗi tải thông tin: " + e.getMessage());
                Log.e(TAG, "Lỗi tải tên: " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                db.close();
            }
        } else {
            tvUserName.setText("Chưa đăng nhập");
            tvUserEmail.setText("Chưa đăng nhập");
            tvUserId.setText("Chưa có ID");
        }
    }
}