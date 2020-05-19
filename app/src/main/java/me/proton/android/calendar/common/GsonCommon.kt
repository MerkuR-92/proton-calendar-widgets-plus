package me.proton.android.calendar.common

import com.google.gson.FieldNamingStrategy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

object GsonCommon {

    val gson = GsonBuilder().apply {
        setFieldNamingStrategy {
            // this allows us to use lowercase properties in code and properly (de)serialize
            //  them when they come from server or are sent as uppercase
            when (it.name) {
                "id" -> "ID"
                "uid" -> "UID"
                "srpSession" -> "SRPSession"
                else -> {
                    it.name
                        .replace("Id", "ID", ignoreCase = false)
                        .replace("Uri", "URI", ignoreCase = false)
                }
            }.capitalize()
        }
//        registerTypeAdapter( // TODO make one enum and boolean adapter for all types
//            ProtonEvent.Action::class.java,
//            ProtonEvent.Action.GsonSerializer()
//        )
    }.create()

    val jsonElementListType: Type = object : TypeToken<List<JsonElement>>() {}.type

}


/**
 * Helper method for loading JSON objects from local resource files for testing.
 */
fun <T: Any> Gson.readLocalResourceTestFile(fileName: String, clazz: Class<T>): T {
    return this.fromJson(File("src/test/resources/$fileName").readText(), clazz) as T
}
