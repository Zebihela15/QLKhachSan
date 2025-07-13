package com.sinhvien.appqlkhachsan;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private RecyclerView rvNotifications;
    private Button btnDeleteAllNotifications;
    private NotificationAdapter notificationAdapter;
    private List<NotificationModel> notificationList;
    private boolean isActive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        rvNotifications = findViewById(R.id.rvNotifications);
        btnDeleteAllNotifications = findViewById(R.id.btnDeleteAllNotifications);

        notificationList = new ArrayList<>();
        notificationAdapter = new NotificationAdapter(notificationList);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(notificationAdapter);

        btnDeleteAllNotifications.setOnClickListener(v -> deleteAllNotifications());

        loadNotifications();
    }

    private void loadNotifications() {
        if (!isActive) return;
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) {
            Toast.makeText(this, "Vui lòng đăng nhập để xem thông báo!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) return;
                    notificationList.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        try {
                            String id = doc.getId();
                            String title = doc.getString("title");
                            String message = doc.getString("message");
                            String timestamp = doc.getString("timestamp");
                            NotificationModel notification = new NotificationModel(id, title, message, timestamp);
                            notificationList.add(notification);
                        } catch (Exception e) {
                            Log.e(TAG, "Lỗi khi parse thông báo: " + e.getMessage());
                        }
                    }
                    notificationAdapter.notifyDataSetChanged();
                    btnDeleteAllNotifications.setVisibility(notificationList.isEmpty() ? View.GONE : View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi tải thông báo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteNotification(String notificationId, int position) {
        if (!isActive) return;
        db.collection("notifications").document(notificationId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (isActive) {
                        notificationList.remove(position);
                        notificationAdapter.notifyItemRemoved(position);
                        notificationAdapter.notifyItemRangeChanged(position, notificationList.size());
                        btnDeleteAllNotifications.setVisibility(notificationList.isEmpty() ? View.GONE : View.VISIBLE);
                        Toast.makeText(this, "Đã xóa thông báo!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi xóa thông báo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteAllNotifications() {
        if (!isActive || notificationList.isEmpty()) return;
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
        if (userId == null) return;

        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isActive) return;
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        batch.delete(db.collection("notifications").document(doc.getId()));
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (isActive) {
                                    notificationList.clear();
                                    notificationAdapter.notifyDataSetChanged();
                                    btnDeleteAllNotifications.setVisibility(View.GONE);
                                    Toast.makeText(this, "Đã xóa toàn bộ thông báo!", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (isActive) {
                                    Toast.makeText(this, "Lỗi xóa toàn bộ thông báo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (isActive) {
                        Toast.makeText(this, "Lỗi tải danh sách thông báo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        loadNotifications();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActive = false;
    }

    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private final List<NotificationModel> notifications;
        private final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

        NotificationAdapter(List<NotificationModel> notifications) {
            this.notifications = notifications;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NotificationModel notification = notifications.get(position);
            holder.titleTextView.setText(notification.getTitle() != null ? notification.getTitle() : "N/A");
            holder.messageTextView.setText(notification.getMessage() != null ? notification.getMessage() : "N/A");

            // Định dạng timestamp
            String displayTimestamp = notification.getTimestamp();
            if (displayTimestamp != null) {
                try {
                    Date date = inputFormat.parse(displayTimestamp);
                    displayTimestamp = displayFormat.format(date);
                } catch (ParseException e) {
                    Log.e(TAG, "Lỗi định dạng timestamp: " + e.getMessage());
                }
            }
            holder.timestampTextView.setText(displayTimestamp != null ? displayTimestamp : "N/A");

            holder.btnDelete.setOnClickListener(v -> deleteNotification(notification.getId(), holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleTextView, messageTextView, timestampTextView;
            LinearLayout btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                titleTextView = itemView.findViewById(R.id.notificationTitle);
                messageTextView = itemView.findViewById(R.id.notificationMessage);
                timestampTextView = itemView.findViewById(R.id.notificationTimestamp);
                btnDelete = itemView.findViewById(R.id.btnDeleteNotification);
            }
        }
    }
}