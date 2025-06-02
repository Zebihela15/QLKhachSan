package com.sinhvien.appqlkhachsan;


public class RoomModel {
    private String name;
    private int imageResId;

    public RoomModel(String name, int imageResId) {
        this.name = name;
        this.imageResId = imageResId;
    }

    public String getName() {
        return name;
    }

    public int getImageResId() {
        return imageResId;
    }
}

