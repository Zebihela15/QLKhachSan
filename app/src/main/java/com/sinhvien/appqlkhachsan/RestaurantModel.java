package com.sinhvien.appqlkhachsan;

import java.io.Serializable;

public class RestaurantModel implements Serializable {
    private int id;
    private String name;
    private String description;
    private int imageResId;

    public RestaurantModel(int id, String name, String description, int imageResId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageResId = imageResId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getImageResId() { return imageResId; }

}