// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.sakugapp.R
import com.sakugapp.data.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DateFilterControl(query:String,edit:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val range=DateRangeFilter.read(query);val unsupported=DateRangeFilter.unsupported(query)
    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(enabled=!unsupported,onClick={focus.clearFocus();keyboard?.hide();open=true},modifier=Modifier.weight(1f).testTag("date-range")){Text(range?.let{"${it.start} – ${it.end}"} ?: text(R.string.ui_date_range))}
        TextButton(enabled=range!=null,onClick={focus.clearFocus();keyboard?.hide();edit(DateRangeFilter.set(query,null))},modifier=Modifier.testTag("clear-date")){Text(text(R.string.ui_clear_date))}
    }
    if(unsupported)Text(text(R.string.ui_date_raw),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    if(open){
        val state=rememberDateRangePickerState(yearRange=minOf(1900,range?.start?.year ?: 1900)..maxOf(2100,range?.end?.year ?: 2100),initialSelectedStartDateMillis=range?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),initialSelectedEndDateMillis=range?.end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(enabled=state.selectedStartDateMillis!=null && state.selectedEndDateMillis!=null,onClick={
            val start=Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate();val end=Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
            edit(DateRangeFilter.set(query,CalendarRange(start,end)));open=false
        }){Text(text(R.string.ui_apply))}},dismissButton={TextButton(onClick={open=false}){Text(text(R.string.ui_cancel))}}){DateRangePicker(state,modifier=Modifier.heightIn(max=440.dp).testTag("date-picker"),title={Text(text(R.string.ui_date_range))})}
    }
}
