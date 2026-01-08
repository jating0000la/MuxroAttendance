package com.muxrotechnologies.muxroattendance.utils

import android.content.Context
import com.muxrotechnologies.muxroattendance.AttendanceApplication
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceLog
import com.muxrotechnologies.muxroattendance.data.entity.AttendanceType
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lightweight HTTP server for serving attendance reports
 */
class ReportServer(private val context: Context, port: Int) : NanoHTTPD(port) {
    
    private val app = AttendanceApplication.getInstance()
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parms
        
        return when {
            uri == "/" || uri == "/index.html" -> serveReportPage(params)
            uri.startsWith("/api/") -> serveApi(uri, params)
            uri.startsWith("/export/") -> serveExport(uri, params)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }
    
    private fun serveReportPage(params: Map<String, String>): Response {
        val template = loadTemplate()
        val year = params["year"]?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = params["month"]?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)
        val tab = params["tab"] ?: "pa"
        
        val reportContent = generateReportContent(year, month, tab)
        val reportData = generateReportData(year, month, tab)
        
        val html = template
            .replace("{{COMPANY_NAME}}", "Muxro Technologies")
            .replace("{{REPORT_TITLE}}", getReportTitle(tab))
            .replace("{{MONTH_YEAR}}", "${getMonthName(month)} $year")
            .replace("{{TOTAL_EMPLOYEES}}", getTotalEmployees().toString())
            .replace("{{GENERATED_TIME}}", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .replace("{{YEAR}}", year.toString())
            .replace("{{REPORT_CONTENT}}", reportContent)
            .replace("{{REPORT_DATA_JSON}}", reportData)
        
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
    
    private fun serveApi(uri: String, params: Map<String, String>): Response {
        val year = params["year"]?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val month = params["month"]?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)
        
        val json = when {
            uri.contains("/api/attendance") -> getAttendanceData(year, month)
            uri.contains("/api/users") -> getUsersData()
            else -> "{\"error\": \"Unknown API endpoint\"}"
        }
        
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }
    
