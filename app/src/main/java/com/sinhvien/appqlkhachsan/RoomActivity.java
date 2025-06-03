package com.sinhvien.appqlkhachsan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class RoomActivity extends AppCompatActivity {
    TextView textRoomName, textArea, textStatus, textPrice, textNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnBookRoom = findViewById(R.id.btnBookRoom);

        // Ánh xạ các TextView
        textRoomName = findViewById(R.id.textRoomName);
        textArea = findViewById(R.id.textArea);
        textStatus = findViewById(R.id.textStatus);
        textPrice = findViewById(R.id.textPrice);
        textNote = findViewById(R.id.textNote);

        // Nhận dữ liệu từ Intent
        Intent intent = getIntent();
        String roomName = intent.getStringExtra("roomName");
        double area = intent.getDoubleExtra("area", 0);
        String status = intent.getStringExtra("status");
        double price = intent.getDoubleExtra("price", 0);
        String note = intent.getStringExtra("note");

        // Gán dữ liệu lên giao diện
        textRoomName.setText("Tên phòng: " + roomName);
        textArea.setText("Diện tích: " + area + " m²");
        textStatus.setText("Tình trạng: " + status);
        textPrice.setText("Giá thuê: " + (int) price + " VND");
        textNote.setText("Ghi chú: " + note);

        // Nút quay lại
        btnBack.setOnClickListener(view -> finish());

        // Nút đặt phòng
        btnBookRoom.setOnClickListener(view -> {
            Intent bookingIntent = new Intent(RoomActivity.this, BookingActivity.class);
            bookingIntent.putExtra("roomName", roomName);
            bookingIntent.putExtra("area", area);
            bookingIntent.putExtra("price", price);
            bookingIntent.putExtra("status", status);
            bookingIntent.putExtra("note", note);
            startActivity(bookingIntent);
        });
    }
}
