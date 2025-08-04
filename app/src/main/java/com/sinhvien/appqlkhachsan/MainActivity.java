package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.sinhvien.appqlkhachsan.admin.StatisticsActivity;
import com.sinhvien.appqlkhachsan.admin.RoomManagementActivity;
import com.sinhvien.appqlkhachsan.migration.InitializeFirestoreData;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FirebaseFirestore db;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private EditText datePicker;
    private Spinner spinnerRoomType, spinnerStatus;
    private ImageView notificationIcon;
    private List<RoomModel> roomList;
    private List<RoomModel> originalRoomList;
    private Map<Integer, String> roomTypeNames;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isActive = true;
    private boolean isRoomsLoaded = false;
    private boolean isLoadingRooms = false;
    private ListenerRegistration invoicesListener;
    private ListenerRegistration roomsListener;
    private String selectedDate;
    private Calendar currentCalendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            db = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khởi tạo Firebase: " + e.getMessage());
            Toast.makeText(this, "Lỗi khởi tạo Firebase, vui lòng kiểm tra kết nối!", Toast.LENGTH_LONG).show();
            return;
        }

        // Initialize data (run once, comment out after first run)
        InitializeFirestoreData init = new InitializeFirestoreData();
        init.initializeData();

        roomRecyclerView = findViewById(R.id.roomRecyclerView);
        datePicker = findViewById(R.id.datePicker);
        spinnerRoomType = findViewById(R.id.spinnerRoomType);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        notificationIcon = findViewById(R.id.notificationIcon);

        roomRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        roomList = new ArrayList<>();
        originalRoomList = new ArrayList<>();
        roomTypeNames = new HashMap<>();
        roomAdapter = new RoomAdapter(roomList);
        roomRecyclerView.setAdapter(roomAdapter);

        String[] roomTypeOptions = {"Tất cả", "Standard", "VIP", "Deluxe"};
        ArrayAdapter<String> roomTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomTypeOptions);
        roomTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRoomType.setAdapter(roomTypeAdapter);

        String[] statusOptions = {"Tất cả", "Trống", "Đã đặt", "Đang sử dụng"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        datePicker.setOnClickListener(v -> showDatePicker());
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().getTime());
        datePicker.setText(selectedDate);
        currentCalendar = Calendar.getInstance();

        spinnerRoomType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Loại phòng được chọn: " + spinnerRoomType.getSelectedItem().toString());
                filterRooms();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Trạng thái được chọn: " + spinnerStatus.getSelectedItem().toString());
                filterRooms();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        notificationIcon.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        BottomNavigationView bottomNavigationView = findViewById(R.id.navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_statistics) {
                startActivity(new Intent(MainActivity.this, ReceptionistRoomDashboardActivity.class));
                return true;
            } else if (itemId == R.id.nav_management) {
                startActivity(new Intent(MainActivity.this, BookingManagementActivity.class));
                return true;
            }
            return false;
        });

        initializeRoomNames();
        setupRoomsListener();
        setupInvoicesListener();
    }

    private void initializeRoomNames() {
        roomTypeNames.put(1, "Standard");
        roomTypeNames.put(2, "VIP");
        roomTypeNames.put(3, "Deluxe");
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    selectedDate = String.format(Locale.getDefault(), "%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                    datePicker.setText(selectedDate);
                    currentCalendar.set(selectedYear, selectedMonth, selectedDay);
                    loadRoomsFromFirestore();
                }, year, month, day);
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void showAvailabilityDialog(RoomModel room) {
        if (!isActive) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_availability, null);
        builder.setView(dialogView);

        TextView roomName = dialogView.findViewById(R.id.dialogRoomName);
        MaterialButton btnPrevMonth = dialogView.findViewById(R.id.btnPrevMonth);
        MaterialButton btnNextMonth = dialogView.findViewById(R.id.btnNextMonth);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnClose);
        AlertDialog dialog = builder.create();

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
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateCalendarView(View dialogView, RoomModel room, Calendar calendar) {
        if (!isActive) return;
        TextView roomName = dialogView.findViewById(R.id.dialogRoomName);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", new Locale("vi", "VN"));
        runOnUiThread(() -> roomName.setText("🏨 " + room.getName() + " - Lịch trống (" + monthFormat.format(calendar.getTime()) + ")"));

        GridLayout gridLayout = dialogView.findViewById(R.id.gridLayout);
        if (gridLayout != null) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK) - 1;
            int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

            executorService.execute(() -> {
                if (!isActive) return;
                Map<String, String> bookedDates = new HashMap<>();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                db.collection("invoices")
                        .whereEqualTo("MaPhong", room.getMaPhong())
                        .whereIn("TrangThai", List.of("Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm"))
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!isActive) return;
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                String tgCheckin = doc.getString("TGCheckin");
                                String tgCheckout = doc.getString("TGCheckout");
                                String trangThai = doc.getString("TrangThai");
                                if (tgCheckin != null && tgCheckout != null) {
                                    try {
                                        Calendar checkinCal = Calendar.getInstance();
                                        Calendar checkoutCal = Calendar.getInstance();
                                        checkinCal.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(tgCheckin));
                                        checkoutCal.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(tgCheckout));
                                        while (!checkinCal.after(checkoutCal)) {
                                            bookedDates.put(dateFormat.format(checkinCal.getTime()), trangThai);
                                            checkinCal.add(Calendar.DAY_OF_MONTH, 1);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Lỗi phân tích ngày: " + e.getMessage());
                                    }
                                }
                            }
                            runOnUiThread(() -> updateGridLayout(gridLayout, firstDayOfMonth, daysInMonth, calendar, bookedDates));
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Lỗi lấy invoices: " + e.getMessage()));
            });
        }
    }

    private void updateGridLayout(GridLayout gridLayout, int firstDayOfMonth, int daysInMonth, Calendar calendar, Map<String, String> bookedDates) {
        if (!isActive) return;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        int[] dayIds = {
                R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6, R.id.day7,
                R.id.day8, R.id.day9, R.id.day10, R.id.day11, R.id.day12, R.id.day13, R.id.day14,
                R.id.day15, R.id.day16, R.id.day17, R.id.day18, R.id.day19, R.id.day20, R.id.day21,
                R.id.day22, R.id.day23, R.id.day24, R.id.day25, R.id.day26, R.id.day27, R.id.day28,
                R.id.day29, R.id.day30, R.id.day31, R.id.day32, R.id.day33, R.id.day34, R.id.day35
        };

        for (int i = 0; i < 35; i++) {
            int dayIndex = i;
            TextView dayView = gridLayout.findViewById(dayIds[i]);
            if (dayView != null) {
                if (dayIndex >= firstDayOfMonth && dayIndex - firstDayOfMonth + 1 <= daysInMonth) {
                    int day = dayIndex - firstDayOfMonth + 1;
                    dayView.setText(String.valueOf(day));
                    Calendar currentDay = (Calendar) calendar.clone();
                    currentDay.set(Calendar.DAY_OF_MONTH, day);
                    String dateStr = dateFormat.format(currentDay.getTime());
                    String status = bookedDates.get(dateStr);
                    if (status != null) {
                        dayView.setBackgroundColor(status.equals("Đã nhận phòng") ? 0xFFFFEB3B : 0xFFF44336);
                    } else {
                        dayView.setBackgroundColor(0xFF4CAF50);
                    }
                    dayView.setTextColor(0xFFFFFFFF);
                } else {
                    dayView.setText("");
                    dayView.setBackgroundColor(0xFFE0E0E0);
                    dayView.setTextColor(0xFF000000);
                }
            }
        }
    }

    private void setupInvoicesListener() {
        if (invoicesListener != null) {
            invoicesListener.remove();
        }
        invoicesListener = db.collection("invoices")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Lỗi lắng nghe invoices: " + e.getMessage());
                        Toast.makeText(this, "Lỗi lắng nghe invoices: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (isActive && snapshots != null) {
                        Log.d(TAG, "Phát hiện thay đổi trong invoices, làm mới danh sách phòng");
                        loadRoomsFromFirestore();
                    }
                });
    }

    private void setupRoomsListener() {
        if (roomsListener != null) {
            roomsListener.remove();
        }
        roomsListener = db.collection("rooms")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Lỗi lắng nghe rooms: " + e.getMessage());
                        Toast.makeText(this, "Lỗi lắng nghe rooms: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (isActive && snapshots != null) {
                        Log.d(TAG, "Phát hiện thay đổi trong rooms, làm mới danh sách phòng");
                        loadRoomsFromFirestore();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
        if (invoicesListener != null) {
            invoicesListener.remove();
        }
        if (roomsListener != null) {
            roomsListener.remove();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        if (!isRoomsLoaded && !isLoadingRooms) {
            loadRoomsFromFirestore();
        }
        setupInvoicesListener();
        setupRoomsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        if (invoicesListener != null) {
            invoicesListener.remove();
        }
        if (roomsListener != null) {
            roomsListener.remove();
        }
        executorService.shutdown();
    }

    private void loadRoomsFromFirestore() {
        if (!isActive || isLoadingRooms) {
            Log.d(TAG, "Bỏ qua loadRoomsFromFirestore: isActive=" + isActive + ", isLoadingRooms=" + isLoadingRooms);
            return;
        }
        isLoadingRooms = true;
        roomList.clear();
        originalRoomList.clear();

        Log.d(TAG, "Bắt đầu tải danh sách phòng từ Firestore");
        db.collection("rooms").orderBy("MaPhong", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) {
                        isLoadingRooms = false;
                        return;
                    }
                    Log.d(TAG, "Tổng số tài liệu từ Firestore: " + querySnapshot.size());
                    for (DocumentSnapshot doc : querySnapshot) {
                        Log.d(TAG, "Tài liệu: ID=" + doc.getId() + ", MaPhong=" + doc.getLong("MaPhong"));
                    }
                    if (querySnapshot.isEmpty()) {
                        Log.w(TAG, "Không có phòng nào trong Firestore!");
                        Toast.makeText(this, "Không có phòng nào trong Firestore!", Toast.LENGTH_LONG).show();
                        isRoomsLoaded = true;
                        isLoadingRooms = false;
                        runOnUiThread(() -> roomAdapter.notifyDataSetChanged());
                        return;
                    }

                    Map<Integer, RoomModel> roomMap = new HashMap<>();
                    for (DocumentSnapshot roomDoc : querySnapshot) {
                        RoomModel room = parseRoom(roomDoc);
                        if (room != null) {
                            Log.d(TAG, "Thêm phòng: MaPhong=" + room.getMaPhong() + ", TenPhong=" + room.getName());
                            roomMap.put(room.getMaPhong(), room);
                        } else {

                        }
                    }

                    List<RoomModel> tempRoomList = new ArrayList<>(roomMap.values());
                    int totalRooms = tempRoomList.size();
                    Log.d(TAG, "Số phòng hợp lệ sau khi parse: " + totalRooms);
                    if (totalRooms == 0) {
                        Toast.makeText(this, "Không có phòng hợp lệ nào để hiển thị!", Toast.LENGTH_LONG).show();
                        isRoomsLoaded = true;
                        isLoadingRooms = false;
                        runOnUiThread(() -> roomAdapter.notifyDataSetChanged());
                        return;
                    }

                    final int[] processedRooms = {0};
                    for (RoomModel room : tempRoomList) {
                        Log.d(TAG, "Kiểm tra trạng thái phòng: MaPhong=" + room.getMaPhong());
                        db.collection("invoices")
                                .whereEqualTo("MaPhong", room.getMaPhong())
                                .whereIn("TrangThai", List.of("Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm"))
                                .whereGreaterThanOrEqualTo("TGCheckin", selectedDate + " 00:00:00")
                                .whereLessThanOrEqualTo("TGCheckout", selectedDate + " 23:59:59")
                                .limit(1)
                                .get()
                                .addOnSuccessListener(bookingSnapshot -> {
                                    if (!isActive) {
                                        isLoadingRooms = false;
                                        return;
                                    }
                                    String displayTrangThai = bookingSnapshot.isEmpty() ? "Trống" :
                                            bookingSnapshot.getDocuments().get(0).getString("TrangThai").equals("Đã nhận phòng") ? "Đang sử dụng" : "Đã đặt";
                                    room.setStatus(displayTrangThai);
                                    Log.d(TAG, "Phòng: " + room.getMaPhong() + ", Trạng thái: " + displayTrangThai);

                                    synchronized (roomList) {
                                        roomList.add(room);
                                        originalRoomList.add(room);
                                    }

                                    processedRooms[0]++;
                                    Log.d(TAG, "Đã xử lý: " + processedRooms[0] + "/" + totalRooms + " phòng");
                                    if (processedRooms[0] == totalRooms) {
                                        runOnUiThread(() -> {
                                            roomAdapter.notifyDataSetChanged();
                                            Toast.makeText(this, "Đã tải " + roomList.size() + " phòng", Toast.LENGTH_SHORT).show();
                                            Log.d(TAG, "Danh sách phòng đã cập nhật: " + roomList.size() + " phòng");
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
                                    Log.e(TAG, "Lỗi kiểm tra trạng thái phòng " + room.getMaPhong() + ": " + e.getMessage());
                                    synchronized (roomList) {
                                        roomList.add(room);
                                        originalRoomList.add(room);
                                    }

                                    processedRooms[0]++;
                                    Log.d(TAG, "Đã xử lý (lỗi): " + processedRooms[0] + "/" + totalRooms + " phòng");
                                    if (processedRooms[0] == totalRooms) {
                                        runOnUiThread(() -> {
                                            roomAdapter.notifyDataSetChanged();
                                            Toast.makeText(this, "Đã tải " + roomList.size() + " phòng", Toast.LENGTH_SHORT).show();
                                            Log.d(TAG, "Danh sách phòng đã cập nhật (on failure): " + roomList.size() + " phòng");
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
                        runOnUiThread(() -> roomAdapter.notifyDataSetChanged());
                    }
                });
    }

    private void filterRooms() {
        String roomType = spinnerRoomType.getSelectedItem().toString();
        String status = spinnerStatus.getSelectedItem().toString();
        Log.d(TAG, "Lọc phòng - Loại phòng: " + roomType + ", Trạng thái: " + status);
        List<RoomModel> filteredList = new ArrayList<>(originalRoomList);

        if (!roomType.equals("Tất cả")) {
            int maLoaiPhong = roomType.equals("Standard") ? 1 : roomType.equals("VIP") ? 2 : 3;
            filteredList.removeIf(room -> room.getMaLoaiPhong() != maLoaiPhong);
        }

        if (!status.equals("Tất cả")) {
            filteredList.removeIf(room -> !room.getStatus().equals(status));
        }

        roomList.clear();
        roomList.addAll(filteredList);
        Log.d(TAG, "Số lượng phòng sau khi lọc: " + roomList.size());
        roomAdapter.notifyDataSetChanged();
    }

    private RoomModel parseRoom(DocumentSnapshot doc) {
        Integer maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
        if (maPhong == 0) {
            Log.d(TAG, "Bỏ qua phòng không hợp lệ hoặc thiếu MaPhong: " + doc.getId());
            return null;
        }
        Integer maLoaiPhong = doc.getLong("MaLoaiPhong") != null ? doc.getLong("MaLoaiPhong").intValue() : 1;
        String tenPhong = doc.getString("TenPhong") != null ? doc.getString("TenPhong") : ("Phòng " + maPhong);
        Double giaPhong = doc.getDouble("GiaPhong") != null ? doc.getDouble("GiaPhong") : getDefaultPrice(maLoaiPhong);
        Integer soLuongNguoiToiDa = doc.getLong("SoLuongNguoiToiDa") != null ? doc.getLong("SoLuongNguoiToiDa").intValue() : 2;
        String trangThai = doc.getString("TrangThai") != null ? doc.getString("TrangThai") : "Trống";
        String moTa = doc.getString("MoTa") != null ? doc.getString("MoTa") : "Không có mô tả";
        List<Integer> tienIch = doc.get("TienIch") != null ? (List<Integer>) doc.get("TienIch") : new ArrayList<>();
        return new RoomModel(maPhong, tenPhong, maLoaiPhong, giaPhong, soLuongNguoiToiDa, trangThai, moTa, tienIch);
    }

    private double getDefaultPrice(int maLoaiPhong) {
        switch (maLoaiPhong) {
            case 1: return 500000.0; // Standard
            case 2: return 800000.0; // VIP
            case 3: return 1200000.0; // Deluxe
            default: return 500000.0;
        }
    }

    private String formatTienIch(List<Integer> tienIch) {
        if (tienIch == null || tienIch.isEmpty()) {
            return "Không có";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tienIch.size(); i++) {
            result.append(tienIch.get(i));
            if (i < tienIch.size() - 1) {
                result.append(",");
            }
        }
        return result.toString();
    }

    private class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {
        private final List<RoomModel> rooms;

        RoomAdapter(List<RoomModel> rooms) {
            this.rooms = rooms;
        }

        @NonNull
        @Override
        public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
            RoomModel room = rooms.get(position);
            holder.roomName.setText(room.getName());
            holder.roomType.setText("Loại: " + roomTypeNames.getOrDefault(room.getMaLoaiPhong(), "Không xác định"));
            holder.roomPrice.setText(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(room.getGiaPhong()) + "/đêm");
            holder.txtRoomDetails.setText(String.format(Locale.getDefault(), "Tối đa %d người, %s, Tiện ích: %s",
                    room.getSoLuongNguoiToiDa(), room.getMoTa(), formatTienIch(room.getTienIch())));

            holder.btnBookRoom.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, BookingActivity.class);
                intent.putExtra("ROOM_ID", room.getMaPhong());
                intent.putExtra("roomName", room.getName());
                intent.putExtra("price", room.getGiaPhong());
                intent.putExtra("selectedDate", selectedDate);
                startActivity(intent);
            });

            holder.btnViewCalendar.setOnClickListener(v -> showAvailabilityDialog(room));
        }

        @Override
        public int getItemCount() {
            Log.d(TAG, "Số lượng phòng trong adapter: " + rooms.size());
            return rooms.size();
        }

        class RoomViewHolder extends RecyclerView.ViewHolder {
            TextView roomName, roomType, roomPrice, txtRoomDetails;
            MaterialButton btnBookRoom, btnViewCalendar;

            RoomViewHolder(@NonNull View itemView) {
                super(itemView);
                roomName = itemView.findViewById(R.id.txtRoomName);
                roomType = itemView.findViewById(R.id.txtRoomType);
                roomPrice = itemView.findViewById(R.id.txtRoomPrice);
                txtRoomDetails = itemView.findViewById(R.id.txtRoomDetails);
                btnBookRoom = itemView.findViewById(R.id.btnBookRoom);
                btnViewCalendar = itemView.findViewById(R.id.btnViewCalendar);
            }
        }
    }
}