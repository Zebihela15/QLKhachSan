package com.sinhvien.appqlkhachsan.admin;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class InitializeFirestoreData {
    private static final String TAG = "InitializeFirestoreData";
    private final FirebaseFirestore db;
    private final Context context;
    private static final String INITIALIZED_KEY = "is_initialized";

    public InitializeFirestoreData(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
    }

    public void initializeSampleData() {
        // Kiểm tra xem dữ liệu đã được khởi tạo chưa
        db.collection("metadata").document("initialization")
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && documentSnapshot.getBoolean(INITIALIZED_KEY) == Boolean.TRUE) {
                        Log.d(TAG, "Dữ liệu mẫu đã được khởi tạo trước đó, bỏ qua.");
                        showToast("Dữ liệu mẫu đã tồn tại.");
                        return;
                    }
                    // Thêm dữ liệu mẫu nếu chưa được khởi tạo
                    addSampleData();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi kiểm tra trạng thái khởi tạo: " + e.getMessage());
                    showToast("Lỗi kiểm tra trạng thái: " + e.getMessage());
                    // Vẫn cố gắng thêm dữ liệu mẫu nếu có lỗi
                    addSampleData();
                });
    }

    private void addSampleData() {
        WriteBatch batch = db.batch();

        // 1. Collection: room_types
        Map<String, Object> roomType1 = new HashMap<>();
        roomType1.put("MaLoaiPhong", 1);
        roomType1.put("TenLoaiPhong", "Standard");
        roomType1.put("GiaPhong", 500000.0);
        batch.set(db.collection("room_types").document("1"), roomType1);

        Map<String, Object> roomType2 = new HashMap<>();
        roomType2.put("MaLoaiPhong", 2);
        roomType2.put("TenLoaiPhong", "VIP");
        roomType2.put("GiaPhong", 800000.0);
        batch.set(db.collection("room_types").document("2"), roomType2);

        Map<String, Object> roomType3 = new HashMap<>();
        roomType3.put("MaLoaiPhong", 3);
        roomType3.put("TenLoaiPhong", "Deluxe");
        roomType3.put("GiaPhong", 1200000.0);
        batch.set(db.collection("room_types").document("3"), roomType3);

        // 2. Collection: rooms
        Map<String, Object> room101 = new HashMap<>();
        room101.put("MaPhong", 101);
        room101.put("TenPhong", "Phòng 101");
        room101.put("LoaiPhong", "Standard");
        room101.put("MaLoaiPhong", 1);
        room101.put("TrangThai", "Đang sử dụng");
        room101.put("SoLuongNguoiToiDa", 2);
        batch.set(db.collection("rooms").document("101"), room101);

        Map<String, Object> room102 = new HashMap<>();
        room102.put("MaPhong", 102);
        room102.put("TenPhong", "Phòng 102");
        room102.put("LoaiPhong", "Standard");
        room102.put("MaLoaiPhong", 1);
        room102.put("TrangThai", "Trống");
        room102.put("SoLuongNguoiToiDa", 2);
        batch.set(db.collection("rooms").document("102"), room102);

        Map<String, Object> room201 = new HashMap<>();
        room201.put("MaPhong", 201);
        room201.put("TenPhong", "Phòng 201");
        room201.put("LoaiPhong", "VIP");
        room201.put("MaLoaiPhong", 2);
        room201.put("TrangThai", "Trống");
        room201.put("SoLuongNguoiToiDa", 3);
        batch.set(db.collection("rooms").document("201"), room201);

        Map<String, Object> room202 = new HashMap<>();
        room202.put("MaPhong", 202);
        room202.put("TenPhong", "Phòng 202");
        room202.put("LoaiPhong", "Deluxe");
        room202.put("MaLoaiPhong", 3);
        room202.put("TrangThai", "Đã đặt");
        room202.put("SoLuongNguoiToiDa", 4);
        batch.set(db.collection("rooms").document("202"), room202);

        // 3. Collection: customers
        Map<String, Object> customer1 = new HashMap<>();
        customer1.put("MaKH", "CUST001");
        customer1.put("TenKH", "Nguyễn Văn A");
        customer1.put("SDT", "0901234567");
        customer1.put("CCCD", "123456789");
        customer1.put("Email", "nguyenvana@example.com");
        customer1.put("BookingCount", 2);
        batch.set(db.collection("customers").document("CUST001"), customer1);

        Map<String, Object> customer2 = new HashMap<>();
        customer2.put("MaKH", "CUST002");
        customer2.put("TenKH", "Trần Thị B");
        customer2.put("SDT", "0912345678");
        customer2.put("CCCD", "987654321");
        customer2.put("Email", "tranthib@example.com");
        customer2.put("BookingCount", 1);
        batch.set(db.collection("customers").document("CUST002"), customer2);

        Map<String, Object> customer3 = new HashMap<>();
        customer3.put("MaKH", "CUST003");
        customer3.put("TenKH", "Lê Văn C");
        customer3.put("SDT", "0923456789");
        customer3.put("CCCD", "456789123");
        customer3.put("Email", "levanc@example.com");
        customer3.put("BookingCount", 1);
        batch.set(db.collection("customers").document("CUST003"), customer3);

        // 4. Collection: bookings
        Map<String, Object> booking1 = new HashMap<>();
        booking1.put("MaKH", "CUST001");
        booking1.put("MaPhong", 101);
        booking1.put("TGCheckin", "2025-07-01 14:00:00");
        booking1.put("TGCheckout", "2025-07-03 12:00:00");
        booking1.put("TrangThaiDD", "Đã nhận phòng");
        booking1.put("YeuCauDacBiet", "Thêm giường phụ");
        booking1.put("SoKhach", 2);
        booking1.put("MaGiam", "VOUCHER10");
        booking1.put("BookingDate", "2025-06-25 09:00:00");
        booking1.put("Source", "Website");
        booking1.put("GhiChu", "");
        batch.set(db.collection("bookings").document("BOOK001"), booking1);

        Map<String, Object> booking2 = new HashMap<>();
        booking2.put("MaKH", "CUST002");
        booking2.put("MaPhong", 102);
        booking2.put("TGCheckin", "2025-07-05 14:00:00");
        booking2.put("TGCheckout", "2025-07-07 12:00:00");
        booking2.put("TrangThaiDD", "Hoàn thành");
        booking2.put("YeuCauDacBiet", "");
        booking2.put("SoKhach", 3);
        booking2.put("MaGiam", "");
        booking2.put("BookingDate", "2025-06-30 10:00:00");
        booking2.put("Source", "App");
        booking2.put("GhiChu", "");
        batch.set(db.collection("bookings").document("BOOK002"), booking2);

        Map<String, Object> booking3 = new HashMap<>();
        booking3.put("MaKH", "CUST003");
        booking3.put("MaPhong", 201);
        booking3.put("TGCheckin", "2025-07-10 14:00:00");
        booking3.put("TGCheckout", "2025-07-12 12:00:00");
        booking3.put("TrangThaiDD", "Đã hủy");
        booking3.put("YeuCauDacBiet", "Cần view biển");
        booking3.put("SoKhach", 1);
        booking3.put("MaGiam", "");
        booking3.put("BookingDate", "2025-07-01 15:00:00");
        booking3.put("Source", "Quầy lễ tân");
        booking3.put("GhiChu", "Hủy bởi khách: Không phù hợp lịch trình");
        batch.set(db.collection("bookings").document("BOOK003"), booking3);

        Map<String, Object> booking4 = new HashMap<>();
        booking4.put("MaKH", "CUST001");
        booking4.put("MaPhong", 202);
        booking4.put("TGCheckin", "2025-07-15 14:00:00");
        booking4.put("TGCheckout", "2025-07-18 12:00:00");
        booking4.put("TrangThaiDD", "Đã xác nhận");
        booking4.put("YeuCauDacBiet", "");
        booking4.put("SoKhach", 2);
        booking4.put("MaGiam", "VOUCHER20");
        booking4.put("BookingDate", "2025-07-05 12:00:00");
        booking4.put("Source", "Website");
        booking4.put("GhiChu", "");
        batch.set(db.collection("bookings").document("BOOK004"), booking4);

        // 5. Collection: invoices
        Map<String, Object> invoice1 = new HashMap<>();
        invoice1.put("MaDon", "BOOK001");
        invoice1.put("TenKhach", "Nguyễn Văn A");
        invoice1.put("SoDienThoai", "0901234567");
        invoice1.put("CCCD", "123456789");
        invoice1.put("Email", "nguyenvana@example.com");
        invoice1.put("TongGia", 1000000.0);
        invoice1.put("TrangThai", "Đã nhận phòng");
        invoice1.put("LyDoHuy", "");
        batch.set(db.collection("invoices").document("INV001"), invoice1);

        Map<String, Object> invoice2 = new HashMap<>();
        invoice2.put("MaDon", "BOOK002");
        invoice2.put("TenKhach", "Trần Thị B");
        invoice2.put("SoDienThoai", "0912345678");
        invoice2.put("CCCD", "987654321");
        invoice2.put("Email", "tranthib@example.com");
        invoice2.put("TongGia", 1000000.0);
        invoice2.put("TrangThai", "Hoàn thành");
        invoice2.put("LyDoHuy", "");
        batch.set(db.collection("invoices").document("INV002"), invoice2);

        Map<String, Object> invoice3 = new HashMap<>();
        invoice3.put("MaDon", "BOOK003");
        invoice3.put("TenKhach", "Lê Văn C");
        invoice3.put("SoDienThoai", "0923456789");
        invoice3.put("CCCD", "456789123");
        invoice3.put("Email", "levanc@example.com");
        invoice3.put("TongGia", 1600000.0);
        invoice3.put("TrangThai", "Đã hủy");
        invoice3.put("LyDoHuy", "Không phù hợp lịch trình");
        batch.set(db.collection("invoices").document("INV003"), invoice3);

        Map<String, Object> invoice4 = new HashMap<>();
        invoice4.put("MaDon", "BOOK004");
        invoice4.put("TenKhach", "Nguyễn Văn A");
        invoice4.put("SoDienThoai", "0901234567");
        invoice4.put("CCCD", "123456789");
        invoice4.put("Email", "nguyenvana@example.com");
        invoice4.put("TongGia", 3240000.0);
        invoice4.put("TrangThai", "Đã xác nhận");
        invoice4.put("LyDoHuy", "");
        batch.set(db.collection("invoices").document("INV004"), invoice4);

        // 6. Collection: services
        Map<String, Object> service1 = new HashMap<>();
        service1.put("ServiceId", "SERV001");
        service1.put("Date", "2025-07-02");
        service1.put("Cost", 200000.0);
        service1.put("Description", "Dịch vụ ăn sáng");
        batch.set(db.collection("services").document("SERV001"), service1);

        Map<String, Object> service2 = new HashMap<>();
        service2.put("ServiceId", "SERV002");
        service2.put("Date", "2025-07-06");
        service2.put("Cost", 300000.0);
        service2.put("Description", "Dịch vụ giặt là");
        batch.set(db.collection("services").document("SERV002"), service2);

        Map<String, Object> service3 = new HashMap<>();
        service3.put("ServiceId", "SERV003");
        service3.put("Date", "2025-07-16");
        service3.put("Cost", 150000.0);
        service3.put("Description", "Dịch vụ nước uống");
        batch.set(db.collection("services").document("SERV003"), service3);

        // 7. Collection: vouchers
        Map<String, Object> voucher1 = new HashMap<>();
        voucher1.put("MaGiam", "VOUCHER10");
        voucher1.put("Valid", true);
        voucher1.put("Discount", 10.0);
        batch.set(db.collection("vouchers").document("VOUCHER10"), voucher1);

        Map<String, Object> voucher2 = new HashMap<>();
        voucher2.put("MaGiam", "VOUCHER20");
        voucher2.put("Valid", true);
        voucher2.put("Discount", 20.0);
        batch.set(db.collection("vouchers").document("VOUCHER20"), voucher2);

        // Đánh dấu rằng dữ liệu đã được khởi tạo
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(INITIALIZED_KEY, true);
        batch.set(db.collection("metadata").document("initialization"), metadata);

        // Thực hiện batch commit
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Thêm dữ liệu mẫu thành công!");
                    showToast("Thêm dữ liệu mẫu thành công!");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lỗi khi thêm dữ liệu mẫu: " + e.getMessage());
                    showToast("Lỗi khi thêm dữ liệu mẫu: " + e.getMessage());
                });
    }

    private void showToast(String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
