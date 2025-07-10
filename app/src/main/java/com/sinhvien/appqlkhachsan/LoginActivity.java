package com.sinhvien.appqlkhachsan;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import com.sinhvien.appqlkhachsan.admin.AdminDashboardActivity;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isActive = true;
    private boolean isLoginInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        FirebaseFirestore.setLoggingEnabled(true);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> {
            if (!isActive || isLoginInProgress) return;
            isLoginInProgress = true;
            btnLogin.setEnabled(false);
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ email và mật khẩu", Toast.LENGTH_SHORT).show();
                isLoginInProgress = false;
                btnLogin.setEnabled(true);
                return;
            }

            Log.d(TAG, "Đang đăng nhập với email: " + email);
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (!isActive) {
                            isLoginInProgress = false;
                            btnLogin.setEnabled(true);
                            return;
                        }
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                Log.d(TAG, "Đăng nhập thành công, UID: " + user.getUid());
                                // Chỉ tạo/cập nhật document cho admin
                                if (email.equals("admin@gmail.com") && password.equals("123456")) {
                                    createAdminDocument(user.getUid(), email, () -> {
                                        if (isActive) {
                                            Log.d(TAG, "Chuyển đến AdminDashboardActivity");
                                            startActivity(new Intent(LoginActivity.this, AdminDashboardActivity.class));
                                            finish();
                                        }
                                        isLoginInProgress = false;
                                        btnLogin.setEnabled(true);
                                    });
                                } else {
                                    Log.d(TAG, "Chuyển đến MainActivity");
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                    isLoginInProgress = false;
                                    btnLogin.setEnabled(true);
                                }
                            } else {
                                Toast.makeText(this, "Không lấy được thông tin người dùng!", Toast.LENGTH_SHORT).show();
                                isLoginInProgress = false;
                                btnLogin.setEnabled(true);
                            }
                        } else {
                            Log.e(TAG, "Lỗi đăng nhập: " + task.getException().getMessage());
                            Toast.makeText(this, "Email hoặc mật khẩu không đúng: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            isLoginInProgress = false;
                            btnLogin.setEnabled(true);
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isActive) {
                            Log.e(TAG, "Lỗi kết nối: " + e.getMessage());
                            Toast.makeText(this, "Lỗi kết nối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        isLoginInProgress = false;
                        btnLogin.setEnabled(true);
                    });
        });

        tvRegister.setOnClickListener(v -> {
            if (isActive) {
                Log.d(TAG, "Chuyển đến RegisterActivity");
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }

    private void createAdminDocument(String uid, String email, Runnable onSuccess) {
        Log.d(TAG, "Kiểm tra tài liệu admin: users/" + uid);
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isActive) return;
                    if (!documentSnapshot.exists()) {
                        Log.d(TAG, "Tài liệu admin không tồn tại, tạo mới");
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("email", email);
                        userData.put("role", "admin");
                        userData.put("fullName", email.split("@")[0]);

                        db.collection("users").document(uid).set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Tạo tài liệu admin thành công: users/" + uid);
                                    onSuccess.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi tạo tài liệu admin: " + e.getMessage());
                                    if (isActive) {
                                        Toast.makeText(this, "Lỗi tạo tài liệu admin: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                    isLoginInProgress = false;
                                    btnLogin.setEnabled(true);
                                });
                    } else if (!"admin".equals(documentSnapshot.getString("role"))) {
                        Log.d(TAG, "Cập nhật role admin cho users/" + uid);
                        db.collection("users").document(uid)
                                .update("role", "admin")
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Cập nhật role admin thành công");
                                    onSuccess.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi cập nhật role admin: " + e.getMessage());
                                    if (isActive) {
                                        Toast.makeText(this, "Lỗi cập nhật role admin: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                    isLoginInProgress = false;
                                    btnLogin.setEnabled(true);
                                });
                    } else {
                        Log.d(TAG, "Tài liệu admin tồn tại: " + documentSnapshot.getData());
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi kiểm tra tài liệu admin: " + e.getMessage());
                    if (isActive) {
                        Toast.makeText(this, "Lỗi kiểm tra tài liệu admin: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    isLoginInProgress = false;
                    btnLogin.setEnabled(true);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }
}