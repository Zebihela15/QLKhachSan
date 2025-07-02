package com.sinhvien.appqlkhachsan;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

public class TienIchAdapter extends RecyclerView.Adapter<TienIchAdapter.TienIchViewHolder> {

    private ArrayList<TienIchModel> services;

    public TienIchAdapter(ArrayList<TienIchModel> services) {
        this.services = services != null ? services : new ArrayList<>();
        Log.d("DEBUG", "TienIchAdapter initialized with " + this.services.size() + " services");
    }

    @NonNull
    @Override
    public TienIchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_service, parent, false);
        return new TienIchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TienIchViewHolder holder, int position) {
        TienIchModel service = services.get(position);
        holder.textService.setText(service.getTenTienIch());
        holder.iconService.setImageResource(service.getIconResource());
        Log.d("DEBUG", "Binding service: " + service.getTenTienIch());
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    static class TienIchViewHolder extends RecyclerView.ViewHolder {
        ImageView iconService;
        TextView textService;

        TienIchViewHolder(@NonNull View itemView) {
            super(itemView);
            iconService = itemView.findViewById(R.id.iconService);
            textService = itemView.findViewById(R.id.textService);
        }
    }
}