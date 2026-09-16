package com.pjsk.toolbox.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通用的 master data 行。
 *
 * 设计取舍：**不为每张表建独立表/实体**。理由：
 *  1) master data 字段随游戏版本变化，硬编码 schema 会导致官方一更新字段 App 就崩；
 *  2) 40+ 张表、单表最大 54 MB，统一存储让同步逻辑只有一份；
 *  3) 需要具体字段时，用 `JsonObject` 解析 `data` 即可（单行解析成本可忽略）。
 */
@Entity(
    tableName = "master_rows",
    primaryKeys = ["tableName", "id"],
    indices = [
        Index(value = ["tableName"]),
        Index(value = ["tableName", "sortValue"]),
        Index(value = ["tableName", "name"]),
    ],
)
data class MasterRowEntity(
    val tableName: String,
    val id: Int,
    /** 原始显示名（日服为日文原文）。 */
    val name: String?,
    /** 简体中文名叠加层（来自简中区服同 id 的行；缺失时为 null，UI 回退到 [name]）。 */
    val nameZh: String?,
    /** 排序用数值（seq / publishedAt / releaseAt…），来自 [com.pjsk.toolbox.data.sync.TableSchema.sortKeyNames]。 */
    val sortValue: Long,
    /** 原始 JSON 文本，按需解析。 */
    val data: String,
)

/** 每张表的同步状态。 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val tableName: String,
    /** 该表最后一次成功导入时的区服 id。 */
    val region: String,
    /** GitHub 的 ETag，用于条件请求（命中 304 则完全不下载）。 */
    val etag: String?,
    /** 远端 Last-Modified。 */
    val lastModified: String?,
    /** 导入的行数。 */
    val rowCount: Int,
    /** 完成时间（epoch millis）。 */
    val updatedAt: Long,
    /** 该表所属数据模块 id。 */
    val module: String,
)

/** 记录远端数据版本（来自 commit message：`master version X asset version Y`）。 */
@Entity(tableName = "version_state")
data class VersionStateEntity(
    @PrimaryKey val region: String,
    /** 例如 "6.7.0.40"。 */
    val masterVersion: String?,
    val assetVersion: String?,
    val commitDate: String?,
    val checkedAt: Long,
)
