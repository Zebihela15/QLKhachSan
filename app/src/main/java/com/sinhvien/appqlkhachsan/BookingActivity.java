package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
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
    private static final String TAG = "BookingActivity";
    private TextView textRoomName, dateRange, timeRange, textTotalPrice, textTotalDays;
    private TextView btnSelectDate;
    private ImageView btnBack, btnRefresh;
    private EditText editCustomerName, editCustomerPhone, editCustomerCCCD, editCustomerEmail, editVoucherCode, editSpecialRequest;
    private NumberPicker numberPickerGuests;
    private Button btnContinue;
    private double roomPrice;
    private String checkInDate, checkOutDate;
    private final String checkInTime = "14:00";
    private final String checkOutTime = "12:00";
    private int roomId, guestCount;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private boolean isBookingInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        textRoomName = findViewById(R.id.hotelName);
        dateRange = findViewById(R.id.dateRange);
        timeRange = findViewById(R.id.timeRange);
        textTotalPrice = findViewById(R.id.textTotalPrice);
        textTotalDays = findViewById(R.id.textTotalDays);
        editCustomerName = findViewById(R.id.editCustomerName);
        editCustomerPhone = findViewById(R.id.editCustomerPhone);
        editCustomerCCCD = findViewById(R.id.editCustomerCCCD);
        editCustomerEmail = findViewById(R.id.editCustomerEmail);
        editVoucherCode = findViewById(R.id.editVoucherCode);
        editSpecialRequest = findViewById(R.id.editSpecialRequest);
        numberPickerGuests = findViewById(R.id.numberPickerGuests);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnContinue = findViewById(R.id.btnContinue);
        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);

        if (textRoomName == null || dateRange == null || timeRange == null || textTotalPrice == null ||
                textTotalDays == null || editCustomerName == null || editCustomerPhone == null ||
                editCustomerCCCD == null || editCustomerEmail == null || editVoucherCode == null ||
                editSpecialRequest == null || numberPickerGuests == null || btnSelectDate == null ||
                btnContinue == null || btnBack == null || btnRefresh == null) {
            Log.e(TAG, "One or more views are null");
            showSnackbar("Lỗi giao diện, vui lòng kiểm tra layout!");
            finish();
            return;
        }

        numberPickerGuests.setMinValue(2);
        numberPickerGuests.setMaxValue(4);
        numberPickerGuests.setValue(2);
        numberPickerGuests.setOnValueChangedListener((picker, oldVal, newVal) -> {
            guestCount = newVal;
            updateTotalPrice();
        });

        initRoomData();
        initDefaultDates();
        initDefaultTimes();

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());
        btnContinue.setOnClickListener(v -> {
            if (!isBookingInProgress) {
                isBookingInProgress = true;
                btnContinue.setEnabled(false);
                handleBooking();
            }
        });
        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> {
            initRoomData();
            initDefaultDates();
            initDefaultTimes();
            numberPickerGuests.setValue(2);
            editCustomerName.setText("");
            editCustomerPhone.setText("");
            editCustomerCCCD.setText("");
            editCustomerEmail.setText("");
            editSpecialRequest.setText("");
            showSnackbar("Đã làm mới dữ liệu");
        });
    }

    private void showSnackbar(String message) {
        Log.d(TAG, "Displaying Snackbar: " + message);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                Log.e(TAG, "Cannot display Snackbar: Activity is finishing or destroyed");
                return;
            }
            try {
                Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
                Log.d(TAG, "Snackbar displayed successfully: " + message);
            } catch (Exception e) {
                Log.e(TAG, "Error displaying Snackbar: " + e.getMessage());
            }
        });
    }

    private void initRoomData() {
        Intent intent = getIntent();
        roomId = intent.getIntExtra("ROOM_ID", 0);
        String roomName = intent.getStringExtra("roomName");
        roomPrice = intent.getDoubleExtra("price", 0.0);
        String selectedDate = intent.getStringExtra("selectedDate");
        guestCount = 2;

        Log.d(TAG, "initRoomData - roomId: " + roomId + ", roomName: " + roomName + ", price: " + roomPrice + ", selectedDate: " + selectedDate);

        if (roomName == null || roomId == 0 || roomPrice <= 0) {
            Log.e(TAG, "Invalid room data: roomId=" + roomId + ", roomName=" + roomName + ", price=" + roomPrice);
            showSnackbar("Thông tin phòng không hợp lệ!");
            finish();
            return;
        }
        if (selectedDate != null && selectedDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            checkInDate = selectedDate;
        } else {
            Log.e(TAG, "Invalid selectedDate: " + selectedDate);
            checkInDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        }
        textRoomName.setText(roomName);
    }

    private void initDefaultDates() {
        try {
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Calendar calendar = Calendar.getInstance();

            if (checkInDate == null || checkInDate.isEmpty()) {
                checkInDate = dbFormat.format(calendar.getTime());
            }
            calendar.setTime(dbFormat.parse(checkInDate));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            checkOutDate = dbFormat.format(calendar.getTime());

            Date checkIn = dbFormat.parse(checkInDate);
            Date checkOut = dbFormat.parse(checkOutDate);
            if (checkIn != null && checkOut != null) {
                dateRange.setText(String.format("%s - %s", displayFormat.format(checkIn), displayFormat.format(checkOut)));
            } else {
                dateRange.setText("Chọn ngày");
                showSnackbar("Ngày không hợp lệ!");
            }
            updateTotalPrice();
        } catch (Exception e) {
            Log.e(TAG, "Exception in initDefaultDates: " + e.getMessage());
            dateRange.setText("Chọn ngày");
            showSnackbar("Lỗi khởi tạo ngày!");
        }
    }

    private void initDefaultTimes() {
        timeRange.setText(String.format("%s - %s", checkInTime, checkOutTime));
        updateTotalPrice();
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        if (checkInDate != null && !checkInDate.isEmpty()) {
            try {
                calendar.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(checkInDate));
            } catch (Exception ignored) {}
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    if (isFinishing()) return;
                    checkInDate = String.format(Locale.getDefault(), "%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                    try {
                        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        if (dbFormat.parse(checkInDate).before(new Date())) {
                            showSnackbar("Không thể chọn ngày quá khứ!");
                            return;
                        }
                        showCheckOutDatePickerDialog();
                    } catch (Exception e) {
                        Log.e(TAG, "Exception in showDatePickerDialog: " + e.getMessage());
                        showSnackbar("Lỗi xử lý ngày!");
                    }
                }, year, month, day);
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void showCheckOutDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        try {
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            if (checkInDate != null && !checkInDate.isEmpty()) {
                calendar.setTime(dbFormat.parse(checkInDate));
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in showCheckOutDatePickerDialog init: " + e.getMessage());
            showSnackbar("Lỗi khởi tạo ngày nhận phòng!");
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    if (isFinishing()) return;
                    checkOutDate = String.format(Locale.getDefault(), "%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                    try {
                        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        Date checkIn = dbFormat.parse(checkInDate);
                        Date checkOut = dbFormat.parse(checkOutDate);
                        if (checkOut.before(checkIn)) {
                            showSnackbar("Ngày trả phòng phải sau ngày nhận phòng!");
                            return;
                        }
                        isRoomAvailable(roomId, checkInDate, checkOutDate, isAvailable -> {
                            if (!isFinishing()) {
                                if (!isAvailable) {
                                    showSnackbar("Phòng đã được đặt trong khoảng thời gian này!");
                                    return;
                                }
                                if (checkIn != null && checkOut != null) {
                                    dateRange.setText(String.format("%s - %s", displayFormat.format(checkIn), displayFormat.format(checkOut)));
                                    updateTotalPrice();
                                } else {
                                    dateRange.setText("Chọn ngày");
                                    showSnackbar("Ngày không hợp lệ!");
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Exception in showCheckOutDatePickerDialog: " + e.getMessage());
                        showSnackbar("Lỗi xử lý ngày!");
                        dateRange.setText("Chọn ngày");
                    }
                }, year, month, day);
        datePickerDialog.getDatePicker().setMinDate(calendar.getTimeInMillis());
        datePickerDialog.show();
    }

    private void isRoomAvailable(int roomId, String checkInDate, String checkOutDate, OnRoomAvailabilityListener listener) {
        try {
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String start = checkInDate + " " + checkInTime + ":00";
            String end = checkOutDate + " " + checkOutTime + ":00";
            Date startDate = dbFormat.parse(start);
            Date endDate = dbFormat.parse(end);
            Log.d(TAG, "isRoomAvailable - roomId: " + roomId + ", start: " + start + ", end: " + end);
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
                                Date bookedStart = dbFormat.parse(bookedStartStr);
                                Date bookedEnd = dbFormat.parse(bookedEndStr);
                                if (!(endDate.before(bookedStart) || startDate.after(bookedEnd))) {
                                    isAvailable = false;
                                    Log.d(TAG, "Room conflict found: bookedStart=" + bookedStartStr + ", bookedEnd=" + bookedEndStr);
                                    break;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing booked dates: " + e.getMessage());
                            }
                        }
                        Log.d(TAG, "Room availability: " + isAvailable);
                        listener.onResult(isAvailable);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking room availability: " + e.getMessage());
                        listener.onResult(false);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in isRoomAvailable: " + e.getMessage());
            listener.onResult(false);
        }
    }

    private interface OnRoomAvailabilityListener {
        void onResult(boolean isAvailable);
    }

    private void handleBooking() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not logged in");
            showSnackbar("Vui lòng đăng nhập để đặt phòng!");
            resetBookingState();
            return;
        }

        String maKH = currentUser.getUid();
        Log.d(TAG, "handleBooking - maKH: " + maKH + ", roomId: " + roomId + ", checkInDate: " + checkInDate + ", checkOutDate: " + checkOutDate);

        try {
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String start = checkInDate + " " + checkInTime + ":00";
            String end = checkOutDate + " " + checkOutTime + ":00";
            Date newCheckIn = dbFormat.parse(start);
            Date newCheckOut = dbFormat.parse(end);

            if (newCheckIn == null || newCheckOut == null) {
                Log.e(TAG, "Invalid check-in/check-out dates: start=" + start + ", end=" + end);
                showSnackbar("Lỗi định dạng ngày đặt phòng!");
                resetBookingState();
                return;
            }

            isRoomAvailable(roomId, checkInDate, checkOutDate, isAvailable -> {
                if (!isFinishing()) {
                    if (!isAvailable) {
                        Log.e(TAG, "Room not available for dates: " + start + " to " + end);
                        showSnackbar("Phòng đã được đặt trong khoảng thời gian này!");
                        resetBookingState();
                        return;
                    }
                    proceedWithBooking();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in handleBooking: " + e.getMessage());
            showSnackbar("Lỗi xử lý ngày đặt phòng!");
            resetBookingState();
        }
    }

    private void proceedWithBooking() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not logged in in proceedWithBooking");
            showSnackbar("Vui lòng đăng nhập để đặt phòng!");
            resetBookingState();
            return;
        }

        String customerName = editCustomerName.getText().toString().trim();
        String customerPhone = editCustomerPhone.getText().toString().trim();
        String customerCCCD = editCustomerCCCD.getText().toString().trim();
        String customerEmail = editCustomerEmail.getText().toString().trim();
        String voucherCode = editVoucherCode.getText().toString().trim();
        String specialRequest = editSpecialRequest.getText().toString().trim();
        String maKH = currentUser.getUid();

        Log.d(TAG, "proceedWithBooking - customerName: " + customerName + ", phone: " + customerPhone + ", CCCD: " + customerCCCD + ", email: " + customerEmail + ", voucher: " + voucherCode);

        if (customerName.isEmpty() || customerPhone.isEmpty() || customerCCCD.isEmpty()) {
            Log.e(TAG, "Missing required fields: name=" + customerName + ", phone=" + customerPhone + ", CCCD=" + customerCCCD);
            showSnackbar("Vui lòng nhập đầy đủ thông tin!");
            resetBookingState();
            return;
        }
        if (!isValidPhone(customerPhone)) {
            Log.e(TAG, "Invalid phone: " + customerPhone);
            editCustomerPhone.setError("Số điện thoại phải là 10 số, bắt đầu bằng 0!");
            showSnackbar("Số điện thoại không hợp lệ!");
            resetBookingState();
            return;
        }
        if (!isValidCCCD(customerCCCD)) {
            Log.e(TAG, "Invalid CCCD: " + customerCCCD);
            editCustomerCCCD.setError("CCCD phải là 12 số!");
            showSnackbar("CCCD không hợp lệ!");
            resetBookingState();
            return;
        }
        if (!isValidEmail(customerEmail)) {
            Log.e(TAG, "Invalid email: " + customerEmail);
            editCustomerEmail.setError("Email phải kết thúc bằng @gmail.com!");
            showSnackbar("Email không hợp lệ!");
            resetBookingState();
            return;
        }
        if (roomId == 0) {
            Log.e(TAG, "Invalid roomId: " + roomId);
            showSnackbar("Phòng không hợp lệ!");
            resetBookingState();
            return;
        }
        if (guestCount < 2 || guestCount > 4) {
            Log.e(TAG, "Invalid guestCount: " + guestCount);
            showSnackbar("Số lượng khách phải từ 2 đến 4!");
            resetBookingState();
            return;
        }

        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String tgDat = dbFormat.format(new Date());
        String tgCheckIn = checkInDate + " " + checkInTime + ":00";
        String tgCheckOut = checkOutDate + " " + checkOutTime + ":00";

        double baseTotalPrice = calculateTotalPrice();
        if (baseTotalPrice <= 0) {
            Log.e(TAG, "Invalid total price: " + baseTotalPrice);
            showSnackbar("Không thể tính giá phòng!");
            resetBookingState();
            return;
        }

        if (!voucherCode.isEmpty()) {
            getVoucherDiscount(voucherCode, discount -> {
                double finalTotalPrice = baseTotalPrice * (1 - discount / 100);
                Log.d(TAG, "Voucher applied: code=" + voucherCode + ", discount=" + discount + "%, finalPrice=" + finalTotalPrice);
                if (discount == 0 && !voucherCode.isEmpty()) {
                    showSnackbar("Mã giảm giá không hợp lệ!");
                }
                saveBooking(maKH, customerName, customerPhone, customerCCCD, customerEmail, voucherCode, tgDat, tgCheckIn, tgCheckOut, finalTotalPrice, specialRequest);
            });
        } else {
            Log.d(TAG, "No voucher applied, finalPrice=" + baseTotalPrice);
            saveBooking(maKH, customerName, customerPhone, customerCCCD, customerEmail, voucherCode, tgDat, tgCheckIn, tgCheckOut, baseTotalPrice, specialRequest);
        }
    }

    private void saveBooking(String maKH, String customerName, String customerPhone, String customerCCCD,
                             String customerEmail, String voucherCode, String tgDat, String tgCheckIn,
                             String tgCheckOut, double totalPrice, String specialRequest) {
        Map<String, Object> customerData = new HashMap<>();
        customerData.put("MaKH", maKH);
        customerData.put("TenKH", customerName);
        customerData.put("SDT", customerPhone);
        customerData.put("Email", customerEmail);
        customerData.put("CCCD", customerCCCD);

        firestore.collection("customers").document(maKH).set(customerData)
                .addOnSuccessListener(aVoid -> {
                    Map<String, Object> bookingData = new HashMap<>();
                    bookingData.put("MaKH", maKH);
                    bookingData.put("MaPhong", roomId);
                    bookingData.put("MaGiam", voucherCode.isEmpty() ? null : voucherCode);
                    bookingData.put("TGDat", tgDat);
                    bookingData.put("TGCheckin", tgCheckIn);
                    bookingData.put("TGCheckout", tgCheckOut);
                    bookingData.put("TrangThaiTT", "Chưa thanh toán");
                    bookingData.put("TrangThaiDD", "Đã xác nhận"); // Changed to "Đã xác nhận"
                    bookingData.put("SoKhach", guestCount);
                    bookingData.put("YeuCauDacBiet", specialRequest.isEmpty() ? null : specialRequest);

                    firestore.collection("bookings").add(bookingData)
                            .addOnSuccessListener(documentReference -> {
                                String maDon = documentReference.getId();
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
                                invoiceData.put("TrangThai", "Đã xác nhận"); // Changed to "Đã xác nhận"
                                invoiceData.put("MaGiamGia", voucherCode.isEmpty() ? null : voucherCode);
                                invoiceData.put("SoKhach", guestCount);
                                invoiceData.put("YeuCauDacBiet", specialRequest.isEmpty() ? null : specialRequest);

                                firestore.collection("invoices").document(invoiceId).set(invoiceData)
                                        .addOnSuccessListener(aVoid1 -> {
                                            firestore.collection("rooms").document(String.valueOf(roomId))
                                                    .update("TrangThai", "Đã đặt")
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        if (!isFinishing()) {
                                                            Intent intent = new Intent(this, InvoiceActivity.class);
                                                            intent.putExtra("invoiceId", invoiceId);
                                                            intent.putExtra("customerName", customerName);
                                                            intent.putExtra("customerPhone", customerPhone);
                                                            intent.putExtra("customerCCCD", customerCCCD);
                                                            intent.putExtra("customerEmail", customerEmail);
                                                            intent.putExtra("roomName", textRoomName.getText().toString());
                                                            intent.putExtra("checkInDate", checkInDate + " " + checkInTime);
                                                            intent.putExtra("checkOutDate", checkOutDate + " " + checkOutTime);
                                                            intent.putExtra("totalPrice", totalPrice);
                                                            intent.putExtra("voucherCode", voucherCode);
                                                            intent.putExtra("guestCount", guestCount);
                                                            intent.putExtra("specialRequest", specialRequest);
                                                            intent.putExtra("status", "Đã xác nhận"); // Changed to "Đã xác nhận"
                                                            Log.d(TAG, "Starting InvoiceActivity with invoiceId: " + invoiceId);
                                                            startActivity(intent);

                                                            sharedPreferences.edit()
                                                                    .putString("USER_NAME", customerName)
                                                                    .putString("USER_PHONE", customerPhone)
                                                                    .putString("USER_EMAIL", customerEmail)
                                                                    .putString("USER_CCCD", customerCCCD)
                                                                    .apply();

                                                            showSnackbar("Đặt phòng thành công!");
                                                            finish();
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        if (!isFinishing()) {
                                                            Log.e(TAG, "Error updating room status: " + e.getMessage());
                                                            showSnackbar("Lỗi cập nhật trạng thái phòng: " + e.getMessage());
                                                        }
                                                        resetBookingState();
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            if (!isFinishing()) {
                                                Log.e(TAG, "Error saving invoice: " + e.getMessage());
                                                showSnackbar("Lỗi lưu hóa đơn: " + e.getMessage());
                                            }
                                            resetBookingState();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                if (!isFinishing()) {
                                    Log.e(TAG, "Error saving booking: " + e.getMessage());
                                    showSnackbar("Lỗi lưu đơn đặt phòng: " + e.getMessage());
                                }
                                resetBookingState();
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing()) {
                        Log.e(TAG, "Error saving customer data: " + e.getMessage());
                        showSnackbar("Lỗi lưu thông tin khách hàng: " + e.getMessage());
                    }
                    resetBookingState();
                });
    }

    private void getVoucherDiscount(String voucherCode, OnDiscountListener listener) {
        if (voucherCode.isEmpty()) {
            Log.d(TAG, "No voucher code provided");
            listener.onResult(0);
            return;
        }
        firestore.collection("vouchers").document(voucherCode).get()
                .addOnSuccessListener(document -> {
                    double discount = document.exists() && document.getDouble("ChietKhau") != null ? document.getDouble("ChietKhau") : 0;
                    Log.d(TAG, "Voucher discount fetched: code=" + voucherCode + ", discount=" + discount);
                    listener.onResult(discount);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching voucher: " + e.getMessage());
                    listener.onResult(0);
                });
    }

    private interface OnDiscountListener {
        void onResult(double discount);
    }

    private double calculateTotalPrice() {
        Log.d(TAG, "calculateTotalPrice - roomPrice: " + roomPrice + ", checkInDate: " + checkInDate + ", checkOutDate: " + checkOutDate + ", guestCount: " + guestCount);
        try {
            if (roomPrice <= 0 || guestCount < 2 || checkInDate == null || checkOutDate == null) {
                Log.e(TAG, "Invalid input: roomPrice=" + roomPrice + ", guestCount=" + guestCount + ", checkInDate=" + checkInDate + ", checkOutDate=" + checkOutDate);
                showSnackbar("Dữ liệu không hợp lệ để tính giá!");
                return 0;
            }

            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date dateCheckIn = dbFormat.parse(checkInDate);
            Date dateCheckOut = dbFormat.parse(checkOutDate);

            if (dateCheckIn == null || dateCheckOut == null) {
                Log.e(TAG, "Failed to parse dates: checkInDate=" + checkInDate + ", checkOutDate=" + checkOutDate);
                showSnackbar("Lỗi định dạng ngày!");
                return 0;
            }

            long diffInDays = (dateCheckOut.getTime() - dateCheckIn.getTime()) / (1000 * 60 * 60 * 24);
            if (diffInDays <= 0) {
                Log.e(TAG, "Invalid date range: diffInDays=" + diffInDays);
                showSnackbar("Ngày không hợp lệ!");
                return 0;
            }

            double basePrice = roomPrice * diffInDays * guestCount;
            if (diffInDays >= 5 && diffInDays <= 10) {
                basePrice *= 0.9;
                Log.d(TAG, "Applied 10% discount for " + diffInDays + " days");
            }

            double totalPrice = basePrice;
            Log.d(TAG, "Total price calculated: basePrice=" + basePrice + ", totalPrice=" + totalPrice);
            return totalPrice;
        } catch (Exception e) {
            Log.e(TAG, "Exception in calculateTotalPrice: " + e.getMessage());
            showSnackbar("Lỗi tính giá!");
            return 0;
        }
    }

    private void updateTotalPrice() {
        if (textTotalPrice == null || textTotalDays == null) {
            Log.e(TAG, "Views are null: textTotalPrice=" + textTotalPrice + ", textTotalDays=" + textTotalDays);
            return;
        }
        try {
            SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date dateCheckIn = dbFormat.parse(checkInDate);
            Date dateCheckOut = dbFormat.parse(checkOutDate);

            if (dateCheckIn == null || dateCheckOut == null) {
                Log.e(TAG, "Invalid dates in updateTotalPrice: checkInDate=" + checkInDate + ", checkOutDate=" + checkOutDate);
                textTotalPrice.setText("0 VNĐ");
                textTotalDays.setText("0 ngày");
                showSnackbar("Lỗi ngày tháng!");
                return;
            }

            long diffInDays = (dateCheckOut.getTime() - dateCheckIn.getTime()) / (1000 * 60 * 60 * 24);
            if (diffInDays <= 0) {
                Log.e(TAG, "Invalid date range in updateTotalPrice: diffInDays=" + diffInDays);
                textTotalPrice.setText("0 VNĐ");
                textTotalDays.setText("0 ngày");
                showSnackbar("Lỗi hiển thị giá!");
                return;
            }

            double totalPrice = calculateTotalPrice();
            if (totalPrice <= 0) {
                Log.e(TAG, "Total price is zero or negative: " + totalPrice);
                textTotalPrice.setText("0 VNĐ");
                textTotalDays.setText(String.format(Locale.getDefault(), "%d ngày, %d khách", diffInDays, guestCount));
                showSnackbar("Không thể tính giá!");
                return;
            }

            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            currencyFormat.setMinimumFractionDigits(0);
            textTotalPrice.setText(currencyFormat.format(totalPrice));
            textTotalDays.setText(String.format(Locale.getDefault(), "%d ngày, %d khách", diffInDays, guestCount));
            Log.d(TAG, "Price updated: totalPrice=" + totalPrice + ", displayText=" + currencyFormat.format(totalPrice) + ", days=" + diffInDays + ", guests=" + guestCount);
        } catch (Exception e) {
            Log.e(TAG, "Exception in updateTotalPrice: " + e.getMessage());
            textTotalPrice.setText("0 VNĐ");
            textTotalDays.setText("0 ngày");
            showSnackbar("Lỗi hiển thị giá!");
        }
    }

    private boolean isValidPhone(String phone) {
        return phone.length() == 10 && phone.matches("0\\d{9}");
    }

    private boolean isValidEmail(String email) {
        return email.isEmpty() || email.matches(".+@gmail\\.com$");
    }

    private boolean isValidCCCD(String cccd) {
        return cccd.matches("^\\d{12}$");
    }

    private void resetBookingState() {
        isBookingInProgress = false;
        btnContinue.setEnabled(true);
    }
}