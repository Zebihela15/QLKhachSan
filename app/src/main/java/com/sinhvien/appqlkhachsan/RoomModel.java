package com.sinhvien.appqlkhachsan;

public class RoomModel {
    private String name;
    private int imageResource;
    private double price;

    public RoomModel(String name, int imageResource, double price) {
        this.name = name;
        this.imageResource = imageResource;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public int getImageResId() {
        return imageResource;
    }

    public double getPrice() {
        return price;
    }
}
