package com.sinhvien.appqlkhachsan;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomerBookingActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private RecyclerView rvBookings;
    private TextView tvPendingCount, tvCompletedCount; // Thêm biến cho TextView
    private List<BookingModel> bookingList;
    private BookingAdapter bookingAdapter;
    private boolean isActive = true;
    private boolean isLoadingBookings = false;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private SimpleDateFormat displayDateTimeFormat = new SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault());
    private final Object lock = new Object();
    private static final long FIVE_DAYS_IN_MILLIS = 5 * 24 * 60 * 60 * 1000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_booking);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Khởi tạo các TextView cho số lượng đơn
        tvPendingCount = findViewById(R.id.tvPendingCount);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);
        rvBookings = findViewById(R.id.rvBookings);
        rvBookings.setLayoutManager(new LinearLayoutManager(this));

        bookingList = Collections.synchronizedList(new ArrayList<>());
        bookingAdapter = new BookingAdapter(bookingList);
        rvBookings.setAdapter(bookingAdapter);

        loadBookings();
    }

    private void loadBookings() {
        if (!isActive || isLoadingBookings) return;
        isLoadingBookings = true;
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            Toast.makeText(this, "Vui lòng đăng nhập!", Toast.LENGTH_SHORT).show();
            finish();
            isLoadingBookings = false;
            return;
        }

        synchronized (lock) {
            bookingList.clear();
        }
        Map<String, BookingModel> uniqueBookings = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger pendingQueries = new AtomicInteger(0);
        // Biến để đếm số đơn
        AtomicInteger pendingCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        db.collection("invoices").whereEqualTo("MaKH", userId).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) {
                        isLoadingBookings = false;
                        return;
                    }
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "Bạn chưa có đơn đặt phòng nào!", Toast.LENGTH_SHORT).show();
                        // Cập nhật TextView về 0 khi không có đơn
                        tvPendingCount.setText("0");
                        tvCompletedCount.setText("0");
                        bookingAdapter.notifyDataSetChanged();
                        isLoadingBookings = false;
                        return;
                    }

                    pendingQueries.set(querySnapshot.size());
                    List<Task<?>> tasks = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        String maDon = doc.getString("MaDon");
                        String invoiceId = doc.getId();
                        Integer maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
                        String tgDat = doc.getString("TGDat") != null ? doc.getString("TGDat") : "";
                        String trangThai = doc.getString("TrangThai") != null ? doc.getString("TrangThai") : "";
                        Double tongGia = doc.getDouble("TongGia") != null ? doc.getDouble("TongGia") : 0.0;
                        String ghiChu = doc.getString("GhiChu") != null ? doc.getString("GhiChu") : "";
                        String maGiamGia = doc.getString("MaGiamGia") != null ? doc.getString("MaGiamGia") : "";
                        String maKhachSan = doc.getString("MaKhachSan") != null ? doc.getString("MaKhachSan") : "";

                        // Đếm số đơn dựa trên trạng thái
                        if ("Chờ xác nhận".equals(trangThai) || "Đã xác nhận".equals(trangThai)) {
                            pendingCount.incrementAndGet();
                        } else if ("Đã trả phòng".equals(trangThai)) {
                            completedCount.incrementAndGet();
                        }

                        Task<Object> bookingTask = db.collection("bookings").document(maDon).get()
                                .continueWithTask(task -> {
                                    if (!isActive) return Tasks.forResult(null);
                                    DocumentSnapshot bookingDoc = task.getResult();
                                    String tgCheckin = bookingDoc.exists() && bookingDoc.getString("TGCheckin") != null ? bookingDoc.getString("TGCheckin") : "";
                                    String tgCheckout = bookingDoc.exists() && bookingDoc.getString("TGCheckout") != null ? bookingDoc.getString("TGCheckout") : "";

                                    return db.collection("rooms").document(String.valueOf(maPhong)).get()
                                            .continueWithTask(roomTask -> {
                                                if (!isActive) return Tasks.forResult(null);
                                                synchronized (lock) {
                                                    DocumentSnapshot roomDoc = roomTask.getResult();
                                                    String roomName = roomDoc.exists() && roomDoc.getString("TenPhong") != null ? roomDoc.getString("TenPhong") : "N/A";

                                                    Task<DocumentSnapshot> hotelTask = maKhachSan.isEmpty() ? Tasks.forResult(null) :
                                                            db.collection("hotels").document(maKhachSan).get();

                                                    return hotelTask.continueWithTask(hotelTaskResult -> {
                                                        if (!isActive) return Tasks.forResult(null);
                                                        DocumentSnapshot hotelDoc = hotelTaskResult.getResult();
                                                        String hotelName = hotelDoc != null && hotelDoc.exists() && hotelDoc.getString("TenKhachSan") != null ?
                                                                hotelDoc.getString("TenKhachSan") : "Khách sạn không xác định";

                                                        String statusMessage;
                                                        int statusColor;
                                                        boolean canRequestCancel = false;
                                                        boolean canRequestSupport = false;

                                                        try {
                                                            Date currentDate = new Date();
                                                            Date bookingDate = tgDat.isEmpty() ? currentDate : dateFormat.parse(tgDat);
                                                            String dateRange = (tgCheckin.isEmpty() || tgCheckout.isEmpty()) ? "N/A" :
                                                                    displayDateFormat.format(dateFormat.parse(tgCheckin)) + " - " +
                                                                            displayDateFormat.format(dateFormat.parse(tgCheckout));

                                                            if ("Chờ xác nhận".equals(trangThai)) {
                                                                statusMessage = "Chờ xác nhận";
                                                                statusColor = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
                                                                canRequestCancel = true;
                                                                canRequestSupport = true;
                                                            } else if ("Đã xác nhận".equals(trangThai)) {
                                                                statusMessage = "Đã xác nhận";
                                                                statusColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark);
                                                                canRequestSupport = true;
                                                                canRequestCancel = true;
                                                            } else if ("Đang ở".equals(trangThai)) {
                                                                statusMessage = "Đang ở";
                                                                statusColor = ContextCompat.getColor(this, android.R.color.holo_green_dark);
                                                                canRequestSupport = true;
                                                                canRequestCancel = currentDate.getTime() - bookingDate.getTime() >= FIVE_DAYS_IN_MILLIS;
                                                            } else if ("Đã trả phòng".equals(trangThai)) {
                                                                statusMessage = "Đã trả phòng";
                                                                statusColor = ContextCompat.getColor(this, android.R.color.darker_gray);
                                                                canRequestCancel = currentDate.getTime() - bookingDate.getTime() >= FIVE_DAYS_IN_MILLIS;
                                                            } else if ("Đã hủy".equals(trangThai)) {
                                                                statusMessage = "Đã hủy";
                                                                statusColor = ContextCompat.getColor(this, android.R.color.holo_red_dark);
                                                            } else {
                                                                statusMessage = "Trạng thái: " + (trangThai != null ? trangThai : "N/A");
                                                                statusColor = Color.BLACK;
                                                            }

                                                            String formattedPrice = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(tongGia);
                                                            String formattedBookingTime = tgDat.isEmpty() ? "N/A" : displayDateTimeFormat.format(bookingDate);

                                                            if (!uniqueBookings.containsKey(maDon)) {
                                                                BookingModel booking = new BookingModel(
                                                                        maDon, invoiceId, maPhong, roomName, dateRange, trangThai,
                                                                        statusMessage, statusColor, formattedPrice, hotelName,
                                                                        formattedBookingTime, ghiChu, maGiamGia, canRequestCancel, canRequestSupport
                                                                );
                                                                uniqueBookings.put(maDon, booking);
                                                            }
                                                        } catch (ParseException e) {
                                                            statusMessage = "Lỗi xử lý ngày!";
                                                            statusColor = Color.BLACK;
                                                        }
                                                        return Tasks.forResult(null);
                                                    });
                                                }
                                            });
                                });

                        tasks.add(bookingTask);
                    }

                    Tasks.whenAllComplete(tasks).addOnCompleteListener(task -> {
                        if (!isActive) {
                            isLoadingBookings = false;
                            return;
                        }
                        synchronized (lock) {
                            bookingList.addAll(uniqueBookings.values());
                            bookingAdapter.notifyDataSetChanged();
                            // Cập nhật số lượng đơn lên TextView
                            tvPendingCount.setText(String.valueOf(pendingCount.get()));
                            tvCompletedCount.setText(String.valueOf(completedCount.get()));
                            isLoadingBookings = false;
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi tải đơn đặt phòng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // Cập nhật TextView về 0 nếu lỗi
                        tvPendingCount.setText("0");
                        tvCompletedCount.setText("0");
                        isLoadingBookings = false;
                    }
                });
    }

    private void handleRequestCancel(String maDon, String invoiceId, String trangThai) {
        try {
            Date currentDate = new Date();
            db.collection("invoices").document(invoiceId).get()
                    .addOnSuccessListener(doc -> {
                        if (!isActive) return;
                        String tgDat = doc.getString("TGDat") != null ? doc.getString("TGDat") : "";
                        try {
                            Date bookingDate = tgDat.isEmpty() ? currentDate : dateFormat.parse(tgDat);

                            if (!"Chờ xác nhận".equals(trangThai) &&
                                    ("Đang ở".equals(trangThai) || "Đã trả phòng".equals(trangThai)) &&
                                    currentDate.getTime() - bookingDate.getTime() < FIVE_DAYS_IN_MILLIS) {
                                Toast.makeText(this, "Không thể gửi yêu cầu hủy vì chưa đủ 5 ngày kể từ ngày đặt!", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            new AlertDialog.Builder(this)
                                    .setTitle("Xác nhận yêu cầu hủy đơn")
                                    .setMessage("Bạn có muốn gửi yêu cầu hủy đơn " + maDon + " đến quản lý?")
                                    .setPositiveButton("Gửi yêu cầu", (dialog, which) -> {
                                        DocumentReference notificationRef = db.collection("notifications").document();
                                        Map<String, Object> notificationData = new HashMap<>();
                                        notificationData.put("userId", "admin");
                                        notificationData.put("title", "Yêu cầu hủy đơn");
                                        notificationData.put("message", "Khách hàng yêu cầu hủy đơn " + maDon);
                                        notificationData.put("timestamp", dateFormat.format(new Date()));
                                        notificationData.put("isRead", false);
                                        notificationData.put("type", "cancel_request");
                                        notificationData.put("maDon", maDon);
                                        notificationData.put("invoiceId", invoiceId);

                                        notificationRef.set(notificationData)
                                                .addOnSuccessListener(aVoid -> {
                                                    if (isActive) {
                                                        Toast.makeText(this, "Yêu cầu hủy đơn " + maDon + " đã được gửi!", Toast.LENGTH_LONG).show();
                                                        loadBookings();
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    if (isActive) {
                                                        Toast.makeText(this, "Lỗi gửi yêu cầu hủy: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    })
                                    .setNegativeButton("Không", null)
                                    .show();
                        } catch (ParseException e) {
                            Toast.makeText(this, "Lỗi xử lý ngày!", Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isActive) {
                            Toast.makeText(this, "Lỗi tải thông tin đơn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi xử lý yêu cầu hủy!", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSupportRequest(String maDon, String invoiceId) {
        new AlertDialog.Builder(this)
                .setTitle("Yêu cầu hỗ trợ")
                .setMessage("Bạn muốn yêu cầu hỗ trợ cho đơn " + maDon + "?")
                .setPositiveButton("Gửi yêu cầu", (dialog, which) -> {
                    DocumentReference notificationRef = db.collection("notifications").document();
                    Map<String, Object> notificationData = new HashMap<>();
                    notificationData.put("userId", "admin");
                    notificationData.put("title", "Yêu cầu hỗ trợ");
                    notificationData.put("message", "Khách hàng yêu cầu hỗ trợ cho đơn " + maDon);
                    notificationData.put("timestamp", dateFormat.format(new Date()));
                    notificationData.put("isRead", false);
                    notificationData.put("type", "support_request");
                    notificationData.put("maDon", maDon);
                    notificationData.put("invoiceId", invoiceId);

                    notificationRef.set(notificationData)
                            .addOnSuccessListener(aVoid -> {
                                if (isActive) {
                                    Toast.makeText(this, "Yêu cầu hỗ trợ đã được gửi!", Toast.LENGTH_SHORT).show();
                                    loadBookings();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (isActive) {
                                    Toast.makeText(this, "Lỗi gửi yêu cầu hỗ trợ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Hủy", null)
                .show();
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
        loadBookings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }

    private static class BookingModel {
        String maDon, invoiceId, roomName, dateRange, trangThai, statusMessage, totalPrice, hotelName, bookingTime, ghiChu, maGiamGia;
        int maPhong, statusColor;
        boolean canRequestCancel, canRequestSupport;

        BookingModel(String maDon, String invoiceId, int maPhong, String roomName, String dateRange,
                     String trangThai, String statusMessage, int statusColor, String totalPrice, String hotelName,
                     String bookingTime, String ghiChu, String maGiamGia, boolean canRequestCancel, boolean canRequestSupport) {
            this.maDon = maDon;
            this.invoiceId = invoiceId;
            this.maPhong = maPhong;
            this.roomName = roomName;
            this.dateRange = dateRange;
            this.trangThai = trangThai;
            this.statusMessage = statusMessage;
            this.statusColor = statusColor;
            this.totalPrice = totalPrice;
            this.hotelName = hotelName;
            this.bookingTime = bookingTime;
            this.ghiChu = ghiChu;
            this.maGiamGia = maGiamGia;
            this.canRequestCancel = canRequestCancel;
            this.canRequestSupport = canRequestSupport;
        }
    }

    private class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {
        private final List<BookingModel> bookings;

        BookingAdapter(List<BookingModel> bookings) {
            this.bookings = bookings;
        }

        @NonNull
        @Override
        public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customer_booking, parent, false);
            return new BookingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
            BookingModel booking = bookings.get(position);
            holder.tvBookingId.setText("Mã đơn: " + (booking.maDon != null ? booking.maDon : "N/A"));
            holder.tvRoomName.setText("🏠 Phòng: " + (booking.roomName != null ? booking.roomName : "N/A"));
            holder.tvDateRange.setText("🗓 " + (booking.dateRange != null ? booking.dateRange : "N/A"));
            holder.tvStatus.setText("⏳ " + (booking.statusMessage != null ? booking.statusMessage : "N/A"));
            holder.tvStatus.setTextColor(booking.statusColor);
            holder.tvPrice.setText("💰 " + (booking.totalPrice != null ? booking.totalPrice : "N/A"));
            holder.tvLocation.setText("📍 " + (booking.hotelName != null ? booking.hotelName : "N/A"));
            holder.tvBookingTime.setText("⏱ Đặt lúc: " + (booking.bookingTime != null ? booking.bookingTime : "N/A"));
            holder.tvNote.setText("📎 Ghi chú: " + (booking.ghiChu != null && !booking.ghiChu.isEmpty() ? booking.ghiChu : "Không có"));
            holder.tvVoucher.setText("🎫 Mã giảm giá: " + (booking.maGiamGia != null && !booking.maGiamGia.isEmpty() ? booking.maGiamGia : "Không có"));

            holder.btnRequestCancel.setVisibility(booking.canRequestCancel ? View.VISIBLE : View.GONE);
            holder.btnSupportRequest.setVisibility(booking.canRequestSupport ? View.VISIBLE : View.GONE);

            holder.btnRequestCancel.setOnClickListener(v -> handleRequestCancel(
                    booking.maDon, booking.invoiceId, booking.trangThai));
            holder.btnSupportRequest.setOnClickListener(v -> handleSupportRequest(booking.maDon, booking.invoiceId));
        }

        @Override
        public int getItemCount() {
            return bookings.size();
        }

        class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView tvBookingId, tvRoomName, tvDateRange, tvStatus, tvPrice, tvLocation, tvBookingTime, tvNote, tvVoucher;
            TextView btnRequestCancel, btnSupportRequest;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBookingId = itemView.findViewById(R.id.tvBookingId);
                tvRoomName = itemView.findViewById(R.id.tvRoomName);
                tvDateRange = itemView.findViewById(R.id.tvDateRange);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvPrice = itemView.findViewById(R.id.tvPrice);
                tvLocation = itemView.findViewById(R.id.tvLocation);
                tvBookingTime = itemView.findViewById(R.id.tvBookingTime);
                tvNote = itemView.findViewById(R.id.tvNote);
                tvVoucher = itemView.findViewById(R.id.tvVoucher);
                btnRequestCancel = itemView.findViewById(R.id.btnRequestCancel);
                btnSupportRequest = itemView.findViewById(R.id.btnSupportRequest);
            }
        }
    }
}