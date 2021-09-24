package ru.polyach.openweather;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ru.polyach.openweather.Model.Town;

public class TownsAdapter extends RecyclerView.Adapter<TownsAdapter.RecyclerViewItemTown> {

    private FindSettlementActivity activity;
    private java.util.List<Town> list;

    public class RecyclerViewItemTown extends RecyclerView.ViewHolder implements View.OnClickListener {

        // View itemView;
        TextView townName;
        TextView townLat;
        TextView townLon;


        RecyclerViewItemTown(View itemView) {
            super(itemView);
            // this.itemView = itemView;
            townName = itemView.findViewById(R.id.item_town_name);
            townLat = itemView.findViewById(R.id.item_lat);
            townLon = itemView.findViewById(R.id.item_lon);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int pos = getAdapterPosition();
            Intent answerIntent = new Intent();
            answerIntent.putExtra(MainActivity.INTENT_CURRENT_COUNTRY, list.get(pos).getCountry());
            answerIntent.putExtra(MainActivity.INTENT_CURRENT_TOWN_ID, list.get(pos).getId());
            activity.setResult(Activity.RESULT_OK, answerIntent);
            activity.finish();
        }
    }

    public TownsAdapter(Context context, java.util.List<Town> list) {
        activity = (FindSettlementActivity)context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerViewItemTown onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_town, parent, false);
        return new RecyclerViewItemTown(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewItemTown holder, int position) {
        holder.townName.setText(list.get(position).getName());
        String latStr;
        double lat = list.get(position).getCoord().getLat();
        if(lat >= 0)
            latStr = activity.getResources().getString(R.string.north);
        else
        {
            lat = - lat;
            latStr = activity.getResources().getString(R.string.south);
        }

        holder.townLat.setText(String.format("%.2f° %s", lat, latStr));

        String lonStr;
        double lon = list.get(position).getCoord().getLon();
        if (lon >= 0)
            lonStr = activity.getResources().getString(R.string.east);
        else
        {
            lon = -lon;
            lonStr = activity.getResources().getString(R.string.west);
        }

        holder.townLon.setText(String.format("%.2f° %s", lon, lonStr));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
