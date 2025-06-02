package com.sinhvien.appqlkhachsan;



import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;

public class RoomAdapter extends ArrayAdapter<RoomModel> {
    private final Activity context;
    private final ArrayList<RoomModel> rooms;

    public RoomAdapter(Activity context, ArrayList<RoomModel> rooms) {
        super(context, R.layout.item_room, rooms);
        this.context = context;
        this.rooms = rooms;
    }

    public View getView(int position, View view, ViewGroup parent) {
        LayoutInflater inflater = context.getLayoutInflater();
        View rowView = inflater.inflate(R.layout.item_room, null, true);

        ImageView imageRoom = rowView.findViewById(R.id.imageRoom);
        TextView textRoom = rowView.findViewById(R.id.textRoom);

        RoomModel room = rooms.get(position);
        imageRoom.setImageResource(room.getImageResId());
        textRoom.setText(room.getName());

        return rowView;
    }
}

