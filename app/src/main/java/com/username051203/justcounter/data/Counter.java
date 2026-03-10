package com.username051203.justcounter.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "counters")
public class Counter {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public int cycleSize;
    public long lifetimeCount;
    public long dailyCount;
    public String sessionDate;
    public int colorTag;
    public boolean soundAlert;
    public boolean hapticRegular;
    public boolean hapticLong;
    public String backgroundUri;   // local file URI or http URL

    public Counter() {
        this.name = "counter";
        this.cycleSize = 108;
        this.lifetimeCount = 0;
        this.dailyCount = 0;
        this.colorTag = 0;
        this.soundAlert = true;
        this.hapticRegular = true;
        this.hapticLong = true;
        this.backgroundUri = null;
    }

    // Called after loading from DB to ensure booleans are sane
    // (Room may store false for unset fields on older rows)
    public void applyDefaults() {
        if (cycleSize <= 0) cycleSize = 108;
        // Note: we intentionally do NOT force soundAlert/haptic to true here
        // so user toggles are respected. This just fixes cycleSize=0 edge case.
    }

    public long getDailyCycles() { return cycleSize > 0 ? dailyCount / cycleSize : 0; }
    public long getLifetimeCycles() { return cycleSize > 0 ? lifetimeCount / cycleSize : 0; }
}
