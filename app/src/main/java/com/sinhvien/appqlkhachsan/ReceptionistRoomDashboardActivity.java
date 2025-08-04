package com.sinhvien.appqlkhachsan;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReceptionistRoomDashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";
    private static final int DAY_RANGE = 7;

    private FirebaseFirestore db;
    private RecyclerView dashboardRecyclerView;
    private DashboardAdapter adapter;
    private EditText datePicker;
    private ProgressBar progressBar;

    private List<RoomModel> roomList = new ArrayList<>();
    private List<String> dateList = new ArrayList<>();
    private Map<Integer, Map<String, String>> statusMap = new HashMap<>();
    private Map<String, String> fullDateMap = new HashMap<>();
    private Calendar selectedCalendar = Calendar.getInstance();

    private ListenerRegistration invoicesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receptionist_dashboard);

        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        dashboardRecyclerView = findViewById(R.id.dashboardRecyclerView);
        datePicker = findViewById(R.id.datePickerDashboard);
        progressBar = findViewById(R.id.progressBar);

        setupDatePicker();
        loadDashboardData();
        setupInvoiceListener();
    }

    private void setupDatePicker() {
        updateDatePickerText();
        datePicker.setOnClickListener(v -> showDatePickerDialog());
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    selectedCalendar.set(year, month, dayOfMonth);
                    updateDatePickerText();
                    loadDashboardData();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void updateDatePickerText() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        datePicker.setText(sdf.format(selectedCalendar.getTime()));
    }

    private void generateDateList() {
        dateList.clear();
        fullDateMap.clear();
        Calendar calendar = (Calendar) selectedCalendar.clone();
        SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM", Locale.getDefault());
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (int i = 0; i < DAY_RANGE; i++) {
            String displayDate = displayFormat.format(calendar.getTime());
            String dbDate = dbFormat.format(calendar.getTime());
            dateList.add(displayDate);
            fullDateMap.put(displayDate, dbDate);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private void loadDashboardData() {
        progressBar.setVisibility(View.VISIBLE);
        dashboardRecyclerView.setVisibility(View.GONE);

        generateDateList();

        Query roomsQuery = db.collection("rooms").orderBy("MaPhong", Query.Direction.ASCENDING);

        String startDate = fullDateMap.get(dateList.get(0)) + " 00:00:00";
        String endDate = fullDateMap.get(dateList.get(dateList.size() - 1)) + " 23:59:59";
        Query invoicesQuery = db.collection("invoices")
                .whereLessThan("TGCheckout", endDate)
                .whereGreaterThan("TGCheckin", startDate);

        Tasks.whenAllSuccess(roomsQuery.get(), invoicesQuery.get()).addOnSuccessListener(results -> {
            QuerySnapshot roomsSnapshot = (QuerySnapshot) results.get(0);
            QuerySnapshot invoicesSnapshot = (QuerySnapshot) results.get(1);

            roomList.clear();
            for (DocumentSnapshot doc : roomsSnapshot) {
                RoomModel room = parseRoom(doc);
                if (room != null) {
                    roomList.add(room);
                }
            }

            processInvoices(invoicesSnapshot);

            updateDashboardView();
            progressBar.setVisibility(View.GONE);
            dashboardRecyclerView.setVisibility(View.VISIBLE);

        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, "Error loading dashboard data", e);
            Toast.makeText(this, "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // Tái sử dụng logic parseRoom từ MainActivity
    private RoomModel parseRoom(DocumentSnapshot doc) {
        Integer maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
        if (maPhong == 0) return null;
        String tenPhong = doc.getString("TenPhong") != null ? doc.getString("TenPhong") : ("Phòng " + maPhong);
        Integer maLoaiPhong = doc.getLong("MaLoaiPhong") != null ? doc.getLong("MaLoaiPhong").intValue() : 1;
        Double giaPhong = doc.getDouble("GiaPhong");
        return new RoomModel(maPhong, tenPhong, maLoaiPhong, giaPhong, 0, "", "", new ArrayList<>());
    }

    private void processInvoices(QuerySnapshot invoicesSnapshot) {
        statusMap.clear();
        SimpleDateFormat dbFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat fullDbFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        for (DocumentSnapshot doc : invoicesSnapshot) {
            if (doc.getLong("MaPhong") == null || doc.getString("TrangThai") == null ||
                    doc.getString("TGCheckin") == null || doc.getString("TGCheckout") == null) continue;

            Integer maPhong = doc.getLong("MaPhong").intValue();
            String status = doc.getString("TrangThai");
            if (status.equals("Đã hủy") || status.equals("Hoàn thành")) continue;

            try {
                Date checkinDate = fullDbFormat.parse(doc.getString("TGCheckin"));
                Date checkoutDate = fullDbFormat.parse(doc.getString("TGCheckout"));
                Calendar cal = Calendar.getInstance();
                cal.setTime(checkinDate);

                while (cal.getTime().before(checkoutDate)) {
                    String dateStr = dbFormat.format(cal.getTime());
                    if (!statusMap.containsKey(maPhong)) {
                        statusMap.put(maPhong, new HashMap<>());
                    }
                    String currentStatus = statusMap.get(maPhong).get(dateStr);
                    if (currentStatus == null || !currentStatus.equals("Đang sử dụng")) {
                        statusMap.get(maPhong).put(dateStr, status);
                    }
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing date from invoice", e);
            }
        }
    }

    private void updateDashboardView() {
        adapter = new DashboardAdapter(this, roomList, dateList, statusMap, fullDateMap, (room, date, status) -> {
            if (status.equals("Trống")) {
                new AlertDialog.Builder(this)
                        .setTitle("Đặt phòng")
                        .setMessage("Bạn có muốn đặt phòng " + room.getName() + " cho ngày " + date + "?")
                        .setPositiveButton("Đặt ngay", (dialog, which) -> {
                            Intent intent = new Intent(this, BookingActivity.class);
                            intent.putExtra("ROOM_ID", room.getMaPhong());
                            intent.putExtra("roomName", room.getName());
                            intent.putExtra("price", room.getGiaPhong());
                            intent.putExtra("selectedDate", date);
                            startActivity(intent);
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            } else {
                Toast.makeText(this, "Phòng " + room.getName() + " đang " + status.toLowerCase() + " vào ngày " + date, Toast.LENGTH_SHORT).show();
            }
        });
        dashboardRecyclerView.setLayoutManager(new GridLayoutManager(this, dateList.size() + 1));
        dashboardRecyclerView.setAdapter(adapter);
    }

    // Tương tự logic trong MainActivity để cập nhật realtime
    private void setupInvoiceListener() {
        if (invoicesListener != null) {
            invoicesListener.remove();
        }
        invoicesListener = db.collection("invoices").addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }
            if (snapshots != null && !snapshots.isEmpty()) {
                Log.d(TAG, "Invoices data changed. Reloading dashboard.");
                processInvoices(snapshots);
                updateDashboardView();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Gỡ listener để tránh memory leak
        if (invoicesListener != null) {
            invoicesListener.remove();
        }
    }
}