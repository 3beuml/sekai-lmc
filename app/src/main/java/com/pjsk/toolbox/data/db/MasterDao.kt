package com.pjsk.toolbox.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDao {

    @Upsert
    suspend fun upsertRows(rows: List<MasterRowEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(rows: List<MasterRowEntity>)

    @Query("DELETE FROM master_rows WHERE tableName = :tableName")
    suspend fun deleteTable(tableName: String)

    @Transaction
    suspend fun replaceTable(tableName: String, rows: List<MasterRowEntity>) {
        deleteTable(tableName)
        insertRows(rows)
    }

    /**
     * 只更新中文名（叠加层导入用），不覆盖原始 data。
     * 简中表与日服表按 id 对齐，缺失的新内容自然保持 nameZh = null。
     */
    @Query(
        """
        UPDATE master_rows
        SET nameZh = :nameZh
        WHERE tableName = :tableName AND id = :id
        """,
    )
    suspend fun updateNameZh(tableName: String, id: Int, nameZh: String)

    @Query(
        """
        SELECT COUNT(*) FROM master_rows
        WHERE tableName = :tableName AND id = :id
        """,
    )
    suspend fun exists(tableName: String, id: Int): Int

    @Query("SELECT COUNT(*) FROM master_rows WHERE tableName = :tableName")
    suspend fun count(tableName: String): Int

    @Query(
        """
        SELECT * FROM master_rows
        WHERE tableName = :tableName
        ORDER BY sortValue ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun page(tableName: String, limit: Int, offset: Int): List<MasterRowEntity>

    @Query(
        """
        SELECT * FROM master_rows
        WHERE tableName = :tableName
          AND (name LIKE '%' || :query || '%'
               OR nameZh LIKE '%' || :query || '%')
        ORDER BY sortValue ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(tableName: String, query: String, limit: Int, offset: Int): List<MasterRowEntity>

    @Query("SELECT * FROM master_rows WHERE tableName = :tableName AND id = :id LIMIT 1")
    suspend fun byId(tableName: String, id: Int): MasterRowEntity?

    @Query("SELECT * FROM master_rows WHERE tableName = :tableName AND id IN (:ids)")
    suspend fun byIds(tableName: String, ids: List<Int>): List<MasterRowEntity>

    @Query("SELECT * FROM master_rows WHERE tableName = :tableName ORDER BY sortValue ASC")
    fun observeAll(tableName: String): Flow<List<MasterRowEntity>>

    @Query("SELECT * FROM master_rows WHERE tableName = :tableName AND id IN (:ids) ORDER BY sortValue ASC")
    fun observeByIds(tableName: String, ids: List<Int>): Flow<List<MasterRowEntity>>

    // ─────────────────────────────────────────────────────────────
    // 阻塞式变体：给「流式导入」用。
    //
    // 导入是「边流式解析 JSON、边分批写库」的循环，回调里无法直接调用 suspend 函数；
    // 而 Room 的阻塞方法只要不在主线程调用就是合法的（导入固定在 Dispatchers.IO 上跑）。
    // 只在 App 内部的导入/清理路径使用，UI 侧一律用上面的 suspend / Flow 版本。
    // ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRowsBlocking(rows: List<MasterRowEntity>)

    @Query("DELETE FROM master_rows WHERE tableName = :tableName")
    fun deleteTableBlocking(tableName: String)

    @Query("UPDATE master_rows SET nameZh = :nameZh WHERE tableName = :tableName AND id = :id")
    fun updateNameZhBlocking(tableName: String, id: Int, nameZh: String)

    @Query(
        """
        SELECT COUNT(*) FROM master_rows
        WHERE tableName = :tableName AND id = :id
        """,
    )
    fun existsBlocking(tableName: String, id: Int): Int

    @Query("SELECT COUNT(*) FROM master_rows WHERE tableName = :tableName")
    fun countBlocking(tableName: String): Int

    @Query("SELECT id FROM master_rows WHERE tableName = :tableName")
    fun allIdsBlocking(tableName: String): List<Int>
}

@Dao
interface SyncStateDao {

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE tableName = :tableName")
    suspend fun byTable(tableName: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state")
    suspend fun all(): List<SyncStateEntity>

    @Query("SELECT * FROM sync_state")
    fun observeAll(): Flow<List<SyncStateEntity>>

    @Query("DELETE FROM sync_state WHERE tableName = :tableName")
    suspend fun delete(tableName: String)

    @Query("DELETE FROM sync_state")
    suspend fun clear()

    @Query("SELECT COALESCE(SUM(rowCount), 0) FROM sync_state")
    fun observeTotalRows(): Flow<Int>
}

@Dao
interface VersionStateDao {

    @Upsert
    suspend fun upsert(state: VersionStateEntity)

    @Query("SELECT * FROM version_state WHERE region = :region")
    suspend fun byRegion(region: String): VersionStateEntity?

    @Query("SELECT * FROM version_state")
    fun observeAll(): Flow<List<VersionStateEntity>>
}
