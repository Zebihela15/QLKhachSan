package com.sinhvien.appqlkhachsan;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

public class BookingManagementActivity extends AppCompatActivity {
    private static final String TAG = "BookingManagement";
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private RecyclerView recyclerViewBookings;
    private LinearLayout layoutEmptyState;
    private MaterialButton btnSort;
    private TextInputEditText editTextSearch;
    private ChipGroup chipGroupFilters;
    private Chip chipAll, chipPending, chipConfirmed, chipCheckedIn, chipCompleted, chipCancelled;
    private TextView tvResultsCount;
    private ExtendedFloatingActionButton fabAddBooking;
    private MaterialToolbar toolbar;
    private List<Booking> bookingList;
    private BookingAdapter bookingAdapter;
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final String[] possibleDateFormats = {
            "yyyy-MM-dd HH:mm:ss",
            "dd/MM/yyyy",
            "yyyy-MM-dd"
    };
    private final List<String> validStatuses = List.of("Chờ xác nhận", "Đã xác nhận", "Đã nhận phòng", "Hoàn thành", "Đã hủy");
    private String currentSortField = "TGDat";
    private Query.Direction currentSortDirection = Query.Direction.DESCENDING;
    private static final int REQUEST_WRITE_STORAGE = 100;
    private ListenerRegistration bookingsListener;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_management);

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize views
        recyclerViewBookings = findViewById(R.id.recyclerViewBookings);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnSort = findViewById(R.id.btnSort);
        editTextSearch = findViewById(R.id.editTextSearch);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        chipAll = findViewById(R.id.chipAll);
        chipPending = findViewById(R.id.chipPending);
        chipConfirmed = findViewById(R.id.chipConfirmed);
        chipCheckedIn = findViewById(R.id.chipCheckedIn);
        chipCompleted = findViewById(R.id.chipCompleted);
        chipCancelled = findViewById(R.id.chipCancelled);
        tvResultsCount = findViewById(R.id.tvResultsCount);

        toolbar = findViewById(R.id.toolbar);

        // Set up toolbar
        setSupportActionBar(toolbar);

        // Initialize RecyclerView
        bookingList = new ArrayList<>();
        recyclerViewBookings.setLayoutManager(new LinearLayoutManager(this));
        bookingAdapter = new BookingAdapter(bookingList, this);
        recyclerViewBookings.setAdapter(bookingAdapter);

        // Set up event listeners
        setupEventListeners();

        // Set up real-time listener
        setupRealtimeListener();

        // Check for auto check-in/check-out
        checkAutoCheckInOut();
    }

    private String parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            Log.w(TAG, "Date string is null or empty");
            return "N/A";
        }
        for (String format : possibleDateFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                return displayFormat.format(sdf.parse(dateStr));
            } catch (ParseException e) {
                Log.d(TAG, "Failed to parse date with format " + format + ": " + dateStr);
            }
        }
        Log.e(TAG, "All date formats failed for: " + dateStr);
        return "N/A";
    }

    private Date parseDateToDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            Log.w(TAG, "Date string is null or empty");
            showSnackbar("Ngày không hợp lệ: " + dateStr);
            return new Date();
        }
        for (String format : possibleDateFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                Date parsedDate = sdf.parse(dateStr);
                Log.d(TAG, "Successfully parsed date: " + dateStr + " with format: " + format);
                return parsedDate;
            } catch (ParseException e) {
                Log.d(TAG, "Failed to parse date with format " + format + ": " + dateStr);
            }
        }
        Log.e(TAG, "All date formats failed for: " + dateStr);
        showSnackbar("Lỗi định dạng ngày: " + dateStr);
        return new Date();
    }

    private void setupEventListeners() {
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                loadBookings(s.toString(), getSelectedStatus(), currentSortField, currentSortDirection);
            }
        });

        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
        });

        btnSort.setOnClickListener(v -> showSortDialog());
    }

    private String getSelectedStatus() {
        int checkedId = chipGroupFilters.getCheckedChipId();
        if (checkedId == R.id.chipAll) return "Tất cả";
        if (checkedId == R.id.chipPending) return "Chờ xác nhận";
        if (checkedId == R.id.chipConfirmed) return "Đã xác nhận";
        if (checkedId == R.id.chipCheckedIn) return "Đã nhận phòng";
        if (checkedId == R.id.chipCompleted) return "Hoàn thành";
        if (checkedId == R.id.chipCancelled) return "Đã hủy";
        return "Tất cả";
    }

    private void showSortDialog() {
        String[] sortOptions = {"Ngày đặt (mới nhất)", "Ngày đặt (cũ nhất)", "Tổng giá (cao đến thấp)", "Tổng giá (thấp đến cao)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sắp xếp theo");
        builder.setItems(sortOptions, (dialog, which) -> {
            switch (which) {
                case 0:
                    currentSortField = "TGDat";
                    currentSortDirection = Query.Direction.DESCENDING;
                    break;
                case 1:
                    currentSortField = "TGDat";
                    currentSortDirection = Query.Direction.ASCENDING;
                    break;
                case 2:
                    currentSortField = "TongGia";
                    currentSortDirection = Query.Direction.DESCENDING;
                    break;
                case 3:
                    currentSortField = "TongGia";
                    currentSortDirection = Query.Direction.ASCENDING;
                    break;
            }
            loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void setupRealtimeListener() {
        Query baseQuery = firestore.collection("bookings").orderBy(currentSortField, currentSortDirection);
        if (bookingsListener != null) {
            bookingsListener.remove();
        }
        bookingsListener = baseQuery.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error listening for bookings: " + error.getMessage());
                showSnackbar("Lỗi tải danh sách đơn đặt phòng!");
                updateUIWithNoBookings("Lỗi tải danh sách đơn đặt phòng!");
                return;
            }
            if (querySnapshot != null) {
                loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
            }
        });
    }

    private void checkAutoCheckInOut() {
        // Auto check-in
        firestore.collection("bookings")
                .whereEqualTo("TrangThaiDD", "Đã xác nhận")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String checkIn = doc.getString("TGCheckin");
                        Date checkInDate = parseDateToDate(checkIn);
                        Date currentDate = new Date();
                        if (checkInDate != null && !currentDate.before(checkInDate)) {
                            String invoiceId = doc.getString("MaDon");
                            int roomId = doc.getLong("MaPhong").intValue();
                            showAutoCheckInDialog(doc.getId(), invoiceId, roomId);
                            Log.d(TAG, "Auto check-in triggered for booking: " + doc.getId());
                        }
                    }
                });

        // Auto check-out
        firestore.collection("bookings")
                .whereEqualTo("TrangThaiDD", "Đã nhận phòng")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String checkOut = doc.getString("TGCheckout");
                        Date checkOutDate = parseDateToDate(checkOut);
                        Date currentDate = new Date();
                        if (checkOutDate != null && !currentDate.before(checkOutDate)) {
                            String invoiceId = doc.getString("MaDon");
                            int roomId = doc.getLong("MaPhong").intValue();
                            showAutoCheckOutDialog(doc.getId(), invoiceId, roomId);
                            Log.d(TAG, "Auto check-out triggered for booking: " + doc.getId());
                        }
                    }
                });
    }

    private void showAutoCheckInDialog(String maDon, String invoiceId, int roomId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Xác nhận Check-in Tự động");
        builder.setMessage("Đơn đặt phòng " + maDon + " đã đến ngày check-in. Xác nhận check-in?");
        builder.setPositiveButton("Xác nhận", (dialog, which) -> updateBookingStatus(invoiceId, roomId, "Đã nhận phòng"));
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showAutoCheckOutDialog(String maDon, String invoiceId, int roomId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Xác nhận Check-out Tự động");
        builder.setMessage("Đơn đặt phòng " + maDon + " đã đến ngày check-out. Xác nhận check-out?");
        builder.setPositiveButton("Xác nhận", (dialog, which) -> updateBookingStatus(invoiceId, roomId, "Hoàn thành"));
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void loadBookings(String query, String status, String sortField, Query.Direction sortDirection) {
        if (isLoading) return;
        isLoading = true;
        Log.d(TAG, "Loading bookings - query: " + query + ", status: " + status +
                ", sortField: " + sortField + ", sortDirection: " + sortDirection);
        Query firestoreQuery = firestore.collection("bookings").orderBy(sortField, sortDirection);

        if (status != null && !status.equals("Tất cả")) {
            if (!validStatuses.contains(status)) {
                showToast("Trạng thái không hợp lệ!");
                isLoading = false;
                return;
            }
            firestoreQuery = firestoreQuery.whereEqualTo("TrangThaiDD", status);
        }

        executeQuery(firestoreQuery, query);
    }

    private void executeQuery(Query firestoreQuery, String searchQuery) {
        String query = searchQuery != null ? searchQuery.toLowerCase(Locale.getDefault()) : "";
        firestoreQuery.get().addOnSuccessListener(querySnapshot -> {
            Log.d(TAG, "Found " + querySnapshot.size() + " bookings");
            bookingList.clear();
            Map<String, Booking> uniqueBookings = new HashMap<>();

            for (QueryDocumentSnapshot doc : querySnapshot) {
                String maDon = doc.getId();
                if (uniqueBookings.containsKey(maDon)) {
                    continue;
                }

                String maKH = doc.getString("MaKH");
                Integer roomId = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
                String checkIn = doc.getString("TGCheckin");
                String checkOut = doc.getString("TGCheckout");
                String statusDD = doc.getString("TrangThaiDD");
                String specialRequest = doc.getString("YeuCauDacBiet");
                Integer guestCount = doc.getLong("SoKhach") != null ? doc.getLong("SoKhach").intValue() : 0;
                String voucherCode = doc.getString("MaGiam");
                String bookingDateStr = doc.getString("TGDat");

                if (!validStatuses.contains(statusDD)) {
                    Log.e(TAG, "Invalid status for booking " + maDon + ": " + statusDD);
                    firestore.collection("bookings").document(maDon).update("TrangThaiDD", "Đã hủy")
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Fixed invalid status for booking: " + maDon));
                    continue;
                }

                Date checkInDate = parseDateToDate(checkIn);
                Date checkOutDate = parseDateToDate(checkOut);
                Date bookingDate = parseDateToDate(bookingDateStr);
                Date currentDate = new Date();

                if (bookingDate == null || checkInDate == null || checkOutDate == null) {
                    Log.e(TAG, "Invalid dates for booking " + maDon + ": bookingDate=" + bookingDateStr + ", checkIn=" + checkIn + ", checkOut=" + checkOut);
                    showSnackbar("Ngày không hợp lệ cho đơn " + maDon);
                    continue;
                }
                if (checkInDate.before(bookingDate)) {
                    Log.e(TAG, "Check-in date is before booking date for booking " + maDon);
                    showSnackbar("Ngày nhận phòng không thể trước ngày đặt: " + maDon);
                    continue;
                }

                firestore.collection("customers").document(maKH).get()
                        .addOnSuccessListener(customerDoc -> {
                            if (!customerDoc.exists()) {
                                Log.e(TAG, "Customer not found for MaKH: " + maKH);
                                showSnackbar("Không tìm thấy khách hàng: " + maKH);
                                return;
                            }

                            firestore.collection("rooms").document(String.valueOf(roomId)).get()
                                    .addOnSuccessListener(roomDoc -> {
                                        if (!roomDoc.exists()) {
                                            Log.e(TAG, "Room not found for MaPhong: " + roomId);
                                            showSnackbar("Phòng không tồn tại: " + roomId);
                                            return;
                                        }
                                        Long maxGuests = roomDoc.getLong("SoLuongNguoiToiDa");
                                        if (maxGuests != null && guestCount > maxGuests) {
                                            Log.e(TAG, "Guest count exceeds room capacity for MaPhong: " + roomId);
                                            return;
                                        }

                                        firestore.collection("bookings")
                                                .whereEqualTo("MaPhong", roomId)
                                                .whereIn("TrangThaiDD", List.of("Đã xác nhận", "Đã nhận phòng"))
                                                .get()
                                                .addOnSuccessListener(overlapSnapshot -> {
                                                    boolean hasOverlap = false;
                                                    for (QueryDocumentSnapshot overlapDoc : overlapSnapshot) {
                                                        if (overlapDoc.getId().equals(maDon)) continue;
                                                        String otherCheckIn = overlapDoc.getString("TGCheckin");
                                                        String otherCheckOut = overlapDoc.getString("TGCheckout");
                                                        Date otherCheckInDate = parseDateToDate(otherCheckIn);
                                                        Date otherCheckOutDate = parseDateToDate(otherCheckOut);
                                                        if (otherCheckInDate != null && otherCheckOutDate != null &&
                                                                !(checkOutDate.before(otherCheckInDate) || checkInDate.after(otherCheckOutDate))) {
                                                            hasOverlap = true;
                                                            break;
                                                        }
                                                    }
                                                    if (hasOverlap) {
                                                        Log.e(TAG, "Overlapping booking detected for MaPhong: " + roomId);
                                                        showSnackbar("Phát hiện trùng lịch đặt phòng cho phòng: " + roomId);
                                                        return;
                                                    }

                                                    if (voucherCode != null) {
                                                        firestore.collection("vouchers").document(voucherCode).get()
                                                                .addOnSuccessListener(voucherDoc -> {
                                                                    if (!voucherDoc.exists() || !voucherDoc.getBoolean("Valid")) {
                                                                        Log.e(TAG, "Invalid or expired voucher: " + voucherCode);
                                                                        showSnackbar("Mã giảm giá không hợp lệ: " + voucherCode);
                                                                        return;
                                                                    }
                                                                    processBookingData(doc, uniqueBookings, query, roomDoc, customerDoc, voucherCode);
                                                                })
                                                                .addOnFailureListener(e -> {
                                                                    Log.e(TAG, "Error validating voucher: " + e.getMessage());
                                                                    showSnackbar("Lỗi kiểm tra mã giảm giá: " + e.getMessage());
                                                                    processBookingData(doc, uniqueBookings, query, roomDoc, customerDoc, null);
                                                                });
                                                    } else {
                                                        processBookingData(doc, uniqueBookings, query, roomDoc, customerDoc, null);
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "Error checking overlapping bookings: " + e.getMessage());
                                                    showSnackbar("Lỗi kiểm tra lịch đặt phòng: " + e.getMessage());
                                                });
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error fetching room: " + e.getMessage());
                                        showSnackbar("Lỗi truy xuất phòng: " + e.getMessage());
                                    });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error fetching customer: " + e.getMessage());
                            showSnackbar("Lỗi truy xuất khách hàng: " + e.getMessage());
                        });
            }

            if (querySnapshot.isEmpty()) {
                updateUIWithNoBookings("Không có đơn đặt phòng nào!");
            }
            isLoading = false;
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error loading bookings: " + e.getMessage());
            showSnackbar("Lỗi tải danh sách đơn đặt phòng!");
            updateUIWithNoBookings("Lỗi tải danh sách đơn đặt phòng!");
            isLoading = false;
        });
    }

    private void processBookingData(QueryDocumentSnapshot doc, Map<String, Booking> uniqueBookings, String query,
                                    DocumentSnapshot roomDoc, DocumentSnapshot customerDoc, String voucherCode) {
        String maDon = doc.getId();
        String maKH = doc.getString("MaKH");
        Integer roomId = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
        String checkIn = doc.getString("TGCheckin");
        String checkOut = doc.getString("TGCheckout");
        String statusDD = doc.getString("TrangThaiDD");
        String specialRequest = doc.getString("YeuCauDacBiet");
        Integer guestCount = doc.getLong("SoKhach") != null ? doc.getLong("SoKhach").intValue() : 0;

        String formattedCheckIn = parseDate(checkIn);
        String formattedCheckOut = parseDate(checkOut);

        firestore.collection("invoices").whereEqualTo("MaDon", maDon).get()
                .addOnSuccessListener(invoiceSnapshot -> {
                    String invoiceId = "N/A";
                    String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";
                    String phone = customerDoc.getString("SDT") != null ? customerDoc.getString("SDT") : "N/A";
                    String cmnd = customerDoc.getString("CCCD") != null ? customerDoc.getString("CCCD") : "N/A";
                    String email = customerDoc.getString("Email") != null ? customerDoc.getString("Email") : "N/A";
                    double totalPrice = 0.0;
                    String invoiceStatus = statusDD;
                    String reasonCancel = null;

                    if (!invoiceSnapshot.isEmpty()) {
                        DocumentSnapshot invoiceDoc = invoiceSnapshot.getDocuments().get(0);
                        invoiceId = invoiceDoc.getId();
                        customerName = invoiceDoc.getString("TenKhach") != null ? invoiceDoc.getString("TenKhach") : customerName;
                        phone = invoiceDoc.getString("SoDienThoai") != null ? invoiceDoc.getString("SoDienThoai") : phone;
                        cmnd = invoiceDoc.getString("CCCD") != null ? invoiceDoc.getString("CCCD") : cmnd;
                        email = invoiceDoc.getString("Email") != null ? invoiceDoc.getString("Email") : email;
                        totalPrice = invoiceDoc.getDouble("TongGia") != null ? invoiceDoc.getDouble("TongGia") : 0.0;
                        invoiceStatus = invoiceDoc.getString("TrangThai") != null ? invoiceDoc.getString("TrangThai") : statusDD;
                        reasonCancel = invoiceDoc.getString("LyDoHuy");
                    }

                    String finalStatus = invoiceSnapshot.isEmpty() ? statusDD : invoiceStatus;
                    if (!validStatuses.contains(finalStatus)) {
                        Log.e(TAG, "Invalid status for booking " + maDon + ": " + finalStatus);
                        showSnackbar("Trạng thái không hợp lệ: " + finalStatus);
                        return;
                    }

                    String roomName = roomDoc.getString("TenPhong") != null ? roomDoc.getString("TenPhong") : "Phòng " + roomId;
                    String roomType = roomDoc.getString("LoaiPhong") != null ? roomDoc.getString("LoaiPhong") : "N/A";

                    if (query.isEmpty() ||
                            (customerName != null && customerName.toLowerCase(Locale.getDefault()).contains(query)) ||
                            String.valueOf(roomId).contains(query) ||
                            invoiceId.toLowerCase(Locale.getDefault()).contains(query) ||
                            (phone != null && phone.contains(query))) {
                        Booking booking = new Booking(invoiceId, maDon, customerName, phone, roomId, roomName,
                                formattedCheckIn, formattedCheckOut, totalPrice, finalStatus, specialRequest,
                                guestCount, voucherCode, roomType, reasonCancel, cmnd, email);
                        uniqueBookings.put(maDon, booking);
                        updateBookingList(uniqueBookings);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching invoice for MaDon " + maDon + ": " + e.getMessage());
                    String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";
                    String phone = customerDoc.getString("SDT") != null ? customerDoc.getString("SDT") : "N/A";
                    String cmnd = customerDoc.getString("CCCD") != null ? customerDoc.getString("CCCD") : "N/A";
                    String email = customerDoc.getString("Email") != null ? customerDoc.getString("Email") : "N/A";
                    String roomName = roomDoc.getString("TenPhong") != null ? roomDoc.getString("TenPhong") : "Phòng " + roomId;
                    String roomType = roomDoc.getString("LoaiPhong") != null ? roomDoc.getString("LoaiPhong") : "N/A";

                    if (query.isEmpty() ||
                            (customerName != null && customerName.toLowerCase(Locale.getDefault()).contains(query)) ||
                            String.valueOf(roomId).contains(query) ||
                            (phone != null && phone.contains(query))) {
                        Booking booking = new Booking("N/A", maDon, customerName, phone, roomId, roomName,
                                formattedCheckIn, formattedCheckOut, 0.0, statusDD, specialRequest,
                                guestCount, voucherCode, roomType, null, cmnd, email);
                        uniqueBookings.put(maDon, booking);
                        updateBookingList(uniqueBookings);
                    }
                });
    }

    private void updateBookingList(Map<String, Booking> uniqueBookings) {
        runOnUiThread(() -> {
            bookingList.clear();
            bookingList.addAll(uniqueBookings.values());
            Log.d(TAG, "Updating UI with " + bookingList.size() + " bookings");
            for (Booking booking : bookingList) {
                Log.d(TAG, "Displaying booking: " + booking.bookingId + ", status: " + booking.status);
            }
            tvResultsCount.setText("Hiển thị " + bookingList.size() + " đơn đặt phòng");
            if (bookingList.isEmpty()) {
                recyclerViewBookings.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            } else {
                recyclerViewBookings.setVisibility(View.VISIBLE);
                layoutEmptyState.setVisibility(View.GONE);
                bookingAdapter.notifyDataSetChanged();
            }
        });
    }

    private void updateUIWithNoBookings(String message) {
        runOnUiThread(() -> {
            recyclerViewBookings.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            tvResultsCount.setText("Hiển thị 0 đơn đặt phòng");
            showSnackbar(message);
            bookingAdapter.notifyDataSetChanged();
        });
    }

    private void showSnackbar(String message) {
        Log.d(TAG, "Displaying Snackbar: " + message);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                Log.e(TAG, "Cannot display Snackbar: Activity is finishing or destroyed");
                return;
            }
            try {
                Snackbar.make(recyclerViewBookings, message, Snackbar.LENGTH_LONG).show();
                Log.d(TAG, "Snackbar displayed successfully: " + message);
            } catch (Exception e) {
                Log.e(TAG, "Error displaying Snackbar: " + e.getMessage());
            }
        });
    }

    void showToast(String message) {
        runOnUiThread(() -> {
            Toast toast = Toast.makeText(BookingManagementActivity.this, message, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    void updateBookingStatus(String invoiceId, int roomId, String newStatus) {
        if (!validStatuses.contains(newStatus)) {
            Log.e(TAG, "Invalid status: " + newStatus);
            showSnackbar("Trạng thái không hợp lệ: " + newStatus);
            return;
        }

        if (newStatus.equals("Đã xác nhận")) {
            firestore.collection("rooms").document(String.valueOf(roomId)).get()
                    .addOnSuccessListener(roomDoc -> {
                        if (!roomDoc.exists()) {
                            Log.e(TAG, "Room does not exist: " + roomId);
                            showSnackbar("Phòng không tồn tại!");
                            return;
                        }
                        String roomStatus = roomDoc.getString("TrangThai");
                        if (!"Trống".equals(roomStatus)) {
                            Log.e(TAG, "Room is not available: " + roomStatus);
                            showSnackbar("Phòng không trống để xác nhận!");
                            return;
                        }
                        proceedUpdateBookingStatus(invoiceId, roomId, newStatus);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking room status: " + e.getMessage());
                        showSnackbar("Lỗi kiểm tra trạng thái phòng: " + e.getMessage());
                    });
        } else if (newStatus.equals("Đã nhận phòng")) {
            firestore.collection("invoices").document(invoiceId).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            Log.e(TAG, "Invoice does not exist: " + invoiceId);
                            showSnackbar("Hóa đơn không tồn tại!");
                            return;
                        }
                        String maDon = doc.getString("MaDon");
                        if (maDon == null) {
                            Log.e(TAG, "MaDon is null for invoice: " + invoiceId);
                            showSnackbar("Không tìm thấy mã đơn đặt phòng!");
                            return;
                        }
                        firestore.collection("bookings").document(maDon).get()
                                .addOnSuccessListener(bookingDoc -> {
                                    if (!bookingDoc.exists()) {
                                        Log.e(TAG, "Booking does not exist: " + maDon);
                                        showSnackbar("Đơn đặt phòng không tồn tại!");
                                        return;
                                    }
                                    String checkIn = bookingDoc.getString("TGCheckin");
                                    Date checkInDate = parseDateToDate(checkIn);
                                    Date currentDate = new Date();
                                    Calendar calendar = Calendar.getInstance();
                                    if (checkInDate != null) {
                                        calendar.setTime(checkInDate);
                                        calendar.add(Calendar.DAY_OF_MONTH, -3);
                                    }
                                    Date earlyCheckInDate = checkInDate != null ? calendar.getTime() : null;
                                    Log.d(TAG, "Comparing dates: currentDate=" + displayFormat.format(currentDate) +
                                            ", checkInDate=" + (checkInDate != null ? displayFormat.format(checkInDate) : "null") +
                                            ", earlyCheckInDate=" + (earlyCheckInDate != null ? displayFormat.format(earlyCheckInDate) : "null"));
                                    if (checkInDate == null || earlyCheckInDate == null) {
                                        Log.e(TAG, "Invalid check-in date for booking: " + maDon + ", checkIn=" + checkIn);
                                        showSnackbar("Ngày nhận phòng không hợp lệ: " + checkIn);
                                        return;
                                    }
                                    if (currentDate.before(earlyCheckInDate)) {
                                        Log.e(TAG, "Check-in date is too early: " + checkIn);
                                        showSnackbar("Chưa đến thời gian nhận phòng! Sớm nhất: " + displayFormat.format(earlyCheckInDate));
                                        return;
                                    }
                                    proceedUpdateBookingStatus(invoiceId, roomId, newStatus);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error checking check-in date: " + e.getMessage());
                                    showSnackbar("Lỗi kiểm tra ngày nhận phòng: " + e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching invoice: " + e.getMessage());
                        showSnackbar("Lỗi truy xuất hóa đơn: " + e.getMessage());
                    });

        } else if (newStatus.equals("Hoàn thành")) {
            firestore.collection("invoices").document(invoiceId).get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) {
                            Log.e(TAG, "Invoice does not exist: " + invoiceId);
                            showSnackbar("Hóa đơn không tồn tại!");
                            return;
                        }
                        String maDon = doc.getString("MaDon");
                        if (maDon == null) {
                            Log.e(TAG, "MaDon is null for invoice: " + invoiceId);
                            showSnackbar("Không tìm thấy mã đơn đặt phòng!");
                            return;
                        }
                        firestore.collection("bookings").document(maDon).get()
                                .addOnSuccessListener(bookingDoc -> {
                                    if (!bookingDoc.exists()) {
                                        Log.e(TAG, "Booking does not exist: " + maDon);
                                        showSnackbar("Đơn đặt phòng không tồn tại!");
                                        return;
                                    }
                                    String checkOut = bookingDoc.getString("TGCheckout");
                                    Date checkOutDate = parseDateToDate(checkOut);
                                    Date currentDate = new Date();
                                    if (checkOutDate == null) {
                                        Log.e(TAG, "Invalid check-out date for booking: " + maDon);
                                        showSnackbar("Ngày trả phòng không hợp lệ: " + checkOut);
                                        return;
                                    }
                                    if (currentDate.before(checkOutDate)) {
                                        Log.d(TAG, "Check-out date is in the future: " + checkOut);
                                        showCheckOutConfirmationDialog(invoiceId, roomId, maDon, bookingDoc, doc, checkOutDate, currentDate);
                                    } else {
                                        performCheckOutValidations(invoiceId, roomId, maDon, bookingDoc, doc, checkOutDate, currentDate);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error checking check-out date: " + e.getMessage());
                                    showSnackbar("Lỗi kiểm tra ngày trả phòng: " + e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching invoice: " + e.getMessage());
                        showSnackbar("Lỗi truy xuất hóa đơn: " + e.getMessage());
                    });
        } else {
            proceedUpdateBookingStatus(invoiceId, roomId, newStatus);
        }
    }

    private void showCheckOutConfirmationDialog(String invoiceId, int roomId, String maDon, DocumentSnapshot bookingDoc, DocumentSnapshot invoiceDoc, Date checkOutDate, Date currentDate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Xác nhận check-out sớm");
        builder.setMessage("Ngày trả phòng dự kiến là " + displayFormat.format(checkOutDate) + ". Bạn có muốn thực hiện check-out sớm vào hôm nay (" + displayFormat.format(currentDate) + ")?");
        builder.setPositiveButton("Xác nhận", (dialog, which) -> performCheckOutValidations(invoiceId, roomId, maDon, bookingDoc, invoiceDoc, checkOutDate, currentDate));
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void performCheckOutValidations(String invoiceId, int roomId, String maDon, DocumentSnapshot bookingDoc, DocumentSnapshot invoiceDoc, Date checkOutDate, Date currentDate) {
        firestore.collection("customers").document(bookingDoc.getString("MaKH")).get()
                .addOnSuccessListener(customerDoc -> {
                    if (!customerDoc.exists()) {
                        Log.e(TAG, "Customer not found for MaKH: " + bookingDoc.getString("MaKH"));
                        showSnackbar("Không tìm thấy thông tin khách hàng!");
                        return;
                    }
                    String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";
                    String cmnd = customerDoc.getString("CCCD") != null ? customerDoc.getString("CCCD") : "N/A";
                    String checkIn = bookingDoc.getString("TGCheckin");
                    Date checkInDate = parseDateToDate(checkIn);
                    if (checkInDate == null) {
                        Log.e(TAG, "Invalid check-in date for booking: " + maDon);
                        showSnackbar("Ngày nhận phòng không hợp lệ: " + checkIn);
                        return;
                    }
                    if (checkInDate.after(currentDate)) {
                        Log.w(TAG, "Check-in date in future: " + checkIn + ", using current date");
                        checkInDate = currentDate;
                    }

                    Date finalCheckInDate = checkInDate;
                    firestore.collection("rooms").document(String.valueOf(roomId)).get()
                            .addOnSuccessListener(roomDoc -> {
                                if (!roomDoc.exists()) {
                                    Log.e(TAG, "Room not found for MaPhong: " + roomId);
                                    showSnackbar("Phòng không tồn tại!");
                                    return;
                                }
                                String roomName = roomDoc.getString("TenPhong") != null ? roomDoc.getString("TenPhong") : "Phòng " + roomId;

                                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                builder.setTitle("Xác nhận Check-out");
                                StringBuilder message = new StringBuilder();
                                message.append("Tên khách: ").append(customerName).append("\n")
                                        .append("Số CMND/CCCD: ").append(cmnd).append("\n")
                                        .append("Phòng: ").append(roomName).append("\n")
                                        .append("Ngày nhận phòng: ").append(finalCheckInDate != null ? displayFormat.format(finalCheckInDate) : "N/A").append("\n")
                                        .append("Ngày trả phòng dự kiến: ").append(displayFormat.format(checkOutDate)).append("\n")
                                        .append("Ngày trả phòng thực tế: ").append(displayFormat.format(currentDate)).append("\n\n")
                                        .append("Vui lòng xác nhận:\n")
                                        .append("- Khách đã rời phòng và không để quên tài sản.\n")
                                        .append("- Thiết bị, nội thất trong phòng không bị hư hỏng hoặc mất mát.\n")
                                        .append("- Minibar và các dịch vụ tính phí đã được ghi nhận.");
                                builder.setMessage(message.toString());

                                LinearLayout layout = new LinearLayout(this);
                                layout.setOrientation(LinearLayout.VERTICAL);
                                layout.setPadding(50, 20, 50, 20);
                                TextInputEditText inputAdditionalFees = new TextInputEditText(this);
                                inputAdditionalFees.setHint("Nhập phụ phí hoặc dịch vụ bổ sung (nếu có)");
                                layout.addView(inputAdditionalFees);
                                builder.setView(layout);

                                builder.setPositiveButton("Xác nhận Check-out", (dialog, which) -> {
                                    String additionalFees = inputAdditionalFees.getText().toString().trim();
                                    double additionalCost = 0.0;
                                    try {
                                        if (!additionalFees.isEmpty()) {
                                            additionalCost = Double.parseDouble(additionalFees.replaceAll("[^0-9.]", ""));
                                        }
                                    } catch (NumberFormatException e) {
                                        Log.e(TAG, "Invalid additional fees format: " + additionalFees);
                                        showSnackbar("Phụ phí không hợp lệ!");
                                        return;
                                    }

                                    double originalTotalPrice = invoiceDoc.getDouble("TongGia") != null ? invoiceDoc.getDouble("TongGia") : 0.0;
                                    long actualDays = calculateDaysDifference(displayFormat.format(finalCheckInDate), displayFormat.format(currentDate));
                                    long expectedDays = calculateDaysDifference(displayFormat.format(finalCheckInDate), displayFormat.format(checkOutDate));
                                    double dailyRate = expectedDays > 0 ? originalTotalPrice / expectedDays : originalTotalPrice;
                                    double updatedTotalPrice = dailyRate * actualDays + additionalCost;

                                    Map<String, Object> invoiceUpdates = new HashMap<>();
                                    invoiceUpdates.put("TongGia", updatedTotalPrice);
                                    invoiceUpdates.put("TrangThai", "Hoàn thành");
                                    invoiceUpdates.put("TGCheckout", dbDateFormat.format(currentDate));
                                    if (!additionalFees.isEmpty()) {
                                        invoiceUpdates.put("GhiChu", "Phụ phí/Dịch vụ bổ sung: " + additionalFees);
                                    }

                                    firestore.collection("invoices").document(invoiceId).update(invoiceUpdates)
                                            .addOnSuccessListener(aVoid -> {
                                                Map<String, Object> bookingUpdates = new HashMap<>();
                                                bookingUpdates.put("TrangThaiDD", "Hoàn thành");
                                                bookingUpdates.put("TGCheckout", dbDateFormat.format(currentDate));

                                                firestore.collection("bookings").document(maDon).update(bookingUpdates)
                                                        .addOnSuccessListener(aVoid1 -> {
                                                            firestore.collection("rooms").document(String.valueOf(roomId))
                                                                    .update("TrangThai", "Trống")
                                                                    .addOnSuccessListener(aVoid2 -> {
                                                                        Log.d(TAG, "Check-out completed for booking: " + maDon + ", updated total: " + updatedTotalPrice);
                                                                        showSnackbar("Check-out thành công! Tổng chi phí: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(updatedTotalPrice));
                                                                        chipCompleted.setChecked(true);
                                                                        loadBookings(editTextSearch.getText().toString(), "Hoàn thành", currentSortField, currentSortDirection);
                                                                        setupRealtimeListener();
                                                                    })
                                                                    .addOnFailureListener(e -> {
                                                                        Log.e(TAG, "Error updating room status: " + e.getMessage());
                                                                        showSnackbar("Lỗi cập nhật trạng thái phòng: " + e.getMessage());
                                                                    });
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Log.e(TAG, "Error updating booking: " + e.getMessage());
                                                            showSnackbar("Lỗi cập nhật booking: " + e.getMessage());
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Error updating invoice: " + e.getMessage());
                                                showSnackbar("Lỗi cập nhật hóa đơn: " + e.getMessage());
                                            });
                                });
                                builder.setNegativeButton("Hủy", null);
                                builder.show();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error fetching room: " + e.getMessage());
                                showSnackbar("Lỗi truy xuất thông tin phòng: " + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching customer: " + e.getMessage());
                    showSnackbar("Lỗi truy xuất thông tin khách hàng: " + e.getMessage());
                });
    }

    private void proceedUpdateBookingStatus(String invoiceId, int roomId, String newStatus) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("TrangThai", newStatus);

        firestore.collection("invoices").document(invoiceId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    firestore.collection("invoices").document(invoiceId).get()
                            .addOnSuccessListener(doc -> {
                                String maDon = doc.getString("MaDon");
                                if (maDon == null) {
                                    Log.e(TAG, "MaDon is null for invoice: " + invoiceId);
                                    showToast("Không tìm thấy mã đơn đặt phòng!");
                                    return;
                                }
                                firestore.collection("bookings").document(maDon).update("TrangThaiDD", newStatus)
                                        .addOnSuccessListener(aVoid1 -> {
                                            String roomStatus = newStatus.equals("Hoàn thành") || newStatus.equals("Đã hủy") ? "Trống" :
                                                    newStatus.equals("Đã nhận phòng") ? "Đang sử dụng" : "Đã đặt";
                                            firestore.collection("rooms").document(String.valueOf(roomId))
                                                    .update("TrangThai", roomStatus)
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        Log.d(TAG, "Updated room status to: " + roomStatus + " for room: " + roomId);
                                                        showToast("Cập nhật trạng thái thành công: " + newStatus);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.e(TAG, "Error updating room status: " + e.getMessage());
                                                        showToast("Lỗi cập nhật trạng thái phòng: " + e.getMessage());
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Error updating booking status: " + e.getMessage());
                                            showToast("Lỗi cập nhật trạng thái booking: " + e.getMessage());
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error fetching invoice: " + e.getMessage());
                                showToast("Lỗi truy xuất thông tin hóa đơn!");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating invoice status: " + e.getMessage());
                    showToast("Lỗi cập nhật trạng thái: " + newStatus + " - " + e.getMessage());
                });
    }

    void showCancelDialog(String invoiceId, int roomId, String currentStatus) {
        if (currentStatus.equals("Đã nhận phòng")) {
            showSnackbar("Vui lòng check-out trước khi hủy!");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Hủy đơn đặt phòng");
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("Nhập lý do hủy");
        builder.setView(input);
        builder.setPositiveButton("Xác nhận", (dialog, which) -> {
            String reason = input.getText().toString().trim();
            if (reason.isEmpty()) {
                showSnackbar("Vui lòng nhập lý do hủy!");
                return;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("TrangThai", "Đã hủy");
            updates.put("LyDoHuy", reason);

            firestore.collection("invoices").document(invoiceId).update(updates)
                    .addOnSuccessListener(aVoid -> {
                        firestore.collection("invoices").document(invoiceId).get()
                                .addOnSuccessListener(doc -> {
                                    String maDon = doc.getString("MaDon");
                                    if (maDon == null) {
                                        Log.e(TAG, "MaDon is null for invoice: " + invoiceId);
                                        showSnackbar("Không tìm thấy mã đơn đặt phòng!");
                                        return;
                                    }
                                    firestore.collection("bookings").document(maDon)
                                            .update("TrangThaiDD", "Đã hủy", "GhiChu", "Hủy bởi admin: " + reason)
                                            .addOnSuccessListener(aVoid1 -> {
                                                firestore.collection("rooms").document(String.valueOf(roomId))
                                                        .update("TrangThai", "Trống")
                                                        .addOnSuccessListener(aVoid2 -> {
                                                            Log.d(TAG, "Cancelled booking and set room status to Trống for room: " + roomId);
                                                            showSnackbar("Hủy đơn thành công!");
                                                        })
                                                        .addOnFailureListener(e -> {
                                                            Log.e(TAG, "Error updating room status: " + e.getMessage());
                                                            showSnackbar("Lỗi cập nhật trạng thái phòng: " + e.getMessage());
                                                        });
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Error updating booking status: " + e.getMessage());
                                                showSnackbar("Lỗi cập nhật trạng thái booking: " + e.getMessage());
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching invoice: " + e.getMessage());
                                    showSnackbar("Lỗi truy xuất thông tin hóa đơn!");
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error cancelling booking: " + e.getMessage());
                        showSnackbar("Lỗi hủy đơn: " + e.getMessage());
                    });
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    void deleteBooking(String invoiceId, String maDon) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Xóa đơn đặt phòng");
        builder.setMessage("Bạn có chắc chắn muốn xóa đơn đặt phòng " + maDon + "?");
        builder.setPositiveButton("Xóa", (dialog, which) -> {
            firestore.collection("invoices").document(invoiceId).delete()
                    .addOnSuccessListener(aVoid -> {
                        firestore.collection("bookings").document(maDon).delete()
                                .addOnSuccessListener(aVoid1 -> {
                                    Log.d(TAG, "Deleted booking and invoice: " + maDon);
                                    showSnackbar("Xóa đơn thành công!");
                                    setupRealtimeListener();
                                    loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error deleting booking: " + e.getMessage());
                                    showSnackbar("Lỗi xóa đơn đặt phòng: " + e.getMessage());
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error deleting invoice: " + e.getMessage());
                        showSnackbar("Lỗi xóa hóa đơn: " + e.getMessage());
                    });
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    void showBookingDetails(Booking booking) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chi Tiết Đơn Đặt Phòng");
        StringBuilder details = new StringBuilder();
        details.append("Mã đơn: ").append(booking.invoiceId != null ? booking.invoiceId : "N/A").append("\n")
                .append("Mã đặt phòng: ").append(booking.bookingId != null ? booking.bookingId : "N/A").append("\n")
                .append("Khách: ").append(booking.customerName != null ? booking.customerName : "N/A").append("\n")
                .append("SĐT: ").append(booking.phone != null ? booking.phone : "N/A").append("\n")
                .append("CCCD: ").append(booking.cmnd != null ? booking.cmnd : "N/A").append("\n")
                .append("Email: ").append(booking.email != null ? booking.email : "N/A").append("\n")
                .append("Phòng: ").append(booking.roomName != null ? booking.roomName : "N/A").append("\n")
                .append("Loại phòng: ").append(booking.roomType != null ? booking.roomType : "N/A").append("\n")
                .append("Nhận phòng: ").append(booking.checkIn != null ? booking.checkIn : "N/A").append("\n")
                .append("Trả phòng: ").append(booking.checkOut != null ? booking.checkOut : "N/A").append("\n")
                .append("Số đêm: ").append(calculateDaysDifference(booking.checkIn, booking.checkOut)).append("\n")
                .append("Số khách: ").append(booking.guestCount).append("\n")
                .append("Tổng giá: ").append(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(booking.totalPrice)).append("\n")
                .append("Mã giảm giá: ").append(booking.voucherCode != null ? booking.voucherCode : "Không có").append("\n")
                .append("Yêu cầu đặc biệt: ").append(booking.specialRequest != null ? booking.specialRequest : "Không có").append("\n")
                .append("Trạng thái: ").append(booking.status != null ? booking.status : "N/A").append("\n");
        if (booking.status != null && booking.status.equals("Đã hủy") && booking.reasonCancel != null) {
            details.append("Lý do hủy: ").append(booking.reasonCancel);
        }

        Log.d(TAG, "Showing details for booking " + booking.bookingId + ", checkIn: " + booking.checkIn);
        builder.setMessage(details.toString());
        if (!booking.status.equals("Hoàn thành") && !booking.status.equals("Đã hủy")) {
            builder.setNeutralButton("Chuyển phòng", (dialog, which) -> showChangeRoomDialog(booking));
        }
        if (booking.status.equals("Hoàn thành")) {
            builder.setPositiveButton("Xuất hóa đơn", (dialog, which) -> generateInvoicePDF(booking));
        }
        builder.setNegativeButton("Đóng", null);
        builder.show();
    }

    private void generateInvoicePDF(Booking booking) {
        try {
            File directory = new File(getExternalFilesDir(null), "Invoices");
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + directory.getAbsolutePath());
                    showSnackbar("Lỗi khi tạo thư mục lưu trữ!");
                    return;
                }
            }

            File pdfFile = new File(directory, "invoice_" + booking.invoiceId + ".pdf");
            PdfDocument document = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(12);

            int yPosition = 50;
            paint.setTextSize(18);
            canvas.drawText("HÓA ĐƠN ĐẶT PHÒNG", 50, yPosition, paint);
            yPosition += 30;
            paint.setTextSize(12);
            canvas.drawText("Mã đơn: " + (booking.invoiceId != null ? booking.invoiceId : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Ngày xuất: " + displayFormat.format(new Date()), 50, yPosition, paint);
            yPosition += 40;

            canvas.drawText("Thông tin khách hàng", 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Tên khách hàng: " + (booking.customerName != null ? booking.customerName : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Số điện thoại: " + (booking.phone != null ? booking.phone : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("CCCD: " + (booking.cmnd != null ? booking.cmnd : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Email: " + (booking.email != null ? booking.email : "N/A"), 50, yPosition, paint);
            yPosition += 40;

            canvas.drawText("Thông tin đặt phòng", 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Mã đặt phòng: " + (booking.bookingId != null ? booking.bookingId : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Phòng: " + (booking.roomName != null ? booking.roomName : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Loại phòng: " + (booking.roomType != null ? booking.roomType : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Ngày nhận phòng: " + (booking.checkIn != null ? booking.checkIn : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Ngày trả phòng: " + (booking.checkOut != null ? booking.checkOut : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Số đêm: " + calculateDaysDifference(booking.checkIn, booking.checkOut), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Số khách: " + booking.guestCount, 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Yêu cầu đặc biệt: " + (booking.specialRequest != null ? booking.specialRequest : "Không có"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Mã giảm giá: " + (booking.voucherCode != null ? booking.voucherCode : "Không có"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Tổng giá: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(booking.totalPrice), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Trạng thái: " + (booking.status != null ? booking.status : "N/A"), 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Lý do hủy: " + (booking.reasonCancel != null ? booking.reasonCancel : "Không có"), 50, yPosition, paint);
            yPosition += 40;

            canvas.drawText("Cảm ơn quý khách đã sử dụng dịch vụ của chúng tôi!", 50, yPosition, paint);
            yPosition += 20;
            canvas.drawText("Khách sạn NightDate - Địa chỉ: 828 Sư Vạn Hạnh, TP. Hồ Chí Minh", 50, yPosition, paint);

            document.finishPage(page);
            try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                document.writeTo(fos);
                Log.d(TAG, "PDF saved at: " + pdfFile.getAbsolutePath());
                showSnackbar("Xuất hóa đơn thành công! File lưu tại: " + pdfFile.getAbsolutePath());
                openInvoicePDF(pdfFile);
            } catch (IOException e) {
                Log.e(TAG, "Error saving PDF file: " + e.getMessage());
                showSnackbar("Lỗi khi xuất hóa đơn: " + e.getMessage());
            } finally {
                document.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error generating invoice PDF: " + e.getMessage());
            showSnackbar("Lỗi khi xuất hóa đơn: " + e.getMessage());
        }
    }

    private void openInvoicePDF(File pdfFile) {
        try {
            Uri pdfUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
            Intent pdfIntent = new Intent(Intent.ACTION_VIEW);
            pdfIntent.setDataAndType(pdfUri, "application/pdf");
            pdfIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(pdfIntent, "Mở hóa đơn"));
        } catch (Exception e) {
            Log.e(TAG, "Error opening PDF: " + e.getMessage());
            showToast("Lỗi khi mở file PDF: " + e.getMessage());
        }
    }

    private long calculateDaysDifference(String checkIn, String checkOut) {
        try {
            if (checkIn == null || checkOut == null || checkIn.equals("N/A") || checkOut.equals("N/A")) {
                return 0;
            }
            java.util.Date date1 = displayFormat.parse(checkIn);
            java.util.Date date2 = displayFormat.parse(checkOut);
            long diff = date2.getTime() - date1.getTime();
            return diff / (1000 * 60 * 60 * 24);
        } catch (ParseException e) {
            Log.e(TAG, "Error calculating days difference: " + e.getMessage());
            return 0;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Quyền truy cập bộ nhớ đã được cấp!");
            } else {
                showToast("Quyền truy cập bộ nhớ bị từ chối, không thể lưu file PDF!");
            }
        }
    }

    private void showChangeRoomDialog(Booking booking) {
        showToast("Tính năng đang cập nhật!");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookingsListener != null) {
            bookingsListener.remove();
        }
    }

    private static class Booking {
        String invoiceId, bookingId, customerName, phone, roomName, checkIn, checkOut, status, specialRequest, voucherCode, roomType, reasonCancel, cmnd, email;
        int roomId, guestCount;
        double totalPrice;

        Booking(String invoiceId, String bookingId, String customerName, String phone, int roomId, String roomName,
                String checkIn, String checkOut, double totalPrice, String status, String specialRequest,
                int guestCount, String voucherCode, String roomType, String reasonCancel, String cmnd, String email) {
            this.invoiceId = invoiceId;
            this.bookingId = bookingId;
            this.customerName = customerName;
            this.phone = phone;
            this.roomId = roomId;
            this.roomName = roomName;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.totalPrice = totalPrice;
            this.status = status;
            this.specialRequest = specialRequest;
            this.guestCount = guestCount;
            this.voucherCode = voucherCode;
            this.roomType = roomType;
            this.reasonCancel = reasonCancel;
            this.cmnd = cmnd;
            this.email = email;
        }
    }

    private static class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.ViewHolder> {
        private final List<Booking> bookings;
        private final BookingManagementActivity activity;

        BookingAdapter(List<Booking> bookings, BookingManagementActivity activity) {
            this.bookings = bookings;
            this.activity = activity;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Booking booking = bookings.get(position);
            try {
                long diffInDays = activity.calculateDaysDifference(booking.checkIn, booking.checkOut);
                NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
                currencyFormat.setMinimumFractionDigits(0);

                holder.textBookingId.setText("#" + (booking.invoiceId != null ? booking.invoiceId : "N/A"));
                holder.textCustomerName.setText(booking.customerName != null ? booking.customerName : "N/A");
                holder.textPhone.setText("📞 " + (booking.phone != null ? booking.phone : "N/A"));
                holder.textRoomInfo.setText("🏨 " + (booking.roomName != null ? booking.roomType + " - " + booking.roomName : "N/A"));
                holder.textCheckInDate.setText("📅 " + (booking.checkIn != null ? booking.checkIn : "N/A"));
                holder.textCheckOutDate.setText("📅 " + (booking.checkOut != null ? booking.checkOut : "N/A"));
                holder.textDuration.setText("🌙 " + diffInDays + " đêm");
                holder.textTotalPrice.setText(currencyFormat.format(booking.totalPrice));
                holder.chipStatus.setText(booking.status != null ? booking.status : "N/A");

                Log.d(TAG, "Binding booking " + booking.invoiceId + ": status=" + booking.status + ", checkIn=" + booking.checkIn);

                int statusColor;
                int chipBackgroundColor;
                switch (booking.status != null ? booking.status : "") {
                    case "Chờ xác nhận":
                        statusColor = activity.getResources().getColor(android.R.color.holo_orange_dark);
                        chipBackgroundColor = activity.getResources().getColor(android.R.color.holo_orange_light);
                        break;
                    case "Đã xác nhận":
                        statusColor = activity.getResources().getColor(android.R.color.holo_green_dark);
                        chipBackgroundColor = activity.getResources().getColor(android.R.color.holo_green_light);
                        break;
                    case "Đã nhận phòng":
                        statusColor = activity.getResources().getColor(android.R.color.holo_blue_dark);
                        chipBackgroundColor = activity.getResources().getColor(android.R.color.holo_blue_light);
                        break;
                    case "Hoàn thành":
                    case "Đã hủy":
                        statusColor = activity.getResources().getColor(android.R.color.holo_red_dark);
                        chipBackgroundColor = activity.getResources().getColor(android.R.color.holo_red_light);
                        break;
                    default:
                        statusColor = activity.getResources().getColor(android.R.color.black);
                        chipBackgroundColor = activity.getResources().getColor(android.R.color.darker_gray);
                }
                holder.colorBar.setBackgroundColor(statusColor);
                holder.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(chipBackgroundColor));

                if (booking.status == null) {
                    holder.btnPrimaryAction.setVisibility(View.GONE);
                    holder.btnSecondaryAction.setVisibility(View.GONE);
                    holder.btnDelete.setVisibility(View.GONE);
                    activity.showSnackbar("Trạng thái đơn đặt phòng không xác định!");
                } else if (booking.status.equals("Chờ xác nhận")) {
                    holder.btnPrimaryAction.setText("Xác nhận");
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setText("Hủy");
                    holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                    holder.btnDelete.setVisibility(View.GONE);
                } else if (booking.status.equals("Đã xác nhận")) {
                    holder.btnPrimaryAction.setText("Check-in");
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setText("Hủy");
                    holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                    holder.btnDelete.setVisibility(View.GONE);
                } else if (booking.status.equals("Đã nhận phòng")) {
                    holder.btnPrimaryAction.setText("Check-out");
                    holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                    holder.btnSecondaryAction.setVisibility(View.GONE);
                    holder.btnDelete.setVisibility(View.GONE);
                } else if (booking.status.equals("Hoàn thành")) {
                    holder.btnPrimaryAction.setVisibility(View.GONE);
                    holder.btnSecondaryAction.setVisibility(View.GONE);
                    holder.btnDelete.setVisibility(View.VISIBLE);
                } else {
                    holder.btnPrimaryAction.setVisibility(View.GONE);
                    holder.btnSecondaryAction.setVisibility(View.GONE);
                    holder.btnDelete.setVisibility(View.GONE);
                }
                holder.btnViewDetails.setVisibility(View.VISIBLE);

                holder.btnPrimaryAction.setOnClickListener(v -> {
                    String newStatus = booking.status.equals("Chờ xác nhận") ? "Đã xác nhận" :
                            booking.status.equals("Đã xác nhận") ? "Đã nhận phòng" : "Hoàn thành";
                    Log.d(TAG, "Primary action clicked for booking " + booking.invoiceId + ": newStatus=" + newStatus);
                    activity.updateBookingStatus(booking.invoiceId, booking.roomId, newStatus);
                });
                holder.btnSecondaryAction.setOnClickListener(v -> {
                    Log.d(TAG, "Secondary action (Cancel) clicked for booking " + booking.invoiceId);
                    activity.showCancelDialog(booking.invoiceId, booking.roomId, booking.status);
                });
                holder.btnDelete.setOnClickListener(v -> {
                    Log.d(TAG, "Delete action clicked for booking " + booking.invoiceId);
                    activity.deleteBooking(booking.invoiceId, booking.bookingId);
                });
                holder.btnViewDetails.setOnClickListener(v -> {
                    Log.d(TAG, "View details clicked for booking " + booking.invoiceId);
                    activity.showBookingDetails(booking);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error binding booking " + booking.invoiceId + ": " + e.getMessage());
                activity.showSnackbar("Lỗi hiển thị đơn đặt phòng: " + booking.invoiceId);
            }
        }

        @Override
        public int getItemCount() {
            return bookings.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View colorBar;
            TextView textBookingId, textCustomerName, textPhone, textRoomInfo, textCheckInDate, textCheckOutDate, textDuration, textTotalPrice;
            Chip chipStatus;
            MaterialButton btnPrimaryAction, btnSecondaryAction, btnViewDetails, btnDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                colorBar = itemView.findViewById(R.id.colorBar);
                textBookingId = itemView.findViewById(R.id.textBookingId);
                chipStatus = itemView.findViewById(R.id.chipStatus);
                textTotalPrice = itemView.findViewById(R.id.textTotalPrice);
                textCustomerName = itemView.findViewById(R.id.textCustomerName);
                textPhone = itemView.findViewById(R.id.textPhone);
                textRoomInfo = itemView.findViewById(R.id.textRoomInfo);
                textCheckInDate = itemView.findViewById(R.id.textCheckInDate);
                textCheckOutDate = itemView.findViewById(R.id.textCheckOutDate);
                textDuration = itemView.findViewById(R.id.textDuration);
                btnPrimaryAction = itemView.findViewById(R.id.btnPrimaryAction);
                btnSecondaryAction = itemView.findViewById(R.id.btnSecondaryAction);
                btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}