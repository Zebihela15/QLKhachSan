package com.sinhvien.appqlkhachsan.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sinhvien.appqlkhachsan.R;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountViewHolder> {

    private List<Map<String, Object>> accountList;
    private BiConsumer<Map<String, Object>, View> onUpdateClick;
    private BiConsumer<Map<String, Object>, View> onDeleteClick;

    public AccountAdapter(List<Map<String, Object>> accountList,
                          BiConsumer<Map<String, Object>, View> onUpdateClick,
                          BiConsumer<Map<String, Object>, View> onDeleteClick) {
        this.accountList = accountList;
        this.onUpdateClick = onUpdateClick;
        this.onDeleteClick = onDeleteClick;
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        Map<String, Object> account = accountList.get(position);
        holder.tvFullName.setText((String) account.get("fullName"));
        holder.tvEmail.setText((String) account.get("email"));
        holder.tvPhone.setText((String) account.get("phone"));
        holder.tvRole.setText((String) account.get("role"));

        holder.btnUpdate.setOnClickListener(v -> onUpdateClick.accept(account, v));
        holder.btnDelete.setOnClickListener(v -> onDeleteClick.accept(account, v));
    }

    @Override
    public int getItemCount() {
        return accountList.size();
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        TextView tvFullName, tvEmail, tvPhone, tvRole;
        Button btnUpdate, btnDelete;

        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvRole = itemView.findViewById(R.id.tvRole);
            btnUpdate = itemView.findViewById(R.id.btnUpdate);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}