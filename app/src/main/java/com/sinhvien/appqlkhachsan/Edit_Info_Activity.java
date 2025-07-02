package com.sinhvien.appqlkhachsan;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Edit_Info_Activity extends AppCompatActivity {

    private static final String TAG = "Edit_Info_Activity";
    private EditText etEditFullName, etEditUsername, etEditPhone, etEditEmail;
    private ImageView btnEditFullName, btnEditUsername, btnEditPhone, btnEditEmail;
    private Button btnSave, btnEditPass;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate called, starting layout inflation");
        try {
            setContentView(R.layout.activity_edit_info);
            Log.d(TAG, "Layout inflated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error inflating layout: " + e.getMessage());
            Toast.makeText(this, "Lỗi tải giao diện: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        etEditFullName = findViewById(R.id.etEditFullName);
        etEditUsername = findViewById(R.id.etEditUsername);
        etEditPhone = findViewById(R.id.etEditPhone);
        etEditEmail = findViewById(R.id.etEditEmail);
        btnEditFullName = findViewById(R.id.btnEditFullName);
        btnEditUsername = findViewById(R.id.btnEditUsername);
        btnEditPhone = findViewById(R.id.btnEditPhone);
        btnEditEmail = findViewById(R.id.btnEditEmail);
        btnSave = findViewById(R.id.btnSave);
        btnEditPass = findViewById(R.id.btnEditPass);

        if (etEditFullName == null || btnEditFullName == null || btnSave == null) {
            Log.e(TAG, "One or more views are null");
            Toast.makeText(this, "Lỗi ánh xạ view", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        String uid = mAuth.getCurrentUser().getUid();
        if (uid == null) {
            Log.e(TAG, "USER_UID is empty");
            Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadUserData(uid);

        setEditable(false);

        btnEditFullName.setOnClickListener(v -> toggleEdit(etEditFullName, "Nhập tên"));
        btnEditUsername.setOnClickListener(v -> toggleEdit(etEditUsername, "Nhập username"));
        btnEditPhone.setOnClickListener(v -> toggleEdit(etEditPhone, "Nhập số điện thoại"));
        btnEditEmail.setOnClickListener(v -> toggleEdit(etEditEmail, "Nhập email"));

        btnSave.setOnClickListener(v -> {
            String newFullName = etEditFullName.getText().toString().trim();
            String newUsername = etEditUsername.getText().toString().trim();
            String newPhone = etEditPhone.getText().toString().trim();
            String newEmail = etEditEmail.getText().toString().trim();

            if (newFullName.isEmpty() || newUsername.isEmpty() || newPhone.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidPhone(newPhone)) {
                Toast.makeText(this, "Số điện thoại không hợp lệ (10-11 số, bắt đầu bằng 0)", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("fullName", newFullName);
            updates.put("phone", newPhone);
            updates.put("email", newEmail);
            updates.put("username", newUsername);

            db.collection("users").document(uid)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("USER_NAME", newFullName);
                        editor.putString("USER_USERNAME", newUsername);
                        editor.putString("USER_PHONE", newPhone);
                        editor.putString("USER_EMAIL", newEmail);
                        editor.apply();
                        setEditable(false);
                        Toast.makeText(this, "Cập nhật thông tin thành công", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating data: " + e.getMessage());
                        Toast.makeText(this, "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        });

        btnEditPass.setOnClickListener(v -> {
            Toast.makeText(this, "Chức năng đổi mật khẩu đang được phát triển", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserData(String uid) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        etEditFullName.setText(documentSnapshot.getString("fullName"));
                        etEditPhone.setText(documentSnapshot.getString("phone"));
                        etEditEmail.setText(documentSnapshot.getString("email"));
                        etEditUsername.setText(documentSnapshot.getString("username"));
                    } else {
                        etEditFullName.setText(prefs.getString("USER_NAME", ""));
                        etEditPhone.setText(prefs.getString("USER_PHONE", ""));
                        etEditEmail.setText(prefs.getString("USER_EMAIL", ""));
                        etEditUsername.setText(prefs.getString("USER_USERNAME", ""));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading user data: " + e.getMessage());
                    Toast.makeText(this, "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void toggleEdit(EditText editText, String hint) {
        if (editText.isEnabled()) {
            editText.setEnabled(false);
            String uid = mAuth.getCurrentUser().getUid();
            loadUserData(uid); // Tải lại dữ liệu gốc
        } else {
            editText.setEnabled(true);
            editText.setFocusableInTouchMode(true);
            editText.setText("");
            editText.setHint(hint);
            editText.requestFocus();
        }
    }

    private void setEditable(boolean isEditable) {
        etEditFullName.setEnabled(isEditable);
        etEditUsername.setEnabled(isEditable);
        etEditPhone.setEnabled(isEditable);
        etEditEmail.setEnabled(isEditable);
    }

    private boolean isValidPhone(String phone) {
        return phone.matches("^0\\d{9,10}$");
    }
}