
package com.google.ai.edge.gallery.ui.common


private const val TAG = "AGUtils"

fun Float.humanReadableDuration(): String {
  val milliseconds = this
  if (milliseconds < 1000) {
    return "$milliseconds ms"
  }
  val seconds = milliseconds / 1000f
  if (seconds < 60) {
    return "%.1f s".format(seconds)
  }

  val minutes = seconds / 60f
  if (minutes < 60) {
    return "%.1f min".format(minutes)
  }

  val hours = minutes / 60f
  return "%.1f h".format(hours)
}
