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
            // Create Users table
            db.execSQL("CREATE TABLE Users (" +
                    "MaKH INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "HoTen TEXT, " +
                    "SDT TEXT, " +
                    "Email TEXT, " +
                    "Username TEXT UNIQUE, " +
                    "Password TEXT, " +
                    "Role TEXT);");

            // Insert default Admin account
            db.execSQL("INSERT INTO Users (HoTen, SDT, Email, Username, Password, Role) VALUES " +
                    "('Admin', '0123456789', 'admin@example.com', 'admin', '123123', 'admin');");

            // Create Voucher table
            db.execSQL("CREATE TABLE Voucher (" +
                    "MaGiam TEXT PRIMARY KEY, " +
                    "LoaiMa TEXT, " +
                    "MoTa TEXT, " +
                    "ChietKhau REAL);");

            // Insert sample Vouchers
            db.execSQL("INSERT INTO Voucher (MaGiam, LoaiMa, MoTa, ChietKhau) VALUES " +
                    "('MG0001', 'Khuyen mai le', 'Giam gia mua le hoi', 10);");
            db.execSQL("INSERT INTO Voucher (MaGiam, LoaiMa, MoTa, ChietKhau) VALUES " +
                    "('MG0002', 'Khach quen', 'Uu dai cho khach hang than thiet', 15);");

            // Create NhaHang table
            db.execSQL("CREATE TABLE NhaHang (" +
                    "MaNhaHang INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Ten TEXT, " +
                    "DiaChi TEXT, " +
                    "MoTa TEXT, " +
                    "GioMoCua TEXT, " +
                    "GioDongCua TEXT, " +
                    "SDT TEXT);");

            // Create LoaiPhong table
            db.execSQL("CREATE TABLE LoaiPhong (" +
                    "MaLoaiPhong INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "LoaiPhong TEXT, " +
                    "GiaPhong REAL);");

            // Insert sample LoaiPhong data
            db.execSQL("INSERT INTO LoaiPhong (LoaiPhong, GiaPhong) VALUES " +
                    "('Tieu chuan', 500000);");
            db.execSQL("INSERT INTO LoaiPhong (LoaiPhong, GiaPhong) VALUES " +
                    "('VIP', 1000000);");
            db.execSQL("INSERT INTO LoaiPhong (LoaiPhong, GiaPhong) VALUES " +
                    "('5 sao', 2000000);");

            // Create Phong table
            db.execSQL("CREATE TABLE Phong (" +
                    "MaPhong INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "TenPhong TEXT, " +
                    "SLGiuong INTEGER, " +
                    "DienTich REAL, " +
                    "TienIch TEXT, " +
                    "TrangThai TEXT, " +
                    "DonViTinh TEXT, " +
                    "MaLoaiPhong INTEGER, " +
                    "FOREIGN KEY(MaLoaiPhong) REFERENCES LoaiPhong(MaLoaiPhong));");

            // Insert sample Phong data
            db.execSQL("INSERT INTO Phong (TenPhong, SLGiuong, DienTich, TienIch, TrangThai, DonViTinh, MaLoaiPhong) VALUES " +
                    "('Phong 101', 2, 25.0, 'May lanh, TV, Wifi', 'Trong', 'm2', 1);");

            // Create DonDatPhong table
            db.execSQL("CREATE TABLE DonDatPhong (" +
                    "MaDon INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "MaKH INTEGER, " +
                    "MaPhong INTEGER, " +
                    "MaGiam TEXT, " +
                    "TGDat TEXT, " +
                    "TGCheckin TEXT, " +
                    "TGCheckout TEXT, " +
                    "TrangThaiTT TEXT, " +
                    "TrangThaiDD TEXT, " +
                    "FOREIGN KEY(MaKH) REFERENCES Users(MaKH), " +
                    "FOREIGN KEY(MaPhong) REFERENCES Phong(MaPhong), " +
                    "FOREIGN KEY(MaGiam) REFERENCES Voucher(MaGiam));");

            // Create DichVuKhachSan table
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

            // Create ThucPhamPhong table
            db.execSQL("CREATE TABLE ThucPhamPhong (" +
                    "MaTP INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Ten TEXT, " +
                    "Gia REAL, " +
                    "SoLuong INTEGER);");

            // Create HoaDon table
            db.execSQL("CREATE TABLE HoaDon (" +
                    "MaPhieu INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "MaKH INTEGER, " +
                    "TGCapHD TEXT, " +
                    "FOREIGN KEY(MaKH) REFERENCES Users(MaKH));");

            // Create QuanLyTraPhong table
            db.execSQL("CREATE TABLE QuanLyTraPhong (" +
                    "MaTra INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "MaKH INTEGER, " +
                    "MaNV TEXT, " +
                    "TGCheckIn TEXT, " +
                    "TGCheckOut TEXT, " +
                    "GhiChu TEXT, " +
                    "FOREIGN KEY(MaKH) REFERENCES Users(MaKH));");

            // Create DoThatLac table
            db.execSQL("CREATE TABLE DoThatLac (" +
                    "Ma INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "NoiDung TEXT, " +
                    "TG TEXT, " +
                    "TrangThai TEXT, " +
                    "MaNV TEXT);");

        } catch (Exception e) {
            Log.e("DB_ERROR", "Error creating database: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop all tables if they exist
        db.execSQL("DROP TABLE IF EXISTS DoThatLac");
        db.execSQL("DROP TABLE IF EXISTS QuanLyTraPhong");
        db.execSQL("DROP TABLE IF EXISTS HoaDon");
        db.execSQL("DROP TABLE IF EXISTS ThucPhamPhong");
        db.execSQL("DROP TABLE IF EXISTS DichVuKhachSan");
        db.execSQL("DROP TABLE IF EXISTS DonDatPhong");
        db.execSQL("DROP TABLE IF EXISTS Phong");
        db.execSQL("DROP TABLE IF EXISTS LoaiPhong");
        db.execSQL("DROP TABLE IF EXISTS NhaHang");
        db.execSQL("DROP TABLE IF EXISTS Voucher");
        db.execSQL("DROP TABLE IF EXISTS Users");

        // Recreate the database
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Enable foreign key constraints
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }
}