package com.sinhvien.appqlkhachsan;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BookingHistoryActivity extends AppCompatActivity {

    private RecyclerView lvOrderHistory;
    private TextView tvNoOrders;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private List<InvoiceModel> invoiceList;
    private static final String TAG = "BookingHistory";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_history);

        // Khởi tạo Firestore và FirebaseAuth
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        lvOrderHistory = findViewById(R.id.lvOrderHistory);
        tvNoOrders = findViewById(R.id.tvNoOrders);

        if (lvOrderHistory == null || tvNoOrders == null) {
            Log.e(TAG, "Một hoặc nhiều view không được khởi tạo trong layout");
            return;
        }

        // Cấu hình RecyclerView
        lvOrderHistory.setLayoutManager(new LinearLayoutManager(this));
        lvOrderHistory.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Tải dữ liệu
        loadInvoiceHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Làm mới dữ liệu khi quay lại activity
        loadInvoiceHistory();
    }

    private void loadInvoiceHistory() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "Người dùng chưa đăng nhập");
            updateUIWithNoOrders("Vui lòng đăng nhập để xem lịch sử đặt phòng!");
            return;
        }

        String maKH = currentUser.getUid(); // Sử dụng UID làm MaKH
        Log.d(TAG, "MaKH: " + maKH);

        invoiceList = new ArrayList<>();
        // Truy vấn bookings để lấy danh sách MaDon của người dùng
        firestore.collection("bookings")
                .whereEqualTo("MaKH", maKH)
                .get()
                .addOnSuccessListener(bookingsSnapshot -> {
                    Set<String> maDonSet = new HashSet<>(); // Sử dụng HashSet để loại bỏ trùng lặp
                    for (QueryDocumentSnapshot bookingDoc : bookingsSnapshot) {
                        String maDon = bookingDoc.getId(); // Sử dụng document ID làm MaDon
                        maDonSet.add(maDon);
                        Log.d(TAG, "Found MaDon: " + maDon);
                    }
                    List<String> maDonList = new ArrayList<>(maDonSet); // Chuyển sang List sau khi loại trùng

                    Log.d(TAG, "Unique MaDon count: " + maDonList.size());

                    if (maDonList.isEmpty()) {
                        updateUIWithNoOrders("Không có lịch sử đặt phòng cho tài khoản này!");
                        return;
                    }

                    // Truy vấn invoices dựa trên MaDon
                    loadInvoices(maDonList);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi tải đơn đặt phòng: " + e.getMessage());
                    updateUIWithNoOrders("Lỗi tải lịch sử đặt phòng!");
                });
    }

    private void loadInvoices(List<String> maDonList) {
        firestore.collection("invoices")
                .whereIn("MaDon", maDonList)
                .get()
                .addOnSuccessListener(invoicesSnapshot -> {
                    if (invoicesSnapshot.isEmpty()) {
                        updateUIWithNoOrders("Không có hóa đơn cho tài khoản này!");
                        return;
                    }

                    Map<String, InvoiceModel> uniqueInvoices = new HashMap<>(); // Sử dụng Map để tránh trùng lặp dựa trên MaDon

                    for (QueryDocumentSnapshot invoiceDoc : invoicesSnapshot) {
                        String maDon = invoiceDoc.getString("MaDon");
                        if (maDon == null || uniqueInvoices.containsKey(maDon)) {
                            continue; // Bỏ qua nếu MaDon null hoặc đã xử lý
                        }

                        String invoiceId = invoiceDoc.getString("MaHoaDon");
                        String maPhong = String.valueOf(invoiceDoc.getLong("MaPhong"));
                        String checkInDate = invoiceDoc.getString("TGCheckin");
                        String checkOutDate = invoiceDoc.getString("TGCheckout");
                        double totalPrice = invoiceDoc.getDouble("TongGia") != null ? invoiceDoc.getDouble("TongGia") : 0.0;
                        String status = invoiceDoc.getString("TrangThai");

                        // Truy vấn rooms để lấy TenPhong
                        firestore.collection("rooms")
                                .document(maPhong)
                                .get()
                                .addOnSuccessListener(roomDoc -> {
                                    String roomName = roomDoc.getString("TenPhong");
                                    InvoiceModel invoice = new InvoiceModel(
                                            invoiceId != null ? invoiceId : "Unknown",
                                            roomName != null ? roomName : "Unknown",
                                            checkInDate != null ? checkInDate : "N/A",
                                            checkOutDate != null ? checkOutDate : "N/A",
                                            totalPrice,
                                            status != null ? status : "N/A"
                                    );
                                    uniqueInvoices.put(maDon, invoice);
                                    Log.d(TAG, "Processed MaDon: " + maDon + ", InvoiceId: " + invoiceId);
                                    updateInvoiceList(uniqueInvoices);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Lỗi lấy thông tin phòng cho MaDon " + maDon + ": " + e.getMessage());
                                    InvoiceModel invoice = new InvoiceModel(
                                            invoiceId != null ? invoiceId : "Unknown",
                                            "Unknown",
                                            checkInDate != null ? checkInDate : "N/A",
                                            checkOutDate != null ? checkOutDate : "N/A",
                                            totalPrice,
                                            status != null ? status : "N/A"
                                    );
                                    uniqueInvoices.put(maDon, invoice);
                                    updateInvoiceList(uniqueInvoices);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi tải hóa đơn: " + e.getMessage());
                    updateUIWithNoOrders("Lỗi tải hóa đơn!");
                });
    }

    private void updateInvoiceList(Map<String, InvoiceModel> uniqueInvoices) {
        runOnUiThread(() -> {
            invoiceList.clear(); // Xóa danh sách cũ
            invoiceList.addAll(uniqueInvoices.values()); // Thêm tất cả invoice duy nhất
            if (lvOrderHistory == null || tvNoOrders == null) {
                Log.e(TAG, "View không được khởi tạo");
                return;
            }
            if (invoiceList.isEmpty()) {
                tvNoOrders.setVisibility(View.VISIBLE);
                lvOrderHistory.setVisibility(View.GONE);
                tvNoOrders.setText("Không có lịch sử đặt phòng cho tài khoản này!");
            } else {
                lvOrderHistory.setVisibility(View.VISIBLE);
                tvNoOrders.setVisibility(View.GONE);
                InvoiceAdapter adapter = new InvoiceAdapter(this, invoiceList);
                lvOrderHistory.setAdapter(adapter);
                Log.d(TAG, "UI updated with " + invoiceList.size() + " items");
            }
        });
    }

    private void updateUIWithNoOrders(String message) {
        runOnUiThread(() -> {
            if (lvOrderHistory == null || tvNoOrders == null) {
                Log.e(TAG, "View không được khởi tạo");
                return;
            }
            lvOrderHistory.setAdapter(null);
            tvNoOrders.setVisibility(View.VISIBLE);
            lvOrderHistory.setVisibility(View.GONE);
            tvNoOrders.setText(message);
        });
    }

    private static class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.InvoiceViewHolder> {
        private final List<InvoiceModel> invoices;
        private final Context context;

        InvoiceAdapter(BookingHistoryActivity context, List<InvoiceModel> invoices) {
            this.context = context;
            this.invoices = invoices;
        }

        @NonNull
        @Override
        public InvoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_booking_history_item, parent, false);
            return new InvoiceViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull InvoiceViewHolder holder, int position) {
            InvoiceModel invoice = invoices.get(position);
            holder.tvInvoiceId.setText("Mã hóa đơn: " + (invoice.invoiceId != null ? invoice.invoiceId : "N/A"));
            holder.tvRoomName.setText("Phòng: " + (invoice.roomName != null ? invoice.roomName : "N/A"));
            holder.tvDates.setText("Ngày nhận - trả: " + (invoice.checkInDate != null ? invoice.checkInDate : "N/A") + " - " + (invoice.checkOutDate != null ? invoice.checkOutDate : "N/A"));
            holder.tvPrice.setText("Giá: " + NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(invoice.totalPrice));
            holder.tvStatus.setText("Trạng thái: " + (invoice.status != null ? invoice.status : "N/A"));
        }

        @Override
        public int getItemCount() {
            return invoices.size();
        }

        static class InvoiceViewHolder extends RecyclerView.ViewHolder {
            TextView tvInvoiceId, tvRoomName, tvDates, tvPrice, tvStatus;

            InvoiceViewHolder(@NonNull View itemView) {
                super(itemView);
                tvInvoiceId = itemView.findViewById(R.id.tvInvoiceId);
                tvRoomName = itemView.findViewById(R.id.tvRoomName);
                tvDates = itemView.findViewById(R.id.tvDates);
                tvPrice = itemView.findViewById(R.id.tvPrice);
                tvStatus = itemView.findViewById(R.id.tvStatus);
            }
        }
    }

    private static class InvoiceModel {
        String invoiceId, roomName, checkInDate, checkOutDate, status;
        double totalPrice;

        InvoiceModel(String invoiceId, String roomName, String checkInDate, String checkOutDate, double totalPrice, String status) {
            this.invoiceId = invoiceId;
            this.roomName = roomName;
            this.checkInDate = checkInDate;
            this.checkOutDate = checkOutDate;
            this.totalPrice = totalPrice;
            this.status = status;
        }
    }
}