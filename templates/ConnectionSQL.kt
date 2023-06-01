package database

import apis.Base
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

private fun castValue(value: Any?): String {
    return when (value) {
        is String -> "'$value'"
        is List<*> -> value.joinToString(prefix = "(", postfix = ")") { castValue(it) }
        else -> "$value"
    }
}

open class ConnectionSQL(val tableName: String, private val connection: Connection) : Base() {

    companion object {
    }

    protected fun getResultSetAsList(resultSet: ResultSet): List<Map<String, Any>> {
        var resultList: MutableList<Map<String, Any>> = mutableListOf()
        while(resultSet.next()) {
            val resultMap = LinkedHashMap<String, Any>()
            val numberOfColumns = resultSet.metaData.columnCount
            for (i in 1 .. numberOfColumns) {
                resultMap[resultSet.metaData.getColumnName(i)] = resultSet.getObject(i)
            }
            resultList.add(resultMap)
        }
        return resultList.toList()
    }

    protected fun getColumn(columnName: String, results: List<Map<String, Any>>): List<Any?> {
        var columnValues = mutableListOf<Any?>()
        for (result in results) {
           columnValues.add(result[columnName])
        }
        return columnValues.toList()
    }

    protected fun runQuery(queryString: String): ResultSet {
        println("QUERY: \n$queryString")
        val preparedStatement = connection.prepareStatement(queryString)
        return preparedStatement.executeQuery()
    }

    protected fun runUpdate(queryString: String): Int {
        println("QUERY: \n$queryString")
        val preparedStatement = connection.prepareStatement(queryString)
        return preparedStatement.executeUpdate()
    }

    protected fun selectAll(): List<Map<String, Any>> {
        return select(listOf("*"))
    }

    protected fun selectAll(column: String): List<Any?> {
        return getColumn(column, selectAll())
    }

    protected fun selectAll(filters: QueryConditions): List<Map<String, Any>> {
        return select(listOf("*"), filters)
    }

    protected fun selectAll(filters: QueryConditions, column: String): List<Any?> {
        return getColumn(column, selectAll(filters))
    }

    protected fun selectAll(sorters: QuerySorters): List<Map<String, Any>> {
        return select(listOf("*"), null, sorters)
    }

    protected fun selectAll(sorters: QuerySorters, column: String): List<Any?> {
        return getColumn(column, selectAll(sorters))
    }

    protected fun select(columns: List<String>): List<Map<String, Any>> {
        return select(columns, null, null)
    }

    protected fun select(columns: List<String>, filters: QueryConditions): List<Map<String, Any>> {
        return select(columns, filters, null)
    }

    protected fun select(columns: List<String>, filters: QueryConditions?, sorters: QuerySorters?): List<Map<String, Any>> {
        var queryString = "SELECT ${columns.joinToString(", ")} FROM $tableName"
        if(filters != null) queryString +=  " ${filters.getConditions()}"
        if(sorters != null) queryString +=  " ${sorters.getOrderBy()}"
        val resultSet = runQuery(queryString)
        return getResultSetAsList(resultSet)
    }

    protected fun insert(columnsAndValues: Map<String, Any>): Boolean {
        var queryString = columnsAndValues.toList().joinToString(separator = ", ", prefix = "INSERT INTO $tableName(", postfix = ") " ) { "${it.first}"}
        queryString += columnsAndValues.toList().joinToString(separator = ", ", prefix = "VALUES (", postfix = ")" ) { "${castValue(it.second)}"}
        val rows = runUpdate(queryString)
        return rows > 0
    }

    protected fun update(columnsAndValues: Map<String, Any>): Boolean {
        return update(columnsAndValues, null)
    }

    protected fun update(columnsAndValues: Map<String, Any>, filters: QueryConditions?): Boolean {
        var queryString = "UPDATE $tableName SET "
        queryString += columnsAndValues.toList().joinToString(separator = ", ") { "${it.first} = ${castValue(it.second)}"}
        if(filters != null) queryString +=  " ${filters.getConditions()}"
        val rows = runUpdate(queryString)
        return rows > 0
    }

    protected fun delete(filters: QueryConditions?): Boolean {
        var queryString = "DELETE FROM $tableName"
        if(filters != null) queryString +=  " ${filters.getConditions()}"
        val rows = runUpdate(queryString)
        return rows > 0
    }
}

class QueryConditions {
    private var conditions: String = ""

        fun where(columnName: String, conditional: String, columnValue: Any): QueryConditions {
        conditions = "WHERE $columnName $conditional ${castValue(columnValue)}"
        return this
    }

    fun and(columnName: String, conditional: String, columnValue: Any): QueryConditions {
        conditions += " AND $columnName $conditional ${castValue(columnValue)}"
        return this
    }

    fun or(columnName: String, conditional: String, columnValue: Any): QueryConditions {
        conditions += " OR $columnName $conditional ${castValue(columnValue)}"
        return this
    }

    fun getConditions(): String {
        return conditions
    }
}

class QuerySorters {
    private val sorters = mutableMapOf<String, String>()

    fun orderBy(column: String, option: String): QuerySorters {
        sorters[column] = option
        return this
    }

    fun andBy(column: String, option: String): QuerySorters {
        return orderBy(column, option)
    }

    fun getOrderBy(): String {
        return sorters.toList().joinToString(separator = ", ", prefix = "ORDER BY " ) { "${it.first} ${it.second.uppercase()}" }
    }
}