package com.example.jupjup

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

        val db = Firebase.firestore

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 화면을 그리기 전에 토큰부터 가져옴
                    TokenAwareScreen(db)
                }
            }
        }
    }

    @Composable
    fun TokenAwareScreen(db: FirebaseFirestore) {
        // 토큰 상태 관리
        var myToken by remember { mutableStateOf<String?>(null) }

        // 토큰 가져오기 - 앱이 켜지면 딱 한 번 실행
        LaunchedEffect(Unit) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FCM", "토큰 가져오기 실패", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("FCM", "내 기기 토큰: $token")
                myToken = token
            }
        }

        if (myToken != null) {
            // 토큰이 준비되면 화면을 보여줌
            KeywordScreen(db, myToken!!)
        } else {
            // 토큰을 가져오는 중이면 로딩 화면
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("알림 주소를 가져오는 중...", modifier = Modifier.padding(top = 50.dp))
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

            // 토큰이 잘 따졌는지 화면에 살짝 보여줌 (개발용)
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
                val data = hashMapOf(
                    "subscribers" to arrayListOf(token)
                )
                docRef.set(data)
            }
            Toast.makeText(this, "'$keyword' 알림 등록 완료!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "에러: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}