package com.sinhvien.appqlkhachsan;

// Tạo file mới tên Room.java
public class Room {
    private int MaPhong;
    private String TenPhong;
    private double GiaPhong;
    private String TrangThai;
    // Thêm các trường khác nếu bạn cần

    public Room() {} // Cần constructor rỗng cho Firestore

    // Getters and Setters
    public int getMaPhong() { return MaPhong; }
    public void setMaPhong(int maPhong) { MaPhong = maPhong; }
    public String getTenPhong() { return TenPhong; }
    public void setTenPhong(String tenPhong) { TenPhong = tenPhong; }
    public double getGiaPhong() { return GiaPhong; }
    public void setGiaPhong(double giaPhong) { GiaPhong = giaPhong; }
    public String getTrangThai() { return TrangThai; }
    public void setTrangThai(String trangThai) { TrangThai = trangThai; }
}