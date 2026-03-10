package com.username051203.justcounter.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "history")
public class HistoryEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int counterId;
    public String date;
    public long count;
    public int cycleSize;

    public long getCycles() { return cycleSize > 0 ? count / cycleSize : 0; }
}
