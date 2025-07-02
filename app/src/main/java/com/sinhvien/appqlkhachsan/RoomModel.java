package com.sinhvien.appqlkhachsan;

import java.util.ArrayList;
import java.util.List;

public class RoomModel {
    private int maPhong;
    private String tenPhong;
    private double dienTich;
    private String trangThai;
    private String donViTinh;
    private int maLoaiPhong;
    private String moTa;
    private String diaChi;
    private String ghiChu;
    private int hinhAnh;
    private List<Integer> tienIch;

    public RoomModel(int maPhong, String tenPhong, double dienTich, String trangThai, String donViTinh,
                     int maLoaiPhong, String moTa, String diaChi, String ghiChu, int hinhAnh,
                     List<Integer> tienIch) {
        this.maPhong = maPhong;
        this.tenPhong = tenPhong;
        this.dienTich = dienTich;
        this.trangThai = trangThai != null ? trangThai : "Trống";
        this.donViTinh = donViTinh != null ? donViTinh : "m²";
        this.maLoaiPhong = maLoaiPhong;
        this.moTa = moTa != null ? moTa : "Không có mô tả";
        this.diaChi = diaChi != null ? diaChi : "Không xác định";
        this.ghiChu = ghiChu != null ? ghiChu : "";
        this.hinhAnh = hinhAnh;
        this.tienIch = tienIch != null ? tienIch : new ArrayList<>();
    }

    public int getMaPhong() { return maPhong; }
    public String getName() { return tenPhong != null ? tenPhong : "Không xác định"; }
    public double getArea() { return dienTich; }
    public String getStatus() { return trangThai != null ? trangThai : "Trống"; }
    public String getDonViTinh() { return donViTinh != null ? donViTinh : "m²"; }
    public int getMaLoaiPhong() { return maLoaiPhong; }
    public String getMoTa() { return moTa != null ? moTa : "Không có mô tả"; }
    public String getDiaChi() { return diaChi != null ? diaChi : "Không xác định"; }
    public String getGhiChu() { return ghiChu != null ? ghiChu : ""; }
    public int getImageResId() { return hinhAnh; }
    public List<Integer> getTienIch() { return tienIch != null ? tienIch : new ArrayList<>(); }
    public void setStatus(String trangThai) { this.trangThai = trangThai; }
}