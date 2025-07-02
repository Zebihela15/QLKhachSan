package com.sinhvien.appqlkhachsan.admin;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sinhvien.appqlkhachsan.R;

public class ClearDataActivity extends AppCompatActivity {

    private static final String TAG = "ClearDataActivity";
    private FirebaseFirestore db;
    private boolean isActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clear_data);

        db = FirebaseFirestore.getInstance();
        Button btnClearData = findViewById(R.id.btnClearData);

        btnClearData.setOnClickListener(v -> {
            if (isActive) {
                clearBookingsAndInvoices();
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }
}