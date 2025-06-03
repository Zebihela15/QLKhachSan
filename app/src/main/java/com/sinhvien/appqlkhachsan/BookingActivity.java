package com.sinhvien.appqlkhachsan;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Spinner;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.Toast;


public class BookingActivity extends AppCompatActivity {

    private Spinner roomTypeSpinner;
    private DatePicker checkinDatePicker, checkoutDatePicker;
    private EditText guestCountEditText;
    private Button bookButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        roomTypeSpinner = findViewById(R.id.room_type_spinner);
        checkinDatePicker = findViewById(R.id.checkin_datepicker);
        checkoutDatePicker = findViewById(R.id.checkout_datepicker);
        guestCountEditText = findViewById(R.id.guest_count);
        bookButton = findViewById(R.id.book_button);

        // Gán dữ liệu cho Spinner
        String[] roomTypes = {"Phòng đơn", "Phòng đôi", "Phòng VIP"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roomTypeSpinner.setAdapter(adapter);

        // Xử lý khi nhấn nút Đặt phòng
        bookButton.setOnClickListener(v -> {
            String selectedRoom = roomTypeSpinner.getSelectedItem().toString();
            int guests = Integer.parseInt(guestCountEditText.getText().toString());

            // Lấy ngày nhận và trả phòng
            String checkIn = checkinDatePicker.getDayOfMonth() + "/" +
                    (checkinDatePicker.getMonth() + 1) + "/" +
                    checkinDatePicker.getYear();

            String checkOut = checkoutDatePicker.getDayOfMonth() + "/" +
                    (checkoutDatePicker.getMonth() + 1) + "/" +
                    checkoutDatePicker.getYear();

            Toast.makeText(this,
                    "Đặt phòng: " + selectedRoom + "\nKhách: " + guests +
                            "\nNhận: " + checkIn + "\nTrả: " + checkOut,
                    Toast.LENGTH_LONG).show();
        });
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            finish(); // Đóng activity, quay về màn trước
        });

    }
}