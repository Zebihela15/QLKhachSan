package com.sinhvien.appqlkhachsan.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.sinhvien.appqlkhachsan.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminBookingListActivity extends AppCompatActivity {
    private FirebaseFirestore db;
    private RecyclerView rvBookingList;
    private Spinner spinnerFilterStatus;
    private List<BookingModel> bookingList;
    private BookingAdapter bookingAdapter;
    private boolean isActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_booking_list);

        db = FirebaseFirestore.getInstance();
        rvBookingList = findViewById(R.id.rvBookingList);
        spinnerFilterStatus = findViewById(R.id.spinnerFilterStatus);

        // Cấu hình Spinner
        String[] statusOptions = {"Tất cả", "Đang xử lý", "Đã nhận phòng", "Hoàn tất"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterStatus.setAdapter(statusAdapter);

        // Cấu hình RecyclerView
        rvBookingList.setLayoutManager(new LinearLayoutManager(this));
        bookingList = new ArrayList<>();
        bookingAdapter = new BookingAdapter(bookingList);
        rvBookingList.setAdapter(bookingAdapter);

        // Lắng nghe sự kiện lọc
        spinnerFilterStatus.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadBookings();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        loadBookings();
    }

    private void loadBookings() {
        if (!isActive) return;
        String selectedStatus = spinnerFilterStatus.getSelectedItem().toString();
        bookingList.clear();

        Query query = db.collection("bookings");
        if (!selectedStatus.equals("Tất cả")) {
            query = query.whereEqualTo("TrangThaiDD", selectedStatus);
        }

        query.get().addOnSuccessListener(querySnapshot -> {
            if (!isActive) return;
            List<DocumentSnapshot> docs = querySnapshot.getDocuments();

            for (DocumentSnapshot doc : docs) {
                String maDon = doc.getId();
                String maKH = doc.getString("MaKH");
                Integer maPhong = doc.getLong("MaPhong") != null ? doc.getLong("MaPhong").intValue() : 0;
                String tgCheckin = doc.getString("TGCheckin");
                String tgCheckout = doc.getString("TGCheckout");
                String trangThaiDD = doc.getString("TrangThaiDD");
                String trangThaiTT = doc.getString("TrangThaiTT");

                // Truy vấn tên phòng
                db.collection("rooms").document(String.valueOf(maPhong)).get().addOnSuccessListener(roomDoc -> {
                    if (!isActive) return;
                    String roomName = roomDoc.getString("TenPhong") != null ? roomDoc.getString("TenPhong") : "N/A";

                    // Truy vấn tên khách
                    db.collection("customers").document(maKH).get().addOnSuccessListener(customerDoc -> {
                        if (!isActive) return;
                        String customerName = customerDoc.getString("TenKH") != null ? customerDoc.getString("TenKH") : "N/A";

                        BookingModel booking = new BookingModel(maDon, maKH, maPhong, roomName, customerName,
                                tgCheckin, tgCheckout, trangThaiDD, trangThaiTT);
                        bookingList.add(booking);

                        // Cập nhật adapter sau khi có đủ dữ liệu
                        bookingAdapter.notifyDataSetChanged();
                    }).addOnFailureListener(e -> {
                        BookingModel booking = new BookingModel(maDon, maKH, maPhong, roomName, "N/A",
                                tgCheckin, tgCheckout, trangThaiDD, trangThaiTT);
                        bookingList.add(booking);
                        bookingAdapter.notifyDataSetChanged();
                    });
                });
            }

            if (docs.isEmpty()) {
                Toast.makeText(this, "Không có đơn đặt phòng nào!", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            if (isActive) {
                Toast.makeText(this, "Lỗi tải danh sách đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    private void handleCheckIn(String maDon, int maPhong) {
        String checkInTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("TrangThaiDD", "Đã nhận phòng");
        updates.put("CheckInTime", checkInTime);

        db.collection("bookings").document(maDon).update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (!isActive) return;
                    db.collection("rooms").document(String.valueOf(maPhong))
                            .update("TrangThai", "Đang sử dụng")
                            .addOnSuccessListener(aVoid1 -> {
                                if (isActive) {
                                    Toast.makeText(this, "Check-in thành công!", Toast.LENGTH_SHORT).show();
                                    loadBookings();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (isActive) {
                                    Toast.makeText(this, "Lỗi cập nhật trạng thái phòng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi check-in: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleCheckOut(String maDon, int maPhong) {
        db.collection("invoices").whereEqualTo("MaDon", maDon).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive || querySnapshot.isEmpty()) {
                        if (isActive) {
                            Toast.makeText(this, "Không tìm thấy hóa đơn!", Toast.LENGTH_LONG).show();
                        }
                        return;
                    }
                    DocumentSnapshot invoiceDoc = querySnapshot.getDocuments().get(0);
                    String invoiceId = invoiceDoc.getId();
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("TrangThai", "Hoàn tất");
                    db.collection("invoices").document(invoiceId).update(updates)
                            .addOnSuccessListener(aVoid -> {
                                if (!isActive) return;
                                db.collection("bookings").document(maDon).update("TrangThaiDD", "Hoàn tất")
                                        .addOnSuccessListener(aVoid1 -> {
                                            if (!isActive) return;
                                            db.collection("rooms").document(String.valueOf(maPhong))
                                                    .update("TrangThai", "Trống")
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        if (isActive) {
                                                            Toast.makeText(this, "Check-out thành công!", Toast.LENGTH_SHORT).show();
                                                            loadBookings();
                                                        }
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        if (isActive) {
                                                            Toast.makeText(this, "Lỗi cập nhật trạng thái phòng: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                        }
                                                    });
                                        })
                                        .addOnFailureListener(e -> {
                                            if (isActive) {
                                                Toast.makeText(this, "Lỗi cập nhật trạng thái đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                if (isActive) {
                                    Toast.makeText(this, "Lỗi cập nhật hóa đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi tải hóa đơn: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
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
        String maDon, maKH, roomName, customerName, tgCheckin, tgCheckout, trangThaiDD, trangThaiTT;
        int maPhong;

        BookingModel(String maDon, String maKH, int maPhong, String roomName, String customerName, String tgCheckin, String tgCheckout, String trangThaiDD, String trangThaiTT) {
            this.maDon = maDon;
            this.maKH = maKH;
            this.maPhong = maPhong;
            this.roomName = roomName;
            this.customerName = customerName;
            this.tgCheckin = tgCheckin;
            this.tgCheckout = tgCheckout;
            this.trangThaiDD = trangThaiDD;
            this.trangThaiTT = trangThaiTT;
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_booking, parent, false);
            return new BookingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
            BookingModel booking = bookings.get(position);
            holder.tvBookingId.setText("Mã đơn: " + booking.maDon);
            holder.tvCustomerName.setText("Khách hàng: " + (booking.customerName != null ? booking.customerName : "N/A"));
            holder.tvRoomName.setText("Phòng: " + (booking.roomName != null ? booking.roomName : "N/A"));
            holder.tvDateRange.setText("Ngày nhận - trả: " + (booking.tgCheckin != null ? booking.tgCheckin : "N/A") + " - " + (booking.tgCheckout != null ? booking.tgCheckout : "N/A"));
            holder.tvStatusDD.setText("Trạng thái: " + (booking.trangThaiDD != null ? booking.trangThaiDD : "N/A"));

            // Hiển thị nút Check-in
            holder.btnCheckIn.setVisibility("Đang xử lý".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnCheckIn.setOnClickListener(v -> handleCheckIn(booking.maDon, booking.maPhong));

            // Hiển thị nút Check-out
            holder.btnCheckOut.setVisibility("Đã nhận phòng".equals(booking.trangThaiDD) ? View.VISIBLE : View.GONE);
            holder.btnCheckOut.setOnClickListener(v -> handleCheckOut(booking.maDon, booking.maPhong));
        }

        @Override
        public int getItemCount() {
            return bookings.size();
        }

        class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView tvBookingId, tvCustomerName, tvRoomName, tvDateRange, tvStatusDD;
            Button btnCheckIn, btnCheckOut;

            BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBookingId = itemView.findViewById(R.id.tvBookingId);
                tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
                tvRoomName = itemView.findViewById(R.id.tvRoomName);
                tvDateRange = itemView.findViewById(R.id.tvDateRange);
                tvStatusDD = itemView.findViewById(R.id.tvStatusDD);
                btnCheckIn = itemView.findViewById(R.id.btnCheckIn);
                btnCheckOut = itemView.findViewById(R.id.btnCheckOut);
            }
        }
    }
}