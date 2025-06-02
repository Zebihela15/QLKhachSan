package com.sinhvien.appqlkhachsan;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class HomePageActivity extends AppCompatActivity {

    private ListView listViewRooms, listViewRestaurants;
    private Spinner spinnerSort;
    private ArrayList<RoomModel> roomList;
    private ArrayList<RestaurantModel> restaurantList;
    private RoomAdapter roomAdapter;
    private ArrayList<RoomModel> originalRoomList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        listViewRooms = findViewById(R.id.listViewRooms);
        listViewRestaurants = findViewById(R.id.listViewRestaurants);
        spinnerSort = findViewById(R.id.spinnerSort);

        // Dữ liệu phòng
        roomList = new ArrayList<>();
        roomList.add(new RoomModel("Phòng tiêu chuẩn - 500.000đ", R.drawable.standard_room));
        roomList.add(new RoomModel("Phòng VIP - 2.000.000đ", R.drawable.vip_room));
        roomList.add(new RoomModel("Phòng 5 sao - 1.500.000đ", R.drawable.five_star_room));

        // Lưu bản gốc để reset khi chọn "Mặc định"
        originalRoomList = new ArrayList<>(roomList);

        roomAdapter = new RoomAdapter(this, roomList);
        listViewRooms.setAdapter(roomAdapter);

        // Dữ liệu nhà hàng
        restaurantList = new ArrayList<>();
        restaurantList.add(new RestaurantModel("Nhà hàng Á Đông", R.drawable.restaurant1));
        restaurantList.add(new RestaurantModel("Nhà hàng Buffet quốc tế", R.drawable.restaurant2));
        restaurantList.add(new RestaurantModel("Nhà hàng Chay An Lạc", R.drawable.restaurant3));

        RestaurantAdapter restaurantAdapter = new RestaurantAdapter(this, restaurantList);
        listViewRestaurants.setAdapter(restaurantAdapter);

        // Spinner sắp xếp
        String[] sortOptions = {"Mặc định", "Theo tên", "Theo giá"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sortAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortRooms(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        listViewRooms.setOnItemClickListener((parent, view, position, id) -> {
            RoomModel room = roomList.get(position);
            Toast.makeText(HomePageActivity.this, "Chọn: " + room.getName(), Toast.LENGTH_SHORT).show();
        });
    }

    private void sortRooms(int type) {
        switch (type) {
            case 1: // Theo tên
                Collections.sort(roomList, Comparator.comparing(RoomModel::getName));
                break;
            case 2: // Theo giá
                Collections.sort(roomList, new Comparator<RoomModel>() {
                    @Override
                    public int compare(RoomModel r1, RoomModel r2) {
                        return Integer.compare(extractPrice(r1.getName()), extractPrice(r2.getName()));
                    }
                });
                break;
            default: // Mặc định
                roomList.clear();
                roomList.addAll(originalRoomList);
                break;
        }
        roomAdapter.notifyDataSetChanged();
    }

    private int extractPrice(String roomName) {
        String digits = roomName.replaceAll("\\D+", "");
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
