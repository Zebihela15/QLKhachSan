package com.sinhvien.appqlkhachsan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class RestaurantActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurant);

        TextView restaurantNameText = findViewById(R.id.restaurantName);
        Intent intent = getIntent();
        String restaurantName = intent.getStringExtra("restaurantName");
        restaurantNameText.setText("Tên nhà hàng: " + restaurantName);
    }
}