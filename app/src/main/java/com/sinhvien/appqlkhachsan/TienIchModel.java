package com.sinhvien.appqlkhachsan;

import java.io.Serializable;

public class TienIchModel implements Serializable {
    private int maTienIch;
    private String tenTienIch;
    private int iconResource;

    public TienIchModel(int maTienIch, String tenTienIch, int iconResource) {
        this.maTienIch = maTienIch;
        this.tenTienIch = tenTienIch;
        this.iconResource = iconResource;
    }

    public int getMaTienIch() { return maTienIch; }
    public String getTenTienIch() { return tenTienIch; }
    public int getIconResource() { return iconResource; }
}