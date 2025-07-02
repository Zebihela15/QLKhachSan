package com.sinhvien.appqlkhachsan;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "HotelManager.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            Log.d("DB_DEBUG", "Creating hotel database...");

            // Bảng Voucher
            db.execSQL("CREATE TABLE Voucher (" +
                    "MaGiam TEXT PRIMARY KEY, " +
                    "LoaiMa TEXT, " +
                    "MoTa TEXT, " +
                    "ChietKhau REAL);");
            db.execSQL("INSERT INTO Voucher VALUES ('MG0001', 'Khuyen mai le', 'Giam gia mua le hoi', 10);");
            db.execSQL("INSERT INTO Voucher VALUES ('MG0002', 'Khach quen', 'Uu dai cho khach hang than thiet', 15);");
            db.execSQL("INSERT INTO Voucher VALUES ('MG0003', 'Khuyen mai moi', 'Giam gia cho khach moi', 20);");

            // Bảng NhaHang
            db.execSQL("CREATE TABLE NhaHang (" +
                    "MaNhaHang INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Ten TEXT, " +
                    "DiaChi TEXT, " +
                    "MoTa TEXT, " +
                    "GioMoCua TEXT, " +
                    "GioDongCua TEXT, " +
                    "SDT TEXT, " +
                    "HinhAnh INTEGER);");
            db.execSQL("INSERT INTO NhaHang (Ten, DiaChi, MoTa, GioMoCua, GioDongCua, SDT, HinhAnh) VALUES " +
                    "('Nhà hàng Buffet quốc tế', '123 Lê Lợi, Q1', 'Buffet đa dạng món ăn', '07:00', '22:00', '0901234567', " + R.drawable.restaurant1 + ");");
            db.execSQL("INSERT INTO NhaHang (Ten, DiaChi, MoTa, GioMoCua, GioDongCua, SDT, HinhAnh) VALUES " +
                    "('Nhà hàng Á Đông', '123 Lê Lợi, Q1', 'Ẩm thực châu Á', '08:00', '23:00', '0907654321', " + R.drawable.restaurant2 + ");");
            db.execSQL("INSERT INTO NhaHang (Ten, DiaChi, MoTa, GioMoCua, GioDongCua, SDT, HinhAnh) VALUES " +
                    "('Nhà hàng Hải sản', '456 Nguyễn Huệ, Q1', 'Hải sản tươi sống', '10:00', '22:00', '0909876543', " + R.drawable.restaurant3 + ");");

            // Bảng LoaiPhong
            db.execSQL("CREATE TABLE LoaiPhong (" +
                    "MaLoaiPhong INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "LoaiPhong TEXT, " +
                    "GiaPhong REAL);");
            db.execSQL("INSERT INTO LoaiPhong (LoaiPhong, GiaPhong) VALUES ('Tiêu chuẩn', 500000);");
            db.execSQL("INSERT INTO LoaiPhong (LoaiPhong, GiaPhong) VALUES ('VIP', 1000000);");
            db.execSQL("INSERT INTO LoaiPhong (LoaiPhong, GiaPhong) VALUES ('Deluxe', 800000);");

            // Bảng TienIch
            db.execSQL("CREATE TABLE TienIch (" +
                    "MaTienIch INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "TenTienIch TEXT, " +
                    "Icon INTEGER);");
            db.execSQL("INSERT INTO TienIch (TenTienIch, Icon) VALUES ('Wifi', " + R.drawable.ic_wifi + ");");
            db.execSQL("INSERT INTO TienIch (TenTienIch, Icon) VALUES ('Điều hòa', " + R.drawable.ic_air_conditioner + ");");
            db.execSQL("INSERT INTO TienIch (TenTienIch, Icon) VALUES ('TV', " + R.drawable.ic_tv + ");");
            db.execSQL("INSERT INTO TienIch (TenTienIch, Icon) VALUES ('Tủ lạnh', " + R.drawable.ic_fridge + ");");
            db.execSQL("INSERT INTO TienIch (TenTienIch, Icon) VALUES ('Ban công', " + R.drawable.ic_balcony + ");");

            // Bảng Phong
            db.execSQL("CREATE TABLE Phong (" +
                    "MaPhong INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "TenPhong TEXT, " +
                    "SLGiuong INTEGER, " +
                    "DienTich REAL, " +
                    "TrangThai TEXT, " +
                    "DonViTinh TEXT, " +
                    "MaLoaiPhong INTEGER, " +
                    "MoTa TEXT, " +
                    "DiaChi TEXT, " +
                    "GhiChu TEXT, " +
                    "HinhAnh INTEGER, " +
                    "FOREIGN KEY(MaLoaiPhong) REFERENCES LoaiPhong(MaLoaiPhong));");
            db.execSQL("INSERT INTO Phong (TenPhong, SLGiuong, DienTich, TrangThai, DonViTinh, MaLoaiPhong, MoTa, DiaChi, GhiChu, HinhAnh) VALUES " +
                    "('Phòng Standard', 2, 25.0, 'Còn trống', 'm2', 1, 'Phòng Standard hiện đại', '123 Lê Lợi, Q1', 'View vườn', " + R.drawable.standard_room + ");");
            db.execSQL("INSERT INTO Phong (TenPhong, SLGiuong, DienTich, TrangThai, DonViTinh, MaLoaiPhong, MoTa, DiaChi, GhiChu, HinhAnh) VALUES " +
                    "('Phòng VIP', 2, 35.0, 'Còn trống', 'm2', 2, 'Phòng VIP sang trọng', '123 Lê Lợi, Q1', 'View thành phố', " + R.drawable.vip_rooms + ");");
            db.execSQL("INSERT INTO Phong (TenPhong, SLGiuong, DienTich, TrangThai, DonViTinh, MaLoaiPhong, MoTa, DiaChi, GhiChu, HinhAnh) VALUES " +
                    "('Phòng Deluxe', 3, 30.0, 'Còn trống', 'm2', 3, 'Phòng Deluxe tiện nghi', '123 Lê Lợi, Q1', 'View biển', " + R.drawable.deluxe_room + ");");

            // Bảng Phong_TienIch
            db.execSQL("CREATE TABLE Phong_TienIch (" +
                    "MaPhong INTEGER, " +
                    "MaTienIch INTEGER, " +
                    "PRIMARY KEY(MaPhong, MaTienIch), " +
                    "FOREIGN KEY(MaPhong) REFERENCES Phong(MaPhong), " +
                    "FOREIGN KEY(MaTienIch) REFERENCES TienIch(MaTienIch));");
            db.execSQL("INSERT INTO Phong_TienIch VALUES (1, 1);"); // Standard - Wifi
            db.execSQL("INSERT INTO Phong_TienIch VALUES (1, 2);"); // Standard - Điều hòa
            db.execSQL("INSERT INTO Phong_TienIch VALUES (2, 1);"); // VIP - Wifi
            db.execSQL("INSERT INTO Phong_TienIch VALUES (2, 2);"); // VIP - Điều hòa
            db.execSQL("INSERT INTO Phong_TienIch VALUES (2, 3);"); // VIP - TV
            db.execSQL("INSERT INTO Phong_TienIch VALUES (3, 1);"); // Deluxe - Wifi
            db.execSQL("INSERT INTO Phong_TienIch VALUES (3, 2);"); // Deluxe - Điều hòa
            db.execSQL("INSERT INTO Phong_TienIch VALUES (3, 3);"); // Deluxe - TV
            db.execSQL("INSERT INTO Phong_TienIch VALUES (3, 4);"); // Deluxe - Tủ lạnh
            db.execSQL("INSERT INTO Phong_TienIch VALUES (3, 5);"); // Deluxe - Ban công

            // Bảng KhachHang
            db.execSQL("CREATE TABLE KhachHang (" +
                    "MaKH TEXT PRIMARY KEY, " +
                    "TenKH TEXT, " +
                    "SDT TEXT, " +
                    "Email TEXT, " +
                    "CCCD TEXT);");

            // Bảng DonDatPhong
            db.execSQL("CREATE TABLE DonDatPhong (" +
                    "MaDon INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "MaKH TEXT, " +
                    "MaPhong INTEGER, " +
                    "MaGiam TEXT, " +
                    "TGDat TEXT, " +
                    "TGCheckin TEXT, " +
                    "TGCheckout TEXT, " +
                    "TrangThaiTT TEXT, " +
                    "TrangThaiDD TEXT, " +
                    "FOREIGN KEY(MaKH) REFERENCES KhachHang(MaKH), " +
                    "FOREIGN KEY(MaPhong) REFERENCES Phong(MaPhong), " +
                    "FOREIGN KEY(MaGiam) REFERENCES Voucher(MaGiam));");

            // Bảng HoaDon
            db.execSQL("CREATE TABLE HoaDon (" +
                    "MaHoaDon TEXT PRIMARY KEY, " +
                    "MaDon INTEGER, " +
                    "MaPhong INTEGER, " +
                    "TenKhach TEXT, " +
                    "SoDienThoai TEXT, " +
                    "CCCD TEXT, " +
                    "Email TEXT, " +
                    "TGCheckin TEXT, " +
                    "TGCheckout TEXT, " +
                    "TongGia REAL, " +
                    "TrangThai TEXT, " +
                    "MaGiamGia TEXT, " +
                    "FOREIGN KEY(MaDon) REFERENCES DonDatPhong(MaDon), " +
                    "FOREIGN KEY(MaPhong) REFERENCES Phong(MaPhong), " +
                    "FOREIGN KEY(MaGiamGia) REFERENCES Voucher(MaGiam));");

            // Bảng DichVuKhachSan
            db.execSQL("CREATE TABLE DichVuKhachSan (" +
                    "MaDV INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "LoaiDV TEXT, " +
                    "DiaDiem TEXT, " +
                    "TGMoCua TEXT, " +
                    "TGDongCua TEXT, " +
                    "TrangThai TEXT, " +
                    "SDT TEXT, " +
                    "Ten TEXT, " +
                    "GiaTB REAL);");

            Log.d("DB_DEBUG", "Database created successfully");
        } catch (Exception e) {
            Log.e("DB_ERROR", "Error creating database: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            db.execSQL("DROP TABLE IF EXISTS Voucher");
            db.execSQL("DROP TABLE IF EXISTS NhaHang");
            db.execSQL("DROP TABLE IF EXISTS LoaiPhong");
            db.execSQL("DROP TABLE IF EXISTS TienIch");
            db.execSQL("DROP TABLE IF EXISTS Phong");
            db.execSQL("DROP TABLE IF EXISTS Phong_TienIch");
            db.execSQL("DROP TABLE IF EXISTS KhachHang");
            db.execSQL("DROP TABLE IF EXISTS DonDatPhong");
            db.execSQL("DROP TABLE IF EXISTS HoaDon");
            db.execSQL("DROP TABLE IF EXISTS DichVuKhachSan");
            onCreate(db);
        } catch (Exception e) {
            Log.e("DB_ERROR", "Error upgrading database: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Log.w("DB_WARNING", "Downgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
            onUpgrade(db, oldVersion, newVersion);
        } catch (Exception e) {
            Log.e("DB_ERROR", "Error downgrading database: " + e.getMessage(), e);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }
}