package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WalkInCheckInActivity extends AppCompatActivity {

    private static final String TAG = "WalkInCheckInActivity";
    private TextView tvRoomName, tvCheckInDate;
    private EditText etCheckOutDate, etCustomerName, etCustomerPhone, etCustomerCCCD, etCustomerEmail;
    private NumberPicker numberPickerGuests;
    private Button btnConfirmCheckIn;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private int roomId;
    private String roomName;
    private double roomPrice;
    private int maxGuests;
    private String checkInDateStr;
    private String checkOutDateStr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_in_check_in);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Ánh xạ View
        tvRoomName = findViewById(R.id.tvRoomName);
        tvCheckInDate = findViewById(R.id.tvCheckInDate);
        etCheckOutDate = findViewById(R.id.etCheckOutDate);
        etCustomerName = findViewById(R.id.etCustomerName);
        etCustomerPhone = findViewById(R.id.etCustomerPhone);
        etCustomerCCCD = findViewById(R.id.etCustomerCCCD);
        etCustomerEmail = findViewById(R.id.etCustomerEmail);
        numberPickerGuests = findViewById(R.id.numberPickerGuests);
        btnConfirmCheckIn = findViewById(R.id.btnConfirmCheckIn);

        // Lấy dữ liệu từ Intent
        roomId = getIntent().getIntExtra("ROOM_ID", 0);
        roomName = getIntent().getStringExtra("roomName");
        roomPrice = getIntent().getDoubleExtra("price", 0.0);

        if (roomId == 0) {
            Toast.makeText(this, "Lỗi: Mã phòng không hợp lệ.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvRoomName.setText("Check-in cho " + roomName);

        // Lấy thông tin sức chứa tối đa của phòng
        fetchRoomDetails();

        // Thiết lập ngày nhận phòng là hôm nay
        Calendar today = Calendar.getInstance();
        SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        tvCheckInDate.setText(displayFormat.format(today.getTime()));
        checkInDateStr = dbFormat.format(today.getTime());

        etCheckOutDate.setOnClickListener(v -> showCheckOutDatePickerDialog());
        btnConfirmCheckIn.setOnClickListener(v -> handleDirectCheckIn());
    }

    private void fetchRoomDetails() {
        db.collection("rooms").document(String.valueOf(roomId)).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long maxGuestsLong = documentSnapshot.getLong("SoLuongNguoiToiDa");
                        maxGuests = (maxGuestsLong != null) ? maxGuestsLong.intValue() : 2;
                        numberPickerGuests.setMinValue(1);
                        numberPickerGuests.setMaxValue(maxGuests);
                        numberPickerGuests.setValue(1);
                    } else {
                        Toast.makeText(this, "Không tìm thấy thông tin phòng.", Toast.LENGTH_SHORT).show();
                        maxGuests = 2; // Default
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải thông tin phòng.", Toast.LENGTH_SHORT).show();
                    maxGuests = 2; // Default
                });
    }

    private void showCheckOutDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        long minDate = calendar.getTimeInMillis();

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar checkOutCalendar = Calendar.getInstance();
                    checkOutCalendar.set(year, month, dayOfMonth);

                    SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    etCheckOutDate.setText(displayFormat.format(checkOutCalendar.getTime()));
                    checkOutDateStr = dbFormat.format(checkOutCalendar.getTime());

                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.getDatePicker().setMinDate(minDate);
        datePickerDialog.show();
    }

    private void handleDirectCheckIn() {
        String customerName = etCustomerName.getText().toString().trim();
        String customerPhone = etCustomerPhone.getText().toString().trim();
        String customerCCCD = etCustomerCCCD.getText().toString().trim();
        String customerEmail = etCustomerEmail.getText().toString().trim();
        int guestCount = numberPickerGuests.getValue();

        // Bắt đầu chuỗi kiểm tra ràng buộc
        if (TextUtils.isEmpty(checkOutDateStr)) {
            Toast.makeText(this, "Vui lòng chọn ngày trả phòng.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(customerName) || TextUtils.isEmpty(customerPhone) || TextUtils.isEmpty(customerCCCD)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ Tên, SĐT, và CCCD.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidPhone(customerPhone)) {
            etCustomerPhone.setError("Số điện thoại phải là 10 số, bắt đầu bằng 0!");
            Toast.makeText(this, "Số điện thoại không hợp lệ.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidCCCD(customerCCCD)) {
            etCustomerCCCD.setError("CCCD phải là 12 số!");
            Toast.makeText(this, "CCCD không hợp lệ.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidEmail(customerEmail)) {
            etCustomerEmail.setError("Email phải kết thúc bằng @gmail.com!");
            Toast.makeText(this, "Email không hợp lệ.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (guestCount > maxGuests) {
            Toast.makeText(this, "Số lượng khách vượt quá sức chứa tối đa (" + maxGuests + " người) của phòng.", Toast.LENGTH_LONG).show();
            return;
        }
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Lỗi: Nhân viên chưa đăng nhập.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tất cả thông tin hợp lệ, tiến hành lưu
        saveDirectCheckIn(customerName, customerPhone, customerCCCD, customerEmail, guestCount);
    }

    private void saveDirectCheckIn(String customerName, String customerPhone, String customerCCCD, String customerEmail, int guestCount) {
        WriteBatch batch = db.batch();
        String maKH = auth.getCurrentUser().getUid();
        SimpleDateFormat dbDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String tgDat = dbDateTimeFormat.format(new Date());
        String tgCheckIn = checkInDateStr + " 14:00:00";
        String tgCheckOut = checkOutDateStr + " 12:00:00";

        // 1. Tạo bản ghi Bookings
        DocumentReference bookingRef = db.collection("bookings").document();
        Map<String, Object> bookingData = new HashMap<>();
        bookingData.put("MaKH", maKH);
        bookingData.put("MaPhong", roomId);
        bookingData.put("TGDat", tgDat);
        bookingData.put("TGCheckin", tgCheckIn);
        bookingData.put("TGCheckout", tgCheckOut);
        bookingData.put("TrangThaiDD", "Đã nhận phòng");
        bookingData.put("TrangThaiTT", "Chưa thanh toán");
        bookingData.put("SoKhach", guestCount);
        batch.set(bookingRef, bookingData);

        // 2. Tạo bản ghi Invoices
        String invoiceId = UUID.randomUUID().toString();
        DocumentReference invoiceRef = db.collection("invoices").document(invoiceId);
        Map<String, Object> invoiceData = new HashMap<>();
        invoiceData.put("MaDon", bookingRef.getId());
        invoiceData.put("MaKH", maKH);
        invoiceData.put("MaPhong", roomId);
        invoiceData.put("TenKhach", customerName);
        invoiceData.put("SoDienThoai", customerPhone);
        invoiceData.put("CCCD", customerCCCD);
        invoiceData.put("Email", customerEmail.isEmpty() ? null : customerEmail);
        invoiceData.put("TGCheckin", tgCheckIn);
        invoiceData.put("TGCheckout", tgCheckOut);
        invoiceData.put("TongGia", 0.0); // Sẽ được tính khi check-out
        invoiceData.put("TrangThai", "Đã nhận phòng");
        invoiceData.put("SoKhach", guestCount);
        batch.set(invoiceRef, invoiceData);

        // 3. Cập nhật bản ghi Rooms
        DocumentReference roomRef = db.collection("rooms").document(String.valueOf(roomId));
        batch.update(roomRef, "TrangThai", "Đang sử dụng");

        // Commit batch
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Check-in thành công cho " + roomName, Toast.LENGTH_LONG).show();
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Check-in thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Lỗi khi thực hiện batch check-in", e);
        });
    }

    // Các hàm kiểm tra ràng buộc được lấy từ BookingActivity
    private boolean isValidPhone(String phone) {
        return phone.length() == 10 && phone.matches("0\\d{9}");
    }

    private boolean isValidEmail(String email) {
        return email.isEmpty() || email.matches(".+@gmail\\.com$");
    }

    private boolean isValidCCCD(String cccd) {
        return cccd.matches("^\\d{12}$");
    }
}