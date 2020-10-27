package util

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Http{
    companion object{
        fun getResponse(url:String,cookies:String): Response {
            var httpclient: OkHttpClient = OkHttpClient.Builder()
//            .cookieJar(cookieJar!!)
                    .build()
            var request= Request.Builder()
                    .url(url)
                    .get().addHeader("Cookie",cookies)
                    .build()
            return httpclient.newCall(request).execute()
        }
    }
}