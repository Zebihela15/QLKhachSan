package com.sinhvien.appqlkhachsan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;
import java.util.Map;

public class DashboardAdapter extends RecyclerView.Adapter<DashboardAdapter.DashboardViewHolder> {

    private final Context context;
    private final List<RoomModel> roomList;
    private final List<String> dateList; // Format "dd/MM"
    private final Map<Integer, Map<String, String>> statusMap; // MaPhong -> <Date (yyyy-MM-dd) -> Status>
    private final Map<String, String> fullDateMap; // "dd/MM" -> "yyyy-MM-dd"
    private final OnCellClickListener onCellClickListener;

    public interface OnCellClickListener {
        void onCellClick(RoomModel room, String date, String status);
    }

    public DashboardAdapter(Context context, List<RoomModel> roomList, List<String> dateList, Map<Integer, Map<String, String>> statusMap, Map<String, String> fullDateMap, OnCellClickListener listener) {
        this.context = context;
        this.roomList = roomList;
        this.dateList = dateList;
        this.statusMap = statusMap;
        this.fullDateMap = fullDateMap;
        this.onCellClickListener = listener;
    }

    @NonNull
    @Override
    public DashboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dashboard_cell, parent, false);
        return new DashboardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DashboardViewHolder holder, int position) {
        int colCount = dateList.size() + 1;
        int row = position / colCount;
        int col = position % colCount;

        holder.cellTextView.setTypeface(null, Typeface.NORMAL);
        holder.cardView.setCardBackgroundColor(Color.WHITE);

        // Header góc trên bên trái
        if (row == 0 && col == 0) {
            holder.cellTextView.setText("Phòng");
            holder.cellTextView.setTypeface(null, Typeface.BOLD);
            // SỬA LỖI: Thay thế màu không hợp lệ bằng màu xám nhạt
            holder.cardView.setCardBackgroundColor(Color.parseColor("#E0E0E0"));
        }
        // Header ngày (hàng đầu tiên)
        else if (row == 0) {
            holder.cellTextView.setText(dateList.get(col - 1));
            holder.cellTextView.setTypeface(null, Typeface.BOLD);
            holder.cardView.setCardBackgroundColor(Color.parseColor("#F0F0F0"));
        }
        // Header phòng (cột đầu tiên)
        else if (col == 0) {
            holder.cellTextView.setText(roomList.get(row - 1).getName());
            holder.cellTextView.setTypeface(null, Typeface.BOLD);
            holder.cardView.setCardBackgroundColor(Color.parseColor("#F0F0F0"));
        }
        // Các ô trạng thái
        else {
            RoomModel room = roomList.get(row - 1);
            String displayDate = dateList.get(col - 1);
            String fullDate = fullDateMap.get(displayDate);

            String status = "Trống"; // Mặc định là trống
            if (statusMap.containsKey(room.getMaPhong()) && statusMap.get(room.getMaPhong()).containsKey(fullDate)) {
                status = statusMap.get(room.getMaPhong()).get(fullDate);
            }
            holder.cellTextView.setText(status);

            int backgroundColor;
            switch (status) {
                case "Đang sử dụng":
                case "Đã nhận phòng":
                    backgroundColor = Color.parseColor("#FFA07A"); // Light Salmon/Red
                    break;
                case "Đã đặt":
                case "Đã xác nhận":
                    backgroundColor = Color.parseColor("#FFD700"); // Gold/Yellow
                    break;
                case "Trống":
                    backgroundColor = Color.parseColor("#90EE90"); // Light Green
                    break;
                default: // Bảo trì hoặc trạng thái khác
                    backgroundColor = Color.parseColor("#C0C0C0"); // Silver/Gray
                    break;
            }
            holder.cardView.setCardBackgroundColor(backgroundColor);

            String finalStatus = status;
            holder.itemView.setOnClickListener(v -> {
                if (onCellClickListener != null) {
                    onCellClickListener.onCellClick(room, fullDate, finalStatus);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        if (roomList.isEmpty() || dateList.isEmpty()) {
            return 0;
        }
        return (roomList.size() + 1) * (dateList.size() + 1);
    }

    static class DashboardViewHolder extends RecyclerView.ViewHolder {
        TextView cellTextView;
        MaterialCardView cardView;

        DashboardViewHolder(@NonNull View itemView) {
            super(itemView);
            cellTextView = itemView.findViewById(R.id.cellTextView);
            cardView = itemView.findViewById(R.id.cellCardView);
        }
    }
}