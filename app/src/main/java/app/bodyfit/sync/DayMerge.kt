package app.bodyfit.sync

import app.bodyfit.data.DailyRecord

/**
 * Reconciles one calendar day held in two places.
 *
 * Plain last-write-wins is wrong here, and the failure is concrete: a phone counting steps
 * and a tablet logging water both touch the same day, and whichever pushes last erases the
 * other's contribution. So the merge is per field.
 *
 * Sensor fields take the larger value. That is not a guess: steps, move minutes, heart
 * points and active calories only ever grow within a day, so the larger number is the one
 * that saw more of the day. Water is the exception, because it is a sum of entries the user
 * can also delete, so it follows the more recently written side.
 */
object DayMerge {

    fun merge(local: DailyRecord, remote: DayDto): DailyRecord {
        require(local.date == remote.date) {
            "merging different days: ${local.date} and ${remote.date}"
        }
        val remoteIsNewer = remote.updatedAt > local.updatedAt
        return local.copy(
            steps = maxOf(local.steps, remote.steps),
            moveMinutes = maxOf(local.moveMinutes, remote.moveMinutes),
            heartPoints = maxOf(local.heartPoints, remote.heartPoints),
            activeKcal = maxOf(local.activeKcal, remote.activeKcal),
            waterMl = if (remoteIsNewer) remote.waterMl else local.waterMl,
            // updatedAt is deliberately left alone. It is a local clock reading, and the
            // server's stamp comes from a different clock; mixing the two into one column
            // makes "changed since" meaningless. The caller stamps it when it writes.
            updatedAt = local.updatedAt,
        )
    }

    /** True when the merge changed anything, so an unchanged day is not pushed back. */
    fun differs(record: DailyRecord, dto: DayDto): Boolean =
        record.steps != dto.steps ||
            record.moveMinutes != dto.moveMinutes ||
            record.heartPoints != dto.heartPoints ||
            record.activeKcal != dto.activeKcal ||
            record.waterMl != dto.waterMl

    fun toDto(record: DailyRecord): DayDto = DayDto(
        date = record.date,
        steps = record.steps,
        moveMinutes = record.moveMinutes,
        heartPoints = record.heartPoints,
        activeKcal = record.activeKcal,
        waterMl = record.waterMl,
        updatedAt = record.updatedAt,
    )

    fun toRecord(dto: DayDto): DailyRecord = DailyRecord(
        date = dto.date,
        steps = dto.steps,
        moveMinutes = dto.moveMinutes,
        heartPoints = dto.heartPoints,
        activeKcal = dto.activeKcal,
        waterMl = dto.waterMl,
        updatedAt = dto.updatedAt,
    )
}
