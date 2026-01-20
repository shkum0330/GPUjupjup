package com.example.jupjup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 알림을 누르고 들어왔는지 확인
        checkIntentForUrl(intent)

        val db = Firebase.firestore

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TokenAwareScreen(db)
                }
            }
        }
    }

    // 알림에 URL이 들어있으면 브라우저를 연다
    private fun checkIntentForUrl(intent: Intent?) {
        val url = intent?.getStringExtra("url")
        if (!url.isNullOrEmpty()) {
            Log.d("Jupjup", "알림 타고 들어옴! URL 이동: $url")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }
    }


    @Composable
    fun TokenAwareScreen(db: FirebaseFirestore) {
        var myToken by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    return@addOnCompleteListener
                }
                myToken = task.result
            }
        }

        if (myToken != null) {
            KeywordScreen(db, myToken!!)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("로딩 중...", modifier = Modifier.padding(top = 50.dp))
            }
        }
    }

    @Composable
    fun KeywordScreen(db: FirebaseFirestore, userToken: String) {
        var text by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "줍줍 설정", style = MaterialTheme.typography.headlineMedium)

            Text(
                text = "내 기기 ID: ${userToken.take(10)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(20.dp))

            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("키워드 입력 (예: 5070)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotEmpty()) {
                        saveKeywordToDb(db, text, userToken)
                        text = ""
                    }
                })
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (text.isNotEmpty()) {
                        saveKeywordToDb(db, text, userToken)
                        text = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("알림 등록하기")
            }
        }
    }

    private fun saveKeywordToDb(db: FirebaseFirestore, keyword: String, token: String) {
        val docRef = db.collection("keywords").document(keyword)

        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                docRef.update("subscribers", FieldValue.arrayUnion(token))
            } else {
                val data = hashMapOf("subscribers" to arrayListOf(token))
                docRef.set(data)
            }
            Toast.makeText(this, "'$keyword' 등록 완료", Toast.LENGTH_SHORT).show()
        }
    }
}