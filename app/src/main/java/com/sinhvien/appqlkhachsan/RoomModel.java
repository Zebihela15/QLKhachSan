package com.sinhvien.appqlkhachsan;

import java.util.List;

public class RoomModel {
    private final int maPhong;
    private final String name;
    private final int maLoaiPhong;
    private final double giaPhong;
    private final int soLuongNguoiToiDa;
    private String status;
    private final String moTa;
    private final List<Integer> tienIch;

    public RoomModel(int maPhong, String name, int maLoaiPhong, double giaPhong, int soLuongNguoiToiDa,
                     String status, String moTa, List<Integer> tienIch) {
        this.maPhong = maPhong;
        this.name = name;
        this.maLoaiPhong = maLoaiPhong;
        this.giaPhong = giaPhong;
        this.soLuongNguoiToiDa = soLuongNguoiToiDa;
        this.status = status;
        this.moTa = moTa;
        this.tienIch = tienIch;
    }

    public int getMaPhong() { return maPhong; }
    public String getName() { return name; }
    public int getMaLoaiPhong() { return maLoaiPhong; }
    public double getGiaPhong() { return giaPhong; }
    public int getSoLuongNguoiToiDa() { return soLuongNguoiToiDa; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMoTa() { return moTa; }
    public List<Integer> getTienIch() { return tienIch; }
}