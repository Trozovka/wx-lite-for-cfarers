package com.trozovka.wxlite

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.trozovka.wxlite.chart.Coordinates
import com.trozovka.wxlite.data.AreaPoint
import com.trozovka.wxlite.data.AreaStore

/**
 * The "second sheet" -- up to 10 waypoints (degrees-minutes, the maritime
 * convention) that the crew connects in order to bound the exact area of
 * their passage plan, replacing the old single default-location concept.
 * Any row can be left blank; the chart connects only the filled-in rows,
 * in order, to outline the area.
 */
class AreaWaypointActivity : Activity() {
    private lateinit var areaStore: AreaStore
    private val rows = mutableListOf<WaypointRow>()

    private data class WaypointRow(
        val latDeg: EditText,
        val latMin: EditText,
        val latHemi: Button,
        val lonDeg: EditText,
        val lonMin: EditText,
        val lonHemi: Button,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        areaStore = AreaStore(this)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        root.addView(
            TextView(this).apply {
                text = "Passage Plan Area"
                textSize = 20f
            },
        )
        root.addView(
            TextView(this).apply {
                text = "Enter up to 10 points, in order. Leave any row blank to skip it."
                textSize = 13f
                setPadding(0, 8, 0, 24)
            },
        )

        val existing = areaStore.get()
        for (i in 0 until AreaStore.MAX_POINTS) {
            root.addView(buildRow(i + 1, existing.getOrNull(i)))
        }

        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener { onSaveClicked() }
        }
        root.addView(saveBtn)

        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setOnClickListener { finish() }
        }
        root.addView(cancelBtn)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun buildRow(pointNumber: Int, existing: AreaPoint?): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        container.addView(
            TextView(this).apply {
                text = "#$pointNumber"
                textSize = 15f
            },
        )

        val existingLatDm = existing?.let { Coordinates.decimalToDegMin(it.lat) }
        val existingLonDm = existing?.let { Coordinates.decimalToDegMin(it.lon) }

        val latDeg = degreesInput(maxLength = 2, hint = "xx")
        val latMin = minutesInput(hint = "xx.x")
        val latHemi = hemisphereButton(if (existingLatDm?.positive == false) "S" else "N", "N", "S")
        existingLatDm?.let {
            latDeg.setText(it.degrees.toString())
            latMin.setText("%.1f".format(it.minutes))
        }
        autoAdvance(latDeg, latMin, maxLength = 2)

        val latRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        latRow.addView(TextView(this).apply { text = "Lat = " })
        latRow.addView(latDeg)
        latRow.addView(TextView(this).apply { text = "-" })
        latRow.addView(latMin)
        latRow.addView(latHemi)
        container.addView(latRow)

        val lonDeg = degreesInput(maxLength = 3, hint = "yyy")
        val lonMin = minutesInput(hint = "yy.y")
        val lonHemi = hemisphereButton(if (existingLonDm?.positive == false) "W" else "E", "E", "W")
        existingLonDm?.let {
            lonDeg.setText(it.degrees.toString())
            lonMin.setText("%.1f".format(it.minutes))
        }
        autoAdvance(lonDeg, lonMin, maxLength = 3)

        val lonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lonRow.addView(TextView(this).apply { text = "Lon = " })
        lonRow.addView(lonDeg)
        lonRow.addView(TextView(this).apply { text = "-" })
        lonRow.addView(lonMin)
        lonRow.addView(lonHemi)
        container.addView(lonRow)

        rows.add(WaypointRow(latDeg, latMin, latHemi, lonDeg, lonMin, lonHemi))
        return container
    }

    private fun degreesInput(maxLength: Int, hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        setEms(maxLength)
    }

    private fun minutesInput(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        filters = arrayOf(InputFilter.LengthFilter(4))
        setEms(4)
    }

    /** Toggles between the two hemisphere letters on tap -- e.g. "N" <-> "S". */
    private fun hemisphereButton(initial: String, positiveLabel: String, negativeLabel: String): Button =
        Button(this).apply {
            text = initial
            setOnClickListener {
                this.text = if (this.text == positiveLabel) negativeLabel else positiveLabel
            }
        }

    /** Jumps focus straight to the minutes field once degrees reaches its
     * max length, so the user never has to manually tab/tap over -- per
     * the operator's explicit ask, no "-" to type either (that's just
     * static display text between the two fields). */
    private fun autoAdvance(degreesField: EditText, minutesField: EditText, maxLength: Int) {
        degreesField.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if ((s?.length ?: 0) >= maxLength) {
                        minutesField.requestFocus()
                    }
                }
            },
        )
    }

    private fun onSaveClicked() {
        val points = arrayOfNulls<AreaPoint>(AreaStore.MAX_POINTS)

        for ((index, row) in rows.withIndex()) {
            val latDegText = row.latDeg.text.toString().trim()
            val lonDegText = row.lonDeg.text.toString().trim()

            // A row counts as "filled" only if both lat and lon degrees are
            // present -- otherwise it's treated as blank/skipped, even if
            // one half was partially typed.
            if (latDegText.isEmpty() && lonDegText.isEmpty()) continue

            val pointNumber = index + 1
            val latDeg = latDegText.toIntOrNull()
            val latMin = row.latMin.text.toString().trim().toDoubleOrNull() ?: 0.0
            val lonDeg = lonDegText.toIntOrNull()
            val lonMin = row.lonMin.text.toString().trim().toDoubleOrNull() ?: 0.0

            if (latDeg == null || lonDeg == null || !Coordinates.isValidLat(latDeg, latMin) ||
                !Coordinates.isValidLon(lonDeg, lonMin)
            ) {
                Toast.makeText(this, "Point #$pointNumber has an invalid lat/lon -- fix or clear it.", Toast.LENGTH_LONG).show()
                return
            }

            val lat = Coordinates.degMinToDecimal(latDeg, latMin, row.latHemi.text == "N")
            val lon = Coordinates.degMinToDecimal(lonDeg, lonMin, row.lonHemi.text == "E")
            points[index] = AreaPoint(lat, lon)
        }

        areaStore.save(points.toList())
        setResult(RESULT_OK)
        finish()
    }
}
