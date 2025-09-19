package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
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

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
    private ListenerRegistration roomsListener;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();

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
        datePicker.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime()));

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filterRooms();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        spinnerRoomType.setOnItemSelectedListener(filterListener);
        spinnerStatus.setOnItemSelectedListener(filterListener);

        notificationIcon.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, NotificationsActivity.class)));

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
    }

    private void initializeRoomNames() {
        roomTypeNames.put(1, "Standard");
        roomTypeNames.put(2, "VIP");
        roomTypeNames.put(3, "Deluxe");
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate));
        } catch (ParseException e) {
            // keep today
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
                    datePicker.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.getTime()));
                    filterRooms();
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - (1000 * 60 * 60 * 24)); // Allow choosing today
        datePickerDialog.show();
    }

    private void showAvailabilityDialog(RoomModel room) {
        if (!isActive) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_availability, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        Calendar calendar = Calendar.getInstance();
        updateCalendarView(dialogView, room, calendar);

        dialogView.findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, -1);
            updateCalendarView(dialogView, room, calendar);
        });

        dialogView.findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, 1);
            updateCalendarView(dialogView, room, calendar);
        });

        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void updateCalendarView(View dialogView, RoomModel room, Calendar calendar) {
        TextView roomName = dialogView.findViewById(R.id.dialogRoomName);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", new Locale("vi", "VN"));
        roomName.setText("Lịch trống: " + room.getName() + " (" + monthFormat.format(calendar.getTime()) + ")");

        GridLayout gridLayout = dialogView.findViewById(R.id.gridLayout);

        executorService.execute(() -> {
            Map<String, String> bookedDates = new HashMap<>();
            db.collection("invoices")
                    .whereEqualTo("MaPhong", room.getMaPhong())
                    .whereIn("TrangThai", List.of("Đã xác nhận", "Đã nhận phòng"))
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        SimpleDateFormat dbDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        SimpleDateFormat mapKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            try {
                                String tgCheckin = doc.getString("TGCheckin");
                                String tgCheckout = doc.getString("TGCheckout");
                                String trangThai = doc.getString("TrangThai");

                                if (tgCheckin != null && tgCheckout != null) {
                                    Calendar checkinCal = Calendar.getInstance();
                                    checkinCal.setTime(dbDateTimeFormat.parse(tgCheckin));
                                    Calendar checkoutCal = Calendar.getInstance();
                                    checkoutCal.setTime(dbDateTimeFormat.parse(tgCheckout));

                                    while (checkinCal.before(checkoutCal)) {
                                        bookedDates.put(mapKeyFormat.format(checkinCal.getTime()), trangThai);
                                        checkinCal.add(Calendar.DAY_OF_MONTH, 1);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Lỗi phân tích ngày cho lịch: ", e);
                            }
                        }

                        runOnUiThread(() -> {
                            updateGridLayout(gridLayout, calendar, bookedDates);
                        });
                    }).addOnFailureListener(e -> Log.e(TAG, "Lỗi lấy lịch phòng: ", e));
        });
    }

    private void updateGridLayout(GridLayout gridLayout, Calendar calendar, Map<String, String> bookedDates) {
        Calendar today = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // SUNDAY is 1, SATURDAY is 7
        int offset = (firstDayOfWeek == Calendar.SUNDAY) ? 6 : firstDayOfWeek - 2;

        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        SimpleDateFormat mapKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int i = 0; i < gridLayout.getChildCount(); i++) {
            TextView dayView = (TextView) gridLayout.getChildAt(i);
            int day = i - offset + 1;

            if (i >= offset && day <= daysInMonth) {
                dayView.setText(String.valueOf(day));
                Calendar currentDay = (Calendar) calendar.clone();
                currentDay.set(Calendar.DAY_OF_MONTH, day);
                String dateStr = mapKeyFormat.format(currentDay.getTime());
                String status = bookedDates.get(dateStr);

                if (status != null) {
                    dayView.setBackgroundColor(status.equals("Đã nhận phòng") ? 0xFFFFD600 : 0xFFF4511E); // Yellow or Deep Orange
                    dayView.setTextColor(Color.WHITE);
                } else {
                    dayView.setBackgroundColor(0xFF388E3C); // Green
                    dayView.setTextColor(Color.WHITE);
                }
            } else {
                dayView.setText("");
                dayView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void setupRoomsListener() {
        if (roomsListener != null) roomsListener.remove();

        roomsListener = db.collection("rooms").orderBy("MaPhong", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Lỗi lắng nghe rooms: ", e);
                        return;
                    }
                    if (isActive && snapshots != null) {
                        List<RoomModel> tempOriginalList = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots) {
                            RoomModel room = parseRoom(doc);
                            if (room != null) {
                                tempOriginalList.add(room);
                            }
                        }
                        originalRoomList.clear();
                        originalRoomList.addAll(tempOriginalList);
                        filterRooms();
                    }
                });
    }

    private void filterRooms() {
        if (originalRoomList.isEmpty()) {
            roomList.clear();
            roomAdapter.notifyDataSetChanged();
            return;
        }

        String selectedRoomType = spinnerRoomType.getSelectedItem().toString();
        String selectedStatus = spinnerStatus.getSelectedItem().toString();
        List<RoomModel> filteredList = new ArrayList<>();

        db.collection("invoices")
                .whereIn("TrangThai", List.of("Đã xác nhận", "Đã nhận phòng"))
                .get()
                .addOnSuccessListener(invoiceSnapshots -> {
                    Map<Integer, String> roomStatusForDate = new HashMap<>();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    try {
                        Date dateStart = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate);
                        Calendar c = Calendar.getInstance();
                        c.setTime(dateStart);
                        c.add(Calendar.DAY_OF_MONTH, 1);
                        Date dateEnd = c.getTime();

                        for (DocumentSnapshot doc : invoiceSnapshots) {
                            Date checkIn = sdf.parse(doc.getString("TGCheckin"));
                            Date checkOut = sdf.parse(doc.getString("TGCheckout"));

                            if (!(dateEnd.before(checkIn) || dateStart.after(checkOut))) {
                                int roomId = doc.getLong("MaPhong").intValue();
                                String status = "Đã đặt";
                                if (doc.getString("TrangThai").equals("Đã nhận phòng")) {
                                    status = "Đang sử dụng";
                                }
                                roomStatusForDate.put(roomId, status);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi phân tích ngày khi lọc", e);
                    }

                    for (RoomModel room : originalRoomList) {
                        String statusOnDate = roomStatusForDate.getOrDefault(room.getMaPhong(), "Trống");
                        room.setStatus(statusOnDate);

                        boolean typeMatches = selectedRoomType.equals("Tất cả") || roomTypeNames.get(room.getMaLoaiPhong()).equals(selectedRoomType);
                        boolean statusMatches = selectedStatus.equals("Tất cả") || room.getStatus().equals(selectedStatus);

                        if (typeMatches && statusMatches) {
                            filteredList.add(room);
                        }
                    }

                    roomList.clear();
                    roomList.addAll(filteredList);
                    roomAdapter.notifyDataSetChanged();
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
        if (roomsListener != null) roomsListener.remove();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        setupRoomsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        if (roomsListener != null) roomsListener.remove();
        if (executorService != null && !executorService.isShutdown()) executorService.shutdown();
    }

    private RoomModel parseRoom(DocumentSnapshot doc) {
        try {
            Integer maPhong = doc.getLong("MaPhong").intValue();
            String tenPhong = doc.getString("TenPhong");
            Integer maLoaiPhong = doc.getLong("MaLoaiPhong").intValue();
            Double giaPhong = doc.getDouble("GiaPhong");
            Integer soLuongNguoiToiDa = doc.getLong("SoLuongNguoiToiDa").intValue();
            String trangThaiGoc = doc.getString("TrangThai");
            String moTa = doc.getString("MoTa");
            List<Integer> tienIch = (List<Integer>) doc.get("TienIch");
            return new RoomModel(maPhong, tenPhong, maLoaiPhong, giaPhong, soLuongNguoiToiDa, trangThaiGoc, moTa, tienIch);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi parse phòng với ID: " + doc.getId(), e);
            return null;
        }
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
            holder.txtRoomDetails.setText("Tối đa " + room.getSoLuongNguoiToiDa() + " người");

            // --- BẮT ĐẦU LOGIC SỬA LỖI ---

            // 1. Nút "Đặt phòng" và "Xem Lịch" luôn hiển thị
            holder.btnBookRoom.setVisibility(View.VISIBLE);
            holder.btnViewCalendar.setVisibility(View.VISIBLE);

            // 2. Kiểm tra điều kiện để hiển thị nút "Check-in Ngay"
            // Lấy ngày hôm nay dưới định dạng yyyy-MM-dd
            String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            // So sánh ngày được chọn với ngày hôm nay VÀ kiểm tra trạng thái phòng
            if (todayStr.equals(selectedDate) && "Trống".equals(room.getStatus())) {
                holder.btnCheckInNow.setVisibility(View.VISIBLE);
            } else {
                holder.btnCheckInNow.setVisibility(View.GONE);
            }

            // --- KẾT THÚC LOGIC SỬA LỖI ---

            holder.btnBookRoom.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, BookingActivity.class);
                intent.putExtra("ROOM_ID", room.getMaPhong());
                intent.putExtra("roomName", room.getName());
                intent.putExtra("price", room.getGiaPhong());
                intent.putExtra("selectedDate", selectedDate);
                startActivity(intent);
            });

            holder.btnCheckInNow.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, WalkInCheckInActivity.class);
                intent.putExtra("ROOM_ID", room.getMaPhong());
                intent.putExtra("roomName", room.getName());
                intent.putExtra("price", room.getGiaPhong());
                startActivity(intent);
            });

            holder.btnViewCalendar.setOnClickListener(v -> showAvailabilityDialog(room));
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class RoomViewHolder extends RecyclerView.ViewHolder {
            TextView roomName, roomType, roomPrice, txtRoomDetails;
            MaterialButton btnBookRoom, btnCheckInNow, btnViewCalendar;

            RoomViewHolder(@NonNull View itemView) {
                super(itemView);
                roomName = itemView.findViewById(R.id.txtRoomName);
                roomType = itemView.findViewById(R.id.txtRoomType);
                roomPrice = itemView.findViewById(R.id.txtRoomPrice);
                txtRoomDetails = itemView.findViewById(R.id.txtRoomDetails);
                btnBookRoom = itemView.findViewById(R.id.btnBookRoom);
                btnCheckInNow = itemView.findViewById(R.id.btnCheckInNow);
                btnViewCalendar = itemView.findViewById(R.id.btnViewCalendar);
            }
        }
    }
}