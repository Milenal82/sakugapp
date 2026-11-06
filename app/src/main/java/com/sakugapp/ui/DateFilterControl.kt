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
    val range=DateRangeFilter.read(query);val unsupported=DateRangeFilter.unsupported(query)
    Row {
        TextButton(enabled=!unsupported,onClick={open=true},modifier=Modifier.testTag("date-range")){Text(text(R.string.ui_date_range))}
        TextButton(enabled=range!=null,onClick={edit(DateRangeFilter.set(query,null))},modifier=Modifier.testTag("clear-date")){Text(text(R.string.ui_clear_date))}
    }
    range?.let{Text(it.token,style=MaterialTheme.typography.bodySmall)}
    if(unsupported)Text(text(R.string.ui_date_raw),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    if(open){
        val state=rememberDateRangePickerState(initialSelectedStartDateMillis=range?.start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),initialSelectedEndDateMillis=range?.end?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest={open=false},confirmButton={TextButton(enabled=state.selectedStartDateMillis!=null && state.selectedEndDateMillis!=null,onClick={
            val start=Instant.ofEpochMilli(state.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate();val end=Instant.ofEpochMilli(state.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
            edit(DateRangeFilter.set(query,CalendarRange(start,end)));open=false
        }){Text(text(R.string.ui_apply))}},dismissButton={TextButton(onClick={open=false}){Text(text(R.string.ui_cancel))}}){DateRangePicker(state,modifier=Modifier.heightIn(max=440.dp),title={Text(text(R.string.ui_date_range))})}
    }
}
