package com.sinhvien.appqlkhachsan;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ImageView imageHotel;
    private TextView textRoomName, textAddress, textDescription, textReadMore, textArea, textStatus, textNote, textRestaurantDescription, textPrice;
    private RecyclerView servicesRecyclerView;
    private Button btnBookRoom;
    private int roomId;
    private String roomName;
    private double roomPrice;
    private int imageResId;
    private Map<Integer, Double> roomTypePrices = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        db = FirebaseFirestore.getInstance();

        // Initialize views
        imageHotel = findViewById(R.id.imageHotel);
        textRoomName = findViewById(R.id.textRoomName);
        textAddress = findViewById(R.id.textAddress);
        textDescription = findViewById(R.id.textDescription);
        textReadMore = findViewById(R.id.textReadMore);
        textArea = findViewById(R.id.textArea);
        textStatus = findViewById(R.id.textStatus);
        textNote = findViewById(R.id.textNote);
        textRestaurantDescription = findViewById(R.id.textRestaurantDescription);
        textPrice = findViewById(R.id.textPrice);
        servicesRecyclerView = findViewById(R.id.servicesRecyclerView);
        btnBookRoom = findViewById(R.id.btnBookRoom);

        // Get ROOM_ID from Intent
        roomId = getIntent().getIntExtra("ROOM_ID", -1);
        if (roomId == -1) {
            finish(); // Đóng activity nếu không có ROOM_ID
            return;
        }

        // Load room types and details
        loadRoomTypesFromFirestore();
        loadRoomDetails();

        // Setup RecyclerView for amenities
        servicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        List<String> amenities = loadAmenities();
        AmenitiesAdapter amenitiesAdapter = new AmenitiesAdapter(amenities);
        servicesRecyclerView.setAdapter(amenitiesAdapter);

        // Setup Read More functionality
        textReadMore.setOnClickListener(v -> {
            if (textDescription.getMaxLines() == 3) {
                textDescription.setMaxLines(Integer.MAX_VALUE);
                textReadMore.setText("Thu gọn");
            } else {
                textDescription.setMaxLines(3);
                textReadMore.setText("Đọc thêm");
            }
        });

        // Setup Book button
        btnBookRoom.setOnClickListener(v -> {
            Intent intent = new Intent(RoomActivity.this, BookingActivity.class);
            intent.putExtra("ROOM_ID", roomId);
            intent.putExtra("roomName", roomName);
            intent.putExtra("price", roomPrice);
            intent.putExtra("imageResource", imageResId);
            startActivity(intent);
        });
    }

    private void loadRoomTypesFromFirestore() {
        db.collection("room_types").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot result = task.getResult();
                if (result != null) {
                    for (DocumentSnapshot doc : result.getDocuments()) {
                        int maLoaiPhong = doc.getLong("MaLoaiPhong").intValue();
                        Double giaPhong = doc.getDouble("GiaPhong");
                        if (giaPhong != null) {
                            roomTypePrices.put(maLoaiPhong, giaPhong);
                        }
                    }
                }
            }
        });
    }

    private void loadRoomDetails() {
        db.collection("rooms").document(String.valueOf(roomId)).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot doc = task.getResult();
                if (doc.exists()) {
                    roomName = doc.getString("TenPhong");
                    String description = doc.getString("MoTa");
                    imageResId = doc.getLong("HinhAnh").intValue();
                    double area = doc.getDouble("DienTich") != null ? doc.getDouble("DienTich") : 0;
                    String status = doc.getString("TrangThai");
                    String note = doc.getString("GhiChu");
                    String address = doc.getString("DiaChi");
                    int maLoaiPhong = doc.getLong("MaLoaiPhong").intValue();
                    roomPrice = roomTypePrices.getOrDefault(maLoaiPhong, 500.0); // Lấy giá từ room_types

                    textRoomName.setText(roomName);
                    textDescription.setText(description);
                    imageHotel.setImageResource(imageResId > 0 ? imageResId : android.R.drawable.ic_menu_gallery);
                    textAddress.setText(address);
                    textPrice.setText(String.format("%,d VND / đêm", (int) roomPrice));
                    textArea.setText(String.format("Diện tích: %.1f m²", area));
                    textStatus.setText("Tình trạng: " + status);
                    textNote.setText("Ghi chú: " + note);
                } else {
                    textRoomName.setText("Phòng không tồn tại");
                    textPrice.setText("Giá: N/A");
                    textDescription.setText("Không có thông tin");
                    imageHotel.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                textRoomName.setText("Lỗi tải thông tin");
                textPrice.setText("Giá: N/A");
                textDescription.setText("Lỗi: " + task.getException().getMessage());
                imageHotel.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        });

        // Load restaurant description (giả lập từ Firestore)
        db.collection("restaurants").limit(1).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                QuerySnapshot result = task.getResult();
                if (result != null && !result.isEmpty()) {
                    String restaurantDesc = result.getDocuments().get(0).getString("MoTa");
                    textRestaurantDescription.setText("Giới thiệu nhà hàng: " + restaurantDesc);
                }
            } else {
                textRestaurantDescription.setText("Lỗi tải thông tin nhà hàng: " + task.getException().getMessage());
            }
        });
    }

    private List<String> loadAmenities() {
        List<String> amenities = new ArrayList<>();
        db.collection("rooms").document(String.valueOf(roomId)).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot doc = task.getResult();
                if (doc.exists()) {
                    @SuppressWarnings("unchecked")
                    List<Object> tienIchIds = (List<Object>) doc.get("TienIch"); // Sử dụng Object để linh hoạt
                    if (tienIchIds != null) {
                        for (Object id : tienIchIds) {
                            int tienIchId = (id instanceof Long) ? ((Long) id).intValue() : ((Integer) id).intValue(); // Chuyển đổi an toàn
                            db.collection("amenities").document(String.valueOf(tienIchId)).get().addOnSuccessListener(amenityDoc -> {
                                String tenTienIch = amenityDoc.getString("TenTienIch");
                                if (tenTienIch != null) {
                                    amenities.add(tenTienIch);
                                    runOnUiThread(() -> {
                                        AmenitiesAdapter adapter = (AmenitiesAdapter) servicesRecyclerView.getAdapter();
                                        if (adapter != null) {
                                            adapter.notifyDataSetChanged();
                                        }
                                    });
                                }
                            });
                        }
                    }
                }
            }
        });
        return amenities;
    }

    // Adapter for amenities RecyclerView
    private static class AmenitiesAdapter extends RecyclerView.Adapter<AmenitiesAdapter.AmenityViewHolder> {
        private final List<String> amenities;

        public AmenitiesAdapter(List<String> amenities) {
            this.amenities = amenities;
        }

        @NonNull
        @Override
        public AmenityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new AmenityViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AmenityViewHolder holder, int position) {
            holder.textView.setText(amenities.get(position));
        }

        @Override
        public int getItemCount() {
            return amenities.size();
        }

        static class AmenityViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            public AmenityViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}