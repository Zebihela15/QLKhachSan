package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BookingActivity extends AppCompatActivity {
    private TextView textRoomName, dateRange, textTotalPrice, textTotalDays;
    private ImageView roomImage, btnBack;
    private EditText editCustomerName, editCustomerPhone, editCustomerCCCD, editCustomerEmail, editVoucherCode;
    private Button btnSelectDate, btnContinue;
    private double roomPrice;
    private String checkInDate, checkOutDate;
    private int roomId;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private boolean isBookingInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        // Khởi tạo
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Ánh xạ view
        textRoomName = findViewById(R.id.hotelName);
        roomImage = findViewById(R.id.hotelImage);
        dateRange = findViewById(R.id.dateRange);
        editCustomerName = findViewById(R.id.editCustomerName);
        editCustomerPhone = findViewById(R.id.editCustomerPhone);
        editCustomerCCCD = findViewById(R.id.editCustomerCCCD);
        editCustomerEmail = findViewById(R.id.editCustomerEmail);
        editVoucherCode = findViewById(R.id.editVoucherCode);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnContinue = findViewById(R.id.btnContinue);
        textTotalPrice = findViewById(R.id.textTotalPrice);
        textTotalDays = findViewById(R.id.textTotalDays);
        btnBack = findViewById(R.id.btnBack);

        initUserData();
        initRoomData();
        initDefaultDates();

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());
        btnContinue.setOnClickListener(v -> {
            if (!isBookingInProgress) {
                isBookingInProgress = true;
                btnContinue.setEnabled(false);
                handleBooking();
            }
        });
        btnBack.setOnClickListener(v -> finish());
    }

    private void initUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showToast("Vui lòng đăng nhập!");
            finish();
            return;
        }

        String maKH = currentUser.getUid();
        String savedName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "";
        String savedPhone = sharedPreferences.getString("USER_PHONE", "");
        String savedEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";

        // Điền dữ liệu mặc định
        editCustomerName.setText(savedName);
        editCustomerPhone.setText(savedPhone);
        editCustomerEmail.setText(savedEmail);
        editCustomerCCCD.setText("");

        // Lấy từ Firestore
        firestore.collection("customers").document(maKH).get()
                .addOnSuccessListener(document -> {
                    if (!isFinishing() && document.exists()) {
                        String name = document.getString("TenKH") != null ? document.getString("TenKH") : savedName;
                        String phone = document.getString("SDT") != null ? document.getString("SDT") : savedPhone;
                        String email = document.getString("Email") != null ? document.getString("Email") : savedEmail;
                        String cccd = document.getString("CCCD") != null ? document.getString("CCCD") : "";
                        editCustomerName.setText(name);
                        editCustomerPhone.setText(phone);
                        editCustomerEmail.setText(email);
                        editCustomerCCCD.setText(cccd);
                    } else {
                        firestore.collection("users").document(maKH).get()
                                .addOnSuccessListener(userDoc -> {
                                    if (!isFinishing() && userDoc.exists()) {
                                        String name = userDoc.getString("fullName") != null ? userDoc.getString("fullName") : savedName;
                                        String email = userDoc.getString("email") != null ? userDoc.getString("email") : savedEmail;
                                        editCustomerName.setText(name);
                                        editCustomerPhone.setText(savedPhone);
                                        editCustomerEmail.setText(email);
                                        editCustomerCCCD.setText("");
                                    }
                                });
                    }
                });
    }

    private void initRoomData() {
        Intent intent = getIntent();
        roomId = intent.getIntExtra("ROOM_ID", 0);
        String roomName = intent.getStringExtra("roomName");
        if (roomName == null || roomId == 0) {
            showToast("Không nhận được thông tin phòng!");
            finish();
            return;
        }
        textRoomName.setText(roomName);
        roomImage.setImageResource(intent.getIntExtra("imageResource", R.drawable.ic_launcher_background));
        roomPrice = intent.getDoubleExtra("price", 0.0);
        if (roomPrice <= 0) {
            showToast("Giá phòng không hợp lệ!");
        }
    }

    private void initDefaultDates() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        checkInDate = dateFormat.format(calendar.getTime());
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        checkOutDate = dateFormat.format(calendar.getTime());
        dateRange.setText(String.format("%s - %s", checkInDate, checkOutDate));
        updateTotalPrice();
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    if (isFinishing()) return;
                    calendar.set(year, month, day);
                    if (calendar.getTime().before(new Date())) {
                        showToast("Không thể chọn ngày quá khứ!");
                        return;
                    }
                    checkInDate = String.format("%02d/%02d/%d", day, month + 1, year);
                    showCheckOutDatePickerDialog();
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void showCheckOutDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(checkInDate));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        } catch (Exception ignored) {}
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    if (isFinishing()) return;
                    calendar.set(year, month, day);
                    try {
                        if (calendar.getTime().before(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(checkInDate))) {
                            showToast("Ngày trả phòng phải sau ngày nhận phòng!");
                            return;
                        }
                        checkOutDate = String.format("%02d/%02d/%d", day, month + 1, year);
                        isRoomAvailable(roomId, checkInDate, checkOutDate, isAvailable -> {
                            if (!isFinishing()) {
                                if (!isAvailable) {
                                    showToast("Phòng đã được đặt trong khoảng thời gian này!");
                                    return;
                                }
                                dateRange.setText(String.format("%s - %s", checkInDate, checkOutDate));
                                updateTotalPrice();
                            }
                        });
                    } catch (Exception e) {
                        showToast("Lỗi xử lý ngày!");
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.getDatePicker().setMinDate(calendar.getTimeInMillis());
        datePickerDialog.show();
    }

    private void isRoomAvailable(int roomId, String checkInDate, String checkOutDate, OnRoomAvailabilityListener listener) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date start = dateFormat.parse(checkInDate);
            Date end = dateFormat.parse(checkOutDate);
            firestore.collection("invoices")
                    .whereEqualTo("MaPhong", roomId)
                    .whereNotEqualTo("TrangThai", "Hủy")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        boolean isAvailable = true;
                        for (DocumentSnapshot document : querySnapshot) {
                            try {
                                String bookedStartStr = document.getString("TGCheckin");
                                String bookedEndStr = document.getString("TGCheckout");
                                if (bookedStartStr == null || bookedEndStr == null) continue;
                                Date bookedStart = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(bookedStartStr);
                                Date bookedEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(bookedEndStr);
                                if (!(end.before(bookedStart) || start.after(bookedEnd))) {
                                    isAvailable = false;
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                        listener.onResult(isAvailable);
                    })
                    .addOnFailureListener(e -> listener.onResult(false));
        } catch (Exception e) {
            listener.onResult(false);
        }
    }

    private interface OnRoomAvailabilityListener {
        void onResult(boolean isAvailable);
    }

    private void handleBooking() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showToast("Vui lòng đăng nhập để đặt phòng!");
            resetBookingState();
            return;
        }

        String customerName = editCustomerName.getText().toString().trim();
        String customerPhone = editCustomerPhone.getText().toString().trim();
        String customerCCCD = editCustomerCCCD.getText().toString().trim();
        String customerEmail = editCustomerEmail.getText().toString().trim();
        String voucherCode = editVoucherCode.getText().toString().trim();
        String maKH = currentUser.getUid();

        // Kiểm tra dữ liệu
        if (customerName.isEmpty() || customerPhone.isEmpty() || customerCCCD.isEmpty()) {
            showToast("Vui lòng nhập đầy đủ thông tin!");
            resetBookingState();
            return;
        }
        if (!isValidPhone(customerPhone)) {
            editCustomerPhone.setError("Số điện thoại phải là 10 số!");
            resetBookingState();
            return;
        }
        if (!isValidEmail(customerEmail)) {
            editCustomerEmail.setError("Email không hợp lệ!");
            resetBookingState();
            return;
        }
        if (!isValidCCCD(customerCCCD)) {
            editCustomerCCCD.setError("CCCD phải là 12 số!");
            resetBookingState();
            return;
        }
        if (roomId == 0) {
            showToast("Phòng không hợp lệ!");
            resetBookingState();
            return;
        }

        // Chuyển đổi định dạng ngày
        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String tgDat = dbFormat.format(new Date());
        String tgCheckIn, tgCheckOut;
        try {
            tgCheckIn = dbFormat.format(inputFormat.parse(checkInDate));
            tgCheckOut = dbFormat.format(inputFormat.parse(checkOutDate));
        } catch (Exception e) {
            showToast("Lỗi định dạng ngày!");
            resetBookingState();
            return;
        }

        // Tính tổng giá
        double baseTotalPrice = calculateTotalPrice();
        if (baseTotalPrice <= 0) {
            resetBookingState();
            return;
        }

        // Kiểm tra mã giảm giá
        if (!voucherCode.isEmpty()) {
            getVoucherDiscount(voucherCode, discount -> {
                double finalTotalPrice = baseTotalPrice * (1 - discount / 100);
                if (discount == 0 && !voucherCode.isEmpty()) {
                    showToast("Mã giảm giá không hợp lệ!");
                }
                saveBooking(maKH, customerName, customerPhone, customerCCCD, customerEmail, voucherCode, tgDat, tgCheckIn, tgCheckOut, finalTotalPrice);
            });
        } else {
            saveBooking(maKH, customerName, customerPhone, customerCCCD, customerEmail, voucherCode, tgDat, tgCheckIn, tgCheckOut, baseTotalPrice);
        }
    }

    private void saveBooking(String maKH, String customerName, String customerPhone, String customerCCCD,
                             String customerEmail, String voucherCode, String tgDat, String tgCheckIn,
                             String tgCheckOut, double totalPrice) {
        // Lưu thông tin khách hàng
        Map<String, Object> customerData = new HashMap<>();
        customerData.put("MaKH", maKH);
        customerData.put("TenKH", customerName);
        customerData.put("SDT", customerPhone);
        customerData.put("Email", customerEmail);
        customerData.put("CCCD", customerCCCD);

        firestore.collection("customers").document(maKH).set(customerData)
                .addOnSuccessListener(aVoid -> {
                    // Lưu đơn đặt phòng
                    Map<String, Object> bookingData = new HashMap<>();
                    bookingData.put("MaKH", maKH);
                    bookingData.put("MaPhong", roomId);
                    bookingData.put("MaGiam", voucherCode.isEmpty() ? null : voucherCode);
                    bookingData.put("TGDat", tgDat);
                    bookingData.put("TGCheckin", tgCheckIn);
                    bookingData.put("TGCheckout", tgCheckOut);
                    bookingData.put("TrangThaiTT", "Chưa thanh toán");
                    bookingData.put("TrangThaiDD", "Đang xử lý");

                    firestore.collection("bookings").add(bookingData)
                            .addOnSuccessListener(documentReference -> {
                                String maDon = documentReference.getId();
                                // Lưu hóa đơn
                                String invoiceId = UUID.randomUUID().toString();
                                Map<String, Object> invoiceData = new HashMap<>();
                                invoiceData.put("MaHoaDon", invoiceId);
                                invoiceData.put("MaDon", maDon);
                                invoiceData.put("MaKH", maKH);
                                invoiceData.put("MaPhong", roomId);
                                invoiceData.put("TenKhach", customerName);
                                invoiceData.put("SoDienThoai", customerPhone);
                                invoiceData.put("CCCD", customerCCCD);
                                invoiceData.put("Email", customerEmail);
                                invoiceData.put("TGCheckin", tgCheckIn);
                                invoiceData.put("TGCheckout", tgCheckOut);
                                invoiceData.put("TongGia", totalPrice);
                                invoiceData.put("TrangThai", "Đang xử lý");
                                invoiceData.put("MaGiamGia", voucherCode.isEmpty() ? null : voucherCode);

                                firestore.collection("invoices").document(invoiceId).set(invoiceData)
                                        .addOnSuccessListener(aVoid1 -> {
                                            // Cập nhật trạng thái phòng
                                            firestore.collection("rooms").document(String.valueOf(roomId))
                                                    .update("TrangThai", "Đã đặt")
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        if (!isFinishing()) {
                                                            // Chuyển sang InvoiceActivity
                                                            Intent intent = new Intent(this, InvoiceActivity.class);
                                                            intent.putExtra("invoiceId", invoiceId);
                                                            intent.putExtra("customerName", customerName);
                                                            intent.putExtra("customerPhone", customerPhone);
                                                            intent.putExtra("customerCCCD", customerCCCD);
                                                            intent.putExtra("customerEmail", customerEmail);
                                                            intent.putExtra("roomName", textRoomName.getText().toString());
                                                            intent.putExtra("checkInDate", checkInDate);
                                                            intent.putExtra("checkOutDate", checkOutDate);
                                                            intent.putExtra("totalPrice", totalPrice);
                                                            intent.putExtra("voucherCode", voucherCode);
                                                            intent.putExtra("status", "Đang xử lý");
                                                            startActivity(intent);

                                                            // Lưu vào SharedPreferences
                                                            sharedPreferences.edit()
                                                                    .putString("USER_NAME", customerName)
                                                                    .putString("USER_PHONE", customerPhone)
                                                                    .putString("USER_EMAIL", customerEmail)
                                                                    .apply();

                                                            showToast("Đặt phòng thành công!");
                                                            finish();
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        if (!isFinishing()) showToast("Lỗi cập nhật trạng thái phòng: " + e.getMessage());
                                                    })
                                                    .addOnCompleteListener(task -> resetBookingState());
                                        })
                                        .addOnFailureListener(e -> {
                                            if (!isFinishing()) showToast("Lỗi lưu hóa đơn: " + e.getMessage());
                                            resetBookingState();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                if (!isFinishing()) showToast("Lỗi lưu đơn đặt phòng: " + e.getMessage());
                                resetBookingState();
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing()) showToast("Lỗi lưu thông tin khách hàng: " + e.getMessage());
                    resetBookingState();
                });
    }

    private void getVoucherDiscount(String voucherCode, OnDiscountListener listener) {
        if (voucherCode.isEmpty()) {
            listener.onResult(0);
            return;
        }
        firestore.collection("vouchers").document(voucherCode).get()
                .addOnSuccessListener(document -> listener.onResult(document.exists() ? document.getDouble("ChietKhau") != null ? document.getDouble("ChietKhau") : 0 : 0))
                .addOnFailureListener(e -> listener.onResult(0));
    }

    private interface OnDiscountListener {
        void onResult(double discount);
    }

    private double calculateTotalPrice() {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date dateCheckIn = dateFormat.parse(checkInDate);
            Date dateCheckOut = dateFormat.parse(checkOutDate);
            long diffInDays = (dateCheckOut.getTime() - dateCheckIn.getTime()) / (1000 * 60 * 60 * 24);
            if (diffInDays <= 0) {
                showToast("Ngày không hợp lệ!");
                return 0;
            }
            double totalPrice = roomPrice * diffInDays;
            if (diffInDays >= 5 && diffInDays <= 10) totalPrice *= 0.9;
            return totalPrice;
        } catch (Exception e) {
            showToast("Lỗi tính giá!");
            return 0;
        }
    }

    private void updateTotalPrice() {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date dateCheckIn = dateFormat.parse(checkInDate);
            Date dateCheckOut = dateFormat.parse(checkOutDate);
            long diffInDays = (dateCheckOut.getTime() - dateCheckIn.getTime()) / (1000 * 60 * 60 * 24);
            if (diffInDays <= 0) {
                textTotalPrice.setText("Không hợp lệ");
                textTotalDays.setText("0 ngày");
                return;
            }
            double totalPrice = calculateTotalPrice();
            textTotalPrice.setText(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalPrice));
            textTotalDays.setText(diffInDays + " ngày");
        } catch (Exception e) {
            textTotalPrice.setText("Lỗi giá");
            textTotalDays.setText("0 ngày");
            showToast("Lỗi hiển thị giá!");
        }
    }

    private boolean isValidPhone(String phone) {
        return phone.length() == 10 && phone.matches("\\d+");
    }

    private boolean isValidEmail(String email) {
        return !email.isEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidCCCD(String cccd) {
        return cccd.length() == 12 && cccd.matches("\\d+");
    }

    private void showToast(String message) {
        if (!isFinishing()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void resetBookingState() {
        isBookingInProgress = false;
        btnContinue.setEnabled(true);
    }
}