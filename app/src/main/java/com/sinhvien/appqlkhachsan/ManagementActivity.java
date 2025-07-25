package com.sinhvien.appqlkhachsan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.sinhvien.appqlkhachsan.admin.RoomManagementActivity;

public class ManagementActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_management);

        Button btnManageRooms = findViewById(R.id.btnManageRooms);
        btnManageRooms.setOnClickListener(v -> {
            Intent intent = new Intent(ManagementActivity.this, RoomManagementActivity.class);
            startActivity(intent);
        });
    }
}