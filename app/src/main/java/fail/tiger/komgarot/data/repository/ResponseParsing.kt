package fail.tiger.komgarot.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import fail.tiger.komgarot.data.remote.dto.PagedDto
import okhttp3.ResponseBody

internal val repositoryGson = Gson()

internal inline fun <reified T> ResponseBody.toFlexibleList(vararg objectKeys: String): List<T> =
    parseListJson(objectKeys.toList()).let { array ->
        repositoryGson.fromJson(array, object : TypeToken<List<T>>() {}.type)
    }

internal inline fun <reified T> ResponseBody.toFlexiblePage(vararg objectKeys: String): PagedDto<T> {
    val element = parseJsonElement()
    val array = when {
        element.isJsonArray -> element.asJsonArray
        element.isJsonObject -> element.arrayFromObject(objectKeys.toList() + listOf("content", "items", "data"))
        else -> JsonArray()
    }
    val content: List<T> = repositoryGson.fromJson(array, object : TypeToken<List<T>>() {}.type)
    val obj = element.takeIf { it.isJsonObject }?.asJsonObject
    return PagedDto(
        content = content,
        totalPages = obj?.get("totalPages")?.asIntOrNull() ?: 1,
        totalElements = obj?.get("totalElements")?.asLongOrNull() ?: content.size.toLong(),
        number = obj?.get("number")?.asIntOrNull() ?: 0,
        size = obj?.get("size")?.asIntOrNull() ?: content.size
    )
}

internal fun ResponseBody.parseJsonElement(): JsonElement =
    use { JsonParser.parseString(it.string()) }

private fun ResponseBody.parseListJson(objectKeys: List<String>): JsonArray {
    val element = parseJsonElement()
    return when {
        element.isJsonArray -> element.asJsonArray
        element.isJsonObject -> element.arrayFromObject(objectKeys + listOf("content", "items", "data"))
        else -> JsonArray()
    }
}

internal fun JsonElement.arrayFromObject(keys: List<String>): JsonArray {
    val obj = asJsonObject
    return keys.firstNotNullOfOrNull { key ->
        obj.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    } ?: JsonArray()
}

private fun JsonElement.asIntOrNull(): Int? =
    runCatching { asInt }.getOrNull()

private fun JsonElement.asLongOrNull(): Long? =
    runCatching { asLong }.getOrNull()
