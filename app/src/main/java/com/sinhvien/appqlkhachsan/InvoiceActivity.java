package com.sinhvien.appqlkhachsan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class InvoiceActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);

        db = FirebaseFirestore.getInstance();

        TextView valueInvoiceId = findViewById(R.id.valueInvoiceId);
        TextView valueCustomerName = findViewById(R.id.valueCustomerName);
        TextView valueContact = findViewById(R.id.valueContact);
        TextView valueRoomName = findViewById(R.id.valueRoomName);
        TextView valueDateRange = findViewById(R.id.valueDateRange);
        TextView valueDays = findViewById(R.id.valueDays);
        TextView valueRoomPrice = findViewById(R.id.valueRoomPrice);
        TextView valueVoucher = findViewById(R.id.valueVoucher);
        TextView valueTotalPrice = findViewById(R.id.valueTotalPrice);
        TextView valueStatus = findViewById(R.id.valueStatus);
        Button btnBack = findViewById(R.id.btnBack);

        Intent intent = getIntent();
        String invoiceId = intent.getStringExtra("invoiceId"); // Sửa từ bookingId thành invoiceId

        if (invoiceId == null) {
            Toast.makeText(this, "Không tìm thấy hóa đơn", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("invoices").document(invoiceId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String customerName = documentSnapshot.getString("TenKhach");
                        String customerPhone = documentSnapshot.getString("SoDienThoai");
                        String customerCCCD = documentSnapshot.getString("CCCD");
                        String customerEmail = documentSnapshot.getString("Email");
                        String checkInDate = documentSnapshot.getString("TGCheckin");
                        String checkOutDate = documentSnapshot.getString("TGCheckout");
                        double totalPrice = documentSnapshot.getDouble("TongGia");
                        String voucherCode = documentSnapshot.getString("MaGiamGia");
                        String status = documentSnapshot.getString("TrangThai");
                        int maPhong = documentSnapshot.getLong("MaPhong").intValue();

                        // Lấy tên phòng từ rooms
                        db.collection("rooms").document(String.valueOf(maPhong)).get()
                                .addOnSuccessListener(roomDoc -> {
                                    String roomName = roomDoc.getString("TenPhong");

                                    // Chuyển định dạng ngày để tính số ngày
                                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                    SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                                    String displayCheckIn, displayCheckOut;
                                    long diffInDays = 0;
                                    try {
                                        displayCheckIn = displayFormat.format(inputFormat.parse(checkInDate));
                                        displayCheckOut = displayFormat.format(inputFormat.parse(checkOutDate));
                                        diffInDays = (inputFormat.parse(checkOutDate).getTime() -
                                                inputFormat.parse(checkInDate).getTime()) / (1000 * 60 * 60 * 24);
                                    } catch (ParseException e) {
                                        displayCheckIn = checkInDate;
                                        displayCheckOut = checkOutDate;
                                        Toast.makeText(this, "Lỗi định dạng ngày!", Toast.LENGTH_SHORT).show();
                                    }

                                    // Tính giá phòng mỗi đêm
                                    double originalPrice = diffInDays > 0 ? totalPrice / diffInDays : totalPrice;

                                    // Lấy thông tin mã giảm giá
                                    if (voucherCode != null && !voucherCode.isEmpty()) {
                                        String finalDisplayCheckIn = displayCheckIn;
                                        String finalDisplayCheckOut = displayCheckOut;
                                        long finalDiffInDays = diffInDays;
                                        db.collection("vouchers").document(voucherCode).get()
                                                .addOnSuccessListener(voucherDoc -> {
                                                    double discount = voucherDoc.exists() ? voucherDoc.getDouble("ChietKhau") : 0;
                                                    updateInvoiceUI(valueInvoiceId, valueCustomerName, valueContact, valueRoomName,
                                                            valueDateRange, valueDays, valueRoomPrice, valueVoucher, valueTotalPrice,
                                                            valueStatus, invoiceId, customerName, customerPhone, customerCCCD,
                                                            customerEmail, roomName, finalDisplayCheckIn, finalDisplayCheckOut, finalDiffInDays,
                                                            originalPrice, totalPrice, voucherCode, discount, status);
                                                });
                                    } else {
                                        updateInvoiceUI(valueInvoiceId, valueCustomerName, valueContact, valueRoomName,
                                                valueDateRange, valueDays, valueRoomPrice, valueVoucher, valueTotalPrice,
                                                valueStatus, invoiceId, customerName, customerPhone, customerCCCD,
                                                customerEmail, roomName, displayCheckIn, displayCheckOut, diffInDays,
                                                originalPrice, totalPrice, voucherCode, 0, status);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Lỗi tải tên phòng!", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Toast.makeText(this, "Không tìm thấy hóa đơn", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi tải hóa đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });

        btnBack.setOnClickListener(v -> finish());
    }

    private void updateInvoiceUI(TextView valueInvoiceId, TextView valueCustomerName, TextView valueContact,
                                 TextView valueRoomName, TextView valueDateRange, TextView valueDays,
                                 TextView valueRoomPrice, TextView valueVoucher, TextView valueTotalPrice,
                                 TextView valueStatus, String invoiceId, String customerName, String customerPhone,
                                 String customerCCCD, String customerEmail, String roomName, String checkInDate,
                                 String checkOutDate, long diffInDays, double originalPrice, double totalPrice,
                                 String voucherCode, double discount, String status) {
        valueInvoiceId.setText(invoiceId.length() > 8 ? invoiceId.substring(0, 8) + "..." : invoiceId);
        valueCustomerName.setText(customerName != null ? customerName : "N/A");
        valueContact.setText(String.format("%s / %s / %s", customerPhone != null ? customerPhone : "N/A",
                customerEmail != null ? customerEmail : "N/A", customerCCCD != null ? customerCCCD : "N/A"));
        valueRoomName.setText(roomName != null ? roomName : "N/A");
        valueDateRange.setText(String.format("%s - %s", checkInDate, checkOutDate));
        valueDays.setText(diffInDays + " ngày");
        valueRoomPrice.setText(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(originalPrice) + " / đêm");
        valueVoucher.setText(voucherCode != null && !voucherCode.isEmpty() ? voucherCode + " (" + discount + "%)" : "Không có");
        valueTotalPrice.setText(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalPrice));
        valueStatus.setText(status != null ? status : "N/A");
    }
}