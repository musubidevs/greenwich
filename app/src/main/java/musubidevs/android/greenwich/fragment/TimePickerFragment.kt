package musubidevs.android.greenwich.fragment

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import musubidevs.android.greenwich.model.SourceTimestamp

/**
 * @author anticobalt
 * @author jmmxp
 */
class TimePickerFragment(private val currentTimestamp: SourceTimestamp) : DialogFragment(),
    TimePickerDialog.OnTimeSetListener {

    private var onTimeSetListener: OnTimeSetInterface? = null

    interface OnTimeSetInterface {
        fun onTimeSet(timestamp: SourceTimestamp)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context !is OnTimeSetInterface) {
            throw ClassCastException("The hosting activity does not implement OnTimeSetInterface")
        }
        onTimeSetListener = context
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return TimePickerDialog(context, this, currentTimestamp.hour, currentTimestamp.minute, true)
    }

    override fun onTimeSet(picker: TimePicker?, hour: Int, minute: Int) {
        onTimeSetListener?.onTimeSet(currentTimestamp.withTime(hour, minute))
    }

}