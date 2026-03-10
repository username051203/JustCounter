package com.username051203.justcounter.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.username051203.justcounter.R;
import com.username051203.justcounter.data.AppDatabase;
import com.username051203.justcounter.data.Counter;

public class SettingsActivity extends AppCompatActivity {
    private AppDatabase db;
    private Counter counter;
    private EditText etCounterName, etCycleSize;
    private SwitchMaterial switchSoundAlert, switchHapticRegular, switchHapticLong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) { getSupportActionBar().setTitle("Counter Settings"); getSupportActionBar().setDisplayHomeAsUpEnabled(true); }
        db = AppDatabase.getInstance(this);
        counter = db.counterDao().getCounterById(getIntent().getIntExtra("counterId", -1));
        if (counter == null) { finish(); return; }
        etCounterName = findViewById(R.id.etCounterName);
        etCycleSize = findViewById(R.id.etCycleSize);
        switchSoundAlert = findViewById(R.id.switchSoundAlert);
        switchHapticRegular = findViewById(R.id.switchHapticRegular);
        switchHapticLong = findViewById(R.id.switchHapticLong);
        etCounterName.setText(counter.name);
        etCycleSize.setText(String.valueOf(counter.cycleSize));
        switchSoundAlert.setChecked(counter.soundAlert);
        switchHapticRegular.setChecked(counter.hapticRegular);
        switchHapticLong.setChecked(counter.hapticLong);
        Button btnSave = findViewById(R.id.btnSave);
        btnSave.setOnClickListener(v -> {
            String name = etCounterName.getText().toString().trim();
            if (name.isEmpty()) { Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show(); return; }
            try {
                int cs = Integer.parseInt(etCycleSize.getText().toString().trim());
                if (cs <= 0) throw new NumberFormatException();
                counter.name = name; counter.cycleSize = cs;
                counter.soundAlert = switchSoundAlert.isChecked();
                counter.hapticRegular = switchHapticRegular.isChecked();
                counter.hapticLong = switchHapticLong.isChecked();
                db.counterDao().updateCounter(counter);
                setResult(RESULT_OK); finish();
            } catch (NumberFormatException e) { Toast.makeText(this, "Enter a valid cycle size", Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
