package com.sinhvien.appqlkhachsan.admin;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.sinhvien.appqlkhachsan.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class AdminBookingListActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private EditText etFilterCheckInDate;
    private SearchView searchView;
    private Spinner spinnerFilterStatus;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private List<BookingModel> bookingList;
    private boolean isActive = true;
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat displayDateTimeFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
    private final Object lock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_booking_list);

        db = FirebaseFirestore.getInstance();
        etFilterCheckInDate = findViewById(R.id.etFilterCheckInDate);
        searchView = findViewById(R.id.searchView);
        spinnerFilterStatus = findViewById(R.id.spinnerFilterStatus);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        String[] statusOptions = {"Tất cả", "Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm", "Đã trả phòng", "Đã hủy"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterStatus.setAdapter(statusAdapter);

        bookingList = new ArrayList<>();
        setupViewPager();

        etFilterCheckInDate.setOnClickListener(v -> showDatePickerDialog());
        spinnerFilterStatus.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadBookingsForAllFragments();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterBookingsForAllFragments(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterBookingsForAllFragments(newText);
                return true;
            }
        });
    }

    private void setupViewPager() {
        viewPager.setAdapter(new BookingPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Đơn chờ xử lý"); break;
                case 1: tab.setText("Đang ở"); break;
                case 2: tab.setText("Đã hủy/Đã check-out"); break;
            }
        }).attach();
    }

    private void loadBookingsForAllFragments() {
        for (int i = 0; i < 3; i++) {
            BookingFragment fragment = (BookingFragment) getSupportFragmentManager().findFragmentByTag("f" + i);
            if (fragment != null) fragment.loadBookings();
        }
    }

    private void filterBookingsForAllFragments(String query) {
        for (int i = 0; i < 3; i++) {
            BookingFragment fragment = (BookingFragment) getSupportFragmentManager().findFragmentByTag("f" + i);
            if (fragment != null) fragment.filterBookings(query);
        }
    }

    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            etFilterCheckInDate.setText(dateFormat.format(calendar.getTime()));
            loadBookingsForAllFragments();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    void loadBookings(String tabFilter, BookingAdapter adapter, List<BookingModel> filteredBookingList) {
        if (!isActive) return;
        String selectedStatus = spinnerFilterStatus.getSelectedItem().toString();
        String checkInDate = etFilterCheckInDate.getText().toString();

        synchronized (lock) {
            bookingList.clear();
            filteredBookingList.clear();
        }

        Map<String, BookingModel> uniqueBookings = Collections.synchronizedMap(new LinkedHashMap<>());
        Query query = db.collection("bookings");
        if (!selectedStatus.equals("Tất cả")) query = query.whereEqualTo("TrangThaiDD", selectedStatus);
        if (!tabFilter.isEmpty()) {
            if (tabFilter.equals("pending")) query = query.whereIn("TrangThaiDD", List.of("Đang xử lý", "Đã xác nhận"));
            else if (tabFilter.equals("checked_in")) query = query.whereIn("TrangThaiDD", List.of("Đã nhận phòng", "Trả phòng sớm"));
            else if (tabFilter.equals("cancelled")) query = query.whereIn("TrangThaiDD", List.of("Đã trả phòng", "Đã hủy"));
        }

        query.get().addOnSuccessListener(querySnapshot -> {
            if (!isActive || querySnapshot.isEmpty()) {
                if (isActive) Toast.makeText(this, "Không có đơn đặt phòng nào!", Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged();
                return;
            }

            List<Task<?>> tasks = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot) {
                String maDon = doc.getId();
                String maKH = doc.getString("MaKH");
                Integer maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
                String tgDat = doc.getString("TGDat");
                String tgCheckin = doc.getString("TGCheckin");
                String tgCheckout = doc.getString("TGCheckout");
                String trangThaiDD = doc.getString("TrangThaiDD");
                String trangThaiTT = doc.getString("TrangThaiTT");
                String ghiChu = doc.getString("GhiChu");

                Task<DocumentSnapshot> roomTask = db.collection("rooms").document(String.valueOf(maPhong)).get();
                Task<DocumentSnapshot> customerTask = db.collection("customers").document(maKH).get();
                Task<DocumentSnapshot> invoiceTask = db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                        .continueWith(task -> task.getResult().getDocuments().isEmpty() ? null : task.getResult().getDocuments().get(0));

                tasks.add(Tasks.whenAllSuccess(roomTask, customerTask, invoiceTask).continueWith(task -> {
                    if (!isActive) return null;
                    DocumentSnapshot roomDoc = roomTask.getResult();
                    DocumentSnapshot customerDoc = customerTask.getResult();
                    DocumentSnapshot invoiceDoc = invoiceTask.getResult();

                    if (roomDoc.exists() && customerDoc.exists()) {
                        String roomName = roomDoc.getString("TenPhong") != null ? roomDoc.getString("TenPhong") : "N/A";
                        String loaiPhong = roomDoc.getString("LoaiPhong") != null ? roomDoc.getString("LoaiPhong") : "N/A";
                        String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";
                        String cmnd = customerDoc.getString("CCCD") != null ? customerDoc.getString("CCCD") : "N/A";
                        String phoneNumber = customerDoc.getString("SoDienThoai") != null ? customerDoc.getString("SoDienThoai") : "N/A";
                        String maKhuyenMai = invoiceDoc != null && invoiceDoc.getString("MaGiamGia") != null ? invoiceDoc.getString("MaGiamGia") : "N/A";
                        String tongTien = invoiceDoc != null && invoiceDoc.getDouble("TongGia") != null ?
                                String.format(Locale.getDefault(), "%,.0f VND", invoiceDoc.getDouble("TongGia")) : "N/A";

                        String formattedDateRange;
                        try {
                            String checkInStr = tgCheckin != null ? displayDateTimeFormat.format(dateTimeFormat.parse(tgCheckin)) : "N/A";
                            String checkOutStr = tgCheckout != null ? displayDateTimeFormat.format(dateTimeFormat.parse(tgCheckout)) : "N/A";
                            formattedDateRange = checkInStr + " - " + checkOutStr;
                        } catch (ParseException e) {
                            formattedDateRange = "N/A";
                        }

                        if (!checkInDate.isEmpty()) {
                            try {
                                if (tgCheckin == null || !dateFormat.format(dateTimeFormat.parse(tgCheckin)).equals(checkInDate)) {
                                    return null;
                                }
                            } catch (ParseException e) {
                                return null;
                            }
                        }

                        synchronized (uniqueBookings) {
                            if (!uniqueBookings.containsKey(maDon)) {
                                uniqueBookings.put(maDon, new BookingModel(maDon, maKH, maPhong, roomName, loaiPhong, customerName,
                                        cmnd, phoneNumber, tgDat, tgCheckin, tgCheckout, trangThaiDD, trangThaiTT, ghiChu,
                                        maKhuyenMai, tongTien));
                            }
                        }
                    }
                    return null;
                }));
            }

            Tasks.whenAllComplete(tasks).addOnCompleteListener(task -> {
                if (!isActive) return;
                synchronized (lock) {
                    bookingList.clear();
                    filteredBookingList.clear();
                    bookingList.addAll(uniqueBookings.values());
                    filteredBookingList.addAll(uniqueBookings.values());
                    adapter.notifyDataSetChanged();
                }
            });
        }).addOnFailureListener(e -> {
            if (isActive) Toast.makeText(this, "Lỗi tải dữ liệu!", Toast.LENGTH_LONG).show();
        });
    }

    void filterBookings(String query, List<BookingModel> filteredBookingList, BookingAdapter adapter) {
        synchronized (lock) {
            filteredBookingList.clear();
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (BookingModel booking : bookingList) {
                if (booking.maDon.toLowerCase().contains(lowerQuery) ||
                        (booking.customerName != null && booking.customerName.toLowerCase().contains(lowerQuery)) ||
                        (booking.phoneNumber != null && booking.phoneNumber.contains(lowerQuery))) {
                    filteredBookingList.add(booking);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private BookingModel findBookingById(String maDon) {
        synchronized (lock) {
            for (BookingModel booking : bookingList) {
                if (booking.maDon.equals(maDon)) return booking;
            }
        }
        return null;
    }

    private void handleConfirm(String maDon, String maKH, String roomName) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("TrangThaiDD", "Đã xác nhận");

        db.collection("bookings").document(maDon).update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (!isActive) return;
                    db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                            .addOnSuccessListener(querySnapshot -> {
                                if (!querySnapshot.isEmpty()) {
                                    String invoiceId = querySnapshot.getDocuments().get(0).getId();
                                    db.collection("invoices").document(invoiceId)
                                            .update("TrangThai", "Đã xác nhận")
                                            .addOnSuccessListener(aVoid1 -> {
                                                sendNotification(maKH, maDon, "Đơn đặt phòng " + maDon + " đã được xác nhận!");
                                                Toast.makeText(this, "Đã xác nhận đơn " + maDon, Toast.LENGTH_SHORT).show();
                                                loadBookingsForAllFragments();
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (isActive) Toast.makeText(this, "Lỗi xác nhận đơn!", Toast.LENGTH_LONG).show();
                });
    }

    private void handleReject(String maDon, String maKH, int maPhong) {
        hasActiveBookingsForRoom(maDon, maPhong, hasActive -> {
            if (hasActive) {
                Toast.makeText(this, "Không thể hủy: Phòng đang có đơn chưa kết thúc!", Toast.LENGTH_LONG).show();
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            EditText etReason = new EditText(this);
            builder.setTitle("Từ chối đơn")
                    .setMessage("Nhập lý do từ chối đơn " + maDon)
                    .setView(etReason)
                    .setPositiveButton("Xác nhận", (dialog, which) -> {
                        String reason = etReason.getText().toString().isEmpty() ? "Không có lý do" : etReason.getText().toString();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("TrangThaiDD", "Đã hủy");
                        updates.put("GhiChu", "Đơn bị hủy bởi admin. Lý do: " + reason);

                        db.collection("bookings").document(maDon).update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    if (!isActive) return;
                                    db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                                            .addOnSuccessListener(querySnapshot -> {
                                                if (!querySnapshot.isEmpty()) {
                                                    String invoiceId = querySnapshot.getDocuments().get(0).getId();
                                                    db.collection("invoices").document(invoiceId)
                                                            .update("TrangThai", "Đã hủy")
                                                            .addOnSuccessListener(aVoid1 -> {
                                                                db.collection("rooms").document(String.valueOf(maPhong))
                                                                        .update("TrangThai", "Trống")
                                                                        .addOnSuccessListener(aVoid2 -> {
                                                                            if (isActive) {
                                                                                sendNotification(maKH, maDon, "Đơn đặt phòng " + maDon + " đã bị từ chối. Lý do: " + reason);
                                                                                Toast.makeText(this, "Đã từ chối đơn " + maDon, Toast.LENGTH_SHORT).show();
                                                                                loadBookingsForAllFragments();
                                                                            }
                                                                        });
                                                            });
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    if (isActive) Toast.makeText(this, "Lỗi từ chối đơn!", Toast.LENGTH_LONG).show();
                                });
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    private void handleCheckIn(String maDon, int maPhong, String maKH, String roomName) {
        BookingModel booking = findBookingById(maDon);
        if (booking == null) {
            Toast.makeText(this, "Không tìm thấy đơn đặt phòng!", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("Đã hủy".equals(booking.trangThaiDD)) {
            Toast.makeText(this, "Không thể check-in: Đơn đã bị hủy!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!"Đã xác nhận".equals(booking.trangThaiDD)) {
            Toast.makeText(this, "Đơn phải ở trạng thái Đã xác nhận để check-in!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Date currentTime = new Date();
            Date checkInDate = dateTimeFormat.parse(booking.tgCheckin);
            Date checkOutDate = dateTimeFormat.parse(booking.tgCheckout);

            if (currentTime.before(checkInDate)) {
                new AlertDialog.Builder(this)
                        .setMessage("Không thể check-in trước ngày nhận phòng (" + displayDateTimeFormat.format(checkInDate) + ")!")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            if (currentTime.after(checkOutDate)) {
                Toast.makeText(this, "Không thể check-in: Đã quá hạn trả phòng!", Toast.LENGTH_LONG).show();
                return;
            }

            db.collection("rooms").document(String.valueOf(maPhong)).get()
                    .addOnSuccessListener(roomDoc -> {
                        if (!isActive) return;
                        String roomStatus = roomDoc.getString("TrangThai");
                        if (!"Trống".equals(roomStatus)) {
                            Toast.makeText(this, "Không thể check-in: Phòng không trống!", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String checkInTime = dateTimeFormat.format(currentTime);
                        String pass = generateRandomPass();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("TrangThaiDD", "Đã nhận phòng");
                        updates.put("CheckInTime", checkInTime);
                        updates.put("RoomPass", pass);

                        db.collection("bookings").document(maDon).update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    if (!isActive) return;
                                    db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                                            .addOnSuccessListener(querySnapshot -> {
                                                if (!querySnapshot.isEmpty()) {
                                                    String invoiceId = querySnapshot.getDocuments().get(0).getId();
                                                    db.collection("invoices").document(invoiceId)
                                                            .update("TrangThai", "Đang ở")
                                                            .addOnSuccessListener(aVoid1 -> {
                                                                db.collection("rooms").document(String.valueOf(maPhong))
                                                                        .update("TrangThai", "Đang sử dụng")
                                                                        .addOnSuccessListener(aVoid2 -> {
                                                                            if (isActive) {
                                                                                sendNotification(maKH, maDon, "Bạn đã nhận phòng " + roomName + ". Mã pass: " + pass);
                                                                                new AlertDialog.Builder(this)
                                                                                        .setTitle("Check-in thành công")
                                                                                        .setMessage("Đã check-in đơn " + maDon + ". Mã pass: " + pass)
                                                                                        .setPositiveButton("OK", null)
                                                                                        .show();
                                                                                loadBookingsForAllFragments();
                                                                            }
                                                                        });
                                                            });
                                                }
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    if (isActive) Toast.makeText(this, "Lỗi check-in!", Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        if (isActive) Toast.makeText(this, "Lỗi kiểm tra trạng thái phòng!", Toast.LENGTH_LONG).show();
                    });
        } catch (ParseException e) {
            Toast.makeText(this, "Lỗi xử lý thời gian!", Toast.LENGTH_LONG).show();
        }
    }

    private void handleCheckOut(String maDon, int maPhong, boolean isEarly) {
        BookingModel booking = findBookingById(maDon);
        if (booking == null) {
            Toast.makeText(this, "Không tìm thấy đơn đặt phòng!", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive || querySnapshot.isEmpty()) {
                        if (isActive) Toast.makeText(this, "Không tìm thấy hóa đơn!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String invoiceId = querySnapshot.getDocuments().get(0).getId();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("TrangThai", "Hoàn tất");

                    String trangThaiDD = isEarly ? "Trả phòng sớm" : "Đã trả phòng";

                    db.collection("invoices").document(invoiceId).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                if (!isActive) return;
                                db.collection("bookings").document(maDon).update("TrangThaiDD", trangThaiDD)
                                        .addOnSuccessListener(aVoid1 -> {
                                            if (!isActive) return;
                                            db.collection("rooms").document(String.valueOf(maPhong))
                                                    .update("TrangThai", "Trống")
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        if (isActive) {
                                                            sendNotification(booking.maKH, maDon, "Bạn đã trả phòng đơn " + maDon + (isEarly ? " sớm" : "") + ". Cảm ơn bạn đã sử dụng dịch vụ!");
                                                            new AlertDialog.Builder(this)
                                                                    .setTitle("Check-out thành công")
                                                                    .setMessage("Đã check-out đơn " + maDon + ". Trạng thái: " + trangThaiDD)
                                                                    .setPositiveButton("OK", null)
                                                                    .show();
                                                            loadBookingsForAllFragments();
                                                        }
                                                    });
                                        });
                            });
                })
                .addOnFailureListener(e -> {
                    if (isActive) Toast.makeText(this, "Lỗi xử lý check-out!", Toast.LENGTH_LONG).show();
                });
    }

    private void handleAcceptCancel(String maDon, int maPhong, String maKH) {
        hasActiveBookingsForRoom(maDon, maPhong, hasActive -> {
            if (hasActive) {
                Toast.makeText(this, "Không thể hủy: Phòng đang có đơn chưa kết thúc!", Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("TrangThaiDD", "Đã hủy");
            updates.put("GhiChu", "Đơn được admin chấp nhận hủy vào " + displayDateTimeFormat.format(new Date()));

            db.collection("bookings").document(maDon).update(updates)
                    .addOnSuccessListener(aVoid -> {
                        if (!isActive) return;
                        db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                                .addOnSuccessListener(querySnapshot -> {
                                    if (!querySnapshot.isEmpty()) {
                                        String invoiceId = querySnapshot.getDocuments().get(0).getId();
                                        db.collection("invoices").document(invoiceId)
                                                .update("TrangThai", "Đã hủy")
                                                .addOnSuccessListener(aVoid1 -> {
                                                    db.collection("rooms").document(String.valueOf(maPhong))
                                                            .update("TrangThai", "Trống")
                                                            .addOnSuccessListener(aVoid2 -> {
                                                                if (isActive) {
                                                                    sendNotification(maKH, maDon, "Yêu cầu hủy đơn " + maDon + " đã được chấp nhận.");
                                                                    Toast.makeText(this, "Đã chấp nhận hủy đơn " + maDon, Toast.LENGTH_SHORT).show();
                                                                    loadBookingsForAllFragments();
                                                                }
                                                            });
                                                });
                                    }
                                });
                    })
                    .addOnFailureListener(e -> {
                        if (isActive) Toast.makeText(this, "Lỗi chấp nhận hủy đơn!", Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void handleRejectCancel(String maDon, String maKH) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        EditText etReason = new EditText(this);
        builder.setTitle("Từ chối yêu cầu hủy")
                .setMessage("Nhập lý do từ chối yêu cầu hủy đơn " + maDon)
                .setView(etReason)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String reason = etReason.getText().toString().isEmpty() ? "Không có lý do" : etReason.getText().toString();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("GhiChu", "Yêu cầu hủy bị từ chối bởi admin. Lý do: " + reason);

                    db.collection("bookings").document(maDon).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                if (!isActive) return;
                                sendNotification(maKH, maDon, "Yêu cầu hủy đơn " + maDon + " đã bị từ chối. Lý do: " + reason);
                                Toast.makeText(this, "Đã từ chối yêu cầu hủy đơn " + maDon, Toast.LENGTH_SHORT).show();
                                loadBookingsForAllFragments();
                            })
                            .addOnFailureListener(e -> {
                                if (isActive) Toast.makeText(this, "Lỗi từ chối yêu cầu hủy!", Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void handleDeleteBooking(String maDon) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa đơn")
                .setMessage("Bạn có chắc chắn muốn xóa đơn " + maDon + "?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    db.collection("bookings").document(maDon).delete()
                            .addOnSuccessListener(aVoid -> {
                                if (!isActive) return;
                                db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                                        .addOnSuccessListener(querySnapshot -> {
                                            if (!querySnapshot.isEmpty()) {
                                                String invoiceId = querySnapshot.getDocuments().get(0).getId();
                                                db.collection("invoices").document(invoiceId).delete()
                                                        .addOnSuccessListener(aVoid1 -> {
                                                            if (isActive) {
                                                                Toast.makeText(this, "Đã xóa đơn " + maDon, Toast.LENGTH_SHORT).show();
                                                                loadBookingsForAllFragments();
                                                            }
                                                        });
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                if (isActive) Toast.makeText(this, "Lỗi xóa đơn!", Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showBookingDetails(BookingModel booking) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_booking_detail, null);
        TextView tvDetailBookingId = dialogView.findViewById(R.id.tvDetailBookingId);
        TextView tvDetailCustomerName = dialogView.findViewById(R.id.tvDetailCustomerName);
        TextView tvDetailCMND = dialogView.findViewById(R.id.tvDetailCMND);
        TextView tvDetailPhoneNumber = dialogView.findViewById(R.id.tvDetailPhoneNumber);
        TextView tvDetailRoom = dialogView.findViewById(R.id.tvDetailRoom);
        TextView tvDetailDateCreated = dialogView.findViewById(R.id.tvDetailDateCreated);
        TextView tvDetailDateRange = dialogView.findViewById(R.id.tvDetailDateRange);
        TextView tvDetailPromoCode = dialogView.findViewById(R.id.tvDetailPromoCode);
        TextView tvDetailTotalPrice = dialogView.findViewById(R.id.tvDetailTotalPrice);
        TextView tvDetailStatus = dialogView.findViewById(R.id.tvDetailStatus);
        TextView tvDetailGhiChu = dialogView.findViewById(R.id.tvDetailGhiChu);
        Button btnCloseDetail = dialogView.findViewById(R.id.btnCloseDetail);

        tvDetailBookingId.setText("Mã đơn: " + booking.maDon);
        tvDetailCustomerName.setText("Khách hàng: " + (booking.customerName != null ? booking.customerName : "N/A"));
        tvDetailCMND.setText("CCCD: " + (booking.cmnd != null ? booking.cmnd : "N/A"));
        tvDetailPhoneNumber.setText("Số điện thoại: " + (booking.phoneNumber != null ? booking.phoneNumber : "N/A"));
        tvDetailRoom.setText("Phòng: " + (booking.roomName != null ? booking.roomName + " (" + booking.loaiPhong + ")" : "N/A"));
        tvDetailDateCreated.setText("Ngày tạo: " + (booking.tgDat != null ? formatDateTime(booking.tgDat) : "N/A"));
        tvDetailDateRange.setText("Ngày nhận - trả: " + (booking.tgCheckin != null && booking.tgCheckout != null ? formatDateTime(booking.tgCheckin) + " - " + formatDateTime(booking.tgCheckout) : "N/A"));
        tvDetailPromoCode.setText("Mã khuyến mãi: " + (booking.maKhuyenMai != null ? booking.maKhuyenMai : "N/A"));
        tvDetailTotalPrice.setText("Tổng tiền: " + (booking.tongTien != null ? booking.tongTien : "N/A"));
        tvDetailStatus.setText("Trạng thái: " + (booking.trangThaiDD != null ? booking.trangThaiDD : "N/A"));
        tvDetailGhiChu.setText("Ghi chú: " + (booking.ghiChu != null ? booking.ghiChu : "Không có"));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        btnCloseDetail.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String formatDateTime(String dateTime) {
        try {
            return displayDateTimeFormat.format(dateTimeFormat.parse(dateTime));
        } catch (ParseException e) {
            return "N/A";
        }
    }

    private void checkRoomAvailability(int newMaPhong, String tgCheckin, String tgCheckout, String maDon, Runnable onAvailable) {
        try {
            Date checkInDate = dateTimeFormat.parse(tgCheckin);
            Date checkOutDate = dateTimeFormat.parse(tgCheckout);
            db.collection("bookings")
                    .whereIn("TrangThaiDD", List.of("Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm"))
                    .whereEqualTo("MaPhong", newMaPhong)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (!isActive) return;
                        boolean isAvailable = true;
                        for (DocumentSnapshot doc : querySnapshot) {
                            if (doc.getId().equals(maDon)) continue;
                            String bookedCheckIn = doc.getString("TGCheckin");
                            String bookedCheckOut = doc.getString("TGCheckout");
                            try {
                                Date bookedCheckInDate = dateTimeFormat.parse(bookedCheckIn);
                                Date bookedCheckOutDate = dateTimeFormat.parse(bookedCheckOut);
                                if (!(checkOutDate.before(bookedCheckInDate) || checkInDate.after(bookedCheckOutDate))) {
                                    isAvailable = false;
                                    break;
                                }
                            } catch (ParseException e) {
                                // Ignore parse error
                            }
                        }
                        if (isAvailable) onAvailable.run();
                        else Toast.makeText(this, "Phòng không trống trong khoảng thời gian đã chọn!", Toast.LENGTH_LONG).show();
                    });
        } catch (ParseException e) {
            Toast.makeText(this, "Lỗi xử lý thời gian!", Toast.LENGTH_LONG).show();
        }
    }


    private void hasActiveBookingsForRoom(String maDon, int maPhong, java.util.function.Consumer<Boolean> callback) {
        db.collection("bookings")
                .whereIn("TrangThaiDD", List.of("Đang xử lý", "Đã xác nhận", "Đã nhận phòng", "Trả phòng sớm"))
                .whereEqualTo("MaPhong", maPhong)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) return;
                    boolean hasActive = false;
                    for (DocumentSnapshot doc : querySnapshot) {
                        if (!doc.getId().equals(maDon)) {
                            hasActive = true;
                            break;
                        }
                    }
                    callback.accept(hasActive);
                })
                .addOnFailureListener(e -> {
                    if (isActive) Toast.makeText(this, "Lỗi kiểm tra trạng thái phòng!", Toast.LENGTH_LONG).show();
                    callback.accept(false);
                });
    }

    private void handleEditBooking(String maDon, String currentCheckIn, String currentCheckOut, int currentMaPhong, String currentGhiChu) {
        try {
            boolean isConfirmed = findBookingById(maDon).trangThaiDD.equals("Đã xác nhận");
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_booking, null);
            EditText etCheckIn = dialogView.findViewById(R.id.etCheckIn);
            EditText etCheckOut = dialogView.findViewById(R.id.etCheckOut);
            EditText etGhiChu = dialogView.findViewById(R.id.etGhiChu);
            Spinner spinnerMaPhong = dialogView.findViewById(R.id.spinnerMaPhong);

            etCheckIn.setText(currentCheckIn != null ? formatDateTime(currentCheckIn) : "");
            etCheckOut.setText(currentCheckOut != null ? formatDateTime(currentCheckOut) : "");
            etGhiChu.setText(currentGhiChu != null ? currentGhiChu : "");

            List<String> roomIds = new ArrayList<>();
            db.collection("rooms").whereEqualTo("TrangThai", "Trống").get()
                    .addOnSuccessListener(querySnapshot -> {
                        roomIds.add(String.valueOf(currentMaPhong));
                        for (DocumentSnapshot doc : querySnapshot) {
                            String roomId = doc.getId();
                            if (!roomId.equals(String.valueOf(currentMaPhong))) roomIds.add(roomId);
                        }
                        ArrayAdapter<String> roomAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomIds);
                        roomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerMaPhong.setAdapter(roomAdapter);
                    });

            etCheckIn.setOnClickListener(v -> showDateTimePickerDialog(etCheckIn, true));
            etCheckOut.setOnClickListener(v -> showDateTimePickerDialog(etCheckOut, false));
            if (isConfirmed) {
                etCheckIn.setEnabled(false);
                etCheckOut.setEnabled(false);
                spinnerMaPhong.setEnabled(false);
            }

            new AlertDialog.Builder(this)
                    .setTitle("Chỉnh sửa thời gian đặt phòng")
                    .setView(dialogView)
                    .setPositiveButton("Lưu", (dialog, which) -> {
                        String newCheckIn = etCheckIn.getText().toString();
                        String newCheckOut = etCheckOut.getText().toString();
                        String newGhiChu = etGhiChu.getText().toString();
                        String newMaPhongStr = spinnerMaPhong.getSelectedItem() != null ? spinnerMaPhong.getSelectedItem().toString() : String.valueOf(currentMaPhong);
                        int newMaPhong = Integer.parseInt(newMaPhongStr);

                        try {
                            Date newCheckInDate = displayDateTimeFormat.parse(newCheckIn);
                            Date newCheckOutDate = displayDateTimeFormat.parse(newCheckOut);
                            if (newCheckInDate.before(new Date())) {
                                Toast.makeText(this, "Thời gian check-in phải từ bây giờ trở đi!", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (!newCheckOutDate.after(newCheckInDate)) {
                                Toast.makeText(this, "Thời gian check-out phải sau thời gian check-in!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String formattedCheckIn = dateTimeFormat.format(newCheckInDate);
                            String formattedCheckOut = dateTimeFormat.format(newCheckOutDate);

                            if (newMaPhong != currentMaPhong) {
                                checkRoomAvailability(newMaPhong, formattedCheckIn, formattedCheckOut, maDon, () -> {
                                    Map<String, Object> updates = new HashMap<>();
                                    updates.put("TGCheckin", formattedCheckIn);
                                    updates.put("TGCheckout", formattedCheckOut);
                                    updates.put("MaPhong", newMaPhong);
                                    updates.put("GhiChu", newGhiChu);
                                    db.collection("bookings").document(maDon).update(updates)
                                            .addOnSuccessListener(aVoid -> {
                                                if (!isActive) return;
                                                db.collection("rooms").document(String.valueOf(currentMaPhong))
                                                        .update("TrangThai", "Trống")
                                                        .addOnSuccessListener(aVoid1 -> {
                                                            if (!isActive) return;
                                                            sendNotification(findBookingById(maDon).maKH, maDon, "Đơn đặt phòng " + maDon + " đã được cập nhật!");
                                                            Toast.makeText(this, "Cập nhật đơn thành công!", Toast.LENGTH_SHORT).show();
                                                            loadBookingsForAllFragments();
                                                        });
                                            });
                                });
                            } else {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("TGCheckin", formattedCheckIn);
                                updates.put("TGCheckout", formattedCheckOut);
                                updates.put("GhiChu", newGhiChu);
                                db.collection("bookings").document(maDon).update(updates)
                                        .addOnSuccessListener(aVoid -> {
                                            if (!isActive) return;
                                            sendNotification(findBookingById(maDon).maKH, maDon, "Đơn đặt phòng " + maDon + " đã được cập nhật!");
                                            Toast.makeText(this, "Cập nhật đơn thành công!", Toast.LENGTH_SHORT).show();
                                            loadBookingsForAllFragments();
                                        });
                            }
                        } catch (ParseException e) {
                            Toast.makeText(this, "Lỗi định dạng thời gian!", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi xử lý chỉnh sửa!", Toast.LENGTH_LONG).show();
        }
    }

    private void showDateTimePickerDialog(EditText editText, boolean isCheckIn) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            new TimePickerDialog(this, (timeView, hour, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour);
                calendar.set(Calendar.MINUTE, minute);
                editText.setText(displayDateTimeFormat.format(calendar.getTime()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String generateRandomPass() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    private void sendNotification(String userId, String bookingId, String message) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", userId);
        notification.put("bookingId", bookingId);
        notification.put("message", message);
        notification.put("timestamp", dateTimeFormat.format(new Date()));
        notification.put("isRead", false);
        db.collection("notifications").add(notification);
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
        loadBookingsForAllFragments();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }

    static class BookingModel {
        String maDon, maKH, roomName, loaiPhong, customerName, cmnd, phoneNumber, tgDat, tgCheckin, tgCheckout, trangThaiDD, trangThaiTT, ghiChu, maKhuyenMai, tongTien;
        int maPhong;

        BookingModel(String maDon, String maKH, int maPhong, String roomName, String loaiPhong, String customerName,
                     String cmnd, String phoneNumber, String tgDat, String tgCheckin, String tgCheckout,
                     String trangThaiDD, String trangThaiTT, String ghiChu, String maKhuyenMai, String tongTien) {
            this.maDon = maDon;
            this.maKH = maKH;
            this.maPhong = maPhong;
            this.roomName = roomName;
            this.loaiPhong = loaiPhong;
            this.customerName = customerName;
            this.cmnd = cmnd;
            this.phoneNumber = phoneNumber;
            this.tgDat = tgDat;
            this.tgCheckin = tgCheckin;
            this.tgCheckout = tgCheckout;
            this.trangThaiDD = trangThaiDD;
            this.trangThaiTT = trangThaiTT;
            this.ghiChu = ghiChu;
            this.maKhuyenMai = maKhuyenMai;
            this.tongTien = tongTien;
        }
    }

    private class BookingPagerAdapter extends FragmentStateAdapter {
        BookingPagerAdapter(FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new BookingFragment("pending");
                case 1: return new BookingFragment("checked_in");
                case 2: return new BookingFragment("cancelled");
                default: return new BookingFragment("");
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {
        private final List<BookingModel> bookings;

        BookingAdapter(List<BookingModel> bookings) {
            this.bookings = bookings;
        }

        @NonNull
        @Override
        public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_booking, parent, false);
            return new BookingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
            BookingModel booking = bookings.get(position);
            holder.tvBookingId.setText("Mã đơn: " + booking.maDon);
            holder.tvCustomerName.setText("Khách hàng: " + (booking.customerName != null ? booking.customerName : "N/A"));
            holder.tvPhoneNumber.setText("Số điện thoại: " + (booking.phoneNumber != null ? booking.phoneNumber : "N/A"));
            holder.tvRoomName.setText("Phòng: " + (booking.roomName != null ? booking.roomName + " (" + booking.loaiPhong + ")" : "N/A"));
            holder.tvDateRange.setText("Ngày nhận - trả: " + (booking.tgCheckin != null && booking.tgCheckout != null ? formatDateTime(booking.tgCheckin) + " - " + formatDateTime(booking.tgCheckout) : "N/A"));
            holder.tvStatusDD.setText("Trạng thái: " + (booking.trangThaiDD != null ? booking.trangThaiDD : "N/A"));
            holder.tvGhiChu.setText("Ghi chú: " + (booking.ghiChu != null ? booking.ghiChu : "Không có"));

            int statusColor;
            switch (booking.trangThaiDD != null ? booking.trangThaiDD : "") {
                case "Đang xử lý":
                case "Đã xác nhận": statusColor = getResources().getColor(android.R.color.holo_orange_dark); break;
                case "Đã nhận phòng":
                case "Trả phòng sớm": statusColor = getResources().getColor(android.R.color.holo_green_dark); break;
                case "Đã trả phòng":
                case "Đã hủy": statusColor = getResources().getColor(android.R.color.holo_red_dark); break;
                default: statusColor = getResources().getColor(android.R.color.black);
            }
            holder.tvStatusIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(statusColor));

            // Kiểm tra yêu cầu hủy dựa trên ghiChu
            boolean hasCancelRequest = booking.ghiChu != null && booking.ghiChu.contains("Khách hàng yêu cầu hủy");
            holder.tvCancelRequest.setVisibility(hasCancelRequest ? View.VISIBLE : View.GONE);
            holder.tvCancelRequest.setText(hasCancelRequest ? "⚠️ Yêu cầu hủy: " + booking.ghiChu : "");

            holder.btnConfirm.setVisibility("Đang xử lý".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnReject.setVisibility("Đang xử lý".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnCheckIn.setVisibility("Đã xác nhận".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnCheckOut.setVisibility("Đã nhận phòng".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnEarlyCheckOut.setVisibility("Trả phòng sớm".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnAcceptCancel.setVisibility(hasCancelRequest && ("Đang xử lý".equals(booking.trangThaiDD) || "Đã xác nhận".equals(booking.trangThaiDD)) ? View.VISIBLE : View.GONE);
            holder.btnRejectCancel.setVisibility(hasCancelRequest && ("Đang xử lý".equals(booking.trangThaiDD) || "Đã xác nhận".equals(booking.trangThaiDD)) ? View.VISIBLE : View.GONE);
            holder.btnDeleteBooking.setVisibility("Đã hủy".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnEditBooking.setVisibility("Đang xử lý".equals(booking.trangThaiDD) || "Đã xác nhận".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnDetails.setVisibility(View.VISIBLE);

            holder.btnConfirm.setOnClickListener(v -> handleConfirm(booking.maDon, booking.maKH, booking.roomName));
            holder.btnReject.setOnClickListener(v -> handleReject(booking.maDon, booking.maKH, booking.maPhong));
            holder.btnCheckIn.setOnClickListener(v -> handleCheckIn(booking.maDon, booking.maPhong, booking.maKH, booking.roomName));
            holder.btnCheckOut.setOnClickListener(v -> handleCheckOut(booking.maDon, booking.maPhong, false));
            holder.btnEarlyCheckOut.setOnClickListener(v -> handleCheckOut(booking.maDon, booking.maPhong, true));
            holder.btnAcceptCancel.setOnClickListener(v -> handleAcceptCancel(booking.maDon, booking.maPhong, booking.maKH));
            holder.btnRejectCancel.setOnClickListener(v -> handleRejectCancel(booking.maDon, booking.maKH));
            holder.btnDeleteBooking.setOnClickListener(v -> handleDeleteBooking(booking.maDon));
            holder.btnEditBooking.setOnClickListener(v -> handleEditBooking(booking.maDon, booking.tgCheckin, booking.tgCheckout, booking.maPhong, booking.ghiChu));
            holder.btnDetails.setOnClickListener(v -> showBookingDetails(booking));
        }

        @Override
        public int getItemCount() {
            return bookings.size();
        }

        class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView tvBookingId, tvCustomerName, tvPhoneNumber, tvRoomName, tvDateRange, tvStatusDD, tvCancelRequest, tvGhiChu;
            TextView tvStatusIndicator;
            Button btnConfirm, btnReject, btnCheckIn, btnCheckOut, btnEarlyCheckOut, btnAcceptCancel, btnRejectCancel, btnDeleteBooking, btnEditBooking, btnDetails;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvStatusIndicator = itemView.findViewById(R.id.tvStatusIndicator);
                tvBookingId = itemView.findViewById(R.id.tvBookingId);
                tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
                tvPhoneNumber = itemView.findViewById(R.id.tvPhoneNumber);
                tvRoomName = itemView.findViewById(R.id.tvRoomName);
                tvDateRange = itemView.findViewById(R.id.tvDateRange);
                tvStatusDD = itemView.findViewById(R.id.tvStatusDD);
                tvCancelRequest = itemView.findViewById(R.id.tvCancelRequest);
                tvGhiChu = itemView.findViewById(R.id.tvGhiChu);
                btnConfirm = itemView.findViewById(R.id.btnConfirm);
                btnReject = itemView.findViewById(R.id.btnReject);
                btnCheckIn = itemView.findViewById(R.id.btnCheckIn);
                btnCheckOut = itemView.findViewById(R.id.btnCheckOut);
                btnEarlyCheckOut = itemView.findViewById(R.id.btnEarlyCheckOut);
                btnAcceptCancel = itemView.findViewById(R.id.btnAcceptCancel);
                btnRejectCancel = itemView.findViewById(R.id.btnRejectCancel);
                btnDeleteBooking = itemView.findViewById(R.id.btnDeleteBooking);
                btnEditBooking = itemView.findViewById(R.id.btnEditBooking);
                btnDetails = itemView.findViewById(R.id.btnDetails);
            }
        }
    }
}