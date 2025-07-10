package com.sinhvien.appqlkhachsan.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.sinhvien.appqlkhachsan.R;
import java.util.ArrayList;
import java.util.List;

public class BookingFragment extends Fragment {
    private RecyclerView rvBookingList;
    private List<AdminBookingListActivity.BookingModel> filteredBookingList;
    private AdminBookingListActivity.BookingAdapter bookingAdapter;
    private final String tabFilter;

    public BookingFragment() {
        this.tabFilter = "";
    }

    public BookingFragment(String tabFilter) {
        this.tabFilter = tabFilter;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_booking_list, container, false);
        rvBookingList = view.findViewById(R.id.rvBookingList);
        rvBookingList.setLayoutManager(new LinearLayoutManager(getContext()));
        filteredBookingList = new ArrayList<>();

        // Ensure the activity is AdminBookingListActivity to access BookingAdapter
        if (getActivity() instanceof AdminBookingListActivity) {
            bookingAdapter = ((AdminBookingListActivity) getActivity()).new BookingAdapter(filteredBookingList);
            rvBookingList.setAdapter(bookingAdapter);
            loadBookings();
        }

        return view;
    }

    public void loadBookings() {
        if (getActivity() instanceof AdminBookingListActivity) {
            ((AdminBookingListActivity) getActivity()).loadBookings(tabFilter, bookingAdapter, filteredBookingList);
        }
    }

    public void filterBookings(String query) {
        if (getActivity() instanceof AdminBookingListActivity) {
            ((AdminBookingListActivity) getActivity()).filterBookings(query, filteredBookingList, bookingAdapter);
        }
    }
}