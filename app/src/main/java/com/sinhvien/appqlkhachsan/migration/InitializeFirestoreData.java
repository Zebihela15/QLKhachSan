package com.sinhvien.appqlkhachsan.migration;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InitializeFirestoreData {
    private static final String TAG = "InitializeFirestoreData";
    private final FirebaseFirestore db;

    public InitializeFirestoreData() {
        db = FirebaseFirestore.getInstance();
    }

    public void initializeData() {
        // Kiểm tra xem rooms đã có dữ liệu chưa
        db.collection("rooms").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty() && querySnapshot.size() >= 6) {
                        Log.d(TAG, "Collection rooms đã có " + querySnapshot.size() + " phòng, bỏ qua khởi tạo");
                        return;
                    }

                    // Initialize room types
                    List<Map<String, Object>> roomTypes = new ArrayList<>();
                    roomTypes.add(createRoomType(1, "Standard", 500000.0));
                    roomTypes.add(createRoomType(2, "VIP", 800000.0));
                    roomTypes.add(createRoomType(3, "Deluxe", 1200000.0));

                    // Lưu room types
                    for (Map<String, Object> roomType : roomTypes) {
                        db.collection("room_types")
                                .document(roomType.get("MaLoaiPhong").toString())
                                .set(roomType)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Room type " + roomType.get("TenLoaiPhong") + " initialized"))
                                .addOnFailureListener(e -> Log.e(TAG, "Error initializing room type: " + e.getMessage()));
                    }

                    // Lấy giá phòng từ room_types
                    Map<Integer, Double> roomTypePrices = new HashMap<>();
                    db.collection("room_types").get()
                            .addOnSuccessListener(roomTypeSnapshot -> {
                                for (DocumentSnapshot doc : roomTypeSnapshot) {
                                    Integer maLoaiPhong = doc.getLong("MaLoaiPhong") != null ? doc.getLong("MaLoaiPhong").intValue() : 0;
                                    Double giaPhong = doc.getDouble("GiaPhong") != null ? doc.getDouble("GiaPhong") : 0.0;
                                    if (maLoaiPhong != 0) {
                                        roomTypePrices.put(maLoaiPhong, giaPhong);
                                    }
                                }

                                // Initialize rooms
                                List<Map<String, Object>> rooms = new ArrayList<>();
                                rooms.add(createRoom(101, "Phòng 101 - Standard", 1, roomTypePrices.getOrDefault(1, 500000.0), 2, "Trống", "Phòng tiêu chuẩn thoải mái", List.of(1, 2)));
                                rooms.add(createRoom(102, "Phòng 102 - Standard", 1, roomTypePrices.getOrDefault(1, 500000.0), 2, "Trống", "Phòng tiêu chuẩn thoải mái", List.of(1, 2)));
                                rooms.add(createRoom(201, "Phòng 201 - VIP", 2, roomTypePrices.getOrDefault(2, 800000.0), 4, "Trống", "Phòng VIP sang trọng", List.of(1, 2, 3)));
                                rooms.add(createRoom(202, "Phòng 202 - VIP", 2, roomTypePrices.getOrDefault(2, 800000.0), 4, "Trống", "Phòng VIP sang trọng", List.of(1, 2, 3)));
                                rooms.add(createRoom(301, "Phòng 301 - Deluxe", 3, roomTypePrices.getOrDefault(3, 1200000.0), 4, "Trống", "Phòng Deluxe cao cấp", List.of(1, 2, 3, 4)));
                                rooms.add(createRoom(302, "Phòng 302 - Deluxe", 3, roomTypePrices.getOrDefault(3, 1200000.0), 4, "Trống", "Phòng Deluxe cao cấp", List.of(1, 2, 3, 4)));

                                // Xóa dữ liệu cũ nếu cần
                                db.collection("rooms").get().addOnSuccessListener(roomSnapshot -> {
                                    for (DocumentSnapshot doc : roomSnapshot) {
                                        db.collection("rooms").document(doc.getId()).delete()
                                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Xóa phòng: " + doc.getId()))
                                                .addOnFailureListener(e -> Log.e(TAG, "Lỗi xóa phòng: " + doc.getId() + ", " + e.getMessage()));
                                    }

                                    // Thêm phòng mới
                                    for (Map<String, Object> room : rooms) {
                                        Integer maPhong = (Integer) room.get("MaPhong");
                                        db.collection("rooms")
                                                .document(maPhong.toString())
                                                .set(room)
                                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Room " + room.get("TenPhong") + " initialized"))
                                                .addOnFailureListener(e -> Log.e(TAG, "Error initializing room: " + e.getMessage()));
                                    }
                                }).addOnFailureListener(e -> Log.e(TAG, "Lỗi truy vấn rooms: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> Log.e(TAG, "Lỗi lấy room_types: " + e.getMessage()));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Lỗi kiểm tra rooms: " + e.getMessage()));
    }

    private Map<String, Object> createRoomType(int maLoaiPhong, String tenLoaiPhong, double giaPhong) {
        Map<String, Object> roomType = new HashMap<>();
        roomType.put("MaLoaiPhong", maLoaiPhong);
        roomType.put("TenLoaiPhong", tenLoaiPhong);
        roomType.put("GiaPhong", giaPhong);
        return roomType;
    }

    private Map<String, Object> createRoom(int maPhong, String tenPhong, int maLoaiPhong, double giaPhong,
                                           int soLuongNguoiToiDa, String trangThai, String moTa,
                                           List<Integer> tienIch) {
        Map<String, Object> room = new HashMap<>();
        room.put("MaPhong", maPhong);
        room.put("TenPhong", tenPhong);
        room.put("MaLoaiPhong", maLoaiPhong);
        room.put("GiaPhong", giaPhong);
        room.put("SoLuongNguoiToiDa", soLuongNguoiToiDa);
        room.put("TrangThai", trangThai);
        room.put("MoTa", moTa);
        room.put("TienIch", tienIch);
        return room;
    }
}