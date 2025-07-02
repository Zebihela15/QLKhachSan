package com.sinhvien.appqlkhachsan;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sinhvien.appqlkhachsan.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class EditBookingDialogFragment extends DialogFragment {

    private EditText etRoomId, etCheckInDate, etCheckOutDate;
    private Button btnSave, btnCancel;
    private FirebaseFirestore db;
    private String bookingId;

    public static EditBookingDialogFragment newInstance(String bookingId, String roomId, String checkInDate, String checkOutDate) {
        EditBookingDialogFragment fragment = new EditBookingDialogFragment();
        Bundle args = new Bundle();
        args.putString("bookingId", bookingId);
        args.putString("roomId", roomId);
        args.putString("checkInDate", checkInDate);
        args.putString("checkOutDate", checkOutDate);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_edit_booking, container, false);

        etRoomId = view.findViewById(R.id.etRoomId);
        etCheckInDate = view.findViewById(R.id.etCheckInDate);
        etCheckOutDate = view.findViewById(R.id.etCheckOutDate);
        btnSave = view.findViewById(R.id.btnSave);
        btnCancel = view.findViewById(R.id.btnCancel);

        db = FirebaseFirestore.getInstance();

        // Populate fields with existing data
        Bundle args = getArguments();
        if (args != null) {
            bookingId = args.getString("bookingId");
            etRoomId.setText(args.getString("roomId"));
            etCheckInDate.setText(args.getString("checkInDate"));
            etCheckOutDate.setText(args.getString("checkOutDate"));
        }

        btnSave.setOnClickListener(v -> saveChanges());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void saveChanges() {
        String roomId = etRoomId.getText().toString().trim();
        String checkInDate = etCheckInDate.getText().toString().trim();
        String checkOutDate = etCheckOutDate.getText().toString().trim();

        if (roomId.isEmpty() || checkInDate.isEmpty() || checkOutDate.isEmpty()) {
            Toast.makeText(getContext(), "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        try {
            Timestamp tgNhan = new Timestamp(sdf.parse(checkInDate));
            Timestamp tgTra = new Timestamp(sdf.parse(checkOutDate));

            db.collection("bookings").document(bookingId)
                    .update("MaPhong", roomId, "ThoiGianNhan", tgNhan, "ThoiGianTra", tgTra)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(getContext(), "Cập nhật thành công", Toast.LENGTH_SHORT).show();
                        dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (ParseException e) {
            Toast.makeText(getContext(), "Định dạng ngày không hợp lệ", Toast.LENGTH_SHORT).show();
        }
    }
}