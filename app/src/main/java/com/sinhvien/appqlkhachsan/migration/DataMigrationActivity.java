package com.sinhvien.appqlkhachsan.migration;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.sinhvien.appqlkhachsan.DatabaseHelper;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DataMigrationActivity extends AppCompatActivity {
    private FirebaseFirestore firestore;
    private SQLiteDatabase localDb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firestore = FirebaseFirestore.getInstance();
        localDb = new DatabaseHelper(this).getReadableDatabase();

        migrateVouchers();
        migrateRestaurants();
        migrateRoomTypes();
        migrateAmenities();
        migrateRooms();
        migrateCustomers();
        migrateBookings();
        migrateInvoices();
        migrateServices();
    }

    private void migrateVouchers() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM Voucher", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("MaGiam", cursor.getString(0));
            data.put("LoaiMa", cursor.getString(1));
            data.put("MoTa", cursor.getString(2));
            data.put("ChietKhau", cursor.getDouble(3));
            firestore.collection("vouchers").document(cursor.getString(0)).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ Voucher migrated");
    }

    private void migrateRestaurants() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM NhaHang", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("Ten", cursor.getString(1));
            data.put("DiaChi", cursor.getString(2));
            data.put("MoTa", cursor.getString(3));
            data.put("GioMoCua", cursor.getString(4));
            data.put("GioDongCua", cursor.getString(5));
            data.put("SDT", cursor.getString(6));
            data.put("HinhAnh", cursor.getInt(7));
            firestore.collection("restaurants").add(data);
        }
        cursor.close();
        Log.d("Migration", "✅ NhaHang migrated");
    }

    private void migrateRoomTypes() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM LoaiPhong", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("MaLoaiPhong", cursor.getInt(0));
            data.put("LoaiPhong", cursor.getString(1));
            data.put("GiaPhong", cursor.getDouble(2));
            firestore.collection("room_types").document(String.valueOf(cursor.getInt(0))).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ LoaiPhong migrated");
    }

    private void migrateAmenities() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM TienIch", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("MaTienIch", cursor.getInt(0));
            data.put("TenTienIch", cursor.getString(1));
            data.put("Icon", cursor.getInt(2));
            firestore.collection("amenities").document(String.valueOf(cursor.getInt(0))).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ TienIch migrated");
    }

    private void migrateRooms() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM Phong", null);
        while (cursor.moveToNext()) {
            int roomId = cursor.getInt(0);

            Map<String, Object> data = new HashMap<>();
            data.put("MaPhong", roomId);
            data.put("TenPhong", cursor.getString(1));
            data.put("SLGiuong", cursor.getInt(2));
            data.put("DienTich", cursor.getDouble(3));
            data.put("TrangThai", cursor.getString(4));
            data.put("DonViTinh", cursor.getString(5));
            data.put("MaLoaiPhong", cursor.getInt(6));
            data.put("MoTa", cursor.getString(7));
            data.put("DiaChi", cursor.getString(8));
            data.put("GhiChu", cursor.getString(9));
            data.put("HinhAnh", cursor.getInt(10));

            // Get amenities for this room
            Cursor amenitiesCursor = localDb.rawQuery(
                    "SELECT MaTienIch FROM Phong_TienIch WHERE MaPhong = ?",
                    new String[]{String.valueOf(roomId)}
            );
            ArrayList<Integer> amenityIds = new ArrayList<>();
            while (amenitiesCursor.moveToNext()) {
                amenityIds.add(amenitiesCursor.getInt(0));
            }
            amenitiesCursor.close();
            data.put("TienIch", amenityIds);

            firestore.collection("rooms").document(String.valueOf(roomId)).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ Phong migrated");
    }

    private void migrateCustomers() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM KhachHang", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("MaKH", cursor.getString(0));
            data.put("TenKH", cursor.getString(1));
            data.put("SDT", cursor.getString(2));
            data.put("Email", cursor.getString(3));
            data.put("CCCD", cursor.getString(4));
            firestore.collection("customers").document(cursor.getString(0)).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ KhachHang migrated");
    }

    private void migrateBookings() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM DonDatPhong", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("MaDon", cursor.getInt(0));
            data.put("MaKH", cursor.getString(1));
            data.put("MaPhong", cursor.getInt(2));
            data.put("MaGiam", cursor.getString(3));
            data.put("TGDat", cursor.getString(4));
            data.put("TGCheckin", cursor.getString(5));
            data.put("TGCheckout", cursor.getString(6));
            data.put("TrangThaiTT", cursor.getString(7));
            data.put("TrangThaiDD", cursor.getString(8));
            firestore.collection("bookings").document(String.valueOf(cursor.getInt(0))).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ DonDatPhong migrated");
    }

    private void migrateInvoices() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM HoaDon", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("MaHoaDon", cursor.getString(0));
            data.put("MaDon", cursor.getInt(1));
            data.put("MaPhong", cursor.getInt(2));
            data.put("TenKhach", cursor.getString(3));
            data.put("SoDienThoai", cursor.getString(4));
            data.put("CCCD", cursor.getString(5));
            data.put("Email", cursor.getString(6));
            data.put("TGCheckin", cursor.getString(7));
            data.put("TGCheckout", cursor.getString(8));
            data.put("TongGia", cursor.getDouble(9));
            data.put("TrangThai", cursor.getString(10));
            data.put("MaGiamGia", cursor.getString(11));
            firestore.collection("invoices").document(cursor.getString(0)).set(data);
        }
        cursor.close();
        Log.d("Migration", "✅ HoaDon migrated");
    }

    private void migrateServices() {
        Cursor cursor = localDb.rawQuery("SELECT * FROM DichVuKhachSan", null);
        while (cursor.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("LoaiDV", cursor.getString(1));
            data.put("DiaDiem", cursor.getString(2));
            data.put("TGMoCua", cursor.getString(3));
            data.put("TGDongCua", cursor.getString(4));
            data.put("TrangThai", cursor.getString(5));
            data.put("SDT", cursor.getString(6));
            data.put("Ten", cursor.getString(7));
            data.put("GiaTB", cursor.getDouble(8));
            firestore.collection("services").add(data);
        }
        cursor.close();
        Log.d("Migration", "✅ DichVu migrated");
    }
}
