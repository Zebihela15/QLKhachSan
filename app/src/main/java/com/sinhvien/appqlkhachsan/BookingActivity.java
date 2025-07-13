package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.NumberPicker;

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
    private TextView textRoomName, dateRange, timeRange, textTotalPrice, textTotalDays;
    private ImageView roomImage, btnBack, btnRefresh;
    private EditText editCustomerName, editCustomerPhone, editCustomerCCCD, editCustomerEmail, editVoucherCode, editSpecialRequest;
    private NumberPicker numberPickerGuests;
    private Button btnContinue;
    private TextView btnSelectDate, btnSelectTime;
    private double roomPrice;
    private String checkInDate, checkOutDate, checkInTime, checkOutTime;
    private int roomId, guestCount;
    private SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private boolean isBookingInProgress;
    private static final double EARLY_CHECKIN_FEE = 50000; // 50,000 VND per hour before 14:00
    private static final double LATE_CHECKOUT_FEE = 50000; // 50,000 VND per hour after 12:00

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        // Initialize
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Bind views
        textRoomName = findViewById(R.id.hotelName);
        roomImage = findViewById(R.id.hotelImage);
        dateRange = findViewById(R.id.dateRange);
        timeRange = findViewById(R.id.timeRange);
        editCustomerName = findViewById(R.id.editCustomerName);
        editCustomerPhone = findViewById(R.id.editCustomerPhone);
        editCustomerCCCD = findViewById(R.id.editCustomerCCCD);
        editCustomerEmail = findViewById(R.id.editCustomerEmail);
        editVoucherCode = findViewById(R.id.editVoucherCode);
        editSpecialRequest = findViewById(R.id.editSpecialRequest);
        numberPickerGuests = findViewById(R.id.numberPickerGuests);
        btnSelectDate = findViewById(R.id.btnSelectDate);
        btnSelectTime = findViewById(R.id.btnSelectTime);
        btnContinue = findViewById(R.id.btnContinue);
        textTotalPrice = findViewById(R.id.textTotalPrice);
        textTotalDays = findViewById(R.id.textTotalDays);
        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);

        // Setup NumberPicker for guest count
        numberPickerGuests.setMinValue(2);
        numberPickerGuests.setMaxValue(4);
        numberPickerGuests.setValue(2);
        numberPickerGuests.setOnValueChangedListener((picker, oldVal, newVal) -> {
            guestCount = newVal;
            updateTotalPrice();
        });

        initUserData();
        initRoomData();
        initDefaultDates();
        initDefaultTimes();

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());
        btnSelectTime.setOnClickListener(v -> showCheckInTimePickerDialog());
        btnContinue.setOnClickListener(v -> {
            if (!isBookingInProgress) {
                isBookingInProgress = true;
                btnContinue.setEnabled(false);
                handleBooking();
            }
        });
        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> {
            initUserData();
            initRoomData();
            initDefaultDates();
            initDefaultTimes();
            numberPickerGuests.setValue(2);
            editSpecialRequest.setText("");
            showToast("Đã làm mới dữ liệu");
        });
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

        editCustomerName.setText(savedName);
        editCustomerPhone.setText(savedPhone);
        editCustomerEmail.setText(savedEmail);
        editCustomerCCCD.setText("");

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
        guestCount = 2; // Default guest count
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

    private void initDefaultTimes() {
        checkInTime = "14:00";
        checkOutTime = "12:00";
        timeRange.setText(String.format("%s - %s", checkInTime, checkOutTime));
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

    private void showCheckInTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minuteOfDay) -> {
                    String selectedTime = String.format("%02d:%02d", hourOfDay, minuteOfDay);
                    if (!selectedTime.equals("14:00")) {
                        editSpecialRequest.setText(String.format("Yêu cầu check-in lúc %s", selectedTime));
                    }
                    checkInTime = "14:00"; // Keep fixed check-in time
                    showCheckOutTimePickerDialog();
                }, hour, minute, true);
        timePickerDialog.show();
    }

    private void showCheckOutTimePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minuteOfDay) -> {
                    String selectedTime = String.format("%02d:%02d", hourOfDay, minuteOfDay);
                    if (!selectedTime.equals("12:00")) {
                        String currentRequest = editSpecialRequest.getText().toString();
                        String newRequest = currentRequest.isEmpty() ?
                                String.format("Yêu cầu check-out lúc %s", selectedTime) :
                                String.format("%s; Yêu cầu check-out lúc %s", currentRequest, selectedTime);
                        editSpecialRequest.setText(newRequest);
                    }
                    checkOutTime = "12:00"; // Keep fixed check-out time
                    timeRange.setText(String.format("%s - %s", checkInTime, checkOutTime));
                }, hour, minute, true);
        timePickerDialog.show();
    }

    private void isRoomAvailable(int roomId, String checkInDate, String checkOutDate, OnRoomAvailabilityListener listener) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            Date start = dateFormat.parse(checkInDate + " " + checkInTime);
            Date end = dateFormat.parse(checkOutDate + " " + checkOutTime);
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

        String maKH = currentUser.getUid();

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            Date newCheckIn = dateFormat.parse(checkInDate + " " + checkInTime);
            Date newCheckOut = dateFormat.parse(checkOutDate + " " + checkOutTime);

            firestore.collection("invoices")
                    .whereEqualTo("MaKH", maKH)
                    .whereNotEqualTo("TrangThai", "Hủy")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        boolean hasOverlap = false;
                        for (DocumentSnapshot document : querySnapshot) {
                            try {
                                String bookedStartStr = document.getString("TGCheckin");
                                String bookedEndStr = document.getString("TGCheckout");
                                if (bookedStartStr == null || bookedEndStr == null) continue;
                                Date bookedStart = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(bookedStartStr);
                                Date bookedEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(bookedEndStr);
                                if (!(newCheckOut.before(bookedStart) || newCheckIn.after(bookedEnd))) {
                                    hasOverlap = true;
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                        if (hasOverlap) {
                            showToast("Bạn đã đặt phòng trong khoảng thời gian này!");
                            resetBookingState();
                            return;
                        }
                        proceedWithBooking();
                    })
                    .addOnFailureListener(e -> {
                        showToast("Lỗi kiểm tra lịch đặt phòng: " + e.getMessage());
                        resetBookingState();
                    });
        } catch (Exception e) {
            showToast("Lỗi xử lý ngày đặt phòng!");
            resetBookingState();
            return;
        }
    }

    private void proceedWithBooking() {
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
        String specialRequest = editSpecialRequest.getText().toString().trim();
        String maKH = currentUser.getUid();

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
        if (guestCount < 2 || guestCount > 4) {
            showToast("Số lượng khách phải từ 2 đến 4!");
            resetBookingState();
            return;
        }

        SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String tgDat = dbFormat.format(new Date());
        String tgCheckIn, tgCheckOut;
        try {
            tgCheckIn = dbFormat.format(inputFormat.parse(checkInDate + " " + checkInTime));
            tgCheckOut = dbFormat.format(inputFormat.parse(checkOutDate + " " + checkOutTime));
        } catch (Exception e) {
            showToast("Lỗi định dạng ngày!");
            resetBookingState();
            return;
        }

        double baseTotalPrice = calculateTotalPrice();
        if (baseTotalPrice <= 0) {
            resetBookingState();
            return;
        }

        if (!voucherCode.isEmpty()) {
            getVoucherDiscount(voucherCode, discount -> {
                double finalTotalPrice = baseTotalPrice * (1 - discount / 100);
                if (discount == 0 && !voucherCode.isEmpty()) {
                    showToast("Mã giảm giá không hợp lệ!");
                }
                saveBooking(maKH, customerName, customerPhone, customerCCCD, customerEmail, voucherCode, tgDat, tgCheckIn, tgCheckOut, finalTotalPrice, specialRequest);
            });
        } else {
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
                    bookingData.put("TrangThaiDD", "Đang xử lý");
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
                                invoiceData.put("TrangThai", "Đang xử lý");
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
                                                            intent.putExtra("status", "Đang xử lý");
                                                            startActivity(intent);

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
            double basePrice = roomPrice * diffInDays * guestCount;
            if (diffInDays >= 5 && diffInDays <= 10) basePrice *= 0.9;

            // Calculate additional fees for special requests
            double additionalFee = 0;
            String specialRequest = editSpecialRequest.getText().toString().trim();
            if (!specialRequest.isEmpty()) {
                if (specialRequest.contains("check-in")) {
                    String[] parts = specialRequest.split("check-in lúc ");
                    if (parts.length > 1) {
                        String time = parts[1].split(";")[0].trim();
                        int hoursEarly = 14 - Integer.parseInt(time.split(":")[0]);
                        if (hoursEarly > 0) additionalFee += hoursEarly * EARLY_CHECKIN_FEE;
                    }
                }
                if (specialRequest.contains("check-out")) {
                    String[] parts = specialRequest.split("check-out lúc ");
                    if (parts.length > 1) {
                        String time = parts[1].trim();
                        int hoursLate = Integer.parseInt(time.split(":")[0]) - 12;
                        if (hoursLate > 0) additionalFee += hoursLate * LATE_CHECKOUT_FEE;
                    }
                }
            }
            return basePrice + additionalFee;
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
                textTotalPrice.setText("0 VNĐ");
                textTotalDays.setText("0 ngày");
                return;
            }
            double totalPrice = calculateTotalPrice();
            textTotalPrice.setText(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalPrice));
            textTotalDays.setText(diffInDays + " ngày, " + guestCount + " khách");
        } catch (Exception e) {
            textTotalPrice.setText("0 VNĐ");
            textTotalDays.setText("0 ngày");
            showToast("Lỗi hiển thị giá!");
        }
    }

    private boolean isValidPhone(String phone) {
        return phone.length() == 10 && phone.matches("\\d+");
    }

    private boolean isValidEmail(String email) {
        return email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
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