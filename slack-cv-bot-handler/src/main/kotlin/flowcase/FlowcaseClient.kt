package no.jpro.slack.cv.flowcase

import no.jpro.slack.cv.objectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class FlowcaseClient(token: String, private val baseUrl: String = " https://jpro.flowcase.com") {
    private val httpClient = OkHttpClient.Builder()
        .authenticator { route, response ->
            response.request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        .build()

    private val userCache = mutableMapOf<String, User>()


     private fun getUserIdByEmail(email: String): User {
        if (userCache.containsKey(email)) {
            return userCache[email]!!
        }

        val url = "$baseUrl/api/v1/users/find?email=$email"
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseBody = response.body?.string() ?: throw IOException("No body in $response")
            val user = objectMapper.readValue(responseBody, User::class.java)
            userCache[email] = user
            return user
        }
    }

    fun getCV(email: String): String {
        val user= getUserIdByEmail(email)
        val url = "$baseUrl/api/v3/cvs/${user.id}/${user.default_cv_id}"
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val responseBody = response.body?.string() ?: throw IOException("No body in $response")
            return responseBody

        }
    }

   private  data class User(
        val id: String,
        val default_cv_id: String,
        val email: String
    )
}
