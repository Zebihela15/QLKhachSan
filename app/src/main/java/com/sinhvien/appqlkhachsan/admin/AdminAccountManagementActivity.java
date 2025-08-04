package com.sinhvien.appqlkhachsan.admin;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sinhvien.appqlkhachsan.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminAccountManagementActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private RecyclerView rvAccountList;
    private AccountAdapter accountAdapter;
    private List<Map<String, Object>> accountList;
    private boolean isActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_account_management);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        rvAccountList = findViewById(R.id.rvAccountList);
        Button btnAddAccount = findViewById(R.id.btnAddAccount);

        accountList = new ArrayList<>();
        accountAdapter = new AccountAdapter(accountList, this::showUpdateDialog, this::showDeleteDialog);
        rvAccountList.setLayoutManager(new LinearLayoutManager(this));
        rvAccountList.setAdapter(accountAdapter);

        loadAccountList();

        btnAddAccount.setOnClickListener(v -> showAddAccountDialog());
    }

    private void loadAccountList() {
        db.collection("users").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isActive) return;
                    accountList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("uid", doc.getId());
                        user.put("fullName", doc.getString("fullName"));
                        user.put("phone", doc.getString("phone"));
                        user.put("email", doc.getString("email"));
                        user.put("role", doc.getString("role"));
                        accountList.add(user);
                    }
                    accountAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi tải danh sách tài khoản: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showAddAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Thêm tài khoản mới");
        final View customLayout = getLayoutInflater().inflate(R.layout.dialog_account_form, null);
        builder.setView(customLayout);

        EditText etFullName = customLayout.findViewById(R.id.etFullName);
        EditText etPhone = customLayout.findViewById(R.id.etPhone);
        EditText etEmail = customLayout.findViewById(R.id.etEmail);
        EditText etPassword = customLayout.findViewById(R.id.etPassword);
        Spinner spinnerRole = customLayout.findViewById(R.id.spinnerRole);

        // Thiết lập adapter cho Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.user_roles,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);
        // Đặt mặc định là "Lễ tân"
        spinnerRole.setSelection(0);

        builder.setPositiveButton("Thêm", (dialog, which) -> {
            String fullName = etFullName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String role = spinnerRole.getSelectedItem().toString().toLowerCase(); // "lễ tân" hoặc "admin"

            // Ràng buộc đầu vào
            if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!phone.matches("\\d{10}")) {
                Toast.makeText(this, "Số điện thoại phải là 10 số!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Email không hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra quyền admin trước khi thêm tài khoản
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                db.collection("users").document(currentUser.getUid()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                                // Tạo tài khoản mới
                                mAuth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                FirebaseUser user = task.getResult().getUser();
                                                if (user != null) {
                                                    String uid = user.getUid();
                                                    Map<String, Object> userData = new HashMap<>();
                                                    userData.put("fullName", fullName);
                                                    userData.put("phone", phone);
                                                    userData.put("email", email);
                                                    userData.put("role", role);

                                                    db.collection("users").document(uid).set(userData)
                                                            .addOnSuccessListener(aVoid -> {
                                                                Toast.makeText(this, "Thêm tài khoản thành công", Toast.LENGTH_SHORT).show();
                                                                loadAccountList();
                                                            })
                                                            .addOnFailureListener(e -> {
                                                                if (isActive) {
                                                                    Toast.makeText(this, "Lỗi lưu thông tin: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                                }
                                                            });
                                                } else {
                                                    Toast.makeText(this, "Lỗi: Không thể lấy thông tin người dùng", Toast.LENGTH_SHORT).show();
                                                }
                                            } else {
                                                Toast.makeText(this, "Thêm tài khoản thất bại: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else {
                                Toast.makeText(this, "Bạn không có quyền admin!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (isActive) {
                                Toast.makeText(this, "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(this, "Bạn chưa đăng nhập!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showUpdateDialog(Map<String, Object> account, View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sửa tài khoản");
        final View customLayout = getLayoutInflater().inflate(R.layout.dialog_account_form, null);
        builder.setView(customLayout);

        EditText etFullName = customLayout.findViewById(R.id.etFullName);
        EditText etPhone = customLayout.findViewById(R.id.etPhone);
        EditText etEmail = customLayout.findViewById(R.id.etEmail);
        EditText etPassword = customLayout.findViewById(R.id.etPassword);
        Spinner spinnerRole = customLayout.findViewById(R.id.spinnerRole);

        // Thiết lập adapter cho Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.user_roles,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        // Đặt vai trò hiện tại
        String currentRole = (String) account.get("role");
        if (currentRole != null) {
            int position = "admin".equalsIgnoreCase(currentRole) ? 1 : 0;
            spinnerRole.setSelection(position);
        }

        etFullName.setText((String) account.get("fullName"));
        etPhone.setText((String) account.get("phone"));
        etEmail.setText((String) account.get("email"));

        builder.setPositiveButton("Lưu", (dialog, which) -> {
            String fullName = etFullName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String role = spinnerRole.getSelectedItem().toString().toLowerCase(); // "lễ tân" hoặc "admin"

            // Ràng buộc đầu vào
            if (fullName.isEmpty() || phone.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!phone.matches("\\d{10}")) {
                Toast.makeText(this, "Số điện thoại phải là 10 số!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Email không hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kiểm tra quyền admin trước khi cập nhật
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                db.collection("users").document(currentUser.getUid()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                                String uid = (String) account.get("uid");
                                Map<String, Object> userData = new HashMap<>();
                                userData.put("fullName", fullName);
                                userData.put("phone", phone);
                                userData.put("email", email);
                                userData.put("role", role);

                                // Cập nhật thông tin người dùng trong Firestore
                                db.collection("users").document(uid).update(userData)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(this, "Cập nhật tài khoản thành công", Toast.LENGTH_SHORT).show();
                                            loadAccountList();
                                        })
                                        .addOnFailureListener(e -> {
                                            if (isActive) {
                                                Toast.makeText(this, "Lỗi cập nhật thông tin: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });

                                // Cập nhật mật khẩu nếu có
                                if (!password.isEmpty()) {
                                    if (password.length() < 6) {
                                        Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    if (currentUser.getUid().equals(uid)) {
                                        currentUser.updatePassword(password)
                                                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Đã cập nhật mật khẩu", Toast.LENGTH_SHORT).show())
                                                .addOnFailureListener(e -> {
                                                    if (isActive) {
                                                        Toast.makeText(this, "Lỗi cập nhật mật khẩu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    } else {
                                        Toast.makeText(this, "Chỉ có thể cập nhật mật khẩu cho tài khoản hiện tại", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            } else {
                                Toast.makeText(this, "Bạn không có quyền admin!", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (isActive) {
                                Toast.makeText(this, "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(this, "Bạn chưa đăng nhập!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showDeleteDialog(Map<String, Object> account, View view) {
        String email = (String) account.get("email");
        String uid = (String) account.get("uid");
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // Kiểm tra không cho xóa tài khoản đang đăng nhập
        if (currentUser != null && uid.equals(currentUser.getUid())) {
            Toast.makeText(this, "Không thể xóa tài khoản đang đăng nhập!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra quyền admin trước khi xóa
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Xóa tài khoản")
                                    .setMessage("Bạn có chắc chắn muốn xóa tài khoản " + email + "?")
                                    .setPositiveButton("Xóa", (dialog, which) -> {
                                        db.collection("users").document(uid).delete()
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(this, "Xóa tài khoản thành công", Toast.LENGTH_SHORT).show();
                                                    loadAccountList();
                                                })
                                                .addOnFailureListener(e -> {
                                                    if (isActive) {
                                                        Toast.makeText(this, "Lỗi xóa tài khoản: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    })
                                    .setNegativeButton("Hủy", null)
                                    .show();
                        } else {
                            Toast.makeText(this, "Bạn không có quyền admin!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isActive) {
                            Toast.makeText(this, "Lỗi kiểm tra quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(this, "Bạn chưa đăng nhập!", Toast.LENGTH_SHORT).show();
        }
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
        loadAccountList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }
}