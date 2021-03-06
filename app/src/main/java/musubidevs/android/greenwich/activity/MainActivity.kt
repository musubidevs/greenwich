package musubidevs.android.greenwich.activity

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import androidx.core.content.edit
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.fatboyindustrial.gsonjodatime.Converters
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jaredrummler.cyanea.app.CyaneaAppCompatActivity
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.helpers.UndoHelper
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import musubidevs.android.greenwich.R
import musubidevs.android.greenwich.helper.TimeZoneFetcher
import musubidevs.android.greenwich.item.ConversionItem
import musubidevs.android.greenwich.layout.SingleColumnCardMargin
import musubidevs.android.greenwich.model.Conversion
import java.util.*

/**
 * Shows a list of all conversions.
 *
 * @author anticobalt
 * @author jmmxp
 */
class MainActivity : CyaneaAppCompatActivity(), ItemTouchCallback,
    SimpleSwipeCallback.ItemSwipeCallback {

    private lateinit var adapter: FastItemAdapter<ConversionItem>
    private lateinit var undoHelper: UndoHelper<ConversionItem>
    private val gson = Converters.registerDateTime(GsonBuilder()).create()
    private val timeZoneFetcher = TimeZoneFetcher().fetch()

    override fun onCreate(savedInstanceState: Bundle?) {

        val resId = getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
            .getInt(
                THEME_PREFS_KEY,
                R.style.DayTheme
            )
        setTheme(resId)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        conversionRecycler.layoutManager = LinearLayoutManager(this)
        createConversionButton.setOnClickListener { addNewConversion() }

        setRecyclerSpacing()
        setGestures()
        setAdapter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.time_zone_activity -> {
                waitForTimeZoneItems()
            }
            R.id.theme -> startActivity(Intent(this, ThemeActivity::class.java))
            R.id.day_night_mode -> {
                val prefs = getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
                val currentThemeResId = prefs.getInt(
                    THEME_PREFS_KEY,
                    R.style.DayTheme
                )
                val newTheme =
                    if (currentThemeResId == R.style.DayTheme) R.style.NightTheme else R.style.DayTheme
                prefs.edit {
                    putInt(THEME_PREFS_KEY, newTheme)
                }
                recreate()
            }
        }

        return false
    }

    override fun onResume() {
        super.onResume()
        adapter.set(readConversionsFromPrefs().map {
            ConversionItem(
                it,
                true,
                supportFragmentManager,
                adapter
            )
        })

    }

    override fun onPause() {
        super.onPause()
        saveConversionsToPrefs()
    }

    /**
     * Add spacing between items that doesn't double-up when re-sorted them via drag.
     * Doubling-up is caused by spacing dependent on position not being reset when item's position
     * changes.
     *
     * Basically, all items have space on top, and no space on bottom. RecyclerView has "see-through"
     * padding at the bottom, so it looks like the last item has space at the bottom.
     * Bottom padding behaviour achieved with clipToPadding=false: https://stackoverflow.com/a/22032130
     *
     * This is a hack.
     */
    private fun setRecyclerSpacing() {
        conversionRecycler.addItemDecoration(
            SingleColumnCardMargin(
                resources.getDimensionPixelSize(
                    R.dimen.card_margin
                )
            )
        )
        conversionRecycler.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.card_margin))
    }

    private fun setGestures() {
        conversionRecycler.itemAnimator = DefaultItemAnimator()
        val swipeCallback = ItemTouchHelper(
            SimpleSwipeCallback(
                this,
                null,
                bgColorLeft = Color.TRANSPARENT
            )
        )
        val dragCallback = ItemTouchHelper(SimpleDragCallback())
        swipeCallback.attachToRecyclerView(conversionRecycler)
        dragCallback.attachToRecyclerView(conversionRecycler)
    }

    private fun setAdapter() {
        adapter = FastItemAdapter()
        conversionRecycler.adapter = adapter

        undoHelper = UndoHelper(adapter, object : UndoHelper.UndoListener<ConversionItem> {
            override fun commitRemove(
                positions: Set<Int>,
                removed: ArrayList<FastAdapter.RelativeInfo<ConversionItem>>
            ) {
                // No implementation required
            }
        })
    }

    private fun waitForTimeZoneItems() {

        var dialogDismissedEarly = false
        val dialog = ProgressDialog(this@MainActivity)
        dialog.setTitle("Getting all time zones")
        dialog.setMessage("Loading...")
        dialog.show()
        dialog.setOnDismissListener {
            dialogDismissedEarly = true
        }

        GlobalScope.launch(Dispatchers.Default) {
            while (timeZoneFetcher.timeZoneItems.isEmpty()) {
                // Loop until time zones are finished loading
            }
            withContext(Dispatchers.Main) {
                if (!dialogDismissedEarly) {
                    val intent = Intent(this@MainActivity, TimeZoneActivity::class.java)
                    intent.putParcelableArrayListExtra(
                        TimeZoneActivity.KEY_TIME_ZONE_ITEMS,
                        ArrayList(timeZoneFetcher.timeZoneItems)
                    )
                    startActivity(intent)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun addNewConversion() {
        adapter.add(
            0, ConversionItem(
                Conversion(),
                true,
                supportFragmentManager,
                adapter
            )
        )
        conversionRecycler.smoothScrollToPosition(0)
    }

    private fun saveConversionsToPrefs() {
        val prefs = getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(
                CONVERSIONS_PREFS_KEY,
                gson.toJson(adapter.adapterItems.map { it.conversion })
            )
        }
    }

    private fun readConversionsFromPrefs(): MutableList<Conversion> {
        val prefs = getSharedPreferences(MAIN_PREFS, Context.MODE_PRIVATE)
        val conversionsJson =
            prefs.getString(CONVERSIONS_PREFS_KEY, "") ?: return mutableListOf()
        if (conversionsJson.isEmpty()) return mutableListOf()
        return gson.fromJson(
            conversionsJson,
            object : TypeToken<MutableList<Conversion>>() {}.type
        )
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        // No need to implement
        return false
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        val items = adapter.adapterItems.toTypedArray()
        val temp = items[oldPosition]
        items[oldPosition] = items[newPosition]
        items[newPosition] = temp
    }

    override fun itemSwiped(position: Int, direction: Int) {
        if (direction == ItemTouchHelper.LEFT) {
            val holder =
                conversionRecycler.findViewHolderForAdapterPosition(position) as ConversionItem.ConversionViewHolder
            holder.hideView()
            val snackbar = undoHelper.remove(
                mainConstraintLayout,
                getString(R.string.delete_conversion),
                getString(R.string.delete_undo),
                Snackbar.LENGTH_LONG,
                setOf(position)
            )
            // https://stackoverflow.com/a/57417537
            snackbar.view.findViewById<Button?>(R.id.snackbar_action)?.background = null

        }
    }

    companion object {
        const val MAIN_PREFS = "main_prefs"
        const val THEME_PREFS_KEY = "theme"
        private const val CONVERSIONS_PREFS_KEY = "conversions"
    }
}
