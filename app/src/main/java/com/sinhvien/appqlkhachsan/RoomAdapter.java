package com.sinhvien.appqlkhachsan;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;
import java.util.Objects;

public class RoomAdapter extends ListAdapter<RoomModel, RoomAdapter.RoomViewHolder> {

    private final Context context;
    private final RoomAdapter.OnRoomClickListener bookListener;
    private final RoomAdapter.OnRoomClickListener availabilityListener;
    private final Map<Integer, Double> roomTypePrices;
    private final Map<Integer, Integer> roomTypeImages;

    public interface OnRoomClickListener {
        void onRoomClick(RoomModel room);
    }

    public RoomAdapter(Context context, Map<Integer, Double> roomTypePrices, Map<Integer, Integer> roomTypeImages,
                       RoomAdapter.OnRoomClickListener bookListener, RoomAdapter.OnRoomClickListener availabilityListener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.roomTypePrices = roomTypePrices;
        this.roomTypeImages = roomTypeImages;
        this.bookListener = bookListener;
        this.availabilityListener = availabilityListener;
        setHasStableIds(true);
    }

    private static final DiffUtil.ItemCallback<RoomModel> DIFF_CALLBACK = new DiffUtil.ItemCallback<RoomModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull RoomModel oldItem, @NonNull RoomModel newItem) {
            return oldItem.getMaPhong() == newItem.getMaPhong();
        }

        @Override
        public boolean areContentsTheSame(@NonNull RoomModel oldItem, @NonNull RoomModel newItem) {
            return oldItem.getMaPhong() == newItem.getMaPhong() &&
                    oldItem.getMaLoaiPhong() == newItem.getMaLoaiPhong() &&
                    Objects.equals(oldItem.getName(), newItem.getName()) &&
                    Objects.equals(oldItem.getStatus(), newItem.getStatus());
        }
    };

    @Override
    public long getItemId(int position) {
        return getItem(position).getMaPhong();
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        RoomModel room = getItem(position);
        Log.d("RoomAdapter", "Binding room: " + room.getName() + ", MaPhong: " + room.getMaPhong() + ", MaLoaiPhong: " + room.getMaLoaiPhong());
        holder.txtRoomName.setText(room.getName() != null ? room.getName() : "Không xác định");
        holder.txtRoomType.setText("Loại: " + room.getMaLoaiPhong());
        double price = roomTypePrices.getOrDefault(room.getMaLoaiPhong(), 500.0);
        holder.txtRoomPrice.setText(String.format("Giá: %,d VND/đêm", (int) price));
        String status = room.getStatus() != null ? room.getStatus() : "Trống";
        holder.txtRoomStatus.setText("Trạng thái: " + status);

        int imageResId = roomTypeImages.getOrDefault(room.getMaLoaiPhong(), R.drawable.ic_launcher_background);
        holder.imgRoom.setImageResource(imageResId);

        int statusColor;
        switch (status) {
            case "Trống":
                statusColor = ContextCompat.getColor(context, R.color.green);
                break;
            case "Đang có khách":
                statusColor = ContextCompat.getColor(context, R.color.red);
                break;
            case "Đã đặt":
                statusColor = ContextCompat.getColor(context, R.color.yellow);
                break;
            case "Bảo trì":
                statusColor = ContextCompat.getColor(context, R.color.black);
                break;
            default:
                statusColor = ContextCompat.getColor(context, R.color.gray);
                break;
        }
        holder.txtRoomStatus.setTextColor(statusColor);

        holder.btnBookRoom.setOnClickListener(v -> {
            if (bookListener != null) {
                bookListener.onRoomClick(room);
            }
        });

        holder.btnViewAvailability.setOnClickListener(v -> {
            if (availabilityListener != null) {
                availabilityListener.onRoomClick(room);
            }
        });

        holder.btnViewInfo.setOnClickListener(v -> {
            Intent intent = new Intent(context, RoomActivity.class);
            intent.putExtra("ROOM_ID", room.getMaPhong());
            intent.putExtra("roomName", room.getName());
            intent.putExtra("price", price);
            intent.putExtra("imageResource", roomTypeImages.getOrDefault(room.getMaLoaiPhong(), R.drawable.ic_launcher_background));
            context.startActivity(intent);
        });
    }

    static class RoomViewHolder extends RecyclerView.ViewHolder {
        ImageView imgRoom;
        TextView txtRoomName, txtRoomType, txtRoomPrice, txtRoomStatus;
        Button btnViewAvailability, btnBookRoom, btnViewInfo;

        RoomViewHolder(@NonNull View itemView) {
            super(itemView);

            txtRoomName = itemView.findViewById(R.id.txtRoomName);
            txtRoomType = itemView.findViewById(R.id.txtRoomType);
            txtRoomPrice = itemView.findViewById(R.id.txtRoomPrice);
            txtRoomStatus = itemView.findViewById(R.id.txtRoomStatus);

            btnBookRoom = itemView.findViewById(R.id.btnBookRoom);

        }
    }
}