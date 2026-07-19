package com.vivara.browser.activity.downloads

import com.vivara.browser.model.Download
import com.vivara.browser.singleton.AppDatabase
import com.vivara.browser.utils.observable.ObservableValue
import com.vivara.browser.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadsHistoryModel: ActiveModel() {
  val allItems = ArrayList<Download>()
  val lastLoadedItems = ObservableValue<List<Download>>(ArrayList())
  private var loading = false

  fun loadNextItems() = modelScope.launch(Dispatchers.Main) {
    if (loading) {
      return@launch
    }
    loading = true

    val newItems = AppDatabase.db.downloadDao().allByLimitOffset(allItems.size.toLong())
    lastLoadedItems.value = newItems
    allItems.addAll(newItems)

    loading = false
  }
}