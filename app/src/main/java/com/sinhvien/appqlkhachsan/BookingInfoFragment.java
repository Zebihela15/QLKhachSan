package com.sinhvien.appqlkhachsan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.sinhvien.appqlkhachsan.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BookingInfoFragment extends Fragment {

    private TextView tvRoom, tvDateRange, tvStatus;
    private Button btnCheckOutEarly;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public BookingInfoFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booking_info, container, false);

        tvRoom = view.findViewById(R.id.tvRoom);
        tvDateRange = view.findViewById(R.id.tvDateRange);
        tvStatus = view.findViewById(R.id.tvStatus);
        btnCheckOutEarly = view.findViewById(R.id.btnCheckOutEarly);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadBookingData();

        btnCheckOutEarly.setOnClickListener(v -> handleEarlyCheckout());

        return view;
    }

    private void loadBookingData() {
        String uid = auth.getUid();
        if (uid == null) {
            tvRoom.setText("Không có đơn nào.");
            btnCheckOutEarly.setEnabled(false);
            return;
        }

        db.collection("bookings")
                .whereEqualTo("MaKH", uid)
                .whereEqualTo("TrangThai", "Đã nhận phòng")
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        tvRoom.setText("Không có đơn nào.");
                        tvDateRange.setText("Ngày: N/A");
                        tvStatus.setText("Trạng thái: N/A");
                        btnCheckOutEarly.setEnabled(false);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : query) {
                        String roomId = doc.getString("MaPhong");
                        Timestamp tgNhan = doc.getTimestamp("ThoiGianNhan");
                        Timestamp tgTra = doc.getTimestamp("ThoiGianTra");

                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        tvRoom.setText("Phòng: " + roomId);
                        tvDateRange.setText("Thời gian: " + sdf.format(tgNhan.toDate()) + " - " + sdf.format(tgTra.toDate()));
                        tvStatus.setText("Trạng thái: Đã nhận phòng");

                        // Enable early checkout only if current time is between check-in and check-out
                        Date now = new Date();
                        btnCheckOutEarly.setEnabled(now.after(tgNhan.toDate()) && now.before(tgTra.toDate()));
                    }
                });
    }

    private void handleEarlyCheckout() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.collection("bookings")
                .whereEqualTo("MaKH", uid)
                .whereEqualTo("TrangThai", "Đã nhận phòng")
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        QueryDocumentSnapshot doc = (QueryDocumentSnapshot) query.getDocuments().get(0);
                        String bookingId = doc.getId();

                        db.collection("bookings").document(bookingId)
                                .update("TrangThai", "Đã trả phòng", "ThoiGianTra", Timestamp.now())
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(getContext(), "Đã trả phòng sớm. Vui lòng thanh toán tại quầy.", Toast.LENGTH_LONG).show();
                                    loadBookingData();
                                });
                    }
                });
    }
}