package app.roll.gallery.recognition

import java.io.File

/**
 * How many of the phone's processor cores are performance cores, given each core's top speed
 * ([maxFrequencies], any unit). Phones mix fast cores with slow power-saving ones, and Android
 * doesn't label them, so a core counts as a performance core when its top speed is at least
 * [SHARE] of the fastest core's. This keeps the mid and prime cores and leaves out the small
 * ones, while phones whose cores are all big (where the slowest still reach most of the top
 * speed) use every core. With no speeds known, half of [fallback] (the processor count) is used.
 */
fun performanceCoreCount(maxFrequencies: List<Long>, fallback: Int): Int {
    val known = maxFrequencies.filter { it > 0 }
    if (known.isEmpty()) return (fallback / 2).coerceAtLeast(2)
    val top = known.max()
    return known.count { it >= top * SHARE }.coerceAtLeast(1)
}

/** The share of the fastest core's top speed a core needs to count as a performance core. */
const val SHARE = 0.7

/**
 * The number of performance cores on this phone, read from each core's top speed in the
 * kernel's processor files (readable by apps), using [performanceCoreCount].
 */
fun performanceCores(): Int {
    val processors = Runtime.getRuntime().availableProcessors()
    val speeds = (0 until processors).map { core ->
        runCatching { File("/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrDefault(0L)
    }
    return performanceCoreCount(speeds, processors)
}
