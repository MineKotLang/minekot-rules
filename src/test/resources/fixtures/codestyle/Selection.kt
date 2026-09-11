package org.minekot.api.utils

import org.minekot.api.platform.objects.MKEntity
import org.minekot.api.platform.objects.MKLocation
import org.minekot.api.platform.objects.MKPlayer
import kotlin.random.Random

/** Weighted table contract. */
class WeightedTable<T> private constructor(

    /** Entries value. */
    private val entries: List<Entry<T>>,
    private val total: Double,
) {
    /** Represents Entry. */
    data class Entry<T>(

        /** Value value. */
        val value: T,

        /** Cumulative weight value. */
        val cumulativeWeight: Double,
    )

    /** Performs pick. */
    fun pick(random: Random = Random.Default): T {
        check(entries.isNotEmpty()) { "Cannot pick from empty weighted table" }

        /** Target value. */
        val target = random.nextDouble(total)

        /** Low value. */
        var low = 0

        /** High value. */
        var high = entries.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (target < entries[middle].cumulativeWeight) high = middle else low = middle + 1
        }
        return entries[low].value
    }

    /** Contract documentation. */
    companion object {
        /** Performs of. */
        fun <T> of(values: Iterable<Pair<T, Number>>): WeightedTable<T> {
            var cumulative = 0.0
            val entries = values.map { (value, weightNumber) ->
                val weight = weightNumber.toDouble()
                require(weight.isFinite() && weight > 0) { "Weights must be finite and positive" }
                cumulative += weight
                Entry(value, cumulative)
            }
            require(entries.isNotEmpty()) { "Weighted table cannot be empty" }
            return WeightedTable(entries, cumulative)
        }
    }
}

/** Performs chance. */
fun chance(probability: Double, random: Random = Random.Default): Boolean {
    require(probability in 0.0..1.0) { "probability must be between 0 and 1" }
    return random.nextDouble() < probability
}

/** Contract documentation. */
fun <T> Iterable<T>.randomOrNull(random: Random = Random.Default): T? =
    toList().takeIf { it.isNotEmpty() }?.random(random)

/** Iterable contract. */
fun Iterable<MKPlayer>.nearest(to: MKLocation, maxDistance: Double = Double.POSITIVE_INFINITY): MKPlayer? =
    nearestEntity(to, maxDistance)

/** Contract documentation. */
fun <T : MKEntity> Iterable<T>.nearestEntity(to: MKLocation, maxDistance: Double = Double.POSITIVE_INFINITY): T? {
    /** Max squared value. */
    val maxSquared = maxDistance * maxDistance

    /** Nearest value. */
    var nearest: T? = null

    /** Nearest squared value. */
    var nearestSquared = maxSquared
    for (entity in this) {
        if (entity.world.key != to.world.key) continue

        /** Distance value. */
        val distance = entity.location.distanceSquared(to)
        if (distance <= nearestSquared) {
            nearest = entity
            nearestSquared = distance
        }
    }
    return nearest
}
