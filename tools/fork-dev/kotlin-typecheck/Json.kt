package org.json
class JSONObject() { constructor(s: String) : this()
  fun optJSONArray(k: String): JSONArray? = null; fun optJSONObject(k: String): JSONObject? = null
  fun optString(k: String): String = ""; fun optString(k: String, d: String): String = d; fun optLong(k: String, d: Long = 0): Long = d
  fun optInt(k: String, d: Int = 0) = d; fun optBoolean(k: String, d: Boolean = false) = d; fun getLong(k: String) = 0L; fun getString(k: String) = ""; fun getJSONObject(k: String) = JSONObject()
  fun put(k: String, v: Any?): JSONObject = this; fun has(k: String) = false; fun length() = 0; fun keys(): Iterator<String> = emptyList<String>().iterator(); fun remove(k: String): Any? = null }
class JSONArray() { constructor(s: String) : this(); constructor(c: Collection<*>) : this(); fun length() = 0; fun optJSONObject(i: Int): JSONObject? = null; fun getJSONObject(i: Int) = JSONObject(); fun optJSONArray(i: Int): JSONArray? = null; fun optString(i: Int) = ""; fun put(v: Any?) = this; fun getString(i: Int) = "" }
