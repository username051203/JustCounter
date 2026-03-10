package com.username051203.justcounter.ui;

import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;
import com.username051203.justcounter.R;
import com.username051203.justcounter.data.*;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) { getSupportActionBar().setTitle("Counter History"); getSupportActionBar().setDisplayHomeAsUpEnabled(true); }
        AppDatabase db = AppDatabase.getInstance(this);
        int counterId = getIntent().getIntExtra("counterId", -1);
        List<HistoryEntry> entries = db.counterDao().getHistoryForCounter(counterId);
        RecyclerView rv = findViewById(R.id.rvHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new HistoryAdapter(entries));
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
        private final List<HistoryEntry> entries;
        HistoryAdapter(List<HistoryEntry> e) { this.entries = e; }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            HistoryEntry e = entries.get(pos);
            h.tvDate.setText(e.date); h.tvCount.setText(String.valueOf(e.count)); h.tvCycles.setText(e.cycleSize + " x " + e.getCycles());
        }
        @Override public int getItemCount() { return entries.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView tvDate, tvCount, tvCycles;
            VH(View v) { super(v); tvDate=v.findViewById(R.id.tvHistoryDate); tvCount=v.findViewById(R.id.tvHistoryCount); tvCycles=v.findViewById(R.id.tvHistoryCycles); }
        }
    }
}
