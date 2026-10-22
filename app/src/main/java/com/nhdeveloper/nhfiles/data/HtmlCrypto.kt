package com.nhdeveloper.nhfiles.data

import android.util.Base64
import java.security.SecureRandom

/**
 * Wraps an HTML file's real content so it is not readable as plain source, while the output
 * file is still a normal, directly-runnable HTML file: opening it in a browser or WebView
 * executes a small bootstrap script that decodes the payload and renders the original page.
 * This is XOR + Base64 (not plain Base64 alone, and not reversible by simply decoding Base64),
 * with a random per-file key, so it is meant to deter casual viewing of the source, not as
 * cryptographic security.
 */
object HtmlCrypto {
    private const val MARKER = "<!--NHCRYPT:v1-->"
    private val keyRegex = Regex("var __k=\"([A-Za-z0-9+/=]+)\";")
    private val payloadRegex = Regex("var __p=\"([A-Za-z0-9+/=]+)\";")

    fun isEncrypted(html: String): Boolean = html.contains(MARKER)

    /** Produces a new, self-decrypting HTML document from the original file's raw bytes. */
    fun encrypt(original: ByteArray): String {
        val key = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val xored = xor(original, key)
        val payloadB64 = Base64.encodeToString(xored, Base64.NO_WRAP)
        val keyB64 = Base64.encodeToString(key, Base64.NO_WRAP)
        return buildString {
            append(MARKER).append('\n')
            append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
            append("<title>Protected page</title></head><body style=\"background:#0d0f12\">")
            append("<script>\n")
            append("var __k=\"").append(keyB64).append("\";\n")
            append("var __p=\"").append(payloadB64).append("\";\n")
            append(
                """
                function __b64ToBytes(b64){
                  var bin=atob(b64),len=bin.length,out=new Uint8Array(len);
                  for(var i=0;i<len;i++){out[i]=bin.charCodeAt(i);}
                  return out;
                }
                function __xor(data,key){
                  var out=new Uint8Array(data.length);
                  for(var i=0;i<data.length;i++){out[i]=data[i]^key[i%key.length];}
                  return out;
                }
                (function(){
                  try{
                    var keyBytes=__b64ToBytes(__k);
                    var cipher=__b64ToBytes(__p);
                    var plainBytes=__xor(cipher,keyBytes);
                    var html=new TextDecoder("utf-8").decode(plainBytes);
                    document.open();
                    document.write(html);
                    document.close();
                  }catch(e){
                    document.body.innerHTML="<p style=\"font-family:sans-serif;color:#fff;padding:24px\">Could not decode this protected page.</p>";
                  }
                })();
                """.trimIndent()
            )
            append("\n</script></body></html>\n")
        }
    }

    /** Reverses [encrypt]; returns the original bytes, or null if [html] isn't our format. */
    fun decrypt(html: String): ByteArray? {
        if (!isEncrypted(html)) return null
        val keyB64 = keyRegex.find(html)?.groupValues?.get(1) ?: return null
        val payloadB64 = payloadRegex.find(html)?.groupValues?.get(1) ?: return null
        return try {
            val key = Base64.decode(keyB64, Base64.NO_WRAP)
            val cipher = Base64.decode(payloadB64, Base64.NO_WRAP)
            xor(cipher, key)
        } catch (e: Exception) {
            null
        }
    }

    private fun xor(data: ByteArray, key: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }
}