    private fun serveExport(uri: String, params: Map<String, String>): Response {
        // Export functionality placeholder
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "Export feature")
    }
    
    private fun loadTemplate(): String {
        return try {
            val inputStream = context.assets.open("report_template.html")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readText()
        } catch (e: Exception) {
            "<html><body><h1>Error loading template</h1></body></html>"
        }
    }
    
    private fun generateReportContent(year: Int, month: Int, tab: String): String {
        val logs = getAttendanceLogs(year, month)
        val users = runBlocking { app.userRepository.getAllUsers() }
        
        return when (tab) {
            "pa" -> generatePAReport(users, logs, year, month)
            "duration" -> generateDurationReport(users, logs, year, month)
            "time" -> generateTimeReport(users, logs, year, month)
            else -> ""
        }
    }
    
    private fun generatePAReport(users: List<com.muxrotechnologies.muxroattendance.data.entity.User>, 
                                 logs: List<AttendanceLog>, year: Int, month: Int): String {
        val daysInMonth = getDaysInMonth(year, month)
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        val todayTimestamp = today.timeInMillis
        
        val sb = StringBuilder()
        
        sb.append("<table>")
        sb.append("<thead><tr>")
        sb.append("<th class='sortable' onclick='sortTable(0)'>Full Name ▲</th>")
        sb.append("<th class='sortable' onclick='sortTable(1)'>Employee ID</th>")
        for (day in 1..daysInMonth) {
            sb.append("<th>$day</th>")
        }
        sb.append("<th class='sortable' onclick='sortTable(${daysInMonth + 2})'>Total</th>")
        sb.append("<th class='sortable' onclick='sortTable(${daysInMonth + 3})'>%</th>")
        sb.append("</tr></thead>")
        sb.append("<tbody>")
        
        for (user in users) {
            sb.append("<tr>")
            
            var presentDays = 0
            var totalWorkingDays = 0
            
            // First pass to count present days and total working days
            for (day in 1..daysInMonth) {
                val calendar = Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                
                // Skip future dates
                if (dayStart > todayTimestamp) continue
                
                totalWorkingDays++
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val dayEnd = calendar.timeInMillis
                
                val dayLogs = logs.filter { 
                    it.userId == user.id && it.timestamp in dayStart..dayEnd
                }
                
                if (dayLogs.isNotEmpty()) {
                    presentDays++
                }
            }
            
            // Calculate attendance percentage
            val attendancePercent = if (totalWorkingDays > 0) {
                (presentDays * 100.0 / totalWorkingDays).toInt()
            } else {
                0
            }
            
            val percentClass = when {
                attendancePercent >= 80 -> "percent-high"
                attendancePercent >= 60 -> "percent-medium"
                else -> "percent-low"
            }
            
            // Display name with percentage badge
            sb.append("<td>${user.name} <span class='attendance-percent $percentClass'>$attendancePercent%</span></td>")
            sb.append("<td>${user.userId}</td>")
            
            // Display attendance for each day
            for (day in 1..daysInMonth) {
                val calendar = Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val dayEnd = calendar.timeInMillis
                
                // Skip future dates - don't mark them as absent
                if (dayStart > todayTimestamp) {
                    sb.append("<td></td>")
                    continue
                }
                
                val dayLogs = logs.filter { 
                    it.userId == user.id && it.timestamp in dayStart..dayEnd
                }
                
                // Add tooltip with time information
                val timeInfo = if (dayLogs.isNotEmpty()) {
                    val firstIn = dayLogs.firstOrNull { it.type == AttendanceType.CHECK_IN }
                    val lastOut = dayLogs.lastOrNull { it.type == AttendanceType.CHECK_OUT }
                    val inTime = firstIn?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp)) } ?: ""
                    val outTime = lastOut?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.timestamp)) } ?: ""
                    "In: $inTime, Out: $outTime"
                } else {
                    "Absent"
                }
                
                val status = if (dayLogs.isNotEmpty()) {
                    "<span class='status-p' title='$timeInfo'>P</span>"
                } else {
                    "<span class='status-a' title='$timeInfo'>A</span>"
                }
                sb.append("<td>$status</td>")
            }
            
            sb.append("<td class='total-cell'>$presentDays</td>")
            sb.append("<td class='total-cell'>$attendancePercent%</td>")
            sb.append("</tr>")
        }
        
        sb.append("</tbody></table>")
        return sb.toString()
    }
    
    private fun generateDurationReport(users: List<com.muxrotechnologies.muxroattendance.data.entity.User>, 
                                       logs: List<AttendanceLog>, year: Int, month: Int): String {
        val daysInMonth = getDaysInMonth(year, month)
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        val todayTimestamp = today.timeInMillis
        
        val sb = StringBuilder()
        
        sb.append("<table>")
        sb.append("<thead><tr>")
        sb.append("<th class='sortable' onclick='sortTable(0)'>Full Name ▲</th>")
        sb.append("<th class='sortable' onclick='sortTable(1)'>Employee ID</th>")
        for (day in 1..daysInMonth) {
            sb.append("<th>$day</th>")
        }
        sb.append("<th class='sortable' onclick='sortTable(${daysInMonth + 2})'>Total Hours</th>")
        sb.append("<th class='sortable' onclick='sortTable(${daysInMonth + 3})'>Avg/Day</th>")
        sb.append("</tr></thead>")
        sb.append("<tbody>")
        
        for (user in users) {
            sb.append("<tr>")
            sb.append("<td>${user.name}</td>")
            sb.append("<td>${user.userId}</td>")
            
            var totalHours = 0.0
            var workingDays = 0
            
            for (day in 1..daysInMonth) {
                val calendar = Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val dayEnd = calendar.timeInMillis
                
                // Skip future dates
                if (dayStart > todayTimestamp) {
                    sb.append("<td></td>")
                    continue
                }
                
                val dayLogs = logs.filter { 
                    it.userId == user.id && it.timestamp in dayStart..dayEnd
                }.sortedBy { it.timestamp }
                
                val duration = calculateDuration(dayLogs)
                if (duration > 0) {
                    workingDays++
                    val hoursFormatted = String.format("%.2f", duration)
                    sb.append("<td class='duration-cell' title='$hoursFormatted hours'>$hoursFormatted</td>")
                } else {
                    sb.append("<td></td>")
                }
                totalHours += duration
            }
            
            val avgHours = if (workingDays > 0) totalHours / workingDays else 0.0
            sb.append("<td class='total-cell'>${String.format("%.1f", totalHours)}h</td>")
            sb.append("<td class='total-cell'>${String.format("%.1f", avgHours)}h</td>")
            sb.append("</tr>")
        }
        
        sb.append("</tbody></table>")
        return sb.toString()
    }
    
    private fun generateTimeReport(users: List<com.muxrotechnologies.muxroattendance.data.entity.User>, 
                                   logs: List<AttendanceLog>, year: Int, month: Int): String {
        val daysInMonth = getDaysInMonth(year, month)
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        val todayTimestamp = today.timeInMillis
        
        val sb = StringBuilder()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        
        sb.append("<table>")
        sb.append("<thead><tr>")
        sb.append("<th class='sortable' onclick='sortTable(0)'>Full Name ▲</th>")
        sb.append("<th class='sortable' onclick='sortTable(1)'>Employee ID</th>")
        for (day in 1..daysInMonth) {
            sb.append("<th>$day</th>")
        }
        sb.append("</tr></thead>")
        sb.append("<tbody>")
        
        for (user in users) {
            sb.append("<tr>")
            sb.append("<td>${user.name}</td>")
            sb.append("<td>${user.userId}</td>")
            
            for (day in 1..daysInMonth) {
                val calendar = Calendar.getInstance()
                calendar.set(year, month - 1, day, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val dayStart = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val dayEnd = calendar.timeInMillis
                
                // Skip future dates
                if (dayStart > todayTimestamp) {
                    sb.append("<td></td>")
                    continue
                }
                
                val dayLogs = logs.filter { 
                    it.userId == user.id && it.timestamp in dayStart..dayEnd
                }.sortedBy { it.timestamp }
                
                val times = StringBuilder()
                for (log in dayLogs) {
                    val time = timeFormat.format(Date(log.timestamp))
                    val cssClass = if (log.type == AttendanceType.CHECK_IN) "time-in" else "time-out"
                    val label = if (log.type == AttendanceType.CHECK_IN) "IN" else "OUT"
                    times.append("<span class='time-cell $cssClass' title='$label at $time'>$time</span><br>")
                }
                
                sb.append("<td class='time-cell'>$times</td>")
            }
            
            sb.append("</tr>")
        }
        
        sb.append("</tbody></table>")
        return sb.toString()
    }
    
    private fun calculateDuration(logs: List<AttendanceLog>): Double {
        if (logs.size < 2) return 0.0
        
        val checkIns = logs.filter { it.type == AttendanceType.CHECK_IN }
        val checkOuts = logs.filter { it.type == AttendanceType.CHECK_OUT }
        
        if (checkIns.isEmpty() || checkOuts.isEmpty()) return 0.0
        
        val firstIn = checkIns.first().timestamp
        val lastOut = checkOuts.last().timestamp
        
        val diffHours = (lastOut - firstIn) / (1000.0 * 60 * 60)
        return diffHours
    }
    
    private fun getAttendanceLogs(year: Int, month: Int): List<AttendanceLog> {
        return runBlocking {
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            calendar.set(Calendar.DAY_OF_MONTH, getDaysInMonth(year, month))
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val endTime = calendar.timeInMillis
            
            app.attendanceRepository.getAttendanceByDateRange(startTime, endTime)
        }
    }
    
    private fun generateReportData(year: Int, month: Int, tab: String): String {
        return "[]" // Placeholder for JavaScript data
    }
    
    private fun getAttendanceData(year: Int, month: Int): String {
        val logs = getAttendanceLogs(year, month)
        return logs.toString() // Convert to proper JSON
    }
    
    private fun getUsersData(): String {
        val users = runBlocking { app.userRepository.getAllUsers() }
        return users.toString() // Convert to proper JSON
    }
    
    private fun getTotalEmployees(): Int {
        return runBlocking { app.userRepository.getAllUsers().size }
    }
    
    private fun getDaysInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    
    private fun getMonthName(month: Int): String {
        val months = arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        return months.getOrNull(month - 1) ?: "Unknown"
    }
    
    private fun getReportTitle(tab: String): String {
        return when (tab) {
            "pa" -> "P/A Report"
            "duration" -> "Duration Report"
            "time" -> "Time Report"
            else -> "Report"
        }
    }
}
