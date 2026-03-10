package com.username051203.justcounter.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.username051203.justcounter.R;
import com.username051203.justcounter.data.AppDatabase;
import com.username051203.justcounter.data.Counter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private AppDatabase db;
    private CounterListAdapter adapter;
    private RecyclerView rvCounters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        db = AppDatabase.getInstance(this);
        rvCounters = findViewById(R.id.rvCounters);
        rvCounters.setLayoutManager(new LinearLayoutManager(this));
        Button btnAdd = findViewById(R.id.btnAddCounter);
        btnAdd.setOnClickListener(v -> showAddCounterDialog());
        refreshCounters();
    }

    @Override protected void onResume() { super.onResume(); refreshCounters(); }

    private void refreshCounters() {
        List<Counter> counters = db.counterDao().getAllCounters();
        if (adapter == null) {
            adapter = new CounterListAdapter(counters,
                counter -> { Intent i = new Intent(this, CounterActivity.class); i.putExtra("counterId", counter.id); startActivity(i); },
                counter -> showCounterOptions(counter));
            rvCounters.setAdapter(adapter);
        } else {
            adapter.updateData(counters);
        }
    }

    private void showAddCounterDialog() {
        EditText input = new EditText(this);
        input.setHint("Counter name");
        input.setText("counter");
        new MaterialAlertDialogBuilder(this).setTitle("New Counter").setView(input)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "counter";
                    Counter c = new Counter();
                    c.name = name;
                    c.sessionDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    db.counterDao().insertCounter(c);
                    refreshCounters();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showCounterOptions(Counter counter) {
        new MaterialAlertDialogBuilder(this).setTitle(counter.name)
                .setItems(new String[]{"Open", "Delete"}, (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(this, CounterActivity.class);
                        i.putExtra("counterId", counter.id); startActivity(i);
                    } else {
                        new MaterialAlertDialogBuilder(this).setTitle("Delete " + counter.name + "?")
                                .setMessage("This will also delete all history.")
                                .setPositiveButton("Delete", (dd, ww) -> {
                                    db.counterDao().deleteHistoryForCounter(counter.id);
                                    db.counterDao().deleteCounter(counter);
                                    refreshCounters();
                                }).setNegativeButton("Cancel", null).show();
                    }
                }).show();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.menu_main, menu); return true; }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_view_history) { startActivity(new Intent(this, HistoryActivity.class)); return true; }
        return super.onOptionsItemSelected(item);
    }
}
