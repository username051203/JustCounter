package com.username051203.justcounter.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface CounterDao {
    @Insert
    long insertCounter(Counter counter);
    @Update
    void updateCounter(Counter counter);
    @Delete
    void deleteCounter(Counter counter);
    @Query("SELECT * FROM counters ORDER BY id ASC")
    List<Counter> getAllCounters();
    @Query("SELECT * FROM counters WHERE id = :id")
    Counter getCounterById(int id);
    @Insert
    void insertHistory(HistoryEntry entry);
    @Query("SELECT * FROM history WHERE counterId = :counterId ORDER BY date DESC")
    List<HistoryEntry> getHistoryForCounter(int counterId);
    @Query("SELECT * FROM history WHERE counterId = :counterId AND date = :date LIMIT 1")
    HistoryEntry getHistoryForDate(int counterId, String date);
    @Query("UPDATE history SET count = :count WHERE counterId = :counterId AND date = :date")
    void updateHistoryCount(int counterId, String date, long count);
    @Query("DELETE FROM history WHERE counterId = :counterId")
    void deleteHistoryForCounter(int counterId);
}
