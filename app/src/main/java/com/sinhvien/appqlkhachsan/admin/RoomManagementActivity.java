package com.sinhvien.appqlkhachsan.admin;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.sinhvien.appqlkhachsan.R;
import com.sinhvien.appqlkhachsan.RoomModel;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoomManagementActivity extends AppCompatActivity {

    private static final String TAG = "RoomManagementActivity";
    private FirebaseFirestore db;
    private RecyclerView roomRecyclerView;
    private AdminRoomAdapter roomAdapter;
    private List<RoomModel> roomList;
    private ListenerRegistration roomsListener;
    private Button btnAddRoom;
    private Map<Integer, String> roomTypeNames;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isActive = true;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_management);
        Snackbar.make(findViewById(android.R.id.content), "Activity đã khởi động", Snackbar.LENGTH_LONG).show();

        // Kiểm tra quyền thông báo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        // Kiểm tra đăng nhập và vai trò admin
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Người dùng chưa đăng nhập");
            Snackbar.make(findViewById(android.R.id.content), "Vui lòng đăng nhập để tiếp tục!", Snackbar.LENGTH_LONG).show();
            finish();
            return;
        }
        Log.d(TAG, "Người dùng hiện tại: " + currentUser.getUid());
        db = FirebaseFirestore.getInstance();
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && "admin".equals(documentSnapshot.getString("role"))) {
                        Log.d(TAG, "Người dùng là admin");
                    } else {
                        Log.e(TAG, "Người dùng không phải admin hoặc không có trường role");
                        runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Bạn không có quyền admin!", Snackbar.LENGTH_LONG).show());
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi kiểm tra vai trò admin: " + e.getMessage());
                    runOnUiThread(() -> Snackbar.make(findViewById(android.R.id.content), "Lỗi kiểm tra vai trò admin: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    finish();
                });

        roomRecyclerView = findViewById(R.id.roomRecyclerView);
        btnAddRoom = findViewById(R.id.btnAddRoom);
        roomList = new ArrayList<>();
        roomTypeNames = new HashMap<>();
        roomAdapter = new AdminRoomAdapter(roomList);
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        roomRecyclerView.setAdapter(roomAdapter);

        initializeRoomNames();
        btnAddRoom.setOnClickListener(v -> {
            Log.d(TAG, "Nút Thêm phòng được nhấn");
            Snackbar.make(roomRecyclerView, "Đã nhấn nút Thêm phòng", Snackbar.LENGTH_LONG).show();
            if (!isNetworkAvailable()) {
                Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show();
                return;
            }
            showAddRoomDialog(null);
        });
        setupRoomsListener();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void initializeRoomNames() {
        roomTypeNames.put(1, "Standard");
        roomTypeNames.put(2, "VIP");
        roomTypeNames.put(3, "Deluxe");
    }

    private void setupRoomsListener() {
        if (roomsListener != null) {
            roomsListener.remove();
        }
        roomsListener = db.collection("rooms")
                .orderBy("MaPhong", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Lỗi lắng nghe danh sách phòng: " + e.getMessage());
                        runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Lỗi lắng nghe danh sách phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                        return;
                    }
                    if (!isActive || snapshots == null || isLoading) {
                        Log.d(TAG, "Bỏ qua snapshot: isActive=" + isActive + ", snapshots=" + (snapshots == null) + ", isLoading=" + isLoading);
                        return;
                    }
                    isLoading = true;
                    loadRoomsFromFirestore();
                });
    }

    private void loadRoomsFromFirestore() {
        if (!isActive || isLoading) {
            Log.d(TAG, "Bỏ qua loadRoomsFromFirestore: isActive=" + isActive + ", isLoading=" + isLoading);
            return;
        }
        if (!isNetworkAvailable()) {
            runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show());
            isLoading = false;
            return;
        }
        isLoading = true;
        Log.d(TAG, "Bắt đầu tải danh sách phòng từ Firestore");
        db.collection("rooms")
                .orderBy("MaPhong", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) {
                        isLoading = false;
                        return;
                    }
                    roomList.clear();
                    Map<Integer, RoomModel> roomMap = new HashMap<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        RoomModel room = parseRoom(doc);
                        if (room != null) {
                            roomMap.put(room.getMaPhong(), room);
                            Log.d(TAG, "Thêm phòng: MaPhong=" + room.getMaPhong() + ", TenPhong=" + room.getName());
                        } else {
                            Log.w(TAG, "Phòng không hợp lệ, bỏ qua: " + doc.getId());
                        }
                    }
                    roomList.addAll(roomMap.values());
                    roomAdapter.notifyDataSetChanged();
                    runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Đã tải " + roomList.size() + " phòng", Snackbar.LENGTH_LONG).show());
                    Log.d(TAG, "Đã tải " + roomList.size() + " phòng");
                    isLoading = false;
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Log.e(TAG, "Lỗi tải danh sách phòng: " + e.getMessage());
                        runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Lỗi tải danh sách phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                        isLoading = false;
                    }
                });
    }

    private void showAddRoomDialog(RoomModel room) {
        boolean isEditMode = room != null;
        Log.d(TAG, "showAddRoomDialog: isEditMode=" + isEditMode + ", Room=" + (room != null ? room.getName() : "null"));
        Snackbar.make(roomRecyclerView, "Đang mở dialog " + (isEditMode ? "sửa" : "thêm") + " phòng", Snackbar.LENGTH_LONG).show();
        if (isEditMode) {
            executorService.execute(() -> {
                Log.d(TAG, "Kiểm tra trạng thái mạng cho sửa phòng: " + room.getName());
                if (!isNetworkAvailable()) {
                    runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show());
                    Log.d(TAG, "Không có kết nối mạng");
                    return;
                }
                Log.d(TAG, "Bắt đầu truy vấn invoices cho MaPhong=" + room.getMaPhong());
                db.collection("invoices")
                        .whereEqualTo("MaPhong", room.getMaPhong())
                        .whereIn("TrangThai", List.of("Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm"))
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            Log.d(TAG, "Truy vấn invoices thành công, isEmpty=" + querySnapshot.isEmpty());
                            if (!isActive) {
                                Log.d(TAG, "Activity không active, bỏ qua");
                                return;
                            }
                            if (!querySnapshot.isEmpty()) {
                                runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Không thể sửa phòng vì phòng đang được đặt!", Snackbar.LENGTH_LONG).show());
                            } else {
                                runOnUiThread(() -> {
                                    Snackbar.make(roomRecyclerView, "Đang mở dialog sửa phòng", Snackbar.LENGTH_LONG).show();
                                    displayRoomDialog(room, true);
                                });
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Lỗi kiểm tra trạng thái đặt phòng: " + e.getMessage());
                            runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Lỗi kiểm tra trạng thái đặt phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                        });
            });
        } else {
            Log.d(TAG, "Kiểm tra trạng thái mạng cho thêm phòng");
            if (!isNetworkAvailable()) {
                Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show();
                Log.d(TAG, "Không có kết nối mạng");
                return;
            }
            Snackbar.make(roomRecyclerView, "Đang mở dialog thêm phòng", Snackbar.LENGTH_LONG).show();
            displayRoomDialog(null, false);
        }
    }

    private void displayRoomDialog(RoomModel room, boolean isEditMode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_room_management, null);
        builder.setView(dialogView);

        EditText editMaPhong = dialogView.findViewById(R.id.editMaPhong);
        Spinner spinnerMaLoaiPhong = dialogView.findViewById(R.id.spinnerMaLoaiPhong);
        EditText editGiaPhong = dialogView.findViewById(R.id.editGiaPhong);
        EditText editSoLuongNguoiToiDa = dialogView.findViewById(R.id.editSoLuongNguoiToiDa);
        Spinner spinnerTrangThai = dialogView.findViewById(R.id.spinnerTrangThai);
        EditText editMoTa = dialogView.findViewById(R.id.editMoTa);
        EditText editTienIch = dialogView.findViewById(R.id.editTienIch);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        String[] roomTypeOptions = {"Standard", "VIP", "Deluxe"};
        ArrayAdapter<String> roomTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomTypeOptions);
        roomTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaLoaiPhong.setAdapter(roomTypeAdapter);

        String[] statusOptions = {"Trống", "Đã đặt", "Đang sử dụng"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTrangThai.setAdapter(statusAdapter);

        Map<Integer, Double> roomTypePrices = new HashMap<>();
        roomTypePrices.put(1, 500000.0);
        roomTypePrices.put(2, 800000.0);
        roomTypePrices.put(3, 1200000.0);

        spinnerMaLoaiPhong.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int maLoaiPhong = position + 1;
                editGiaPhong.setText(String.format(Locale.getDefault(), "%.0f", roomTypePrices.get(maLoaiPhong)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                editGiaPhong.setText("500000");
            }
        });

        if (isEditMode) {
            editMaPhong.setText(String.valueOf(room.getMaPhong()));
            editMaPhong.setEnabled(false);
            spinnerMaLoaiPhong.setSelection(getRoomTypePosition(room.getMaLoaiPhong()));
            editGiaPhong.setText(String.format(Locale.getDefault(), "%.0f", room.getGiaPhong()));
            editSoLuongNguoiToiDa.setText(String.valueOf(room.getSoLuongNguoiToiDa()));
            spinnerTrangThai.setSelection(getStatusPosition(room.getStatus()));
            editMoTa.setText(room.getMoTa());
            editTienIch.setText(formatTienIch(room.getTienIch()));
        } else {
            editGiaPhong.setText("500000");
            editSoLuongNguoiToiDa.setText("2");
            spinnerTrangThai.setSelection(0);
            editMoTa.setText("Không có mô tả");
        }

        AlertDialog dialog = builder.create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            Log.d(TAG, "Nút Lưu được nhấn");
            String maPhongStr = editMaPhong.getText().toString().trim();
            String giaPhongStr = editGiaPhong.getText().toString().trim();
            String soLuongNguoiToiDaStr = editSoLuongNguoiToiDa.getText().toString().trim();
            String moTa = editMoTa.getText().toString().trim();
            String tienIchStr = editTienIch.getText().toString().trim();
            String trangThai = spinnerTrangThai.getSelectedItem().toString();
            int maLoaiPhong = spinnerMaLoaiPhong.getSelectedItemPosition() + 1;

            StringBuilder errorMessage = new StringBuilder();
            if (maPhongStr.isEmpty()) errorMessage.append("Mã phòng trống. ");
            if (giaPhongStr.isEmpty()) errorMessage.append("Giá phòng trống. ");
            if (soLuongNguoiToiDaStr.isEmpty()) errorMessage.append("Số lượng người tối đa trống.");
            if (errorMessage.length() > 0) {
                Snackbar.make(dialogView, errorMessage.toString(), Snackbar.LENGTH_LONG).show();
                return;
            }

            int maPhong;
            double giaPhong;
            int soLuongNguoiToiDa;
            try {
                maPhong = Integer.parseInt(maPhongStr);
                giaPhong = Double.parseDouble(giaPhongStr);
                soLuongNguoiToiDa = Integer.parseInt(soLuongNguoiToiDaStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Dữ liệu số không hợp lệ: " + e.getMessage());
                Snackbar.make(dialogView, "Dữ liệu số không hợp lệ!", Snackbar.LENGTH_LONG).show();
                return;
            }

            if (!isEditMode) {
                for (RoomModel r : roomList) {
                    if (r.getMaPhong() == maPhong) {
                        Snackbar.make(dialogView, "Mã phòng đã tồn tại!", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                }
            }

            List<Integer> tienIch = new ArrayList<>();
            if (!tienIchStr.isEmpty()) {
                try {
                    for (String id : tienIchStr.split(",")) {
                        tienIch.add(Integer.parseInt(id.trim()));
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Định dạng tiện ích không hợp lệ: " + e.getMessage());
                    Snackbar.make(dialogView, "Định dạng tiện ích không hợp lệ!", Snackbar.LENGTH_LONG).show();
                    return;
                }
            }

            String tenPhong = "Phòng " + maPhong + " - " + roomTypeNames.get(maLoaiPhong);

            Map<String, Object> roomData = new HashMap<>();
            roomData.put("MaPhong", maPhong);
            roomData.put("TenPhong", tenPhong);
            roomData.put("MaLoaiPhong", maLoaiPhong);
            roomData.put("GiaPhong", giaPhong);
            roomData.put("SoLuongNguoiToiDa", soLuongNguoiToiDa);
            roomData.put("TrangThai", trangThai);
            roomData.put("MoTa", moTa.isEmpty() ? "Không có mô tả" : moTa);
            roomData.put("TienIch", tienIch);

            if (!isNetworkAvailable()) {
                Snackbar.make(dialogView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show();
                return;
            }

            db.collection("rooms")
                    .document(String.valueOf(maPhong))
                    .set(roomData)
                    .addOnSuccessListener(aVoid -> {
                        Snackbar.make(roomRecyclerView, isEditMode ? "Cập nhật phòng thành công!" : "Thêm phòng thành công!", Snackbar.LENGTH_LONG).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi lưu phòng: " + e.getMessage());
                        Snackbar.make(roomRecyclerView, "Lỗi lưu phòng: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                    });
        });

        dialog.show();
    }

    private int getRoomTypePosition(int maLoaiPhong) {
        return maLoaiPhong - 1;
    }

    private int getStatusPosition(String status) {
        switch (status) {
            case "Trống": return 0;
            case "Đã đặt": return 1;
            case "Đang sử dụng": return 2;
            default: return 0;
        }
    }

    private void deleteRoom(RoomModel room) {
        Log.d(TAG, "Bắt đầu xóa phòng: " + room.getName());
        Snackbar.make(roomRecyclerView, "Đang kiểm tra trạng thái phòng " + room.getName(), Snackbar.LENGTH_LONG).show();
        executorService.execute(() -> {
            if (!isNetworkAvailable()) {
                Log.d(TAG, "Không có kết nối mạng khi xóa phòng: " + room.getName());
                runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show());
                return;
            }
            Log.d(TAG, "Truy vấn invoices cho MaPhong=" + room.getMaPhong());
            db.collection("invoices")
                    .whereEqualTo("MaPhong", room.getMaPhong())
                    .whereIn("TrangThai", List.of("Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm"))
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        Log.d(TAG, "Truy vấn invoices thành công, isEmpty=" + querySnapshot.isEmpty() + " cho phòng: " + room.getName());
                        if (!querySnapshot.isEmpty()) {
                            runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Không thể xóa phòng " + room.getName() + " vì phòng đang được đặt!", Snackbar.LENGTH_LONG).show());
                            return;
                        }
                        Log.d(TAG, "Bắt đầu xóa phòng " + room.getName() + " từ Firestore");
                        db.collection("rooms")
                                .document(String.valueOf(room.getMaPhong()))
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Xóa phòng thành công: " + room.getName());
                                    runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Xóa phòng " + room.getName() + " thành công!", Snackbar.LENGTH_LONG).show());
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi xóa phòng " + room.getName() + ": " + e.getMessage());
                                    runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Lỗi xóa phòng " + room.getName() + ": " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Lỗi kiểm tra lịch đặt cho phòng " + room.getName() + ": " + e.getMessage());
                        runOnUiThread(() -> Snackbar.make(roomRecyclerView, "Lỗi kiểm tra lịch đặt cho phòng " + room.getName() + ": " + e.getMessage(), Snackbar.LENGTH_LONG).show());
                    });
        });
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
            case 1: return 500000.0;
            case 2: return 800000.0;
            case 3: return 1200000.0;
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

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
        if (roomsListener != null) {
            roomsListener.remove();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        if (!isLoading) {
            loadRoomsFromFirestore();
        }
        setupRoomsListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
        if (roomsListener != null) {
            roomsListener.remove();
        }
        executorService.shutdown();
    }

    private class AdminRoomAdapter extends RecyclerView.Adapter<AdminRoomAdapter.RoomViewHolder> {
        private final List<RoomModel> rooms;

        AdminRoomAdapter(List<RoomModel> rooms) {
            this.rooms = rooms;
        }

        @NonNull
        @Override
        public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_room, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
            RoomModel room = rooms.get(position);
            holder.roomName.setText(room.getName());
            holder.roomType.setText("Loại: " + roomTypeNames.getOrDefault(room.getMaLoaiPhong(), "Không xác định"));
            holder.roomStatus.setText("Trạng thái: " + room.getStatus());
            holder.roomDetails.setText(String.format(Locale.getDefault(), "Giá: %s, Tối đa %d người, %s, Tiện ích: %s",
                    NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(room.getGiaPhong()),
                    room.getSoLuongNguoiToiDa(), room.getMoTa(), formatTienIch(room.getTienIch())));

            holder.btnEdit.setOnClickListener(v -> {
                Log.d(TAG, "Nút Sửa được nhấn cho phòng: " + room.getName());
                Snackbar.make(roomRecyclerView, "Đã nhấn nút Sửa cho " + room.getName(), Snackbar.LENGTH_LONG).show();
                if (!isNetworkAvailable()) {
                    Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show();
                    return;
                }
                showAddRoomDialog(room);
            });
            holder.btnDelete.setOnClickListener(v -> {
                Log.d(TAG, "Nút Xóa được nhấn cho phòng: " + room.getName());
                Snackbar.make(roomRecyclerView, "Đã nhấn nút Xóa cho " + room.getName(), Snackbar.LENGTH_LONG).show();
                if (!isNetworkAvailable()) {
                    Snackbar.make(roomRecyclerView, "Không có kết nối mạng!", Snackbar.LENGTH_LONG).show();
                    return;
                }
                new AlertDialog.Builder(RoomManagementActivity.this)
                        .setTitle("Xác nhận xóa")
                        .setMessage("Bạn có chắc muốn xóa phòng " + room.getName() + "?")
                        .setPositiveButton("Xóa", (dialog, which) -> deleteRoom(room))
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class RoomViewHolder extends RecyclerView.ViewHolder {
            TextView roomName, roomType, roomStatus, roomDetails;
            Button btnEdit, btnDelete;
            CardView cardView;

            RoomViewHolder(@NonNull View itemView) {
                super(itemView);
                roomName = itemView.findViewById(R.id.txtRoomName);
                roomType = itemView.findViewById(R.id.txtRoomType);
                roomStatus = itemView.findViewById(R.id.txtRoomStatus);
                roomDetails = itemView.findViewById(R.id.txtRoomDetails);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                cardView = itemView.findViewById(R.id.cardView);
            }
        }
    }
}