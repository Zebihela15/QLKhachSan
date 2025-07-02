package com.sinhvien.appqlkhachsan;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private RecyclerView roomRecyclerView;
    private EditText searchBar;
    private Spinner spinnerSort;
    private RoomAdapter roomAdapter;
    private List<RoomModel> roomList;
    private List<RoomModel> originalRoomList;
    private Map<Integer, Double> roomTypePrices = new HashMap<>();
    private Map<Integer, Integer> roomTypeImages = new HashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isActive = true;
    private boolean isRoomsLoaded = false;
    private boolean isLoadingRooms = false; // Biến mới để khóa tải phòng

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            db = FirebaseFirestore.getInstance();
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khởi tạo Firebase: " + e.getMessage());
            Toast.makeText(this, "Lỗi khởi tạo Firebase, vui lòng kiểm tra kết nối!", Toast.LENGTH_LONG).show();
            return;
        }

        roomRecyclerView = findViewById(R.id.roomRecyclerView);
        searchBar = findViewById(R.id.searchBar);
        spinnerSort = findViewById(R.id.spinnerSort);

        String[] sortOptions = {"Sắp xếp: Mặc định", "Sắp xếp: Giá thấp đến cao", "Sắp xếp: Giá cao đến thấp"};
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sortOptions);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sortAdapter);

        roomList = new ArrayList<>();
        originalRoomList = new ArrayList<>();
        roomAdapter = new RoomAdapter(this, roomTypePrices, roomTypeImages, room -> {
            Intent intent = new Intent(MainActivity.this, BookingActivity.class);
            intent.putExtra("ROOM_ID", room.getMaPhong());
            intent.putExtra("roomName", room.getName());
            intent.putExtra("price", getRoomPrice(room.getMaLoaiPhong()));
            intent.putExtra("imageResource", getRoomImageResource(room.getMaLoaiPhong()));
            startActivity(intent);
        }, room -> showAvailabilityDialog(room));

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        roomRecyclerView.setLayoutManager(layoutManager);
        roomRecyclerView.setAdapter(roomAdapter);

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            filterAndSortRooms();
            return true;
        });

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterAndSortRooms();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        BottomNavigationView bottomNavigationView = findViewById(R.id.navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, UserInfoActivity.class));
                return true;
            } else if (itemId == R.id.nav_booking) {
                startActivity(new Intent(MainActivity.this, BookingActivity.class));
                return true;
            }
            return false;

        });

        initializeRoomImages();
        loadRoomTypesFromFirestore();
        loadRoomsFromFirestore();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        if (!isRoomsLoaded && !isLoadingRooms) {
            loadRoomsFromFirestore();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        executorService.shutdown();
    }

    private void initializeRoomImages() {
        roomTypeImages.put(1, R.drawable.standard_room);
        roomTypeImages.put(2, R.drawable.vip_rooms);   // Sửa cho Phòng VIP
        roomTypeImages.put(3, R.drawable.deluxe_room); // Sửa cho Phòng Deluxe
    }

    private int getRoomImageResource(int maLoaiPhong) {
        return roomTypeImages.getOrDefault(maLoaiPhong, R.drawable.ic_launcher_background);
    }

    private void loadRoomTypesFromFirestore() {
        if (!isActive) return;
        executorService.execute(() -> {
            db.collection("room_types").get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && isActive) {
                    QuerySnapshot result = task.getResult();
                    if (result != null) {
                        for (DocumentSnapshot doc : result.getDocuments()) {
                            int maLoaiPhong = doc.getLong("MaLoaiPhong").intValue();
                            Double giaPhong = doc.getDouble("GiaPhong");
                            if (giaPhong != null) {
                                runOnUiThread(() -> roomTypePrices.put(maLoaiPhong, giaPhong));
                            }
                        }
                        // Cập nhật RoomAdapter sau khi giá phòng được tải
                        runOnUiThread(() -> roomAdapter.notifyDataSetChanged());
                    }
                }
            });
        });
    }

    private double getRoomPrice(int maLoaiPhong) {
        return roomTypePrices.getOrDefault(maLoaiPhong, 500.0);
    }

    private void showAvailabilityDialog(RoomModel room) {
        if (!isActive) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_availability, null);
        builder.setView(dialogView);

        TextView roomName = dialogView.findViewById(R.id.dialogRoomName);
        Button btnPrevMonth = dialogView.findViewById(R.id.btnPrevMonth);
        Button btnNextMonth = dialogView.findViewById(R.id.btnNextMonth);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        Calendar calendar = Calendar.getInstance();
        updateCalendarView(dialogView, room, calendar);

        btnPrevMonth.setOnClickListener(v -> {
            if (isActive) {
                calendar.add(Calendar.MONTH, -1);
                updateCalendarView(dialogView, room, calendar);
            }
        });

        btnNextMonth.setOnClickListener(v -> {
            if (isActive) {
                calendar.add(Calendar.MONTH, 1);
                updateCalendarView(dialogView, room, calendar);
            }
        });

        btnClose.setOnClickListener(v -> {
            if (isActive) {
                AlertDialog dialog = builder.create();
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateCalendarView(View dialogView, RoomModel room, Calendar calendar) {
        if (!isActive) return;
        TextView roomName = dialogView.findViewById(R.id.dialogRoomName);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", new Locale("vi", "VN"));
        runOnUiThread(() -> roomName.setText("🏨 Phòng " + room.getName() + " - Lịch trống (" + monthFormat.format(calendar.getTime()) + ")"));

        GridLayout gridLayout = dialogView.findViewById(R.id.gridLayout);
        if (gridLayout != null) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

            executorService.execute(() -> {
                if (!isActive) return;
                Map<String, Boolean> bookedDates = new HashMap<>();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                db.collection("bookings")
                        .whereEqualTo("MaPhong", room.getMaPhong())
                        .limit(50)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!isActive) return;
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                String tgCheckin = doc.getString("TGCheckin");
                                String tgCheckout = doc.getString("TGCheckout");
                                if (tgCheckin != null && tgCheckout != null) {
                                    try {
                                        Calendar checkinCal = Calendar.getInstance();
                                        Calendar checkoutCal = Calendar.getInstance();
                                        checkinCal.setTime(dateFormat.parse(tgCheckin));
                                        checkoutCal.setTime(dateFormat.parse(tgCheckout));
                                        while (!checkinCal.after(checkoutCal)) {
                                            bookedDates.put(dateFormat.format(checkinCal.getTime()), true);
                                            checkinCal.add(Calendar.DAY_OF_MONTH, 1);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing dates", e);
                                    }
                                }
                            }
                            runOnUiThread(() -> updateGridLayout(gridLayout, firstDayOfMonth, daysInMonth, calendar, bookedDates));
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Error fetching bookings", e));
            });
        }
    }

    private void updateGridLayout(GridLayout gridLayout, int firstDayOfMonth, int daysInMonth, Calendar calendar, Map<String, Boolean> bookedDates) {
        if (!isActive) return;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int i = 1; i <= 35; i++) {
            int dayIndex = i - 1;
            int viewIndex = 7 + dayIndex;
            if (viewIndex < gridLayout.getChildCount()) {
                TextView dayView = (TextView) gridLayout.getChildAt(viewIndex);
                if (dayIndex >= firstDayOfMonth && dayIndex - firstDayOfMonth + 1 <= daysInMonth) {
                    int day = dayIndex - firstDayOfMonth + 1;
                    dayView.setText(String.valueOf(day));
                    Calendar currentDay = (Calendar) calendar.clone();
                    currentDay.set(Calendar.DAY_OF_MONTH, day);
                    String dateStr = dateFormat.format(currentDay.getTime());
                    boolean isBooked = bookedDates.containsKey(dateStr);
                    dayView.setBackgroundColor(isBooked ? 0xFFF44336 : 0xFF4CAF50);
                } else {
                    dayView.setText("");
                    dayView.setBackgroundColor(0xFFE0E0E0);
                }
            }
        }
    }

    private void filterAndSortRooms() {
        if (!isActive) return;
        String searchQuery = searchBar.getText().toString().trim().toLowerCase();
        List<RoomModel> filteredList = new ArrayList<>(originalRoomList);

        if (!TextUtils.isEmpty(searchQuery)) {
            filteredList.removeIf(room -> !room.getName().toLowerCase().contains(searchQuery));
        }

        String sortOption = spinnerSort.getSelectedItem().toString();
        if (sortOption.equals("Sắp xếp: Giá thấp đến cao")) {
            filteredList.sort(Comparator.comparingDouble(room -> getRoomPrice(room.getMaLoaiPhong())));
        } else if (sortOption.equals("Sắp xếp: Giá cao đến thấp")) {
            filteredList.sort((r1, r2) -> Double.compare(getRoomPrice(r2.getMaLoaiPhong()), getRoomPrice(r1.getMaLoaiPhong())));
        }

        roomAdapter.submitList(new ArrayList<>(filteredList));
    }

    private void loadRoomsFromFirestore() {
        if (!isActive || isRoomsLoaded || isLoadingRooms) {
            Log.d(TAG, "Bỏ qua loadRoomsFromFirestore: isActive=" + isActive + ", isRoomsLoaded=" + isRoomsLoaded + ", isLoadingRooms=" + isLoadingRooms);
            return;
        }
        isLoadingRooms = true; // Khóa tải phòng
        roomList.clear();
        originalRoomList.clear();
        Set<Integer> addedRoomIds = new HashSet<>();
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        Log.d(TAG, "Current User ID: " + currentUserId);

        if (currentUserId == null) {
            Toast.makeText(this, "Không tìm thấy người dùng đăng nhập!", Toast.LENGTH_LONG).show();
            isLoadingRooms = false;
            return;
        }

        db.collection("rooms").get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) {
                        isLoadingRooms = false;
                        return;
                    }
                    Log.d(TAG, "Tổng số phòng từ Firestore: " + querySnapshot.size());
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "Không có phòng nào trong Firestore!", Toast.LENGTH_LONG).show();
                        isRoomsLoaded = true;
                        isLoadingRooms = false;
                        runOnUiThread(() -> roomAdapter.submitList(new ArrayList<>(roomList)));
                        return;
                    }

                    List<RoomModel> tempRoomList = new ArrayList<>();
                    for (DocumentSnapshot roomDoc : querySnapshot) {
                        try {
                            Integer maPhong = roomDoc.getLong("MaPhong") != null ? roomDoc.getLong("MaPhong").intValue() : 0;
                            if (tempRoomList.stream().anyMatch(room -> room.getMaPhong() == maPhong)) {
                                Log.d(TAG, "Bỏ qua phòng trùng lặp trong Firestore: MaPhong " + maPhong + ", TenPhong: " + roomDoc.getString("TenPhong"));
                                continue;
                            }
                            String tenPhong = roomDoc.getString("TenPhong");
                            Double dienTich = roomDoc.getDouble("DienTich") != null ? roomDoc.getDouble("DienTich") : 0.0;
                            String trangThai = roomDoc.getString("TrangThai");
                            String donViTinh = roomDoc.getString("DonViTinh") != null ? roomDoc.getString("DonViTinh") : "m²";
                            Integer maLoaiPhong = roomDoc.getLong("MaLoaiPhong") != null ? roomDoc.getLong("MaLoaiPhong").intValue() : 1;
                            String moTa = roomDoc.getString("MoTa") != null ? roomDoc.getString("MoTa") : "Không có mô tả";
                            String diaChi = roomDoc.getString("DiaChi") != null ? roomDoc.getString("DiaChi") : "Không xác định";
                            String ghiChu = roomDoc.getString("GhiChu") != null ? roomDoc.getString("GhiChu") : "";
                            Integer hinhAnh = roomDoc.getLong("HinhAnh") != null ? roomDoc.getLong("HinhAnh").intValue() : getRoomImageResource(maLoaiPhong);
                            List<Integer> tienIch = roomDoc.get("TienIch") != null ? (List<Integer>) roomDoc.get("TienIch") : new ArrayList<>();

                            RoomModel room = new RoomModel(maPhong, tenPhong, dienTich, trangThai != null ? trangThai : "Trống",
                                    donViTinh, maLoaiPhong, moTa, diaChi, ghiChu, hinhAnh, tienIch);
                            tempRoomList.add(room);
                            Log.d(TAG, "Đã thêm phòng tạm thời: " + tenPhong + ", MaPhong: " + maPhong + ", MaLoaiPhong: " + maLoaiPhong + ", Trạng thái: " + (trangThai != null ? trangThai : "Trống"));
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi parse phòng " + roomDoc.getId() + ": " + e.getMessage());
                        }
                    }

                    int totalRooms = tempRoomList.size();
                    final int[] processedRooms = {0};

                    if (totalRooms == 0) {
                        isRoomsLoaded = true;
                        isLoadingRooms = false;
                        runOnUiThread(() -> roomAdapter.submitList(new ArrayList<>(roomList)));
                        Log.d(TAG, "Không có phòng để xử lý, submitList với roomList rỗng");
                        return;
                    }

                    for (RoomModel room : tempRoomList) {
                        db.collection("bookings")
                                .whereEqualTo("MaPhong", room.getMaPhong())
                                .whereEqualTo("MaKH", currentUserId)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(bookingSnapshot -> {
                                    if (!isActive) {
                                        isLoadingRooms = false;
                                        return;
                                    }
                                    String displayTrangThai = bookingSnapshot.isEmpty() ? "Trống" : room.getStatus();
                                    room.setStatus(displayTrangThai);
                                    synchronized (roomList) {
                                        if (!addedRoomIds.contains(room.getMaPhong())) {
                                            roomList.add(room);
                                            originalRoomList.add(room);
                                            addedRoomIds.add(room.getMaPhong());
                                            Log.d(TAG, "Đã thêm phòng vào roomList: " + room.getName() + ", MaPhong: " + room.getMaPhong() + ", MaLoaiPhong: " + room.getMaLoaiPhong() + ", Trạng thái: " + displayTrangThai);
                                        } else {
                                            Log.d(TAG, "Bỏ qua phòng trùng lặp trong booking check: " + room.getName() + ", MaPhong: " + room.getMaPhong());
                                        }
                                    }

                                    processedRooms[0]++;
                                    Log.d(TAG, "Đã xử lý phòng " + room.getName() + ", processedRooms: " + processedRooms[0] + "/" + totalRooms);
                                    if (processedRooms[0] == totalRooms) {
                                        runOnUiThread(() -> {
                                            Log.d(TAG, "Đã gọi submitList, kích thước roomList: " + roomList.size() + ", Phòng: " + roomListToString());
                                            roomAdapter.submitList(new ArrayList<>(roomList));
                                            isRoomsLoaded = true;
                                            isLoadingRooms = false;
                                        });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (!isActive) {
                                        isLoadingRooms = false;
                                        return;
                                    }
                                    Log.e(TAG, "Lỗi kiểm tra booking cho phòng " + room.getMaPhong() + ": " + e.getMessage());
                                    synchronized (roomList) {
                                        if (!addedRoomIds.contains(room.getMaPhong())) {
                                            roomList.add(room);
                                            originalRoomList.add(room);
                                            addedRoomIds.add(room.getMaPhong());
                                            Log.d(TAG, "Đã thêm phòng mặc định vào roomList: " + room.getName() + ", MaPhong: " + room.getMaPhong() + ", MaLoaiPhong: " + room.getMaLoaiPhong() + ", Trạng thái: " + room.getStatus());
                                        }
                                    }

                                    processedRooms[0]++;
                                    Log.d(TAG, "Đã xử lý phòng (mặc định) " + room.getName() + ", processedRooms: " + processedRooms[0] + "/" + totalRooms);
                                    if (processedRooms[0] == totalRooms) {
                                        runOnUiThread(() -> {
                                            Log.d(TAG, "Đã gọi submitList (mặc định), kích thước roomList: " + roomList.size() + ", Phòng: " + roomListToString());
                                            roomAdapter.submitList(new ArrayList<>(roomList));
                                            isRoomsLoaded = true;
                                            isLoadingRooms = false;
                                        });
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải danh sách phòng: " + e.getMessage());
                        Toast.makeText(this, "Lỗi tải danh sách phòng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        isRoomsLoaded = true;
                        isLoadingRooms = false;
                    }
                });
    }

    private String roomListToString() {
        StringBuilder sb = new StringBuilder();
        for (RoomModel room : roomList) {
            sb.append(room.getName()).append(" (MaPhong: ").append(room.getMaPhong()).append(", MaLoaiPhong: ").append(room.getMaLoaiPhong()).append("), ");
        }
        return sb.toString();
    }
}