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
    import android.widget.LinearLayout;
    import android.widget.Spinner;
    import android.widget.TextView;
    import android.widget.Toast;
    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.app.ActivityCompat;
    import androidx.core.content.FileProvider;
    import androidx.recyclerview.widget.LinearLayoutManager;
    import androidx.recyclerview.widget.RecyclerView;
    import androidx.room.Room;

    import com.google.android.material.button.MaterialButton;
    import com.google.android.material.chip.Chip;
    import com.google.android.material.chip.ChipGroup;
    import com.google.android.material.snackbar.Snackbar;
    import com.google.android.material.textfield.TextInputEditText;
    import com.google.firebase.auth.FirebaseAuth;
    import com.google.firebase.firestore.DocumentReference;
    import com.google.firebase.firestore.DocumentSnapshot;
    import com.google.firebase.firestore.FirebaseFirestore;
    import com.google.firebase.firestore.ListenerRegistration;
    import com.google.firebase.firestore.Query;
    import com.google.firebase.firestore.QueryDocumentSnapshot;
    import com.google.firebase.firestore.WriteBatch;

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
        private com.google.android.material.appbar.MaterialToolbar toolbar;
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
    
            firestore = FirebaseFirestore.getInstance();
            auth = FirebaseAuth.getInstance();
    
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
    
            setSupportActionBar(toolbar);
    
            bookingList = new ArrayList<>();
            recyclerViewBookings.setLayoutManager(new LinearLayoutManager(this));
            bookingAdapter = new BookingAdapter(bookingList, this);
            recyclerViewBookings.setAdapter(bookingAdapter);
    
            setupEventListeners();
            setupRealtimeListener();
            checkAutoCheckInOutAndNotify();
        }
    
        private String parseDate(String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return "N/A";
            }
            for (String format : possibleDateFormats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                    sdf.setLenient(false);
                    return displayFormat.format(sdf.parse(dateStr));
                } catch (ParseException e) {
                    // Continue to next format
                }
            }
            return "N/A";
        }
    
        private Date parseDateToDate(String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            for (String format : possibleDateFormats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                    sdf.setLenient(false);
                    return sdf.parse(dateStr);
                } catch (ParseException e) {
                    // Continue to next format
                }
            }
            return null;
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
                    showSnackbar("Lỗi tải danh sách đơn đặt phòng!");
                    return;
                }
                if (querySnapshot != null) {
                    loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
                }
            });
        }
    
        private void checkAutoCheckInOutAndNotify() {
            Date currentDate = new Date();
            String todayDateStr = displayFormat.format(currentDate);
    
            firestore.collection("bookings")
                    .whereEqualTo("TrangThaiDD", "Đã xác nhận")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String checkIn = doc.getString("TGCheckin");
                            Date checkInDate = parseDateToDate(checkIn);
                            if (checkInDate != null && todayDateStr.equals(displayFormat.format(checkInDate))) {
                                int roomId = doc.getLong("MaPhong").intValue();
                                firestore.collection("rooms").document(String.valueOf(roomId)).get()
                                        .addOnSuccessListener(roomDoc -> {
                                            if (roomDoc.exists()) {
                                                String roomName = roomDoc.getString("TenPhong");
                                                String notificationMessage = "Phòng " + roomName + " có khách check-in vào lúc 14:00 hôm nay.";
                                                createNotification("Thông báo Check-in", notificationMessage);
                                            }
                                        });
                            }
                        }
                    });
    
            firestore.collection("bookings")
                    .whereEqualTo("TrangThaiDD", "Đã nhận phòng")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            String checkOut = doc.getString("TGCheckout");
                            Date checkOutDate = parseDateToDate(checkOut);
                            if (checkOutDate != null && todayDateStr.equals(displayFormat.format(checkOutDate))) {
                                int roomId = doc.getLong("MaPhong").intValue();
                                firestore.collection("rooms").document(String.valueOf(roomId)).get()
                                        .addOnSuccessListener(roomDoc -> {
                                            if (roomDoc.exists()) {
                                                String roomName = roomDoc.getString("TenPhong");
                                                String notificationMessage = "Khách phòng " + roomName + " sẽ check-out lúc 12:00.";
                                                createNotification("Thông báo Check-out", notificationMessage);
                                            }
                                        });
                            }
                        }
                    });
        }
    
        private void createNotification(String title, String message) {
            String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
            if (userId == null) {
                return;
            }
    
            Map<String, Object> notification = new HashMap<>();
            notification.put("userId", userId);
            notification.put("title", title);
            notification.put("message", message);
            notification.put("timestamp", dbDateFormat.format(new Date()));
    
            firestore.collection("notifications").add(notification)
                    .addOnSuccessListener(documentReference -> Log.d(TAG, "Notification created: " + title))
                    .addOnFailureListener(e -> Log.e(TAG, "Error creating notification", e));
        }
    
        private void loadBookings(String query, String status, String sortField, Query.Direction sortDirection) {
            if (isLoading) return;
            isLoading = true;
    
            Query firestoreQuery = firestore.collection("bookings").orderBy(sortField, sortDirection);
    
            if (status != null && !status.equals("Tất cả")) {
                firestoreQuery = firestoreQuery.whereEqualTo("TrangThaiDD", status);
            }
            executeQuery(firestoreQuery, query);
        }
    
        private void executeQuery(Query firestoreQuery, String searchQuery) {
            String query = searchQuery != null ? searchQuery.toLowerCase(Locale.getDefault()) : "";
            firestoreQuery.get().addOnSuccessListener(querySnapshot -> {
                Map<String, Booking> uniqueBookings = new HashMap<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    String maDon = doc.getId();
                    if (uniqueBookings.containsKey(maDon)) {
                        continue;
                    }
                    String maKH = doc.getString("MaKH");
                    Integer roomId = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
    
                    firestore.collection("customers").document(maKH).get()
                            .addOnSuccessListener(customerDoc -> {
                                if (!customerDoc.exists()) return;
                                firestore.collection("rooms").document(String.valueOf(roomId)).get()
                                        .addOnSuccessListener(roomDoc -> {
                                            if (!roomDoc.exists()) return;
                                            processBookingData(doc, uniqueBookings, query, roomDoc, customerDoc, doc.getString("MaGiam"));
                                        });
                            });
                }
                if (querySnapshot.isEmpty()) {
                    updateUIWithNoBookings("Không có đơn đặt phòng nào!");
                }
                isLoading = false;
            }).addOnFailureListener(e -> {
                showSnackbar("Lỗi tải danh sách đơn đặt phòng!");
                isLoading = false;
            });
        }
    
        private void processBookingData(QueryDocumentSnapshot doc, Map<String, Booking> uniqueBookings, String query,
                                        DocumentSnapshot roomDoc, DocumentSnapshot customerDoc, String voucherCode) {
            String maDon = doc.getId();
            Integer roomId = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
            String checkIn = doc.getString("TGCheckin");
            String checkOut = doc.getString("TGCheckout");
            String statusDD = doc.getString("TrangThaiDD");
            String specialRequest = doc.getString("YeuCauDacBiet");
            Integer guestCount = doc.getLong("SoKhach") != null ? doc.getLong("SoKhach").intValue() : 0;
            String formattedCheckIn = parseDate(checkIn);
            String formattedCheckOut = parseDate(checkOut);
    
            String currentRoomStatus = roomDoc.getString("TrangThai");
    
            firestore.collection("invoices").whereEqualTo("MaDon", maDon).get()
                    .addOnSuccessListener(invoiceSnapshot -> {
                        String invoiceId = "N/A";
                        String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";
                        String phone = customerDoc.getString("SDT") != null ? customerDoc.getString("SDT") : "N/A";
                        String cmnd = customerDoc.getString("CCCD") != null ? customerDoc.getString("CCCD") : "N/A";
                        String email = customerDoc.getString("Email") != null ? customerDoc.getString("Email") : "N/A";
                        double totalPrice = 0.0;
                        String finalStatus = statusDD;
                        String reasonCancel = null;
    
                        if (!invoiceSnapshot.isEmpty()) {
                            DocumentSnapshot invoiceDoc = invoiceSnapshot.getDocuments().get(0);
                            invoiceId = invoiceDoc.getId();
                            totalPrice = invoiceDoc.getDouble("TongGia") != null ? invoiceDoc.getDouble("TongGia") : 0.0;
                            finalStatus = invoiceDoc.getString("TrangThai") != null ? invoiceDoc.getString("TrangThai") : statusDD;
                            reasonCancel = invoiceDoc.getString("LyDoHuy");
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
                                    guestCount, voucherCode, roomType, reasonCancel, cmnd, email, currentRoomStatus);
                            uniqueBookings.put(maDon, booking);
                            updateBookingList(uniqueBookings);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching invoice", e);
                    });
        }
    
        private void updateBookingList(Map<String, Booking> uniqueBookings) {
            runOnUiThread(() -> {
                bookingList.clear();
                bookingList.addAll(uniqueBookings.values());
                tvResultsCount.setText("Hiển thị " + bookingList.size() + " đơn đặt phòng");
                if (bookingList.isEmpty()) {
                    recyclerViewBookings.setVisibility(View.GONE);
                    layoutEmptyState.setVisibility(View.VISIBLE);
                } else {
                    recyclerViewBookings.setVisibility(View.VISIBLE);
                    layoutEmptyState.setVisibility(View.GONE);
                }
                bookingAdapter.notifyDataSetChanged();
            });
        }
    
        private void updateUIWithNoBookings(String message) {
            runOnUiThread(() -> {
                bookingList.clear();
                bookingAdapter.notifyDataSetChanged();
                recyclerViewBookings.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
                tvResultsCount.setText("Hiển thị 0 đơn đặt phòng");
            });
        }
    
        private void showSnackbar(String message) {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Snackbar.make(recyclerViewBookings, message, Snackbar.LENGTH_LONG).show();
            });
        }
    
        void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(BookingManagementActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    
        public void markRoomAsCleaned(int roomId) {
            firestore.collection("rooms").document(String.valueOf(roomId))
                    .update("TrangThai", "Trống")
                    .addOnSuccessListener(aVoid -> {
                        showSnackbar("Phòng " + roomId + " đã được chuyển về trạng thái 'Trống'.");
                        loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
                    })
                    .addOnFailureListener(e -> {
                        showSnackbar("Lỗi: Không thể cập nhật trạng thái phòng.");
                        Log.e(TAG, "Error setting room to 'Trống'", e);
                    });
        }

        void updateBookingStatus(String invoiceId, int roomId, String newStatus) {
            if (newStatus.equals("Hoàn thành")) {
                // Logic check-out hiện tại của bạn
                firestore.collection("invoices").document(invoiceId).get()
                        .addOnSuccessListener(doc -> {
                            if (!doc.exists()) return;
                            String maDon = doc.getString("MaDon");
                            if (maDon == null) return;
                            firestore.collection("bookings").document(maDon).get()
                                    .addOnSuccessListener(bookingDoc -> {
                                        if (!bookingDoc.exists()) return;

                                        String checkOut = bookingDoc.getString("TGCheckout");
                                        Date checkOutDate = parseDateToDate(checkOut);
                                        Date currentDate = new Date();
                                        if (checkOutDate == null) return;

                                        if (currentDate.before(checkOutDate)) {
                                            showCheckOutConfirmationDialog(invoiceId, roomId, maDon, bookingDoc, doc, checkOutDate, currentDate);
                                        } else {
                                            performCheckOutValidations(invoiceId, roomId, maDon, bookingDoc, doc, checkOutDate, currentDate);
                                        }
                                    });
                        });
            } else if (newStatus.equals("Đã nhận phòng")) {
                // Thêm logic kiểm tra check-in sớm
                firestore.collection("invoices").document(invoiceId).get()
                        .addOnSuccessListener(doc -> {
                            if (!doc.exists()) {
                                showSnackbar("Không tìm thấy hóa đơn!");
                                return;
                            }
                            String maDon = doc.getString("MaDon");
                            if (maDon == null) {
                                showSnackbar("Không tìm thấy mã đơn đặt phòng!");
                                return;
                            }
                            firestore.collection("bookings").document(maDon).get()
                                    .addOnSuccessListener(bookingDoc -> {
                                        if (!bookingDoc.exists()) {
                                            showSnackbar("Không tìm thấy đơn đặt phòng!");
                                            return;
                                        }
                                        String checkIn = bookingDoc.getString("TGCheckin");
                                        Date checkInDate = parseDateToDate(checkIn);
                                        Date currentDate = new Date();
                                        if (checkInDate == null) {
                                            showSnackbar("Ngày nhận phòng không hợp lệ: " + checkIn);
                                            return;
                                        }

                                        // Tính khoảng cách giữa ngày hiện tại và ngày check-in
                                        long diffInMillies = checkInDate.getTime() - currentDate.getTime();
                                        long diffInDays = diffInMillies / (1000 * 60 * 60 * 24);

                                        if (diffInDays > 3) {
                                            // Check-in sớm hơn 3 ngày
                                            showCheckInTooEarlyDialog(invoiceId, roomId, newStatus, checkInDate, currentDate);
                                        } else {
                                            // Cho phép check-in nếu trong vòng 3 ngày hoặc đã đến ngày
                                            proceedUpdateBookingStatus(invoiceId, roomId, newStatus);
                                        }
                                    })
                                    .addOnFailureListener(e -> showSnackbar("Lỗi truy xuất đơn đặt phòng: " + e.getMessage()));
                        })
                        .addOnFailureListener(e -> showSnackbar("Lỗi truy xuất hóa đơn: " + e.getMessage()));
            } else {
                // Các trạng thái khác (Chờ xác nhận, Đã xác nhận, Đã hủy)
                proceedUpdateBookingStatus(invoiceId, roomId, newStatus);
            }
        }
        private void showCheckInTooEarlyDialog(String invoiceId, int roomId, String newStatus, Date checkInDate, Date currentDate) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Check-in sớm không hợp lệ");
            builder.setMessage("Ngày check-in dự kiến là " + displayFormat.format(checkInDate) +
                    ". Bạn chỉ có thể check-in sớm tối đa 3 ngày. Hôm nay là " + displayFormat.format(currentDate) +
                    ". Bạn có muốn tiếp tục với check-in sớm không?");
            builder.setPositiveButton("Xác nhận", (dialog, which) -> {
                // Cho phép check-in sớm nếu người dùng xác nhận
                proceedUpdateBookingStatus(invoiceId, roomId, newStatus);
            });
            builder.setNegativeButton("Hủy", null);
            builder.show();
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
                            showSnackbar("Không tìm thấy thông tin khách hàng!");
                            return;
                        }
                        String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";
                        String cmnd = customerDoc.getString("CCCD") != null ? customerDoc.getString("CCCD") : "N/A";
                        String checkIn = bookingDoc.getString("TGCheckin");
                        Date checkInDate = parseDateToDate(checkIn);
    
                        if (checkInDate == null) {
                            showSnackbar("Ngày nhận phòng không hợp lệ: " + checkIn);
                            return;
                        }
                        if (checkInDate.after(currentDate)) {
                            checkInDate = currentDate;
                        }
                        Date finalCheckInDate = checkInDate;
    
                        firestore.collection("rooms").document(String.valueOf(roomId)).get()
                                .addOnSuccessListener(roomDoc -> {
                                    if (!roomDoc.exists()) {
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
                                            .append("Vui lòng xác nhận các thông tin trên là chính xác.");
                                    builder.setMessage(message.toString());
    
                                    LinearLayout layout = new LinearLayout(this);
                                    layout.setOrientation(LinearLayout.VERTICAL);
                                    layout.setPadding(50, 20, 50, 20);
                                    TextInputEditText inputAdditionalFees = new TextInputEditText(this);
                                    inputAdditionalFees.setHint("Nhập phụ phí (nếu có)");
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
    
                                        firestore.collection("invoices").document(invoiceId).update(invoiceUpdates)
                                                .addOnSuccessListener(aVoid -> {
                                                    Map<String, Object> bookingUpdates = new HashMap<>();
                                                    bookingUpdates.put("TrangThaiDD", "Hoàn thành");
                                                    bookingUpdates.put("TGCheckout", dbDateFormat.format(currentDate));
                                                    firestore.collection("bookings").document(maDon).update(bookingUpdates)
                                                            .addOnSuccessListener(aVoid1 -> {
                                                                firestore.collection("rooms").document(String.valueOf(roomId))
                                                                        .update("TrangThai", "Đang dọn dẹp")
                                                                        .addOnSuccessListener(aVoid2 -> {
                                                                            Log.d(TAG, "Check-out completed for booking: " + maDon + ". Room status set to 'Đang dọn dẹp'");
                                                                            showSnackbar("Check-out thành công! Phòng đã được chuyển sang trạng thái dọn dẹp.");
                                                                            chipCompleted.setChecked(true);
                                                                            loadBookings(editTextSearch.getText().toString(), "Hoàn thành", currentSortField, currentSortDirection);
                                                                        })
                                                                        .addOnFailureListener(e -> showSnackbar("Lỗi cập nhật trạng thái phòng: " + e.getMessage()));
                                                            })
                                                            .addOnFailureListener(e -> showSnackbar("Lỗi cập nhật booking: " + e.getMessage()));
                                                })
                                                .addOnFailureListener(e -> showSnackbar("Lỗi cập nhật hóa đơn: " + e.getMessage()));
                                    });
                                    builder.setNegativeButton("Hủy", null);
                                    builder.show();
                                })
                                .addOnFailureListener(e -> showSnackbar("Lỗi truy xuất thông tin phòng: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> showSnackbar("Lỗi truy xuất thông tin khách hàng: " + e.getMessage()));
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
                                        showToast("Không tìm thấy mã đơn đặt phòng!");
                                        return;
                                    }
                                    firestore.collection("bookings").document(maDon).update("TrangThaiDD", newStatus)
                                            .addOnSuccessListener(aVoid1 -> {
                                                String roomStatus = newStatus.equals("Đã hủy") ? "Trống" :
                                                        newStatus.equals("Đã nhận phòng") ? "Đang sử dụng" : "Đã đặt";
                                                if (!newStatus.equals("Hoàn thành")) { // Don't change status on 'Hoàn thành' here
                                                    firestore.collection("rooms").document(String.valueOf(roomId))
                                                            .update("TrangThai", roomStatus)
                                                            .addOnSuccessListener(aVoid2 -> showToast("Cập nhật trạng thái thành công: " + newStatus))
                                                            .addOnFailureListener(e -> showToast("Lỗi cập nhật trạng thái phòng: " + e.getMessage()));
                                                } else {
                                                    showToast("Cập nhật trạng thái thành công: " + newStatus);
                                                }
                                            })
                                            .addOnFailureListener(e -> showToast("Lỗi cập nhật trạng thái booking: " + e.getMessage()));
                                })
                                .addOnFailureListener(e -> showToast("Lỗi truy xuất thông tin hóa đơn!"));
                    })
                    .addOnFailureListener(e -> showToast("Lỗi cập nhật trạng thái: " + newStatus + " - " + e.getMessage()));
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
                                            showSnackbar("Không tìm thấy mã đơn đặt phòng!");
                                            return;
                                        }
                                        firestore.collection("bookings").document(maDon)
                                                .update("TrangThaiDD", "Đã hủy")
                                                .addOnSuccessListener(aVoid1 -> {
                                                    firestore.collection("rooms").document(String.valueOf(roomId))
                                                            .update("TrangThai", "Trống")
                                                            .addOnSuccessListener(aVoid2 -> showSnackbar("Hủy đơn thành công!"))
                                                            .addOnFailureListener(e -> showSnackbar("Lỗi cập nhật trạng thái phòng: " + e.getMessage()));
                                                })
                                                .addOnFailureListener(e -> showSnackbar("Lỗi cập nhật trạng thái booking: " + e.getMessage()));
                                    })
                                    .addOnFailureListener(e -> showSnackbar("Lỗi truy xuất thông tin hóa đơn!"));
                        })
                        .addOnFailureListener(e -> showSnackbar("Lỗi hủy đơn: " + e.getMessage()));
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
                                        showSnackbar("Xóa đơn thành công!");
                                        loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
                                    })
                                    .addOnFailureListener(e -> showSnackbar("Lỗi xóa đơn đặt phòng: " + e.getMessage()));
                        })
                        .addOnFailureListener(e -> showSnackbar("Lỗi xóa hóa đơn: " + e.getMessage()));
            });
            builder.setNegativeButton("Hủy", null);
            builder.show();
        }
    
        void showBookingDetails(Booking booking) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Chi Tiết Đơn Đặt Phòng");
            StringBuilder details = new StringBuilder();
            details.append("Mã đơn: ").append(booking.invoiceId).append("\n")
                    .append("Mã đặt phòng: ").append(booking.bookingId).append("\n")
                    .append("Khách: ").append(booking.customerName).append("\n")
                    .append("SĐT: ").append(booking.phone).append("\n")
                    .append("CCCD: ").append(booking.cmnd).append("\n")
                    .append("Email: ").append(booking.email).append("\n")
                    .append("Phòng: ").append(booking.roomName).append("\n")
                    .append("Loại phòng: ").append(booking.roomType).append("\n")
                    .append("Nhận phòng: ").append(booking.checkIn).append("\n")
                    .append("Trả phòng: ").append(booking.checkOut).append("\n")
                    .append("Số đêm: ").append(calculateDaysDifference(booking.checkIn, booking.checkOut)).append("\n")
                    .append("Số khách: ").append(booking.guestCount).append("\n")
                    .append("Tổng giá: ").append(NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(booking.totalPrice)).append("\n")
                    .append("Mã giảm giá: ").append(booking.voucherCode != null ? booking.voucherCode : "Không có").append("\n")
                    .append("Yêu cầu đặc biệt: ").append(booking.specialRequest != null ? booking.specialRequest : "Không có").append("\n")
                    .append("Trạng thái: ").append(booking.status).append("\n");
    
            if (booking.status != null && booking.status.equals("Đã hủy") && booking.reasonCancel != null) {
                details.append("Lý do hủy: ").append(booking.reasonCancel);
            }
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

        private void showChangeRoomDialog(Booking originalBooking) {
            Date checkInDate = parseDateToDate(originalBooking.checkIn);
            Date checkOutDate = parseDateToDate(originalBooking.checkOut);

            if (checkInDate == null || checkOutDate == null) {
                // Thay thế showToast bằng AlertDialog
                new AlertDialog.Builder(this)
                        .setTitle("Lỗi Dữ liệu")
                        .setMessage("Không thể thực hiện chuyển phòng vì ngày check-in hoặc check-out của đơn đặt này không hợp lệ (có thể đang là 'N/A'). Vui lòng kiểm tra lại.")
                        .setPositiveButton("Đã hiểu", null)
                        .show();
                return; // Dừng lại
            }

            firestore.collection("rooms").get().addOnSuccessListener(allRoomsSnapshot -> {
                List<RoomModel> allRooms = new ArrayList<>();
                // Parse thủ công để khớp với RoomModel của bạn
                for (DocumentSnapshot doc : allRoomsSnapshot) {
                    try {
                        int maPhong = doc.getLong("MaPhong").intValue();
                        String name = doc.getString("TenPhong");
                        int maLoaiPhong = doc.getLong("MaLoaiPhong").intValue();
                        double giaPhong = doc.getDouble("GiaPhong");
                        int soLuongNguoiToiDa = doc.getLong("SoLuongNguoiToiDa").intValue();
                        String status = doc.getString("TrangThai");
                        String moTa = doc.getString("MoTa");
                        List<Integer> tienIch = (List<Integer>) doc.get("TienIch");
                        allRooms.add(new RoomModel(maPhong, name, maLoaiPhong, giaPhong, soLuongNguoiToiDa, status, moTa, tienIch));
                    } catch (Exception e) {
                        Log.e(TAG, "Lỗi khi parse RoomModel: " + doc.getId(), e);
                    }
                }

                firestore.collection("bookings")
                        .whereIn("TrangThaiDD", List.of("Đã xác nhận", "Đã nhận phòng"))
                        .get()
                        .addOnSuccessListener(bookingsSnapshot -> {
                            List<Integer> unavailableRoomIds = new ArrayList<>();
                            for (QueryDocumentSnapshot bookingDoc : bookingsSnapshot) {
                                Date otherCheckIn = parseDateToDate(bookingDoc.getString("TGCheckin"));
                                Date otherCheckOut = parseDateToDate(bookingDoc.getString("TGCheckout"));

                                if (otherCheckIn != null && otherCheckOut != null &&
                                        checkInDate.before(otherCheckOut) && checkOutDate.after(otherCheckIn)) {
                                    unavailableRoomIds.add(bookingDoc.getLong("MaPhong").intValue());
                                }
                            }

                            List<RoomModel> availableRooms = new ArrayList<>();
                            for (RoomModel room : allRooms) {
                                if (!unavailableRoomIds.contains(room.getMaPhong()) && room.getMaPhong() != originalBooking.roomId) {
                                    availableRooms.add(room);
                                }
                            }

                            if (availableRooms.isEmpty()) {
                                showToast("Không có phòng trống phù hợp để chuyển.");
                                return;
                            }

                            displayChangeRoomSelectionDialog(originalBooking, availableRooms);
                        });
            });
        }

        // HÀM 2: Sửa lại để hiển thị thông tin từ RoomModel
        private void displayChangeRoomSelectionDialog(Booking originalBooking, List<RoomModel> availableRooms) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_room, null);
            builder.setView(view);

            TextView tvTitle = view.findViewById(R.id.tvChangeRoomTitle);
            TextView tvCurrentRoom = view.findViewById(R.id.tvCurrentRoomInfo);
            Spinner spinner = view.findViewById(R.id.spinnerAvailableRooms);
            MaterialButton btnCancel = view.findViewById(R.id.btnCancelChangeRoom);
            MaterialButton btnConfirm = view.findViewById(R.id.btnConfirmChangeRoom);

            tvTitle.setText("Chuyển phòng cho đơn " + originalBooking.bookingId);
            tvCurrentRoom.setText("Phòng hiện tại: " + originalBooking.roomName);

            List<String> roomDisplayList = new ArrayList<>();
            for (RoomModel room : availableRooms) {
                roomDisplayList.add(room.getName() + " (" + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(room.getGiaPhong()) + ")");
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomDisplayList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);

            AlertDialog dialog = builder.create();

            btnCancel.setOnClickListener(v -> dialog.dismiss());
            btnConfirm.setOnClickListener(v -> {
                int selectedPosition = spinner.getSelectedItemPosition();
                RoomModel selectedRoom = availableRooms.get(selectedPosition);
                performRoomChange(originalBooking, selectedRoom);
                dialog.dismiss();
            });

            dialog.show();
        }

        // HÀM 2: Sửa lại để hiển thị thông tin từ RoomModel


        // HÀM 3: Sửa lại để nhận tham số là RoomModel
        private void performRoomChange(Booking originalBooking, RoomModel newRoom) {
            WriteBatch batch = firestore.batch();

            DocumentReference invoiceRef = firestore.collection("invoices").document(originalBooking.invoiceId);
            long days = calculateDaysDifference(originalBooking.checkIn, originalBooking.checkOut);
            if (days <= 0) days = 1;
            double newTotalPrice = newRoom.getGiaPhong() * days;
            batch.update(invoiceRef, "MaPhong", newRoom.getMaPhong());
            batch.update(invoiceRef, "TongGia", newTotalPrice);

            DocumentReference bookingRef = firestore.collection("bookings").document(originalBooking.bookingId);
            batch.update(bookingRef, "MaPhong", newRoom.getMaPhong());

            DocumentReference newRoomRef = firestore.collection("rooms").document(String.valueOf(newRoom.getMaPhong()));
            String newRoomStatus = originalBooking.status.equals("Đã nhận phòng") ? "Đang sử dụng" : "Đã đặt";
            batch.update(newRoomRef, "TrangThai", newRoomStatus);

            DocumentReference oldRoomRef = firestore.collection("rooms").document(String.valueOf(originalBooking.roomId));
            batch.update(oldRoomRef, "TrangThai", "Trống");

            batch.commit().addOnSuccessListener(aVoid -> {
                showToast("Chuyển phòng thành công!");
                loadBookings(editTextSearch.getText().toString(), getSelectedStatus(), currentSortField, currentSortDirection);
            }).addOnFailureListener(e -> {
                showToast("Chuyển phòng thất bại: " + e.getMessage());
                Log.e(TAG, "Lỗi khi chuyển phòng: ", e);
            });
        }



        private void generateInvoicePDF(Booking booking) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
                return;
            }
    
            try {
                File directory = new File(getExternalFilesDir(null), "Invoices");
                if (!directory.exists()) {
                    if (!directory.mkdirs()) {
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
    
                canvas.drawText("Mã đơn: " + (booking.invoiceId), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Ngày xuất: " + displayFormat.format(new Date()), 50, yPosition, paint);
                yPosition += 40;
                canvas.drawText("Thông tin khách hàng", 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Tên khách hàng: " + (booking.customerName), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Số điện thoại: " + (booking.phone), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("CCCD: " + (booking.cmnd), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Email: " + (booking.email), 50, yPosition, paint);
                yPosition += 40;
                canvas.drawText("Thông tin đặt phòng", 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Mã đặt phòng: " + (booking.bookingId), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Phòng: " + (booking.roomName), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Ngày nhận phòng: " + (booking.checkIn), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Ngày trả phòng: " + (booking.checkOut), 50, yPosition, paint);
                yPosition += 20;
                canvas.drawText("Tổng giá: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(booking.totalPrice), 50, yPosition, paint);
    
                document.finishPage(page);
    
                try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                    document.writeTo(fos);
                    showSnackbar("Xuất hóa đơn thành công! File lưu tại: " + pdfFile.getAbsolutePath());
                    openInvoicePDF(pdfFile);
                } catch (IOException e) {
                    showSnackbar("Lỗi khi xuất hóa đơn: " + e.getMessage());
                } finally {
                    document.close();
                }
            } catch (Exception e) {
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
                showToast("Lỗi khi mở file PDF: " + e.getMessage());
            }
        }
    
        private long calculateDaysDifference(String checkIn, String checkOut) {
            try {
                if (checkIn == null || checkOut == null || checkIn.equals("N/A") || checkOut.equals("N/A")) return 0;
                Date date1 = displayFormat.parse(checkIn);
                Date date2 = displayFormat.parse(checkOut);
                long diff = date2.getTime() - date1.getTime();
                return diff / (1000 * 60 * 60 * 24);
            } catch (ParseException e) {
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
    
        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (bookingsListener != null) {
                bookingsListener.remove();
            }
        }
    
        private static class Booking {
            String invoiceId, bookingId, customerName, phone, roomName, checkIn, checkOut, status, specialRequest, voucherCode, roomType, reasonCancel, cmnd, email;
            String roomStatus;
            int roomId, guestCount;
            double totalPrice;
    
            Booking(String invoiceId, String bookingId, String customerName, String phone, int roomId, String roomName,
                    String checkIn, String checkOut, double totalPrice, String status, String specialRequest,
                    int guestCount, String voucherCode, String roomType, String reasonCancel, String cmnd, String email,
                    String roomStatus) {
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
                this.roomStatus = roomStatus;
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
    
                    holder.textBookingId.setText("#" + booking.invoiceId);
                    holder.textCustomerName.setText(booking.customerName);
                    holder.textPhone.setText(" 📞 " + booking.phone);
                    holder.textRoomInfo.setText(" 🏨 " + booking.roomType + " - " + booking.roomName);
                    holder.textCheckInDate.setText(" 📅 " + booking.checkIn);
                    holder.textCheckOutDate.setText(" 📅 " + booking.checkOut);
                    holder.textDuration.setText(" 🌙 " + diffInDays + " đêm");
                    holder.textTotalPrice.setText(currencyFormat.format(booking.totalPrice));
                    holder.chipStatus.setText(booking.status);
    
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
    
                    holder.btnPrimaryAction.setVisibility(View.GONE);
                    holder.btnSecondaryAction.setVisibility(View.GONE);
                    holder.btnDelete.setVisibility(View.GONE);
                    holder.btnMarkAsCleaned.setVisibility(View.GONE);
                    holder.btnViewDetails.setVisibility(View.VISIBLE);
    
                    if (booking.status != null) {
                        switch (booking.status) {
                            case "Chờ xác nhận":
                                holder.btnPrimaryAction.setText("Xác nhận");
                                holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                                holder.btnSecondaryAction.setText("Hủy");
                                holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                                break;
                            case "Đã xác nhận":
                                holder.btnPrimaryAction.setText("Check-in");
                                holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                                holder.btnSecondaryAction.setText("Hủy");
                                holder.btnSecondaryAction.setVisibility(View.VISIBLE);
                                break;
                            case "Đã nhận phòng":
                                holder.btnPrimaryAction.setText("Check-out");
                                holder.btnPrimaryAction.setVisibility(View.VISIBLE);
                                break;
                            case "Hoàn thành":
                                if ("Đang dọn dẹp".equals(booking.roomStatus)) {
                                    holder.btnMarkAsCleaned.setText("Dọn xong");
                                    holder.btnMarkAsCleaned.setVisibility(View.VISIBLE);
                                } else {
                                    holder.btnDelete.setVisibility(View.VISIBLE);
                                }
                                break;
                            case "Đã hủy":
                                holder.btnDelete.setVisibility(View.VISIBLE);
                                break;
                        }
                    }
    
                    holder.btnPrimaryAction.setOnClickListener(v -> {
                        String newStatus = booking.status.equals("Chờ xác nhận") ? "Đã xác nhận" :
                                booking.status.equals("Đã xác nhận") ? "Đã nhận phòng" : "Hoàn thành";
                        activity.updateBookingStatus(booking.invoiceId, booking.roomId, newStatus);
                    });
    
                    holder.btnSecondaryAction.setOnClickListener(v -> activity.showCancelDialog(booking.invoiceId, booking.roomId, booking.status));
                    holder.btnDelete.setOnClickListener(v -> activity.deleteBooking(booking.invoiceId, booking.bookingId));
                    holder.btnViewDetails.setOnClickListener(v -> activity.showBookingDetails(booking));
                    holder.btnMarkAsCleaned.setOnClickListener(v -> activity.markRoomAsCleaned(booking.roomId));
    
                } catch (Exception e) {
                    Log.e(TAG, "Error binding booking " + booking.invoiceId, e);
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
                MaterialButton btnMarkAsCleaned;
    
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
                    btnMarkAsCleaned = itemView.findViewById(R.id.btnMarkAsCleaned);
                }
            }
        }
    }