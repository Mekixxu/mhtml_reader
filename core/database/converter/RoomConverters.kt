package core.database.converter

import androidx.room.TypeConverter
import core.database.entity.enums.*
import core.database.entity.enums.fromSafeFileType
import core.database.entity.enums.fromSafeNetworkProtocol
import core.database.entity.enums.fromSafeSourceType

/**
 * 使用“安全解析”避免 valueOf() 因未知枚举值导致崩溃。
 * 这对导入旧数据/未来扩展枚举值非常关键。
 */
object RoomConverters {

    @TypeConverter
    @JvmStatic
    fun fromFavoriteType(value: FavoriteType?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toFavoriteType(value: String?): FavoriteType =
        try {
            if (value.isNullOrBlank()) FavoriteType.FILE else FavoriteType.valueOf(value)
        } catch (_: Exception) {
            // 兜底：未知值按 FILE 处理（也可改成抛错）
            FavoriteType.FILE
        }

    @TypeConverter
    @JvmStatic
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toSourceType(value: String?): SourceType = fromSafeSourceType(value)

    @TypeConverter
    @JvmStatic
    fun fromFileType(value: FileType?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toFileType(value: String?): FileType = fromSafeFileType(value)

    @TypeConverter
    @JvmStatic
    fun fromNetworkProtocol(value: NetworkProtocol?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toNetworkProtocol(value: String?): NetworkProtocol = fromSafeNetworkProtocol(value)
}

