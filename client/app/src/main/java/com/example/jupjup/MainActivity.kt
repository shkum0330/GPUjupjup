package com.example.jupjup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
        checkIntentForUrl(intent)

        val db = Firebase.firestore

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TokenAwareScreen(db)
                }
            }
        }
    }

    private fun checkIntentForUrl(intent: Intent?) {
        val url = intent?.getStringExtra("url")
        if (!url.isNullOrEmpty()) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        }
    }

    @Composable
    fun TokenAwareScreen(db: FirebaseFirestore) {
        var myToken by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) myToken = task.result
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
        // 내 키워드 목록을 저장할 상태 변수
        var myKeywords by remember { mutableStateOf<List<String>>(emptyList()) }

        // firestore에서 내 키워드 목록을 가져오는 함수
        fun refreshKeywords() {
            db.collection("keywords")
                .whereArrayContains("subscribers", userToken) // 내 토큰이 있는 것만 검색
                .get()
                .addOnSuccessListener { result ->
                    myKeywords = result.documents.map { it.id } // 문서 ID(=키워드)만 뽑아냄
                }
        }

        // 화면이 처음 켜질 때 목록 한 번 불러오기
        LaunchedEffect(Unit) {
            refreshKeywords()
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "줍줍 설정", style = MaterialTheme.typography.headlineMedium)

            Text(
                text = "ID: ${userToken.take(5)}...",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 입력창 & 등록 버튼
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("키워드 (예: 4090)") },
                    modifier = Modifier.weight(1f).testTag("inputField"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotEmpty()) {
                            saveKeyword(db, text, userToken) { refreshKeywords() }
                            text = ""
                        }
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (text.isNotEmpty()) {
                            saveKeyword(db, text, userToken) { refreshKeywords() }
                            text = ""
                        }
                    },
                    modifier = Modifier.testTag("registButton")
                ) {
                    Text("등록")
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))

            // 등록된 키워드 목록 리스트
            Text(
                text = "내 알림 목록 (${myKeywords.size}개)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f), // 남은 공간 다 쓰기
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(myKeywords) { keyword ->
                    KeywordItem(keyword = keyword, onDelete = {
                        deleteKeyword(db, keyword, userToken) { refreshKeywords() }
                    })
                }
            }
        }
    }

    // 리스트의 각 아이템 디자인 (카드 형태)
    @Composable
    fun KeywordItem(keyword: String, onDelete: () -> Unit) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = keyword, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red)
                }
            }
        }
    }

    // 등록 함수 (등록 후 콜백 실행)
    private fun saveKeyword(db: FirebaseFirestore, keyword: String, token: String, onSuccess: () -> Unit) {
        val docRef = db.collection("keywords").document(keyword)
        docRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                docRef.update("subscribers", FieldValue.arrayUnion(token))
                    .addOnSuccessListener { onSuccess() }
            } else {
                val data = hashMapOf("subscribers" to arrayListOf(token))
                docRef.set(data).addOnSuccessListener { onSuccess() }
            }
            Toast.makeText(this, "'$keyword' 등록됨", Toast.LENGTH_SHORT).show()
        }
    }

    // 삭제 함수
    private fun deleteKeyword(db: FirebaseFirestore, keyword: String, token: String, onSuccess: () -> Unit) {
        val docRef = db.collection("keywords").document(keyword)

        // 내 토큰만 배열에서 쏙 뺌 (남들은 영향 없음)
        docRef.update("subscribers", FieldValue.arrayRemove(token))
            .addOnSuccessListener {
                Toast.makeText(this, "'$keyword' 삭제됨", Toast.LENGTH_SHORT).show()
                onSuccess() // 목록 새로고침
            }
    }
}