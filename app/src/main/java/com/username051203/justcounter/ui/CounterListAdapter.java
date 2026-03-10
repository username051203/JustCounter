package com.username051203.justcounter.ui;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.username051203.justcounter.R;
import com.username051203.justcounter.data.Counter;
import java.util.List;
import java.util.Locale;

public class CounterListAdapter extends RecyclerView.Adapter<CounterListAdapter.ViewHolder> {
    public interface OnCounterClickListener { void onClick(Counter counter); }

    private List<Counter> counters;
    private final OnCounterClickListener click, longClick;

    public CounterListAdapter(List<Counter> counters, OnCounterClickListener click, OnCounterClickListener longClick) {
        this.counters = counters; this.click = click; this.longClick = longClick;
    }

    public void updateData(List<Counter> data) { this.counters = data; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_counter, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Counter c = counters.get(pos);
        h.tvName.setText(c.name);
        h.tvDate.setText(c.sessionDate != null ? c.sessionDate : "");
        h.tvToday.setText(String.valueOf(c.dailyCount));
        h.tvDailyCycles.setText(c.cycleSize + " x " + c.getDailyCycles());
        h.tvLifetime.setText(String.format(Locale.getDefault(), "%,d", c.lifetimeCount));
        h.tvLifetimeCycles.setText(c.cycleSize + " x " + c.getLifetimeCycles());
        h.itemView.setOnClickListener(v -> click.onClick(c));
        h.itemView.setOnLongClickListener(v -> { longClick.onClick(c); return true; });
    }

    @Override public int getItemCount() { return counters.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate, tvToday, tvDailyCycles, tvLifetime, tvLifetimeCycles;
        ViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvCounterName);
            tvDate = v.findViewById(R.id.tvDate);
            tvToday = v.findViewById(R.id.tvTodayCount);
            tvDailyCycles = v.findViewById(R.id.tvDailyCycles);
            tvLifetime = v.findViewById(R.id.tvLifetimeCount);
            tvLifetimeCycles = v.findViewById(R.id.tvLifetimeCycles);
        }
    }
}
