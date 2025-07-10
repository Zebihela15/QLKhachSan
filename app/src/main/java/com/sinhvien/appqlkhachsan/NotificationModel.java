package com.sinhvien.appqlkhachsan;

public class NotificationModel {
    private String id;
    private String title;
    private String message;
    private String timestamp;

    public NotificationModel(String id, String title, String message, String timestamp) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }
}