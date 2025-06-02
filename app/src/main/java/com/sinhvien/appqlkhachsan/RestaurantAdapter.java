package com.sinhvien.appqlkhachsan;



import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;

public class RestaurantAdapter extends ArrayAdapter<RestaurantModel> {
    private final Activity context;
    private final ArrayList<RestaurantModel> restaurants;

    public RestaurantAdapter(Activity context, ArrayList<RestaurantModel> restaurants) {
        super(context, R.layout.item_restaurant, restaurants);
        this.context = context;
        this.restaurants = restaurants;
    }

    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.item_restaurant, null, true);

        ImageView imageRestaurant = rowView.findViewById(R.id.imageRestaurant);
        TextView textRestaurant = rowView.findViewById(R.id.textRestaurant);

        RestaurantModel res = restaurants.get(position);
        imageRestaurant.setImageResource(res.getImageResId());
        textRestaurant.setText(res.getName());

        return rowView;
    }
}
