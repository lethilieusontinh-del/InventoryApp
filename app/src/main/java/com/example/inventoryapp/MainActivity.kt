package com.example.inventoryapp
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItems(): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)
}

@Database(entities = [Item::class], version = 1, exportSchema = false)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var Instance: InventoryDatabase? = null
        fun getDatabase(context: Context): InventoryDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, InventoryDatabase::class.java, "item_database")
                    .fallbackToDestructiveMigration()
                    .build().also { Instance = it }
            }
        }
    }
}

// ================= CHẠY ỨNG DỤNG =================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InventoryAppScreen()
                }
            }
        }
    }
}

// ================= TOÀN BỘ GIAO DIỆN APP =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAppScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Khởi tạo Database nhanh
    val database = remember { InventoryDatabase.getDatabase(context) }
    val itemDao = database.itemDao()

    // Tự động lắng nghe dữ liệu thay đổi real-time từ Room chuyển lên giao diện
    val itemList by itemDao.getAllItems().collectAsState(initial = emptyList())

    // Các biến quản lý form nhập liệu
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    // Biến tạm giữ món hàng khi người dùng bấm vào để Sửa
    var selectedItemForEdit by remember { mutableStateOf<Item?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory App - Quản Lý Kho", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF3F51B5)) // Màu xanh Indigo
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------------- Ô NHẬP THÊM HÀNG ----------------
            Text(text = "THÊM SẢN PHẨM MỚI", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
            Spacer(modifier = Modifier.height(12.dp))

            TextField(value = name, onValueChange = { name = it }, label = { Text("Tên sản phẩm") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Giá ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Số lượng") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isEmpty() || price.isEmpty() || quantity.isEmpty()) {
                        Toast.makeText(context, "Vui lòng nhập đủ thông tin!", Toast.LENGTH_SHORT).show()
                    } else {
                        val p = price.toDoubleOrNull() ?: 0.0
                        val q = quantity.toIntOrNull() ?: 0

                        // Chạy Coroutine lưu vào database dưới luồng phụ
                        coroutineScope.launch {
                            itemDao.insertItem(Item(name = name, price = p, quantity = q))
                            name = ""; price = ""; quantity = "" // Xóa trống form
                            Toast.makeText(context, "Đã thêm vào kho!", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
            ) {
                Text("Lưu Vào Kho Hàng (Room)")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // ---------------- DANH SÁCH HÀNG TRONG KHO ----------------
            Text(text = "DANH SÁCH TRONG KHO (${itemList.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(itemList) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedItemForEdit = item }, // Bấm vào món hàng để mở bảng Sửa
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(text = "Giá: $${item.price}  |  Số lượng: ${item.quantity}", color = Color.Gray)
                            }
                            // Nút xóa nhanh món hàng
                            IconButton(onClick = {
                                coroutineScope.launch { itemDao.deleteItem(item) }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Xóa", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            // ================= HỘP THOẠI SỬA SẢN PHẨM =================
            if (selectedItemForEdit != null) {
                var editName by remember { mutableStateOf(selectedItemForEdit!!.name) }
                var editPrice by remember { mutableStateOf(selectedItemForEdit!!.price.toString()) }
                var editQuantity by remember { mutableStateOf(selectedItemForEdit!!.quantity.toString()) }

                AlertDialog(
                    onDismissRequest = { selectedItemForEdit = null },
                    title = { Text("Cập nhật sản phẩm") },
                    text = {
                        Column {
                            TextField(value = editName, onValueChange = { editName = it }, label = { Text("Tên sản phẩm") })
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(value = editPrice, onValueChange = { editPrice = it }, label = { Text("Giá") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(value = editQuantity, onValueChange = { editQuantity = it }, label = { Text("Số lượng") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val p = editPrice.toDoubleOrNull() ?: 0.0
                                val q = editQuantity.toIntOrNull() ?: 0
                                val updated = Item(id = selectedItemForEdit!!.id, name = editName, price = p, quantity = q)

                                coroutineScope.launch {
                                    itemDao.updateItem(updated)
                                    selectedItemForEdit = null
                                    Toast.makeText(context, "Đã cập nhật!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) { Text("Cập Nhật") }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedItemForEdit = null }) { Text("Hủy") }
                    }
                )
            }
        }
    }
}