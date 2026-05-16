
package com.google.ai.edge.gallery.data

import androidx.annotation.StringRes
import com.google.ai.edge.gallery.R

/**
 * Stores basic info about a Category
 *
 * A category is a tab on the home page which contains a list of tasks. Category is set through
 * Task.
 */
data class CategoryInfo(
  // The id of the category.
  val id: String,

  // The string resource id of the label of the resource, for display purpose.
  @StringRes val labelStringRes: Int? = null,

  // The string label. It takes precedence over labelStringRes above.
  val label: String? = null,
)

/** Pre-defined categories. */
object Category {
  val LLM = CategoryInfo(id = "llm", labelStringRes = R.string.category_llm)
}
