package com.meshi.code.backup

import android.content.Context
import android.net.Uri
import com.meshi.code.db.ContactNicknameDao
import com.meshi.code.db.ContactNicknameEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ContactBackupManager {

    /** Reads all contacts from the DAO and returns a formatted JSON string. */
    suspend fun export(dao: ContactNicknameDao): String {
        val contacts = dao.observeAll().first()
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("pubKeyHex",  c.pubKeyHex)
                    put("nickname",   c.nickname)
                    put("isVerified", c.isVerified)
                    put("tag",        c.tag)
                }
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("contacts", arr)
            .toString(2)
    }

    /**
     * Parses a backup [Uri] and upserts contacts into the DAO.
     * @return the number of contacts imported, or -1 on parse error.
     */
    suspend fun import(context: Context, uri: Uri, dao: ContactNicknameDao): Int {
        return try {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return -1
            val arr = JSONObject(json).getJSONArray("contacts")
            var count = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pubKeyHex = obj.optString("pubKeyHex").takeIf { it.isNotEmpty() } ?: continue
                dao.setNickname(
                    ContactNicknameEntity(
                        pubKeyHex  = pubKeyHex,
                        nickname   = obj.optString("nickname", ""),
                        isVerified = obj.optBoolean("isVerified", false),
                        tag        = obj.optString("tag", ""),
                    )
                )
                count++
            }
            count
        } catch (e: JSONException) {
            -1
        } catch (e: Exception) {
            -1
        }
    }
}
