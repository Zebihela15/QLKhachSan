package com.sinhvien.appqlkhachsan;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

public class RestaurantAdapter extends ListAdapter<RestaurantModel, RestaurantAdapter.ViewHolder> {
    private final OnRestaurantClickListener clickListener;

    public interface OnRestaurantClickListener {
        void onRestaurantClick(RestaurantModel restaurant);
    }

    public RestaurantAdapter(OnRestaurantClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<RestaurantModel> DIFF_CALLBACK = new DiffUtil.ItemCallback<RestaurantModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull RestaurantModel oldItem, @NonNull RestaurantModel newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull RestaurantModel oldItem, @NonNull RestaurantModel newItem) {
            return oldItem.getName().equals(newItem.getName()) &&
                    oldItem.getDescription().equals(newItem.getDescription()) &&
                    oldItem.getImageResId() == newItem.getImageResId();
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restaurant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RestaurantModel restaurant = getItem(position);
        Log.d("RestaurantAdapter", "Binding restaurant: " + restaurant.getName() + ", imageResId: " + restaurant.getImageResId());

        Glide.with(holder.itemView.getContext())
                .load(restaurant.getImageResId())
                .thumbnail(0.25f)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(android.R.drawable.ic_menu_gallery)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.restaurantImage);

        holder.restaurantName.setText(restaurant.getName());
        holder.restaurantDescription.setText(restaurant.getDescription());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onRestaurantClick(restaurant);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView restaurantImage;
        TextView restaurantName;
        TextView restaurantDescription;

        ViewHolder(View itemView) {
            super(itemView);
            restaurantImage = itemView.findViewById(R.id.restaurantImage);
            restaurantName = itemView.findViewById(R.id.restaurantName);
            restaurantDescription = itemView.findViewById(R.id.restaurantDescription);
        }
    }
}